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
import bisq.offer.mu_sig.MuSigOffer;
import bisq.offer.mu_sig.MuSigTradeAmountLimits;
import bisq.offer.mu_sig.draft.AmountMappingService;
import bisq.offer.mu_sig.draft.AmountUtils;
import bisq.offer.mu_sig.draft.TradeAmountConstraints;
import bisq.offer.mu_sig.draft.TradeAmountLimits;
import bisq.offer.mu_sig.draft.take_offer.amount.TakeOfferAmountService;
import bisq.offer.mu_sig.draft.take_offer.direction.TakeOfferDirectionService;
import bisq.offer.mu_sig.draft.take_offer.market.TakeOfferMarketService;
import bisq.offer.mu_sig.draft.take_offer.price.TakeOfferPriceService;
import bisq.offer.price.PriceUtil;
import bisq.offer.price.spec.PriceSpec;

import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Internal state-transition engine for {@link TakeOfferService}.
 * <p>
 * Design: this package-local component applies market/direction/input-mode transitions in a
 * deterministic order, recomputes derived constraints, and keeps amount fields/clamp state
 * consistent. The workflow remains a user-facing facade and persistence coordinator.
 */
public class TakeOfferDraftStateEngine {
    private final TakeOfferMarketService takeOfferMarketService;
    private final TakeOfferDirectionService takeOfferDirectionService;
    private final TakeOfferPriceService takeOfferPriceService;
    private final TakeOfferAmountService takeOfferAmountService;
    private final MarketPriceService marketPriceService;
    private final TakeOfferTradeAmountConstraintsService tradeAmountConstraintsService;
    private final AmountMappingService amountMappingService;
    private final Supplier<PaymentRail> selectedPaymentRailSupplier;
    private final Runnable updatePaymentMethodsHandler;
    private final Fiat defaultTradeAmountInUsd;

    /* --------------------------------------------------------------------- */
    // Construction
    /* --------------------------------------------------------------------- */

    TakeOfferDraftStateEngine(TakeOfferMarketService takeOfferMarketService,
                              TakeOfferDirectionService takeOfferDirectionService,
                              TakeOfferPriceService takeOfferPriceService,
                              TakeOfferAmountService takeOfferAmountService,
                              MarketPriceService marketPriceService,
                              TakeOfferTradeAmountConstraintsService tradeAmountConstraintsService,
                              AmountMappingService amountMappingService,
                              Supplier<PaymentRail> selectedPaymentRailSupplier,
                              Runnable updatePaymentMethodsHandler,
                              Fiat defaultTradeAmountInUsd) {
        this.takeOfferMarketService = checkNotNull(takeOfferMarketService, "takeOfferMarketService must not be null");
        this.takeOfferDirectionService = checkNotNull(takeOfferDirectionService, "takeOfferDirectionService must not be null");
        this.takeOfferPriceService = checkNotNull(takeOfferPriceService, "takeOfferPriceService must not be null");
        this.takeOfferAmountService = checkNotNull(takeOfferAmountService, "takeOfferAmountService must not be null");
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
        Direction takersDirection = muSigOffer.getTakersDirection();
        takeOfferMarketService.initialize(muSigOffer);
        takeOfferDirectionService.initialize(muSigOffer);

        // Price
        PriceSpec priceSpec = muSigOffer.getPriceSpec();
        PriceQuote priceQuote = PriceUtil.getQuoteOrThrow(marketPriceService, priceSpec, market);
        takeOfferPriceService.setPriceQuote(priceQuote);

        // Amount
        takeOfferAmountService.setAmountSpec(muSigOffer.getAmountSpec());
        takeOfferAmountService.setUseBaseCurrencyForAmountInput(useBaseCurrencyForAmountInput);
        PriceQuote marketPriceQuote = marketPriceService.getMarketPriceQuoteOrThrow(market);

        Fiat maxTradeLimitInUsd = evaluateMaxTradeLimitInUsd();
        TradeAmountConstraints tradeAmountConstraints = tradeAmountConstraintsService.compute(market,
                takersDirection,
                takeOfferAmountService.getAmountSpec(),
                priceQuote,
                marketPriceQuote,
                maxTradeLimitInUsd);
        applyTradeAmountConstraints(tradeAmountConstraints);

        TradeAmount defaultTradeAmount = AmountUtils.getTradeAmountFromUsd(marketPriceService, market, defaultTradeAmountInUsd);
        TradeAmount clampedDefaultTradeAmount = clampTradeAmount(defaultTradeAmount, true);
        takeOfferAmountService.setFixTradeAmount(clampedDefaultTradeAmount);

        updateUserSpecificTradeAmountLimitAsSliderValue(takersDirection, takeOfferAmountService.getUserSpecificTradeAmountLimit());
        updateAmountSliderValues();
        updatePaymentMethodsHandler.run();
    }

    boolean applyUseBaseCurrencyForAmountInputChanged(boolean useBaseCurrencyForAmountInput) {
        takeOfferAmountService.setUseBaseCurrencyForAmountInput(useBaseCurrencyForAmountInput);
        Direction direction = takeOfferDirectionService.getDirection();
        if (!isDerivedStateInitialized() || direction == null) {
            return false;
        }

        updateInputAmountLimits(takeOfferAmountService.getTradeAmountLimits());
        updateUserSpecificTradeAmountLimitAsSliderValue(direction, takeOfferAmountService.getUserSpecificTradeAmountLimit());
        updateAmountSliderValues();
        return true;
    }

    /* --------------------------------------------------------------------- */
    // Amount writes
    /* --------------------------------------------------------------------- */

    void setFixTradeAmount(TradeAmount tradeAmount) {
        checkNotNull(tradeAmount, "tradeAmount must not be null");
        TradeAmount valueToSet = isDerivedStateInitialized() ? clampTradeAmount(tradeAmount, true) : tradeAmount;
        takeOfferAmountService.setFixTradeAmount(valueToSet);
        if (isDerivedStateInitialized()) {
            updateFixAmountSliderValue();
        }
    }

    public void recalculateTradeAmountConstraintsForSelectedPaymentRail() {
        if (!hasPricingContext()) {
            return;
        }

        Market market = takeOfferMarketService.getMarket();
        Direction direction = takeOfferDirectionService.getDirection();
        PriceQuote offerPriceQuote = takeOfferPriceService.getPriceQuote();
        PriceQuote marketPriceQuote = marketPriceService.getMarketPriceQuoteOrThrow(market);
        Fiat maxTradeLimitInUsd = evaluateMaxTradeLimitInUsd();
        TradeAmountConstraints tradeAmountConstraints = tradeAmountConstraintsService.compute(market,
                direction,
                checkNotNull(takeOfferAmountService.getAmountSpec(), "amountSpec must not be null"),
                offerPriceQuote,
                marketPriceQuote,
                maxTradeLimitInUsd);
        applyTradeAmountConstraints(tradeAmountConstraints);

        if (takeOfferAmountService.getFixTradeAmount() != null) {
            takeOfferAmountService.setFixTradeAmount(clampTradeAmount(takeOfferAmountService.getFixTradeAmount(), true));
        }

        updateUserSpecificTradeAmountLimitAsSliderValue(direction, takeOfferAmountService.getUserSpecificTradeAmountLimit());
        updateAmountSliderValues();
    }

    private Fiat evaluateMaxTradeLimitInUsd() {
        // Initially, the selected payment rail is null, and we use the MAX_TRADE_AMOUNT_IN_USD
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
        Market market = checkNotNull(takeOfferMarketService.getMarket(), "market must not be null");
        PriceQuote priceQuote = checkNotNull(takeOfferPriceService.getPriceQuote(), "priceQuote must not be null");
        TradeAmountRange limits = getClampLimits(true);
        return amountMappingService.toTradeAmountFromInputAmount(market, priceQuote, amount, limits);
    }

    TradeAmount toTradeAmountFromSliderValue(TradeAmount tradeAmount, double sliderValue) {
        checkNotNull(tradeAmount, "tradeAmount must not be null");
        Market market = checkNotNull(takeOfferMarketService.getMarket(), "market must not be null");
        PriceQuote priceQuote = checkNotNull(takeOfferPriceService.getPriceQuote(), "priceQuote must not be null");
        TradeAmountRange limits = getClampLimits(true);
        MonetaryRange inputAmountLimits = checkNotNull(takeOfferAmountService.getInputAmountLimits(), "inputAmountLimits must not be null");
        return amountMappingService.toTradeAmountFromSliderValue(market,
                priceQuote,
                tradeAmount,
                limits,
                inputAmountLimits,
                takeOfferAmountService.getUseBaseCurrencyForAmountInput(),
                sliderValue);
    }

    TradeAmountRange getClampLimits(boolean includeUserSpecificTradeAmountLimit) {
        TradeAmountRange tradeAmountLimits = takeOfferAmountService.getTradeAmountLimits();
        Optional<TradeAmount> userSpecificTradeAmountLimit = takeOfferAmountService.getUserSpecificTradeAmountLimit();
        return TradeAmountLimits.getClampLimits(tradeAmountLimits,
                userSpecificTradeAmountLimit,
                includeUserSpecificTradeAmountLimit);
    }

    boolean isDerivedStateInitialized() {
        return takeOfferAmountService.getTradeAmountLimits() != null && takeOfferAmountService.getInputAmountLimits() != null;
    }

    /* --------------------------------------------------------------------- */
    // Internal recalculation helpers
    /* --------------------------------------------------------------------- */

    private boolean hasPricingContext() {
        return takeOfferMarketService.getMarket() != null
                && takeOfferDirectionService.getDirection() != null
                && takeOfferPriceService.getPriceQuote() != null
                && isDerivedStateInitialized();
    }

    private void applyTradeAmountConstraints(TradeAmountConstraints tradeAmountConstraints) {
        takeOfferAmountService.setTradeAmountLimits(tradeAmountConstraints.tradeAmountLimits());
        takeOfferAmountService.setUserSpecificTradeAmountLimit(tradeAmountConstraints.userSpecificTradeAmountLimit());
        updateInputAmountLimits(tradeAmountConstraints.tradeAmountLimits());
    }

    private void updateInputAmountLimits(TradeAmountRange tradeAmountLimits) {
        checkNotNull(tradeAmountLimits, "tradeAmountLimits must not be null");
        MonetaryRange inputAmountLimits = amountMappingService.toInputAmountLimits(tradeAmountLimits,
                takeOfferAmountService.getUseBaseCurrencyForAmountInput());
        takeOfferAmountService.setInputAmountLimits(inputAmountLimits);
    }

    private void updateUserSpecificTradeAmountLimitAsSliderValue(Direction direction,
                                                                 Optional<TradeAmount> userSpecificTradeAmountLimit) {
        if (direction.isBuy() && userSpecificTradeAmountLimit.isPresent() && takeOfferAmountService.getInputAmountLimits() != null) {
            double sliderValue = toSliderValue(userSpecificTradeAmountLimit.get());
            setUserSpecificTradeAmountLimitAsSliderValue(Optional.of(sliderValue));
        } else {
            setUserSpecificTradeAmountLimitAsSliderValue(Optional.empty());
        }
    }

    private double toSliderValue(TradeAmount tradeAmount) {
        TradeAmountRange limits = getClampLimits(true);
        MonetaryRange inputAmountLimits = checkNotNull(takeOfferAmountService.getInputAmountLimits(), "inputAmountLimits must not be null");
        return amountMappingService.toSliderValue(tradeAmount,
                limits,
                inputAmountLimits,
                takeOfferAmountService.getUseBaseCurrencyForAmountInput());
    }

    /* --------------------------------------------------------------------- */
    // Internal slider helpers
    /* --------------------------------------------------------------------- */

    private void updateAmountSliderValues() {
        if (takeOfferAmountService.getFixTradeAmount() != null) {
            updateFixAmountSliderValue();
        }
    }

    private void updateFixAmountSliderValue() {
        setFixAmountSliderValue(toSliderValue(takeOfferAmountService.getFixTradeAmount()));
    }

    private void setUserSpecificTradeAmountLimitAsSliderValue(Optional<Double> value) {
        value.ifPresent(v -> checkArgument(v >= 0 && v <= 1, "value must be in range of 0 and 1"));
        takeOfferAmountService.setUserSpecificTradeAmountLimitAsSliderValue(value);
    }

    private void setFixAmountSliderValue(double sliderValue) {
        checkArgument(sliderValue >= 0 && sliderValue <= 1, "sliderValue must be in range of 0 and 1");
        takeOfferAmountService.setFixAmountSliderValue(sliderValue);
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
