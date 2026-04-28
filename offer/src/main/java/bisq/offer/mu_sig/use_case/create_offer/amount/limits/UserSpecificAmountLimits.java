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
import bisq.common.observable.Observable;
import bisq.common.observable.ReadOnlyObservable;
import bisq.offer.Direction;

import java.util.Optional;

import static bisq.offer.mu_sig.use_case.create_offer.amount.limits.TradeAmountLimitUtils.toTradeAmountLimit;
import static com.google.common.base.Preconditions.checkNotNull;

public class UserSpecificAmountLimits {
    private static final long USER_SPECIFIC_LIMIT_IN_USD = 4000;

    private final MarketPriceService marketPriceService;
    protected final Observable<Optional<TradeAmount>> tradeAmountLimit = new Observable<>(Optional.empty());

    public UserSpecificAmountLimits(MarketPriceService marketPriceService) {
        this.marketPriceService = checkNotNull(marketPriceService, "marketPriceService must not be null");
    }

    //todo
    public static Fiat getUserSpecificLimitInUsd() {
        return Fiat.fromFaceValue(USER_SPECIFIC_LIMIT_IN_USD, "USD");
    }


    /* --------------------------------------------------------------------- */
    // Update
    /* --------------------------------------------------------------------- */

    public void update(Market market,
                       Direction displayDirection,
                       PriceQuote priceQuote) {
        checkNotNull(market, "market must not be null");
        checkNotNull(displayDirection, "displayDirection must not be null");
        checkNotNull(priceQuote, "priceQuote must not be null");

        Direction offerDirection = Direction.displayDirectionToOfferDirection(displayDirection, market);
        if (!market.isBtcFiatMarket() || offerDirection.isSell()) {
            tradeAmountLimit.set(Optional.empty());
            return;
        }

        Fiat userSpecificLimitInUsd = getUserSpecificLimitInUsd();
        TradeAmount limit = toTradeAmountLimit(marketPriceService, market, priceQuote, userSpecificLimitInUsd);
        tradeAmountLimit.set(Optional.of(limit));
    }


    /* --------------------------------------------------------------------- */
    // Getters
    /* --------------------------------------------------------------------- */

    public ReadOnlyObservable<Optional<TradeAmount>> tradeAmountLimitObservable() {
        return tradeAmountLimit;
    }

    public Optional<TradeAmount> getTradeAmountLimit() {
        return tradeAmountLimit.get();
    }
}
