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

package bisq.offer.mu_sig.use_case.create_offer.amount.limits;

import bisq.account.accounts.Account;
import bisq.account.payment_method.PaymentMethod;
import bisq.account.payment_method.PaymentRail;
import bisq.account.payment_method.fiat.FiatPaymentRail;
import bisq.common.monetary.Fiat;
import bisq.common.observable.Observable;
import bisq.common.observable.ReadOnlyObservable;
import com.google.common.collect.ImmutableMap;

import java.util.Comparator;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class PaymentMethodBasedAmountLimits {
    protected final Observable<Fiat> paymentRailBasedTradeLimitInUsd = new Observable<>();

    public PaymentMethodBasedAmountLimits() {
    }

    public void handleAccountByPaymentMethodChange(ImmutableMap<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethod) {
        checkNotNull(accountByPaymentMethod, "accountByPaymentMethod must not be null");
        Fiat limit = evaluateLimit(accountByPaymentMethod);
        setPaymentRailBasedTradeLimitInUsd(limit);
    }


    /* --------------------------------------------------------------------- */
    // Model
    /* --------------------------------------------------------------------- */

    void setPaymentRailBasedTradeLimitInUsd(Fiat paymentRailBasedTradeLimitInUsd) {
        this.paymentRailBasedTradeLimitInUsd.set(paymentRailBasedTradeLimitInUsd);
    }

    public ReadOnlyObservable<Fiat> paymentRailBasedTradeLimitInUsdObservable() {
        return paymentRailBasedTradeLimitInUsd;
    }

    public Fiat getPaymentRailBasedTradeLimitInUsd() {
        return paymentRailBasedTradeLimitInUsd.get();
    }


    /* --------------------------------------------------------------------- */
    // Static
    /* --------------------------------------------------------------------- */

    public  static Fiat evaluateLimit(Map<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethod) {
        return accountByPaymentMethod.values().stream()
                .map(Account::getPaymentMethod)
                .map(PaymentMethod::getPaymentRail)
                .map(PaymentRail.class::cast)
                .min(Comparator.comparing(PaymentMethodBasedAmountLimits::evaluateLimit))
                .map(PaymentMethodBasedAmountLimits::evaluateLimit)
                .orElse(AbsoluteAmountLimits.MAX_TRADE_AMOUNT_IN_USD);
    }

    public  static Fiat evaluateLimit(PaymentRail paymentRail) {
        Fiat maxTradeLimitByProtocol = AbsoluteAmountLimits.MAX_TRADE_AMOUNT_IN_USD;
        if (paymentRail instanceof FiatPaymentRail fiatPaymentRail) {
            switch (fiatPaymentRail.getChargebackRisk()) {
                case VERY_LOW -> {
                    return maxTradeLimitByProtocol;
                }
                case LOW -> {
                    return maxTradeLimitByProtocol.multiply(0.8);
                }
                case MEDIUM -> {
                    return maxTradeLimitByProtocol.multiply(0.65);
                }
                case MODERATE -> {
                    return maxTradeLimitByProtocol.multiply(0.5);
                }
                default -> {
                    return maxTradeLimitByProtocol.multiply(0d);
                }
            }
        } else {
            return maxTradeLimitByProtocol;
        }
    }
}
