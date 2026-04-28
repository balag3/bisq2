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

package bisq.offer.mu_sig.use_case.create_offer.amount;

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.MonetaryRange;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.offer.amount.spec.AmountSpec;
import bisq.offer.amount.spec.AmountSpecFactory;
import bisq.offer.mu_sig.use_case.AmountMappingService;
import bisq.offer.mu_sig.use_case.AmountUtils;
import bisq.offer.mu_sig.use_case.TradeAmountLimits;
import bisq.offer.mu_sig.use_case.create_offer.amount.limits.AmountLimits;
import bisq.offer.mu_sig.use_case.dependencies.CreateOfferDraftCookieStore;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class CreateOfferAmountUseCase {
    public static final Fiat DEFAULT_TRADE_AMOUNT_IN_USD = Fiat.fromFaceValue(500, "USD");
    @Getter(AccessLevel.PACKAGE)
    @Delegate
    private final CreateOfferAmountModel model;
    private final MarketPriceService marketPriceService;
    private final CreateOfferDraftCookieStore cookieStore;
    private final AmountLimits amountLimits;
    private final AmountMappingService amountMappingService;

    public CreateOfferAmountUseCase(MarketPriceService marketPriceService,
                                    CreateOfferDraftCookieStore cookieStore,
                                    AmountLimits amountLimits,
                                    AmountMappingService amountMappingService) {
        this.marketPriceService = marketPriceService;
        this.cookieStore = cookieStore;
        this.amountLimits = amountLimits;
        this.amountMappingService = amountMappingService;
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

    public TradeAmount clampTradeAmount(TradeAmount tradeAmount, boolean includeUserSpecificTradeAmountLimit) {
        checkNotNull(tradeAmount, "tradeAmount must not be null");
        TradeAmountRange limits = getClampLimits(includeUserSpecificTradeAmountLimit);
        return TradeAmountLimits.clampTradeAmount(limits, tradeAmount);
    }

    public TradeAmountRange getClampLimits(boolean includeUserSpecificTradeAmountLimit) {
        TradeAmountRange tradeAmountLimits = getTradeAmountLimits();
        Optional<TradeAmount> userSpecificTradeAmountLimit = getUserSpecificTradeAmountLimit();
        return TradeAmountLimits.getClampLimits(tradeAmountLimits,
                userSpecificTradeAmountLimit,
                includeUserSpecificTradeAmountLimit);
    }

    public boolean isDerivedStateInitialized() {
        return getTradeAmountLimits() != null && getInputAmountLimits() != null;
    }

    public void clampCurrentTradeAmounts(boolean includeUserSpecificTradeAmountLimit) {
        if (getFixTradeAmount() != null) {
            setFixTradeAmount(clampTradeAmount(getFixTradeAmount(), includeUserSpecificTradeAmountLimit));
        }
        if (getMinTradeAmount() != null) {
            setMinTradeAmount(clampTradeAmount(getMinTradeAmount(), includeUserSpecificTradeAmountLimit));
        }
        if (getMaxTradeAmount() != null) {
            setMaxTradeAmount(clampTradeAmount(getMaxTradeAmount(), includeUserSpecificTradeAmountLimit));
        }
    }

    public void setUseBaseCurrencyForAmountInput(boolean value) {
        model.setUseBaseCurrencyForAmountInput(value);
    }

    public void setUseRangeAmount(boolean useRangeAmount) {
        model.setUseRangeAmount(useRangeAmount);
    }


    public  void setFixTradeAmount(TradeAmount tradeAmount) {
        checkNotNull(tradeAmount, "tradeAmount must not be null");
        TradeAmount valueToSet = isDerivedStateInitialized() ? clampTradeAmount(tradeAmount, true) : tradeAmount;
        model.setFixTradeAmount(valueToSet);
        if (isDerivedStateInitialized()) {
            updateFixAmountSliderValue();
        }
    }

    public  void setMinTradeAmount(TradeAmount tradeAmount) {
        checkNotNull(tradeAmount, "tradeAmount must not be null");
        TradeAmount valueToSet = isDerivedStateInitialized() ? clampTradeAmount(tradeAmount, true) : tradeAmount;
        model.setMinTradeAmount(valueToSet);
        if (isDerivedStateInitialized()) {
            updateMinAmountSliderValue();
        }
    }

    public  void setMaxTradeAmount(TradeAmount tradeAmount) {
        checkNotNull(tradeAmount, "tradeAmount must not be null");
        TradeAmount valueToSet = isDerivedStateInitialized() ? clampTradeAmount(tradeAmount, true) : tradeAmount;
        model.setMaxTradeAmount(valueToSet);
        if (isDerivedStateInitialized()) {
            updateMaxAmountSliderValue();
        }
    }

    public void setUserSpecificTradeAmountLimit(Optional<TradeAmount> userSpecificTradeAmountLimit) {
        model.setUserSpecificTradeAmountLimit(userSpecificTradeAmountLimit);
    }

    public void setUserSpecificTradeAmountLimitAsSliderValue(Optional<Double> sliderValue) {
        sliderValue.ifPresent(value -> checkArgument(value >= 0 && value <= 1, "value must be in range of 0 and 1"));
        model.setUserSpecificTradeAmountLimitAsSliderValue(sliderValue);
    }

    public void setTradeAmountLimits(TradeAmountRange tradeAmountLimits) {
        model.setTradeAmountLimits(tradeAmountLimits);
    }

    public void setInputAmountLimits(MonetaryRange inputAmountLimits) {
        model.setInputAmountLimits(inputAmountLimits);
    }

    public void setFixAmountSliderValue(double sliderValue) {
        checkArgument(sliderValue >= 0 && sliderValue <= 1, "sliderValue must be in range of 0 and 1");
        model.setFixAmountSliderValue(sliderValue);
    }

    public void setMinAmountSliderValue(double sliderValue) {
        checkArgument(sliderValue >= 0 && sliderValue <= 1, "sliderValue must be in range of 0 and 1");
        model.setMinAmountSliderValue(sliderValue);
    }

    public void setMaxAmountSliderValue(double sliderValue) {
        checkArgument(sliderValue >= 0 && sliderValue <= 1, "sliderValue must be in range of 0 and 1");
        model.setMaxAmountSliderValue(sliderValue);
    }

    public AmountSpec createAndGetAmountSpec(Market market) {
        checkNotNull(market, "market must not be null");
        boolean isBtcFiatMarket = market.isBtcFiatMarket();
        boolean useRangeAmount = getUseRangeAmount();
        return AmountSpecFactory.createAmountSpec(isBtcFiatMarket,
                useRangeAmount,
                getMinTradeAmount(),
                getMaxTradeAmount(),
                getFixTradeAmount());
    }

    public void updateAmountSliderValues() {
        if (getFixTradeAmount() != null) {
            updateFixAmountSliderValue();
        }
        if (getMinTradeAmount() != null) {
            updateMinAmountSliderValue();
        }
        if (getMaxTradeAmount() != null) {
            updateMaxAmountSliderValue();
        }
    }

    public void updateFixAmountSliderValue() {
        double sliderValue = toSliderValue(getFixTradeAmount());
        setFixAmountSliderValue(sliderValue);
    }

    public void updateMinAmountSliderValue() {
        double sliderValue = toSliderValue(getMinTradeAmount());
        setMinAmountSliderValue(sliderValue);
    }

    public void updateMaxAmountSliderValue() {
        double sliderValue = toSliderValue(getMaxTradeAmount());
        setMaxAmountSliderValue(sliderValue);
    }

    public double toSliderValue(TradeAmount tradeAmount) {
        TradeAmountRange limits = getClampLimits(true);
        MonetaryRange inputAmountLimits = checkNotNull(getInputAmountLimits(), "inputAmountLimits must not be null");
        return amountMappingService.toSliderValue(tradeAmount,
                limits,
                inputAmountLimits,
                getUseBaseCurrencyForAmountInput());
    }
}
