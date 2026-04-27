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

package bisq.offer.mu_sig.draft.create_offer;

import bisq.account.payment_method.PaymentRail;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.market.MarketRepository;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.offer.Direction;
import bisq.offer.mu_sig.MuSigTradeAmountLimits;
import bisq.offer.mu_sig.draft.TradeAmountConstraints;
import bisq.offer.mu_sig.draft.TradeAmountLimits;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Computes effective trade-amount constraints for the current draft context.
 * <p>
 * Design: converts market, pricing, direction, and payment-rail inputs into a single immutable
 * {@link TradeAmountConstraints} result so callers do not duplicate protocol limit logic.
 */
class CreateOfferTradeAmountConstraintsService {
    private final MarketPriceService marketPriceService;

    CreateOfferTradeAmountConstraintsService(MarketPriceService marketPriceService) {
        this.marketPriceService = checkNotNull(marketPriceService, "marketPriceService must not be null");
    }

    /* --------------------------------------------------------------------- */
    // Constraint computation
    /* --------------------------------------------------------------------- */

    TradeAmountConstraints compute(Market market,
                                   Direction direction,
                                   PriceQuote offerPriceQuote,
                                   PriceQuote marketPriceQuote,
                                   PaymentRail paymentRail) {
        checkNotNull(market, "market must not be null");
        checkNotNull(direction, "direction must not be null");
        checkNotNull(offerPriceQuote, "offerPriceQuote must not be null");
        checkNotNull(marketPriceQuote, "marketPriceQuote must not be null");

        Market usdBitcoinMarket = MarketRepository.getUSDBitcoinMarket();
        PriceQuote btcUsdPriceQuote = marketPriceService.getMarketPriceQuoteOrThrow(usdBitcoinMarket);

        Fiat maxTradeLimitInUsd = MuSigTradeAmountLimits.getMaxTradeLimitInUsd(paymentRail);
        Fiat minTradeAmountInUsd = MuSigTradeAmountLimits.MIN_TRADE_AMOUNT_IN_USD;
        TradeAmountRange tradeAmountLimits = TradeAmountLimits.toTradeAmountLimits(market,
                offerPriceQuote,
                btcUsdPriceQuote,
                marketPriceQuote,
                minTradeAmountInUsd,
                maxTradeLimitInUsd);

        if (direction.isSell()) {
            return new TradeAmountConstraints(tradeAmountLimits, Optional.empty());
        } else {
            TradeAmount userSpecificTradeAmountLimit = TradeAmountLimits.toUserSpecificTradeAmountLimit(market,
                    offerPriceQuote,
                    btcUsdPriceQuote,
                    marketPriceQuote,
                    TradeAmountLimits.getUserSpecificLimitInUsdAmount());
            return new TradeAmountConstraints(tradeAmountLimits, Optional.of(userSpecificTradeAmountLimit));
        }
    }
}
