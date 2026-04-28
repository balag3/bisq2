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
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.observable.Observable;
import bisq.common.observable.ReadOnlyObservable;
import com.google.common.collect.ImmutableMap;

import java.util.Comparator;
import java.util.Map;

import static bisq.offer.mu_sig.use_case.create_offer.amount.limits.TradeAmountLimitUtils.toTradeAmountLimit;
import static com.google.common.base.Preconditions.checkNotNull;

public class PaymentMethodBasedAmountLimits {
    protected final Observable<Fiat> amountLimitInUsd = new Observable<>(AbsoluteAmountLimits.MAX_TRADE_AMOUNT_IN_USD); //todo remove
    protected final Observable<TradeAmount> tradeAmountLimit = new Observable<>();

    private final MarketPriceService marketPriceService;

    public PaymentMethodBasedAmountLimits(MarketPriceService marketPriceService) {
        this.marketPriceService = checkNotNull(marketPriceService, "marketPriceService must not be null");
    }
    /* --------------------------------------------------------------------- */
    // Update
    /* --------------------------------------------------------------------- */

    public void update(Market market,
                       PriceQuote priceQuote,
                       ImmutableMap<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethod) {
        checkNotNull(market, "market must not be null");
        checkNotNull(priceQuote, "offerPriceQuote must not be null");
        checkNotNull(accountByPaymentMethod, "accountByPaymentMethod must not be null");
        Fiat limitInUsd = evaluateLimitInUsd(accountByPaymentMethod);
        amountLimitInUsd.set(limitInUsd);

        TradeAmount limit = toTradeAmountLimit(marketPriceService, market, priceQuote, limitInUsd);
        tradeAmountLimit.set(limit);
    }


    /* --------------------------------------------------------------------- */
    // Getters
    /* --------------------------------------------------------------------- */

    public ReadOnlyObservable<TradeAmount> tradeAmountLimitObservable() {
        return tradeAmountLimit;
    }

    public TradeAmount getTradeAmountLimit() {
        return tradeAmountLimit.get();
    }

    //todo remove
    public ReadOnlyObservable<Fiat> amountLimitInUsdObservable() {
        return amountLimitInUsd;
    }

    //todo remove
    public Fiat getAmountLimitInUsd() {
        return amountLimitInUsd.get();
    }


    /* --------------------------------------------------------------------- */
    // Static
    /* --------------------------------------------------------------------- */

    public static Fiat evaluateLimitInUsd(Map<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethod) {
        return accountByPaymentMethod.values().stream()
                .map(Account::getPaymentMethod)
                .map(PaymentMethod::getPaymentRail)
                .map(PaymentRail.class::cast)
                .min(Comparator.comparing(PaymentMethodBasedAmountLimits::evaluateLimitInUsd))
                .map(PaymentMethodBasedAmountLimits::evaluateLimitInUsd)
                .orElse(AbsoluteAmountLimits.MAX_TRADE_AMOUNT_IN_USD);
    }

    public static Fiat evaluateLimitInUsd(PaymentRail paymentRail) {
        Fiat maxTradeLimitByProtocol = AbsoluteAmountLimits.MAX_TRADE_AMOUNT_IN_USD;
        if (paymentRail instanceof FiatPaymentRail fiatPaymentRail) {
            return switch (fiatPaymentRail.getChargebackRisk()) {
                case VERY_LOW -> maxTradeLimitByProtocol;
                case LOW -> maxTradeLimitByProtocol.multiply(0.8);
                case MEDIUM -> maxTradeLimitByProtocol.multiply(0.65);
                case MODERATE -> maxTradeLimitByProtocol.multiply(0.5);
            };
        }
        return maxTradeLimitByProtocol;
    }
}
