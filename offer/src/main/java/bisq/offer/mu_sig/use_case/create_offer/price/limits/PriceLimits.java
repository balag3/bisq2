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

package bisq.offer.mu_sig.use_case.create_offer.price.limits;

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.application.UseCase;
import bisq.common.market.Market;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.PriceQuoteRange;
import bisq.common.observable.Observable;
import bisq.common.observable.ReadOnlyObservable;
import bisq.common.util.MathUtils;
import bisq.offer.mu_sig.use_case.create_offer.market.CreateOfferMarketUseCase;
import bisq.offer.price.PriceUtil;

import static com.google.common.base.Preconditions.checkNotNull;

public class PriceLimits extends UseCase {
    public static final double MIN_PERCENTAGE_FROM_MARKET_PRICE = -0.1;
    public static final double MAX_PERCENTAGE_FROM_MARKET_PRICE = 0.5;

    protected final Observable<PriceQuoteRange> tradeAmountLimits = new Observable<>();

    private final MarketPriceService marketPriceService;
    private final CreateOfferMarketUseCase marketService;

    public PriceLimits(MarketPriceService marketPriceService, CreateOfferMarketUseCase marketService) {
        checkNotNull(marketPriceService, "marketPriceService must not be null");
        checkNotNull(marketService, "marketService must not be null");
        this.marketService = marketService;
        this.marketPriceService = marketPriceService;
    }

    public void initialize() {
        pin(marketService.marketObservable().addObserver(market -> {
            if (market != null) {
                PriceQuote minTradeAmount = percentageToPriceQuote(marketPriceService,
                        market,
                        MIN_PERCENTAGE_FROM_MARKET_PRICE);
                PriceQuote maxTradeAmount = percentageToPriceQuote(marketPriceService,
                        market,
                        MAX_PERCENTAGE_FROM_MARKET_PRICE);
                tradeAmountLimits.set(new PriceQuoteRange(minTradeAmount, maxTradeAmount));
            }
        }));
    }


    public PriceQuote clamp(PriceQuote priceQuote) {
        return priceQuote.clamp(getAmountLimits());
    }

    public double clamp(double pricePercentage) {
        return MathUtils.bounded(MIN_PERCENTAGE_FROM_MARKET_PRICE, MAX_PERCENTAGE_FROM_MARKET_PRICE, pricePercentage);
    }


    /* --------------------------------------------------------------------- */
    // Getters
    /* --------------------------------------------------------------------- */

    public ReadOnlyObservable<PriceQuoteRange> amountLimitsObservable() {
        return tradeAmountLimits;
    }

    public PriceQuoteRange getAmountLimits() {
        return tradeAmountLimits.get();
    }

    static PriceQuote percentageToPriceQuote(MarketPriceService marketPriceService,
                                             Market market,
                                             double pricePercentage) {
        PriceQuote marketPriceQuote = marketPriceService.getMarketPriceQuoteOrThrow(market);
        return PriceUtil.fromMarketPriceMarkup(marketPriceQuote, pricePercentage);
    }
}
