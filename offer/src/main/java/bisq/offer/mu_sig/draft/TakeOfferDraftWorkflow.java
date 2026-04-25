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

package bisq.offer.mu_sig.draft;

import bisq.account.AccountService;
import bisq.account.accounts.Account;
import bisq.account.payment_method.PaymentMethod;
import bisq.account.payment_method.PaymentRail;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.Monetary;
import bisq.common.monetary.MonetaryRange;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.offer.mu_sig.MuSigOffer;
import bisq.offer.mu_sig.draft.dependencies.AccountsProvider;
import bisq.offer.mu_sig.draft.dependencies.DefaultAccountsProvider;
import bisq.offer.mu_sig.draft.dependencies.DefaultTakeOfferDraftCookieStore;
import bisq.offer.mu_sig.draft.dependencies.TakeOfferDraftCookieStore;
import bisq.settings.SettingsService;
import com.google.common.collect.ImmutableMap;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * User-facing workflow facade for creating an offer draft.
 * <p>
 * Design: exposes stable UI/API mutation methods and persistence side effects (cookies), while
 * delegating transition ordering and derived-state recalculation to {@link TakeOfferDraftStateEngine}
 * and isolated domain services.
 */
@Slf4j
public class TakeOfferDraftWorkflow extends OfferDraftWorkflow<TakeOfferDraft> {
    public static final Fiat DEFAULT_TRADE_AMOUNT_IN_USD = Fiat.fromFaceValue(500, "USD");

    private final TakeOfferDraftCookieStore cookieStore;
    private final AmountMappingService amountMappingService;
    private final PaymentMethodSelectionService paymentMethodSelectionService;
    private final TakeOfferDraftStateEngine stateEngine;
    @Delegate
    protected TakeOfferDraft takeOfferDraft;

    public enum PaymentMethodSelectionStatus {
        NO_ACCOUNT_AVAILABLE,
        SINGLE_ACCOUNT_SELECTED,
        ACCOUNT_SELECTION_REQUIRED
    }

    public record PaymentMethodSelectionResult(PaymentMethodSelectionStatus status,
                                               List<Account<?, ?>> accountsRequiringSelection) {
        public PaymentMethodSelectionResult {
            checkNotNull(status, "status must not be null");
            checkNotNull(accountsRequiringSelection, "accountsRequiringSelection must not be null");
            accountsRequiringSelection = List.copyOf(accountsRequiringSelection);
        }

        public static PaymentMethodSelectionResult noAccountAvailable() {
            return new PaymentMethodSelectionResult(PaymentMethodSelectionStatus.NO_ACCOUNT_AVAILABLE, List.of());
        }

        public static PaymentMethodSelectionResult singleAccountSelected() {
            return new PaymentMethodSelectionResult(PaymentMethodSelectionStatus.SINGLE_ACCOUNT_SELECTED, List.of());
        }

        public static PaymentMethodSelectionResult accountSelectionRequired(List<Account<?, ?>> accountsRequiringSelection) {
            checkNotNull(accountsRequiringSelection, "accountsRequiringSelection must not be null");
            return new PaymentMethodSelectionResult(PaymentMethodSelectionStatus.ACCOUNT_SELECTION_REQUIRED, accountsRequiringSelection);
        }
    }

    /* --------------------------------------------------------------------- */
    // Construction
    /* --------------------------------------------------------------------- */

    public TakeOfferDraftWorkflow(MarketPriceService marketPriceService,
                                  SettingsService settingsService,
                                  AccountService accountService) {
        this(marketPriceService,
                new DefaultTakeOfferDraftCookieStore(settingsService),
                new DefaultAccountsProvider(accountService));
    }

    TakeOfferDraftWorkflow(MarketPriceService marketPriceService,
                           TakeOfferDraftCookieStore cookieStore,
                           AccountsProvider accountsProvider) {
        super(new TakeOfferDraft());

        this.cookieStore = checkNotNull(cookieStore, "cookieStore must not be null");
        checkNotNull(accountsProvider, "accountsProvider must not be null");
        checkNotNull(marketPriceService, "marketPriceProvider must not be null");

        amountMappingService = new AmountMappingService();
        TakeOfferTradeAmountConstraintsService tradeAmountConstraintsService = new TakeOfferTradeAmountConstraintsService(marketPriceService);
        paymentMethodSelectionService = new PaymentMethodSelectionService(accountsProvider);

        takeOfferDraft = offerDraft;
        stateEngine = new TakeOfferDraftStateEngine(takeOfferDraft,
                marketPriceService,
                tradeAmountConstraintsService,
                amountMappingService,
                this::getSelectedPaymentRail,
                this::updatePaymentMethods,
                DEFAULT_TRADE_AMOUNT_IN_USD);
    }


    /* --------------------------------------------------------------------- */
    // Lifecycle
    /* --------------------------------------------------------------------- */

    public void initialize(MuSigOffer muSigOffer) {
        checkNotNull(muSigOffer, "muSigOffer must not be null");

        offerDraft.setOffer(muSigOffer);

        Market market = muSigOffer.getMarket();
        boolean useBaseCurrencyForAmountInput = cookieStore.getUseBaseCurrencyForAmountInput(market);

        stateEngine.initialize(muSigOffer, useBaseCurrencyForAmountInput);
    }


    /* --------------------------------------------------------------------- */
    // Amount input entry points
    /* --------------------------------------------------------------------- */

    public void setFixTradeAmountFromInputAmount(Monetary amount) {
        TradeAmount tradeAmount = stateEngine.toClampedTradeAmount(amount);
        setFixTradeAmount(tradeAmount);
    }

    public void setFixTradeAmountFromSliderValue(double sliderValue) {
        TradeAmount fixTradeAmount = checkNotNull(getFixTradeAmount(), "fixTradeAmount must not be null");
        TradeAmount tradeAmount = stateEngine.toTradeAmountFromSliderValue(fixTradeAmount, sliderValue);
        setFixTradeAmount(tradeAmount);
    }



    /* --------------------------------------------------------------------- */
    // Amount conversion
    /* --------------------------------------------------------------------- */

    public Monetary toInputAmount(TradeAmount tradeAmount, boolean includeUserSpecificTradeAmountLimit) {
        boolean useBaseCurrencyForAmountInput = getUseBaseCurrencyForAmountInput();
        TradeAmountRange limits = getClampLimits(includeUserSpecificTradeAmountLimit);
        return amountMappingService.toInputAmount(tradeAmount, limits, useBaseCurrencyForAmountInput);
    }

    public Monetary toPassiveAmount(TradeAmount tradeAmount, boolean includeUserSpecificTradeAmountLimit) {
        boolean useBaseCurrencyForAmountInput = getUseBaseCurrencyForAmountInput();
        TradeAmountRange limits = getClampLimits(includeUserSpecificTradeAmountLimit);
        return amountMappingService.toPassiveAmount(tradeAmount, limits, useBaseCurrencyForAmountInput);
    }


    /* --------------------------------------------------------------------- */
    // Mutation API
    /* --------------------------------------------------------------------- */

    // Core market/pricing state
    public void setMarket(Market market) {
        checkNotNull(market, "Market must not be null");
        if (market.equals(getMarket())) {
            return;
        }
        stateEngine.applyMarketChanged(market);
    }

    public void setUseBaseCurrencyForAmountInput(boolean value) {
        if (value == getUseBaseCurrencyForAmountInput()) {
            return;
        }

        Market market = getMarket();
        if (market == null) {
            offerDraft.setUseBaseCurrencyForAmountInput(value);
            return;
        }

        if (stateEngine.applyUseBaseCurrencyForAmountInputChanged(value)) {
            cookieStore.persistUseBaseCurrencyForAmountInput(market, value);
        }
    }

    // Amount state
    public void setFixTradeAmount(TradeAmount tradeAmount) {
        stateEngine.setFixTradeAmount(tradeAmount);
    }

    public void setTradeAmountLimits(TradeAmountRange tradeAmountRange) {
        checkNotNull(tradeAmountRange, "TradeAmountRange must not be null");
        offerDraft.setTradeAmountLimits(tradeAmountRange);
    }

    public void setUserSpecificTradeAmountLimit(Optional<TradeAmount> tradeAmount) {
        tradeAmount.ifPresent(amount -> checkNotNull(amount, "tradeAmount must not be null"));
        offerDraft.setUserSpecificTradeAmountLimit(tradeAmount);
    }

    public void setInputAmountLimits(MonetaryRange inputAmountLimits) {
        checkNotNull(inputAmountLimits, "inputAmountLimits must not be null");
        offerDraft.setInputAmountLimits(inputAmountLimits);
    }

    // Payment account state
    public void putAccountsByPaymentMethod(PaymentMethod<?> paymentMethod, List<Account<?, ?>> account) {
        checkNotNull(paymentMethod, "paymentMethod must not be null");
        checkNotNull(account, "account must not be null");
        takeOfferDraft.putAccountsByPaymentMethod(paymentMethod, account);
    }

    public void removeAccountsByPaymentMethod(PaymentMethod<?> paymentMethod) {
        offerDraft.removeAccountsByPaymentMethod(paymentMethod);
    }

    public void putAllAccountsByPaymentMethod(Map<PaymentMethod<?>, List<Account<?, ?>>> selectedAccountByPaymentMethod) {
        checkNotNull(selectedAccountByPaymentMethod, "selectedAccountByPaymentMethod must not be null");
        offerDraft.putAllAccountsByPaymentMethod(selectedAccountByPaymentMethod);
    }

    public void clearAccountsByPaymentMethod() {
        offerDraft.clearAccountsByPaymentMethod();
    }

    public void putSelectedAccountByPaymentMethod(PaymentMethod<?> paymentMethod, Account<?, ?> account) {
        checkNotNull(paymentMethod, "paymentMethod must not be null");
        checkNotNull(account, "account must not be null");
        putSelectedAccountByPaymentMethod(paymentMethod, account, true);
    }

    public void removeSelectedAccountByPaymentMethod(PaymentMethod<?> paymentMethod) {
        removeSelectedAccountByPaymentMethod(paymentMethod, true);
    }

    public void putAllSelectedAccountByPaymentMethod(Map<PaymentMethod<?>, Account<?, ?>> selectedAccountByPaymentMethod) {
        checkNotNull(selectedAccountByPaymentMethod, "selectedAccountByPaymentMethod must not be null");
        putAllSelectedAccountByPaymentMethod(selectedAccountByPaymentMethod, true);
    }

    public void clearSelectedAccountByPaymentMethod() {
        clearSelectedAccountByPaymentMethod(true);
    }

    public PaymentMethodSelectionResult onPaymentMethodSelected(PaymentMethod<?> paymentMethod) {
        checkNotNull(paymentMethod, "paymentMethod must not be null");

        PaymentMethodSelectionService.PaymentMethodAccountsSelection selection = paymentMethodSelectionService.findAccountsSelection(
                getAccountsByPaymentMethod(),
                paymentMethod);
        if (selection.accountToAutoSelect().isPresent()) {
            putSelectedAccountByPaymentMethod(paymentMethod, selection.accountToAutoSelect().get());
            return PaymentMethodSelectionResult.singleAccountSelected();
        }

        if (!selection.accountsRequiringSelection().isEmpty()) {
            return PaymentMethodSelectionResult.accountSelectionRequired(selection.accountsRequiringSelection());
        }

        return PaymentMethodSelectionResult.noAccountAvailable();
    }


    /* --------------------------------------------------------------------- */
    // Internal helpers
    /* --------------------------------------------------------------------- */

    /* --------------------------------------------------------------------- */
    // PaymentMethods
    /* --------------------------------------------------------------------- */

    private void updatePaymentMethods() {
        Market market = getMarket();
        PaymentMethodSelectionService.MarketAccounts marketAccounts = paymentMethodSelectionService.loadAccountsForMarket(market);
        List<Account<?, ?>> accountsForMarket = marketAccounts.accountsForMarket();
        Map<PaymentMethod<?>, List<Account<?, ?>>> map = marketAccounts.accountsByPaymentMethod();
        if (!getAccountsByPaymentMethod().equals(map)) {
            clearAccountsByPaymentMethod();
            putAllAccountsByPaymentMethod(map);
        }

        boolean selectedAccountsChanged = false;

        // Remove payment methods which are not present in the eligible accounts
        ImmutableMap<PaymentMethod<?>, Account<?, ?>> selectedAccountByPaymentMethod = getSelectedAccountByPaymentMethod();
        List<? extends PaymentMethod<?>> paymentMethodsToRemove = paymentMethodSelectionService.findSelectedPaymentMethodsToRemove(selectedAccountByPaymentMethod,
                accountsForMarket);
        if (!paymentMethodsToRemove.isEmpty()) {
            selectedAccountsChanged = true;
            paymentMethodsToRemove.forEach(paymentMethod -> removeSelectedAccountByPaymentMethod(paymentMethod, false));
        }

        // If we have only one, we pre-select
        Optional<Account<?, ?>> accountToAutoSelect = paymentMethodSelectionService.findAccountToAutoSelect(accountsForMarket,
                getSelectedAccountByPaymentMethod());
        if (accountToAutoSelect.isPresent()) {
            Account<?, ?> account = accountToAutoSelect.get();
            selectedAccountsChanged |= putSelectedAccountByPaymentMethod(account.getPaymentMethod(), account, false);
        }

        if (selectedAccountsChanged) {
            stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }
    }

    private boolean putSelectedAccountByPaymentMethod(PaymentMethod<?> paymentMethod,
                                                      Account<?, ?> account,
                                                      boolean recalculateTradeAmountConstraints) {
        Account<?, ?> existing = getSelectedAccountByPaymentMethod().get(paymentMethod);
        if (account.equals(existing)) {
            return false;
        }
        takeOfferDraft.putSelectedAccountByPaymentMethod(paymentMethod, account);
        if (recalculateTradeAmountConstraints) {
            stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }
        return true;
    }

    private boolean removeSelectedAccountByPaymentMethod(PaymentMethod<?> paymentMethod,
                                                         boolean recalculateTradeAmountConstraints) {
        if (!getSelectedAccountByPaymentMethod().containsKey(paymentMethod)) {
            return false;
        }
        offerDraft.removeSelectedAccountByPaymentMethod(paymentMethod);
        if (recalculateTradeAmountConstraints) {
            stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }
        return true;
    }

    private boolean putAllSelectedAccountByPaymentMethod(Map<PaymentMethod<?>, Account<?, ?>> selectedAccountByPaymentMethod,
                                                         boolean recalculateTradeAmountConstraints) {
        if (selectedAccountByPaymentMethod.isEmpty()) {
            return clearSelectedAccountByPaymentMethod(recalculateTradeAmountConstraints);
        }
        ImmutableMap<PaymentMethod<?>, Account<?, ?>> existing = getSelectedAccountByPaymentMethod();
        boolean changed = selectedAccountByPaymentMethod.entrySet().stream()
                .anyMatch(entry -> !entry.getValue().equals(existing.get(entry.getKey())));
        if (!changed) {
            return false;
        }
        offerDraft.putAllSelectedAccountByPaymentMethod(selectedAccountByPaymentMethod);
        if (recalculateTradeAmountConstraints) {
            stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }
        return true;
    }

    private boolean clearSelectedAccountByPaymentMethod(boolean recalculateTradeAmountConstraints) {
        if (getSelectedAccountByPaymentMethod().isEmpty()) {
            return false;
        }
        offerDraft.clearSelectedAccountByPaymentMethod();
        if (recalculateTradeAmountConstraints) {
            stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }
        return true;
    }

    private PaymentRail getSelectedPaymentRail() {
        return paymentMethodSelectionService.findMostRestrictiveSelectedPaymentRail(getSelectedAccountByPaymentMethod());
    }

    private TradeAmountRange getClampLimits(boolean includeUserSpecificTradeAmountLimit) {
        return stateEngine.getClampLimits(includeUserSpecificTradeAmountLimit);
    }
}
