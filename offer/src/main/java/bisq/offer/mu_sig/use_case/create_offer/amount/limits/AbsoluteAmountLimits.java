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

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.common.observable.Observable;
import bisq.common.observable.ReadOnlyObservable;

import static com.google.common.base.Preconditions.checkNotNull;

public class AbsoluteAmountLimits {
    public static final Fiat MIN_TRADE_AMOUNT_IN_USD = Fiat.fromFaceValue(10, "USD");
    public static final Fiat MAX_TRADE_AMOUNT_IN_USD = Fiat.fromFaceValue(10000, "USD");

    protected final Observable<TradeAmountRange> tradeAmountLimits = new Observable<>();

    private final MarketPriceService marketPriceService;

    public AbsoluteAmountLimits(MarketPriceService marketPriceService) {
        checkNotNull(marketPriceService, "marketPriceService must not be null");
        this.marketPriceService = marketPriceService;
    }


    /* --------------------------------------------------------------------- */
    // Update
    /* --------------------------------------------------------------------- */

    public void update(Market market,
                       PriceQuote priceQuote) {
        checkNotNull(market, "market must not be null");
        checkNotNull(priceQuote, "priceQuote must not be null");

        TradeAmount minTradeAmount = TradeAmountLimitUtils.toTradeAmountLimit(marketPriceService,
                market,
                priceQuote,
                MIN_TRADE_AMOUNT_IN_USD);
        TradeAmount maxTradeAmount = TradeAmountLimitUtils.toTradeAmountLimit(marketPriceService,
                market,
                priceQuote,
                MAX_TRADE_AMOUNT_IN_USD);
        tradeAmountLimits.set(new TradeAmountRange(minTradeAmount, maxTradeAmount));
    }

    /* --------------------------------------------------------------------- */
    // Getters
    /* --------------------------------------------------------------------- */

    public ReadOnlyObservable<TradeAmountRange> amountLimitsObservable() {
        return tradeAmountLimits;
    }

    public TradeAmountRange getAmountLimits() {
        return tradeAmountLimits.get();
    }
}
