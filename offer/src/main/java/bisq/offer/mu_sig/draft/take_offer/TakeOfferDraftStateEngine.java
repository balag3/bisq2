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

package bisq.offer.mu_sig.draft.take_offer;

import bisq.account.payment_method.PaymentRail;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.Monetary;
import bisq.common.monetary.MonetaryRange;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.offer.Direction;
import bisq.offer.amount.spec.AmountSpec;
import bisq.offer.mu_sig.MuSigOffer;
import bisq.offer.mu_sig.MuSigTradeAmountLimits;
import bisq.offer.mu_sig.draft.AmountMappingService;
import bisq.offer.mu_sig.draft.AmountUtils;
import bisq.offer.mu_sig.draft.TradeAmountConstraints;
import bisq.offer.mu_sig.draft.TradeAmountLimits;
import bisq.offer.price.PriceUtil;
import bisq.offer.price.spec.PriceSpec;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Internal state-transition engine for {@link TakeOfferDraft}.
 * <p>
 * Design: this package-local component applies market/direction/price/input-mode transitions in a
 * deterministic order, recomputes derived constraints, and keeps draft amount fields/clamp state
 * consistent. The workflow remains a user-facing facade and persistence coordinator.
 */
@Slf4j
class TakeOfferDraftStateEngine {
    private final TakeOfferDraft offerDraft;
    private final MarketPriceService marketPriceService;
    private final TakeOfferTradeAmountConstraintsService tradeAmountConstraintsService;
    private final AmountMappingService amountMappingService;
    private final Supplier<PaymentRail> selectedPaymentRailSupplier;
    private final Runnable updatePaymentMethodsHandler;
    private final Fiat defaultTradeAmountInUsd;

    /* --------------------------------------------------------------------- */
    // Construction
    /* --------------------------------------------------------------------- */

    TakeOfferDraftStateEngine(TakeOfferDraft offerDraft,
                              MarketPriceService marketPriceService,
                              TakeOfferTradeAmountConstraintsService tradeAmountConstraintsService,
                              AmountMappingService amountMappingService,
                              Supplier<PaymentRail> selectedPaymentRailSupplier,
                              Runnable updatePaymentMethodsHandler,
                              Fiat defaultTradeAmountInUsd) {
        this.offerDraft = checkNotNull(offerDraft, "offerDraft must not be null");
        this.marketPriceService = checkNotNull(marketPriceService, "marketPriceService must not be null");
        this.tradeAmountConstraintsService = checkNotNull(tradeAmountConstraintsService, "tradeAmountConstraintsService must not be null");
        this.amountMappingService = checkNotNull(amountMappingService, "amountMappingService must not be null");
        this.selectedPaymentRailSupplier = checkNotNull(selectedPaymentRailSupplier, "selectedPaymentRailSupplier must not be null");
        this.updatePaymentMethodsHandler = checkNotNull(updatePaymentMethodsHandler, "updatePaymentMethodsHandler must not be null");
        this.defaultTradeAmountInUsd = checkNotNull(defaultTradeAmountInUsd, "defaultTradeAmountInUsd must not be null");
    }

    /* --------------------------------------------------------------------- */
    // State transitions
    /* --------------------------------------------------------------------- */

    void initialize(MuSigOffer muSigOffer,
                    boolean useBaseCurrencyForAmountInput) {
        checkNotNull(muSigOffer, "muSigOffer must not be null");

        Market market = muSigOffer.getMarket();
        Direction takersDirection = offerDraft.getOffer().getTakersDirection();

        // Price
        PriceSpec priceSpec = muSigOffer.getPriceSpec();
        PriceQuote priceQuote = PriceUtil.getQuoteOrThrow(marketPriceService, priceSpec, market);
        offerDraft.setPriceQuote(priceQuote);

        // Amount
        offerDraft.setUseBaseCurrencyForAmountInput(useBaseCurrencyForAmountInput);
        PriceQuote marketPriceQuote = marketPriceService.getMarketPriceQuoteOrThrow(market);
        AmountSpec amountSpec = muSigOffer.getAmountSpec();

        Fiat maxTradeLimitInUsd = evaluateMaxTradeLimitInUsd();
        TradeAmountConstraints tradeAmountConstraints = tradeAmountConstraintsService.compute(market,
                takersDirection,
                amountSpec,
                priceQuote,
                marketPriceQuote,
                maxTradeLimitInUsd);
        applyTradeAmountConstraints(tradeAmountConstraints);

        TradeAmount defaultTradeAmount = AmountUtils.getTradeAmountFromUsd(marketPriceService, market, defaultTradeAmountInUsd);
        TradeAmount clampedDefaultTradeAmount = clampTradeAmount(defaultTradeAmount, true);
        offerDraft.setFixTradeAmount(clampedDefaultTradeAmount);

        updateUserSpecificTradeAmountLimitAsSliderValue(takersDirection, offerDraft.getUserSpecificTradeAmountLimit());
        updateAmountSliderValues();
        updatePaymentMethodsHandler.run();
    }

    void applyMarketChanged(Market market) {
        checkNotNull(market, "market must not be null");
        // offerDraft.setMarket(market);
        if (!isDerivedStateInitialized() || offerDraft.getOffer().getTakersDirection() == null) {
            return;
        }

        Direction direction = offerDraft.getOffer().getTakersDirection();
        PriceQuote marketPriceQuote = marketPriceService.getMarketPriceQuoteOrThrow(market);
        // offerDraft.setPriceQuote(marketPriceQuote);
        PriceQuote priceQuote = offerDraft.getPriceQuote();
        AmountSpec amountSpec = offerDraft.getAmountSpec();
        Fiat maxTradeLimitInUsd = evaluateMaxTradeLimitInUsd();
        TradeAmountConstraints tradeAmountConstraints = tradeAmountConstraintsService.compute(market,
                direction,
                amountSpec,
                priceQuote,
                marketPriceQuote,
                maxTradeLimitInUsd);
        applyTradeAmountConstraints(tradeAmountConstraints);

        TradeAmount defaultTradeAmount = AmountUtils.getTradeAmountFromUsd(marketPriceService, market, defaultTradeAmountInUsd);
        TradeAmount clampedDefaultTradeAmount = clampTradeAmount(defaultTradeAmount, true);
        offerDraft.setFixTradeAmount(clampedDefaultTradeAmount);

        updateUserSpecificTradeAmountLimitAsSliderValue(direction, offerDraft.getUserSpecificTradeAmountLimit());
        updateAmountSliderValues();
        updatePaymentMethodsHandler.run();
    }

    boolean applyDirectionChanged(Direction direction) {
        checkNotNull(direction, "direction must not be null");
        // offerDraft.setDirection(direction);
        if (!hasPricingContext()) {
            return false;
        }

        Market market = offerDraft.getMarket();
        PriceQuote offerPriceQuote = offerDraft.getPriceQuote();
        AmountSpec amountSpec = offerDraft.getAmountSpec();
        PriceQuote marketPriceQuote = marketPriceService.getMarketPriceQuoteOrThrow(market);
        Fiat maxTradeLimitInUsd = evaluateMaxTradeLimitInUsd();
        TradeAmountConstraints tradeAmountConstraints = tradeAmountConstraintsService.compute(market,
                direction,
                amountSpec,
                offerPriceQuote,
                marketPriceQuote,
                maxTradeLimitInUsd);
        applyTradeAmountConstraints(tradeAmountConstraints);

        updateUserSpecificTradeAmountLimitAsSliderValue(direction, offerDraft.getUserSpecificTradeAmountLimit());
        updateAmountSliderValues();
        return true;
    }

    boolean applyUseBaseCurrencyForAmountInputChanged(boolean useBaseCurrencyForAmountInput) {
        offerDraft.setUseBaseCurrencyForAmountInput(useBaseCurrencyForAmountInput);
        Direction direction = offerDraft.getOffer().getTakersDirection();
        if (!isDerivedStateInitialized() || direction == null) {
            return false;
        }

        updateInputAmountLimits(offerDraft.getTradeAmountLimits());
        updateUserSpecificTradeAmountLimitAsSliderValue(direction, offerDraft.getUserSpecificTradeAmountLimit());
        updateAmountSliderValues();
        return true;
    }

    /* --------------------------------------------------------------------- */
    // Amount writes
    /* --------------------------------------------------------------------- */

    void setFixTradeAmount(TradeAmount tradeAmount) {
        checkNotNull(tradeAmount, "tradeAmount must not be null");
        TradeAmount valueToSet = isDerivedStateInitialized() ? clampTradeAmount(tradeAmount, true) : tradeAmount;
        offerDraft.setFixTradeAmount(valueToSet);
        if (isDerivedStateInitialized()) {
            updateFixAmountSliderValue();
        }
    }

    void recalculateTradeAmountConstraintsForSelectedPaymentRail() {
        if (!hasPricingContext()) {
            return;
        }

        Market market = offerDraft.getMarket();
        Direction direction = offerDraft.getOffer().getTakersDirection();
        AmountSpec amountSpec = offerDraft.getAmountSpec();
        PriceQuote offerPriceQuote = offerDraft.getPriceQuote();
        PriceQuote marketPriceQuote = marketPriceService.getMarketPriceQuoteOrThrow(market);
        Fiat maxTradeLimitInUsd = evaluateMaxTradeLimitInUsd();
        TradeAmountConstraints tradeAmountConstraints = tradeAmountConstraintsService.compute(market,
                direction,
                amountSpec,
                offerPriceQuote,
                marketPriceQuote,
                maxTradeLimitInUsd);
        applyTradeAmountConstraints(tradeAmountConstraints);

        if (offerDraft.getFixTradeAmount() != null) {
            offerDraft.setFixTradeAmount(clampTradeAmount(offerDraft.getFixTradeAmount(), true));
        }

        updateUserSpecificTradeAmountLimitAsSliderValue(direction, offerDraft.getUserSpecificTradeAmountLimit());
        updateAmountSliderValues();
    }

    private Fiat evaluateMaxTradeLimitInUsd() {
        // Initially, the selected payment rail us null, and we use the MAX_TRADE_AMOUNT_IN_USD
        PaymentRail selectedPaymentRail = getSelectedPaymentRail();
        return selectedPaymentRail != null
                ? MuSigTradeAmountLimits.getMaxTradeLimitInUsd(selectedPaymentRail)
                : MuSigTradeAmountLimits.MAX_TRADE_AMOUNT_IN_USD;
    }

    /* --------------------------------------------------------------------- */
    // Package scope helpers used by workflow facade
    /* --------------------------------------------------------------------- */

    TradeAmount toClampedTradeAmount(Monetary amount) {
        checkNotNull(amount, "amount must not be null");
        Market market = checkNotNull(offerDraft.getMarket(), "market must not be null");
        PriceQuote priceQuote = checkNotNull(offerDraft.getPriceQuote(), "priceQuote must not be null");
        TradeAmountRange limits = getClampLimits(true);
        return amountMappingService.toTradeAmountFromInputAmount(market, priceQuote, amount, limits);
    }

    TradeAmount toTradeAmountFromSliderValue(TradeAmount tradeAmount, double sliderValue) {
        checkNotNull(tradeAmount, "tradeAmount must not be null");
        Market market = checkNotNull(offerDraft.getMarket(), "market must not be null");
        PriceQuote priceQuote = checkNotNull(offerDraft.getPriceQuote(), "priceQuote must not be null");
        TradeAmountRange limits = getClampLimits(true);
        MonetaryRange inputAmountLimits = checkNotNull(offerDraft.getInputAmountLimits(), "inputAmountLimits must not be null");
        return amountMappingService.toTradeAmountFromSliderValue(market,
                priceQuote,
                tradeAmount,
                limits,
                inputAmountLimits,
                offerDraft.getUseBaseCurrencyForAmountInput(),
                sliderValue);
    }

    TradeAmountRange getClampLimits(boolean includeUserSpecificTradeAmountLimit) {
        TradeAmountRange tradeAmountLimits = offerDraft.getTradeAmountLimits();
        Optional<TradeAmount> userSpecificTradeAmountLimit = offerDraft.getUserSpecificTradeAmountLimit();
        return TradeAmountLimits.getClampLimits(tradeAmountLimits,
                userSpecificTradeAmountLimit,
                includeUserSpecificTradeAmountLimit);
    }

    boolean isDerivedStateInitialized() {
        return offerDraft.getTradeAmountLimits() != null && offerDraft.getInputAmountLimits() != null;
    }

    /* --------------------------------------------------------------------- */
    // Internal recalculation helpers
    /* --------------------------------------------------------------------- */

    private boolean hasPricingContext() {
        return offerDraft.getMarket() != null
                && offerDraft.getOffer().getTakersDirection() != null
                && offerDraft.getPriceQuote() != null
                && isDerivedStateInitialized();
    }

    private void applyTradeAmountConstraints(TradeAmountConstraints tradeAmountConstraints) {
        offerDraft.setTradeAmountLimits(tradeAmountConstraints.tradeAmountLimits());
        offerDraft.setUserSpecificTradeAmountLimit(tradeAmountConstraints.userSpecificTradeAmountLimit());
        updateInputAmountLimits(tradeAmountConstraints.tradeAmountLimits());
    }

    private void updateInputAmountLimits(TradeAmountRange tradeAmountLimits) {
        checkNotNull(tradeAmountLimits, "tradeAmountLimits must not be null");
        MonetaryRange inputAmountLimits = amountMappingService.toInputAmountLimits(tradeAmountLimits,
                offerDraft.getUseBaseCurrencyForAmountInput());
        offerDraft.setInputAmountLimits(inputAmountLimits);
    }

    private void updateUserSpecificTradeAmountLimitAsSliderValue(Direction direction,
                                                                 Optional<TradeAmount> userSpecificTradeAmountLimit) {
        if (direction.isBuy() && userSpecificTradeAmountLimit.isPresent() && offerDraft.getInputAmountLimits() != null) {
            double sliderValue = toSliderValue(userSpecificTradeAmountLimit.get());
            setUserSpecificTradeAmountLimitAsSliderValue(Optional.of(sliderValue));
        } else {
            setUserSpecificTradeAmountLimitAsSliderValue(Optional.empty());
        }
    }

    private TradeAmount toUpdatedPassiveAmount(Market market,
                                               PriceQuote priceQuote,
                                               TradeAmount tradeAmount,
                                               TradeAmountRange oldClampLimits,
                                               TradeAmountRange newClampLimits) {
        return amountMappingService.toUpdatedPassiveAmount(market,
                priceQuote,
                tradeAmount,
                oldClampLimits,
                newClampLimits,
                offerDraft.getUseBaseCurrencyForAmountInput());
    }

    private double toSliderValue(TradeAmount tradeAmount) {
        TradeAmountRange limits = getClampLimits(true);
        MonetaryRange inputAmountLimits = checkNotNull(offerDraft.getInputAmountLimits(), "inputAmountLimits must not be null");
        return amountMappingService.toSliderValue(tradeAmount,
                limits,
                inputAmountLimits,
                offerDraft.getUseBaseCurrencyForAmountInput());
    }

    /* --------------------------------------------------------------------- */
    // Internal slider helpers
    /* --------------------------------------------------------------------- */

    private void updateAmountSliderValues() {
        if (offerDraft.getFixTradeAmount() != null) {
            updateFixAmountSliderValue();
        }
    }

    private void updateFixAmountSliderValue() {
        setFixAmountSliderValue(toSliderValue(offerDraft.getFixTradeAmount()));
    }

    private void setUserSpecificTradeAmountLimitAsSliderValue(Optional<Double> value) {
        value.ifPresent(v -> checkArgument(v >= 0 && v <= 1, "value must be in range of 0 and 1"));
        offerDraft.setUserSpecificTradeAmountLimitAsSliderValue(value);
    }

    private void setFixAmountSliderValue(double sliderValue) {
        checkArgument(sliderValue >= 0 && sliderValue <= 1, "sliderValue must be in range of 0 and 1");
        offerDraft.setFixAmountSliderValue(sliderValue);
    }


    private TradeAmount clampTradeAmount(TradeAmount tradeAmount, boolean includeUserSpecificTradeAmountLimit) {
        TradeAmountRange limits = getClampLimits(includeUserSpecificTradeAmountLimit);
        return TradeAmountLimits.clampTradeAmount(limits, tradeAmount);
    }

    /* --------------------------------------------------------------------- */
    // Internal callbacks
    /* --------------------------------------------------------------------- */

    private PaymentRail getSelectedPaymentRail() {
        return selectedPaymentRailSupplier.get();
    }
}
