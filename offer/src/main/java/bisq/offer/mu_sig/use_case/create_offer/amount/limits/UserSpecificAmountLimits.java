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
import bisq.common.application.UseCase;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.observable.Observable;
import bisq.common.observable.ReadOnlyObservable;
import bisq.offer.Direction;
import bisq.offer.mu_sig.use_case.create_offer.direction.CreateOfferDirectionUseCase;
import bisq.offer.mu_sig.use_case.create_offer.market.CreateOfferMarketUseCase;
import bisq.offer.mu_sig.use_case.create_offer.price.CreateOfferPriceUseCase;

import java.util.Optional;

import static bisq.offer.mu_sig.use_case.create_offer.amount.limits.TradeAmountLimitUtils.toTradeAmountLimit;
import static com.google.common.base.Preconditions.checkNotNull;

public class UserSpecificAmountLimits extends UseCase {
    private static final long USER_SPECIFIC_LIMIT_IN_USD = 4000;

    private final MarketPriceService marketPriceService;
    private final CreateOfferMarketUseCase marketService;
    private final CreateOfferDirectionUseCase directionService;
    private final CreateOfferPriceUseCase priceService;
    private final Observable<Optional<TradeAmount>> tradeAmountLimit = new Observable<>(Optional.empty());

    public UserSpecificAmountLimits(MarketPriceService marketPriceService,
                                    CreateOfferMarketUseCase marketService,
                                    CreateOfferDirectionUseCase directionService,
                                    CreateOfferPriceUseCase priceService) {
        this.marketPriceService = checkNotNull(marketPriceService, "marketPriceService must not be null");
        this.marketService = marketService;
        this.directionService = directionService;
        this.priceService = priceService;
    }

    @Override
    public void initialize() {
        pin(marketService.addMarketListener(market ->
                update(market,
                        directionService.getDisplayDirection(),
                        priceService.getPriceQuote())));
        pin(directionService.addDisplayDirectionListener(direction ->
                update(marketService.getMarket(),
                        direction,
                        priceService.getPriceQuote())));
        pin(priceService.addPriceQuoteListener(priceQuote ->
                update(marketService.getMarket(),
                        directionService.getDisplayDirection(),
                        priceQuote)));
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
        if (dependenciesValid(market, displayDirection, priceQuote)) {
            if (market.isBtcFiatMarket() && displayDirection.isBuy()) {
                Fiat userSpecificLimitInUsd = getUserSpecificLimitInUsd();
                TradeAmount limit = toTradeAmountLimit(marketPriceService, market, priceQuote, userSpecificLimitInUsd);
                tradeAmountLimit.set(Optional.of(limit));
            } else {
                tradeAmountLimit.set(Optional.empty());
            }
        }
    }


    private static boolean dependenciesValid(Market market, Direction displayDirection, PriceQuote priceQuote) {
        return market != null &&
                displayDirection != null &&
                priceQuote != null &&
                market.equals(priceQuote.getMarket());
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
