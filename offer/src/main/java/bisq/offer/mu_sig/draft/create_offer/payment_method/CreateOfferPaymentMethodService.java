/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.offer.mu_sig.draft.create_offer.payment_method;

import bisq.account.accounts.Account;
import bisq.account.payment_method.PaymentMethod;
import bisq.account.payment_method.PaymentRail;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.observable.Pin;
import bisq.offer.mu_sig.MuSigTradeAmountLimits;
import bisq.offer.mu_sig.draft.dependencies.AccountsProvider;
import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class CreateOfferPaymentMethodService {
    public static final int MAX_NUM_PAYMENT_METHODS = 4;

    @Getter(AccessLevel.PACKAGE)
    @Delegate
    private final CreateOfferPaymentMethodModel model;
    private final Set<Consumer<Map.Entry<PaymentMethod<?>, Account<?, ?>>>> methodAccountEntryListeners = new CopyOnWriteArraySet<>();
    private final Set<Pin> pins = new HashSet<>();
    private final AccountsProvider accountsProvider;

    public CreateOfferPaymentMethodService(AccountsProvider accountsProvider) {
        this.accountsProvider = accountsProvider;
        this.model = new CreateOfferPaymentMethodModel();
    }

    public void initialize(Market market) {
        checkNotNull(market, "market must not be null");

        // If selectedAccountByPaymentMethod changes we update the payment rail-based trade limit in USD
        pins.add(model.accountByPaymentMethodObservable().addObserver(() -> {
            ImmutableMap<PaymentMethod<?>, Account<?, ?>> selectedAccountByPaymentMethod = model.getAccountByPaymentMethod();
            Fiat paymentRailBasedTradeLimitInUsd = getMaxTradeLimitInUsd(selectedAccountByPaymentMethod);
            model.setPaymentRailBasedTradeLimitInUsd(paymentRailBasedTradeLimitInUsd);
        }));
    }

    public void dispose() {
        pins.forEach(Pin::unbind);
        pins.clear();
    }


    /* --------------------------------------------------------------------- */
    // User interaction
    /* --------------------------------------------------------------------- */

    public PaymentMethodSelectionResult evaluatePaymentMethodSelectionResult(PaymentMethod<?> paymentMethod) {
        checkNotNull(paymentMethod, "paymentMethod must not be null");

        Map<PaymentMethod<?>, List<Account<?, ?>>> accountsByPaymentMethod = getAccountsByPaymentMethod();
        PaymentMethodAccountSelection selection = findAccountsSelection(
                accountsByPaymentMethod,
                paymentMethod);
        if (selection.accountToAutoSelect().isPresent()) {
            Account<?, ?> account = selection.accountToAutoSelect().get();
            Map.Entry<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethodEntry = Map.entry(paymentMethod, account);
            return PaymentMethodSelectionResult.singleAccountSelected(accountByPaymentMethodEntry);
        }

        List<Account<?, ?>> accountsRequiringSelection = selection.accountsRequiringSelection();
        if (!accountsRequiringSelection.isEmpty()) {
            return PaymentMethodSelectionResult.accountSelectionRequired(accountsRequiringSelection);
        }

        return PaymentMethodSelectionResult.noAccountAvailable();
    }

    public void onAddAccountByPaymentMethodEntry(Map.Entry<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethodEntry) {
        checkNotNull(accountByPaymentMethodEntry, "accountByPaymentMethodEntry must not be null");
        checkArgument(accountByPaymentMethodEntry.getValue().getPaymentMethod().equals(accountByPaymentMethodEntry.getKey()),
                "PaymentMethod must be the same as in account");
        model.addAccountByPaymentMethodEntry(accountByPaymentMethodEntry);
        methodAccountEntryListeners.forEach(listener -> listener.accept(accountByPaymentMethodEntry));
    }

    public void onDeselectPaymentMethod(PaymentMethod<?> paymentMethod) {
        model.removeAccountByPaymentMethod(paymentMethod);
    }


    /* --------------------------------------------------------------------- */
    // Handle changes from dependencies
    /* --------------------------------------------------------------------- */

    // If market changes we update the accounts by paymentMethod map, maybe remove the selected accountByPaymentMethodEntry 
    // and maybe pre-select the accountByPaymentMethodEntry if only one account is present.
    public void handleMarketChanged(Market market) {
        checkNotNull(market, "market must not be null");
        MarketAccounts marketAccounts = loadAccountsForMarket(market, accountsProvider);
        List<Account<?, ?>> accountsForMarket = marketAccounts.accountsForMarket();
        Map<PaymentMethod<?>, List<Account<?, ?>>> map = marketAccounts.accountsByPaymentMethod();
        if (!getAccountsByPaymentMethod().equals(map)) {
            model.clearAccountsByPaymentMethod();
            model.putAllAccountsByPaymentMethod(map);
        }

        // Remove payment methods which are not present in the eligible accounts
        ImmutableMap<PaymentMethod<?>, Account<?, ?>> selectedAccountByPaymentMethod = getAccountByPaymentMethod();
        findSelectedPaymentMethodsToRemove(selectedAccountByPaymentMethod, accountsForMarket)
                .forEach(model::removeAccountByPaymentMethod);

        // If we have only one, we pre-select
        selectedAccountByPaymentMethod = getAccountByPaymentMethod(); // read it again as it might have changed from remove call
        findAccountToAutoSelect(accountsForMarket, selectedAccountByPaymentMethod)
                .ifPresent(account -> {
                    PaymentMethod<?> paymentMethod = account.getPaymentMethod();
                    Map.Entry<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethodEntry = Map.entry(paymentMethod, account);
                    model.addAccountByPaymentMethodEntry(accountByPaymentMethodEntry);
                });
    }

    public void addMethodAccountEntryListener(Consumer<Map.Entry<PaymentMethod<?>, Account<?, ?>>> listener) {
        methodAccountEntryListeners.add(listener);
    }

    public void removeMethodAccountEntryListener(Consumer<Map.Entry<PaymentMethod<?>, Account<?, ?>>> listener) {
        methodAccountEntryListeners.remove(listener);
    }


    /* --------------------------------------------------------------------- */
    // Static helpers
    /* --------------------------------------------------------------------- */

    static Fiat getMaxTradeLimitInUsd(Map<PaymentMethod<?>, Account<?, ?>> selectedAccountByPaymentMethod) {
        checkNotNull(selectedAccountByPaymentMethod, "selectedAccountByPaymentMethod must not be null");
        return selectedAccountByPaymentMethod.values().stream()
                .map(Account::getPaymentMethod)
                .map(PaymentMethod::getPaymentRail)
                .map(PaymentRail.class::cast)
                .min(Comparator.comparing(MuSigTradeAmountLimits::getMaxTradeLimitInUsd))
                .map(MuSigTradeAmountLimits::getMaxTradeLimitInUsd)
                .orElse(MuSigTradeAmountLimits.MAX_TRADE_AMOUNT_IN_USD);
    }


    /* --------------------------------------------------------------------- */
    // Account helpers
    /* --------------------------------------------------------------------- */

    static MarketAccounts loadAccountsForMarket(Market market, AccountsProvider accountsProvider) {
        checkNotNull(market, "market must not be null");
        List<Account<?, ?>> accountsForMarket = checkNotNull(accountsProvider.findAccountsForMarket(market),
                "accountsForMarket must not be null");
        Map<PaymentMethod<?>, List<Account<?, ?>>> accountsByPaymentMethod = accountsForMarket.stream()
                .collect(Collectors.groupingBy(Account::getPaymentMethod, Collectors.toList()));
        return new MarketAccounts(accountsForMarket, accountsByPaymentMethod);
    }

    static Optional<Account<?, ?>> findAccountToAutoSelect(List<Account<?, ?>> accountsForMarket,
                                                           ImmutableMap<PaymentMethod<?>, Account<?, ?>> selectedAccountByPaymentMethod) {
        checkNotNull(accountsForMarket, "accountsForMarket must not be null");
        checkNotNull(selectedAccountByPaymentMethod, "selectedAccountByPaymentMethod must not be null");

        if (accountsForMarket.size() != 1) {
            return Optional.empty();
        }

        Account<?, ?> account = accountsForMarket.getFirst();
        Account<?, ?> existing = selectedAccountByPaymentMethod.get(account.getPaymentMethod());
        return account.equals(existing) ? Optional.empty() : Optional.of(account);
    }

    static PaymentMethodAccountSelection findAccountsSelection(Map<PaymentMethod<?>, List<Account<?, ?>>> accountsByPaymentMethod,
                                                               PaymentMethod<?> paymentMethod) {
        checkNotNull(accountsByPaymentMethod, "accountsByPaymentMethod must not be null");
        checkNotNull(paymentMethod, "paymentMethod must not be null");

        List<Account<?, ?>> accountsForPaymentMethod = accountsByPaymentMethod.get(paymentMethod);
        if (accountsForPaymentMethod == null || accountsForPaymentMethod.isEmpty()) {
            return PaymentMethodAccountSelection.noAccount();
        }

        if (accountsForPaymentMethod.size() == 1) {
            return PaymentMethodAccountSelection.singleAccount(accountsForPaymentMethod.getFirst());
        }

        return PaymentMethodAccountSelection.multipleAccounts(accountsForPaymentMethod);
    }


    static List<? extends PaymentMethod<?>> findSelectedPaymentMethodsToRemove(ImmutableMap<PaymentMethod<?>, Account<?, ?>> selectedAccountByPaymentMethod,
                                                                               List<Account<?, ?>> accountsForMarket) {
        checkNotNull(selectedAccountByPaymentMethod, "selectedAccountByPaymentMethod must not be null");
        checkNotNull(accountsForMarket, "accountsForMarket must not be null");
        return selectedAccountByPaymentMethod.entrySet().stream()
                .filter(entry -> !accountsForMarket.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
