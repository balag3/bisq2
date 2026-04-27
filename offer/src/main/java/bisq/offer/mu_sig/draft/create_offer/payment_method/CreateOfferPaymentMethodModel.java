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
import bisq.common.monetary.Fiat;
import bisq.common.observable.Observable;
import bisq.common.observable.ReadOnlyObservable;
import bisq.common.observable.map.ObservableHashMap;
import bisq.common.observable.map.ReadOnlyObservableMap;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

public class CreateOfferPaymentMethodModel implements CreateOfferPaymentMethodReadOnlyModel {
    private final ObservableHashMap<PaymentMethod<?>, List<Account<?, ?>>> accountsByPaymentMethod = new ObservableHashMap<>();
    private final ObservableHashMap<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethod = new ObservableHashMap<>();

    protected final Observable<Fiat> paymentRailBasedTradeLimitInUsd = new Observable<>();

    //Fiat paymentRailBasedTradeLimitInUsd = MuSigTradeAmountLimits.getMaxTradeLimitInUsd(paymentRail);
    public CreateOfferPaymentMethodModel() {
    }

    void setPaymentRailBasedTradeLimitInUsd(Fiat paymentRailBasedTradeLimitInUsd) {
        this.paymentRailBasedTradeLimitInUsd.set(paymentRailBasedTradeLimitInUsd);
    }

    @Override
    public ReadOnlyObservable<Fiat> paymentRailBasedTradeLimitInUsdObservable() {
        return paymentRailBasedTradeLimitInUsd;
    }

    @Override
    public Fiat getPaymentRailBasedTradeLimitInUsd() {
        return paymentRailBasedTradeLimitInUsd.get();
    }

    /* --------------------------------------------------------------------- */
    // accountsByPaymentMethod
    /* --------------------------------------------------------------------- */

    void clearAccountsByPaymentMethod() {
        accountsByPaymentMethod.clear();
    }

    void putAccountsByPaymentMethod(PaymentMethod<?> paymentMethod, List<Account<?, ?>> account) {
        accountsByPaymentMethod.put(paymentMethod, account);
    }

    void removeAccountsByPaymentMethod(PaymentMethod<?> paymentMethod) {
        accountsByPaymentMethod.remove(paymentMethod);
    }

    void putAllAccountsByPaymentMethod(Map<PaymentMethod<?>, List<Account<?, ?>>> accountsByPaymentMethod) {
        this.accountsByPaymentMethod.putAll(accountsByPaymentMethod);
    }

    @Override
    public ReadOnlyObservableMap<PaymentMethod<?>, List<Account<?, ?>>> accountsByPaymentMethodObservable() {
        return accountsByPaymentMethod;
    }

    @Override
    public ImmutableMap<PaymentMethod<?>, List<Account<?, ?>>> getAccountsByPaymentMethod() {
        return ImmutableMap.copyOf(accountsByPaymentMethod);
    }

    /* --------------------------------------------------------------------- */
    // selectedAccountByPaymentMethod
    /* --------------------------------------------------------------------- */

    void clearAccountByPaymentMethod() {
        accountByPaymentMethod.clear();
    }

    void addAccountByPaymentMethodEntry(Map.Entry<PaymentMethod<?>, Account<?, ?>> entry) {
        accountByPaymentMethod.put(entry.getKey(), entry.getValue());
    }

    void removeAccountByPaymentMethodEntry(Map.Entry<PaymentMethod<?>, Account<?, ?>> entry) {
        accountByPaymentMethod.remove(entry.getKey(), entry.getValue());
    }

    void putAccountByPaymentMethod(PaymentMethod<?> paymentMethod, Account<?, ?> account) {
        accountByPaymentMethod.put(paymentMethod, account);
    }

    void removeAccountByPaymentMethod(PaymentMethod<?> paymentMethod) {
        accountByPaymentMethod.remove(paymentMethod);
    }

    void putAllAccountByPaymentMethod(Map<PaymentMethod<?>, Account<?, ?>> map) {
        this.accountByPaymentMethod.putAll(map);
    }

    @Override
    public ReadOnlyObservableMap<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethodObservable() {
        return accountByPaymentMethod;
    }

    @Override
    public ImmutableMap<PaymentMethod<?>, Account<?, ?>> getAccountByPaymentMethod() {
        return ImmutableMap.copyOf(accountByPaymentMethod);
    }
}
