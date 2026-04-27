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

package bisq.offer.mu_sig.draft.create_offer.amount;

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.MonetaryRange;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.offer.amount.spec.AmountSpec;
import bisq.offer.amount.spec.AmountSpecFactory;
import bisq.offer.mu_sig.draft.AmountUtils;
import bisq.offer.mu_sig.draft.TradeAmountLimits;
import bisq.offer.mu_sig.draft.dependencies.CreateOfferDraftCookieStore;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

public class CreateOfferAmountService {
    public static final Fiat DEFAULT_TRADE_AMOUNT_IN_USD = Fiat.fromFaceValue(500, "USD");
    @Getter(AccessLevel.PACKAGE)
    @Delegate
    private final CreateOfferAmountModel model;
    private final MarketPriceService marketPriceService;
    private final CreateOfferDraftCookieStore cookieStore;

    public CreateOfferAmountService(MarketPriceService marketPriceService, CreateOfferDraftCookieStore cookieStore) {
        this.marketPriceService = marketPriceService;
        this.cookieStore = cookieStore;
        this.model = new CreateOfferAmountModel();
    }

    public void initialize(Market market) {
        boolean useBaseCurrencyForAmountInput = cookieStore.getUseBaseCurrencyForAmountInput(market);
        setUseBaseCurrencyForAmountInput(useBaseCurrencyForAmountInput);

        boolean useRangeAmount = cookieStore.getUseRangeAmount();
        setUseRangeAmount(useRangeAmount);

        // Not clamped yet as we do not have established the trade amount limits
        TradeAmount defaultTradeAmount = AmountUtils.getTradeAmountFromUsd(marketPriceService, market, DEFAULT_TRADE_AMOUNT_IN_USD);
        setFixTradeAmount(defaultTradeAmount);
        setMinTradeAmount(defaultTradeAmount);
        setMaxTradeAmount(defaultTradeAmount);
    }

    private TradeAmount clampTradeAmount(TradeAmount tradeAmount, boolean includeUserSpecificTradeAmountLimit) {
        TradeAmountRange limits = getClampLimits(includeUserSpecificTradeAmountLimit);
        return TradeAmountLimits.clampTradeAmount(limits, tradeAmount);
    }

    private TradeAmountRange getClampLimits(boolean includeUserSpecificTradeAmountLimit) {
        TradeAmountRange tradeAmountLimits = getTradeAmountLimits();
        Optional<TradeAmount> userSpecificTradeAmountLimit = getUserSpecificTradeAmountLimit();
        return TradeAmountLimits.getClampLimits(tradeAmountLimits,
                userSpecificTradeAmountLimit,
                includeUserSpecificTradeAmountLimit);
    }

    public void setUseBaseCurrencyForAmountInput(boolean value) {
        model.setUseBaseCurrencyForAmountInput(value);
    }

    public void setUseRangeAmount(boolean useRangeAmount) {
        model.setUseRangeAmount(useRangeAmount);
    }

    public void setFixTradeAmount(TradeAmount fixTradeAmount) {
        model.setFixTradeAmount(fixTradeAmount);
    }

    public void setMinTradeAmount(TradeAmount minTradeAmount) {
        model.setMinTradeAmount(minTradeAmount);
    }

    public void setMaxTradeAmount(TradeAmount maxTradeAmount) {
        model.setMaxTradeAmount(maxTradeAmount);
    }

    public void setUserSpecificTradeAmountLimit(Optional<TradeAmount> userSpecificTradeAmountLimit) {
        model.setUserSpecificTradeAmountLimit(userSpecificTradeAmountLimit);
    }

    public void setUserSpecificTradeAmountLimitAsSliderValue(Optional<Double> sliderValue) {
        model.setUserSpecificTradeAmountLimitAsSliderValue(sliderValue);
    }

    public void setTradeAmountLimits(TradeAmountRange tradeAmountLimits) {
        model.setTradeAmountLimits(tradeAmountLimits);
    }

    public void setInputAmountLimits(MonetaryRange inputAmountLimits) {
        model.setInputAmountLimits(inputAmountLimits);
    }

    public void setFixAmountSliderValue(double sliderValue) {
        model.setFixAmountSliderValue(sliderValue);
    }

    public void setMinAmountSliderValue(double sliderValue) {
        model.setMinAmountSliderValue(sliderValue);
    }

    public void setMaxAmountSliderValue(double sliderValue) {
        model.setMaxAmountSliderValue(sliderValue);
    }

    public AmountSpec getAmountSpec(Market market) {
        checkNotNull(market, "market must not be null");
        boolean isBtcFiatMarket = market.isBtcFiatMarket();
        boolean useRangeAmount = getUseRangeAmount();
        return AmountSpecFactory.createAmountSpec(isBtcFiatMarket,
                useRangeAmount,
                getMinTradeAmount(),
                getMaxTradeAmount(),
                getFixTradeAmount());
    }
}
