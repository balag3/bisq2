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

package bisq.offer.mu_sig.use_case.take_offer;

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.market.MarketRepository;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountConversion;
import bisq.common.monetary.TradeAmountFactory;
import bisq.common.monetary.TradeAmountRange;
import bisq.offer.Direction;
import bisq.offer.amount.spec.AmountSpec;
import bisq.offer.amount.spec.AmountSpecUtil;
import bisq.offer.amount.spec.FixedAmountSpec;
import bisq.offer.amount.spec.RangeAmountSpec;
import bisq.offer.mu_sig.use_case.TradeAmountConstraints;
import bisq.offer.mu_sig.use_case.TradeAmountLimits;
import bisq.offer.mu_sig.use_case.create_offer.amount.limits.UserSpecificAmountLimits;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Computes effective trade-amount constraints for the current draft context.
 * <p>
 * Design: converts market, pricing, direction, and payment-rail inputs into a single immutable
 * {@link TradeAmountConstraints} result so callers do not duplicate protocol limit logic.
 */
@Slf4j
class TakeOfferTradeAmountConstraintsService {
    private final MarketPriceService marketPriceService;

    TakeOfferTradeAmountConstraintsService(MarketPriceService marketPriceService) {
        this.marketPriceService = checkNotNull(marketPriceService, "marketPriceService must not be null");
    }

    /* --------------------------------------------------------------------- */
    // Constraint computation
    /* --------------------------------------------------------------------- */

    TradeAmountConstraints compute(Market market,
                                   Direction takersDirection,
                                   AmountSpec amountSpec,
                                   PriceQuote offerPriceQuote,
                                   PriceQuote marketPriceQuote,
                                   Fiat maxTradeLimitInUsd) {
        checkNotNull(market, "market must not be null");
        checkNotNull(takersDirection, "takersDirection must not be null");
        checkNotNull(amountSpec, "amountSpec must not be null");
        checkNotNull(offerPriceQuote, "offerPriceQuote must not be null");
        checkNotNull(marketPriceQuote, "marketPriceQuote must not be null");
        checkNotNull(maxTradeLimitInUsd, "maxTradeLimitInUsd must not be null");

        TradeAmountRange tradeAmountRange;
        Market usdBitcoinMarket = MarketRepository.getUSDBitcoinMarket();

        PriceQuote btcUsdPriceQuote = marketPriceService.getMarketPriceQuoteOrThrow(usdBitcoinMarket);
        if (amountSpec instanceof RangeAmountSpec rangeAmountSpec) {
            tradeAmountRange = AmountSpecUtil.toTradeAmountRange(rangeAmountSpec, offerPriceQuote);
        } else if (amountSpec instanceof FixedAmountSpec fixedAmountSpec) {
            TradeAmount tradeAmount = AmountSpecUtil.toTradeAmount(fixedAmountSpec, offerPriceQuote);
            tradeAmountRange = new TradeAmountRange(tradeAmount, tradeAmount);
        } else {
            throw new IllegalArgumentException("Unsupported amount spec type: " + amountSpec.getClass().getSimpleName());
        }

        TradeAmount maxTradeLimit = TradeAmountConversion.toTradeAmount(MarketRepository.getUSDBitcoinMarket(),
                marketPriceQuote,
                maxTradeLimitInUsd);
        long maxTradeLimitAsBtcValue = maxTradeLimit.getBaseSideAmount().getValue();
        boolean isBtcFiatMarket = market.isBtcFiatMarket();
        long tradeAmountRangeMaxAsBtcValue;
        long tradeAmountRangeMinAsBtcValue;
        if (isBtcFiatMarket) {
            tradeAmountRangeMaxAsBtcValue = tradeAmountRange.getMax().getBaseSideAmount().getValue();
            tradeAmountRangeMinAsBtcValue = tradeAmountRange.getMin().getBaseSideAmount().getValue();
        } else {
            tradeAmountRangeMaxAsBtcValue = tradeAmountRange.getMax().getQuoteSideAmount().getValue();
            tradeAmountRangeMinAsBtcValue = tradeAmountRange.getMin().getQuoteSideAmount().getValue();
        }

        if (tradeAmountRangeMaxAsBtcValue > maxTradeLimitAsBtcValue) {
            if (maxTradeLimitAsBtcValue > tradeAmountRangeMinAsBtcValue) {
                TradeAmount newMax = isBtcFiatMarket
                        ? TradeAmountFactory.fromBaseSideAmount(maxTradeLimitAsBtcValue, marketPriceQuote)
                        : TradeAmountFactory.fromQuoteSideAmount(maxTradeLimitAsBtcValue, marketPriceQuote);
                tradeAmountRange = new TradeAmountRange(tradeAmountRange.getMin(), newMax);
            } else {
                // Only in the branch where strict clamping is impossible (limit <= min) we allow a small tolerance.
                Fiat maxTradeLimitInUsdWithTolerance = maxTradeLimitInUsd.multiply(1.05);
                TradeAmount maxTradeLimitWithTolerance = TradeAmountConversion.toTradeAmount(MarketRepository.getUSDBitcoinMarket(),
                        marketPriceQuote,
                        maxTradeLimitInUsdWithTolerance);
                long maxTradeLimitWithToleranceAsBtcValue = maxTradeLimitWithTolerance.getBaseSideAmount().getValue();
                if (tradeAmountRangeMaxAsBtcValue > maxTradeLimitWithToleranceAsBtcValue) {
                    throw new IllegalStateException("The offers max trade amount exceeds the maximum allowed limit derived from the payment method.");
                }
            }
        }

        if (takersDirection.isSell()) {
            return new TradeAmountConstraints(tradeAmountRange, Optional.empty());
        } else {
            TradeAmount userSpecificTradeAmountLimit = TradeAmountLimits.toUserSpecificTradeAmountLimit(market,
                    offerPriceQuote,
                    btcUsdPriceQuote,
                    marketPriceQuote,
                    UserSpecificAmountLimits.getUserSpecificLimitInUsd());
            return new TradeAmountConstraints(tradeAmountRange, Optional.of(userSpecificTradeAmountLimit));
        }
    }
}
