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

package bisq.offer.mu_sig.use_case.create_offer;

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.Monetary;
import bisq.common.monetary.MonetaryRange;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.offer.Direction;
import bisq.offer.mu_sig.use_case.AmountMappingService;
import bisq.offer.mu_sig.use_case.AmountUtils;
import bisq.offer.mu_sig.use_case.TradeAmountConstraints;
import bisq.offer.mu_sig.use_case.create_offer.amount.CreateOfferAmountUseCase;
import bisq.offer.mu_sig.use_case.create_offer.amount.limits.PaymentMethodBasedAmountLimits;
import bisq.offer.mu_sig.use_case.create_offer.direction.CreateOfferDirectionUseCase;
import bisq.offer.mu_sig.use_case.create_offer.market.CreateOfferMarketUseCase;
import bisq.offer.mu_sig.use_case.create_offer.payment_method.CreateOfferPaymentMethodUseCase;
import bisq.offer.mu_sig.use_case.create_offer.price.CreateOfferPriceUseCase;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Internal state-transition engine for {@link CreateOfferDraft}.
 * <p>
 * Design: this package-local component applies market/direction/price/input-mode transitions in a
 * deterministic order, recomputes derived constraints, and keeps draft amount fields/clamp state
 * consistent. The workflow remains a user-facing facade and persistence coordinator.
 */
public class CreateOfferDraftStateEngine {
    private final CreateOfferMarketUseCase marketService;
    private final CreateOfferDirectionUseCase directionService;
    private final CreateOfferPriceUseCase priceService;
    private final CreateOfferAmountUseCase amountService;
    private final MarketPriceService marketPriceService;
    private final PaymentMethodBasedAmountLimits paymentMethodSpecificAmountLimits;
    private final CreateOfferTradeAmountConstraintsService tradeAmountConstraintsService;
    private final AmountMappingService amountMappingService;
    private final Fiat defaultTradeAmountInUsd;
    private final CreateOfferPaymentMethodUseCase paymentMethodService;


    /* --------------------------------------------------------------------- */
    // Construction
    /* --------------------------------------------------------------------- */

    CreateOfferDraftStateEngine(CreateOfferMarketUseCase marketService,
                                CreateOfferDirectionUseCase directionService,
                                CreateOfferPaymentMethodUseCase paymentMethodService,
                                CreateOfferPriceUseCase priceService,
                                CreateOfferAmountUseCase amountService,
                                MarketPriceService marketPriceService,
                                AmountMappingService amountMappingService,
                                PaymentMethodBasedAmountLimits paymentMethodSpecificAmountLimits,
                                CreateOfferTradeAmountConstraintsService tradeAmountConstraintsService,
                                Fiat defaultTradeAmountInUsd) {
        this.marketService = checkNotNull(marketService, "createOfferMarketService must not be null");
        this.directionService = checkNotNull(directionService, "createOfferDirectionService must not be null");
        this.paymentMethodService = checkNotNull(paymentMethodService, "paymentMethodService must not be null");
        this.priceService = checkNotNull(priceService, "createOfferPriceService must not be null");
        this.amountService = checkNotNull(amountService, "createOfferAmountService must not be null");
        this.marketPriceService = checkNotNull(marketPriceService, "marketPriceService must not be null");
        this.amountMappingService = checkNotNull(amountMappingService, "amountMappingService must not be null");
        this.paymentMethodSpecificAmountLimits = paymentMethodSpecificAmountLimits;
        this.tradeAmountConstraintsService = tradeAmountConstraintsService;
        this.defaultTradeAmountInUsd = checkNotNull(defaultTradeAmountInUsd, "defaultTradeAmountInUsd must not be null");
    }

    /* --------------------------------------------------------------------- */
    // State transitions
    /* --------------------------------------------------------------------- */

    void initialize() {
        marketService.addMarketListener(this::onMarketChanged);
        directionService.addDisplayDirectionListener(this::onDirectionChanged);

        Market market = marketService.getMarket();
        Direction offerDirection = getOfferDirection(market);
        PriceQuote priceQuote = priceService.getPriceQuote();

        Fiat paymentRailBasedTradeLimitInUsd = paymentMethodSpecificAmountLimits.getAmountLimitInUsd();
        TradeAmountConstraints tradeAmountConstraints = tradeAmountConstraintsService.compute(
                market,
                offerDirection,
                priceQuote,
                paymentRailBasedTradeLimitInUsd);
        applyTradeAmountConstraints(tradeAmountConstraints);

        amountService.clampCurrentTradeAmounts(true);

        updateUserSpecificTradeAmountLimitAsSliderValue(offerDirection, amountService.getUserSpecificTradeAmountLimit());
        amountService.updateAmountSliderValues();
    }

    void dispose() {
    }

    // Impact on:
    // - selected payment method -> trade limits
    // - price quote -> amount, trade limits
    // - Derived: trade limits, amount (input amount, passive amount, slider value)
    void onMarketChanged(Market market) {
        checkNotNull(market, "market must not be null");

        //paymentMethodService.onMarketChanged(market);

        if (!isDerivedStateInitialized()) {
            return;
        }
        boolean selectedAccountsChanged = false;
      /*  boolean selectedAccountsChanged = paymentMethodService.updatePaymentMethods(market);
        if (selectedAccountsChanged) {
            recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }*/

        Direction offerDirection = getOfferDirection(market);

        // At new market we use market price as default offer price
        PriceQuote offerPriceQuote = marketPriceService.getMarketPriceQuoteOrThrow(market);
        priceService.setPriceQuoteFromMarketChange(offerPriceQuote);

        TradeAmountConstraints tradeAmountConstraints = tradeAmountConstraintsService.compute(market,
                offerDirection,
                offerPriceQuote,
                paymentMethodSpecificAmountLimits.getAmountLimitInUsd());
        applyTradeAmountConstraints(tradeAmountConstraints);

        TradeAmount defaultTradeAmount = AmountUtils.getTradeAmountFromUsd(marketPriceService, market, defaultTradeAmountInUsd);
        TradeAmount clampedDefaultTradeAmount = amountService.clampTradeAmount(defaultTradeAmount, true);
        amountService.setFixTradeAmount(clampedDefaultTradeAmount);
        amountService.setMinTradeAmount(clampedDefaultTradeAmount);
        amountService.setMaxTradeAmount(clampedDefaultTradeAmount);

        updateUserSpecificTradeAmountLimitAsSliderValue(offerDirection, amountService.getUserSpecificTradeAmountLimit());
        amountService.updateAmountSliderValues();
    }

    // Impact on:
    // - user-specific trade limits
    // - Derived: amount: inputamunt, passive amount, slider value
    boolean onDirectionChanged(Direction direction) {
        checkNotNull(direction, "direction must not be null");
        if (!hasPricingContext()) {
            return false;
        }

        Market market = marketService.getMarket();
        Direction offerDirection = Direction.displayDirectionToOfferDirection(direction, market);
        PriceQuote offerPriceQuote = priceService.getPriceQuote();
        TradeAmountConstraints tradeAmountConstraints = tradeAmountConstraintsService.compute(market,
                offerDirection,
                offerPriceQuote,
                paymentMethodSpecificAmountLimits.getAmountLimitInUsd());
        applyTradeAmountConstraints(tradeAmountConstraints);

        updateUserSpecificTradeAmountLimitAsSliderValue(offerDirection, amountService.getUserSpecificTradeAmountLimit());
        amountService.updateAmountSliderValues();
        return true;
    }

    void applyUseFixPriceChanged(boolean useFixPrice) {
        priceService.applyUseFixPriceChanged(useFixPrice);
    }

    void applyPriceQuoteChanged(PriceQuote priceQuote) {
        checkNotNull(priceQuote, "priceQuote must not be null");

        if (!hasPricingContext()) {
            return;
        }

        applyUseFixPriceChanged(priceService.getUseFixPrice());

        Market market = marketService.getMarket();
        Direction offerDirection = getOfferDirection(market);
        TradeAmount fixTradeAmount = amountService.getFixTradeAmount();
        TradeAmount minTradeAmount = amountService.getMinTradeAmount();
        TradeAmount maxTradeAmount = amountService.getMaxTradeAmount();
        TradeAmountRange oldClampLimits = amountService.getClampLimits(true);

        TradeAmountConstraints tradeAmountConstraints = tradeAmountConstraintsService.compute(market,
                offerDirection,
                priceQuote,
                paymentMethodSpecificAmountLimits.getAmountLimitInUsd());
        applyTradeAmountConstraints(tradeAmountConstraints);

        TradeAmountRange newClampLimits = amountService.getClampLimits(true);
        if (fixTradeAmount != null) {
            amountService.setFixTradeAmount(toUpdatedPassiveAmount(market, priceQuote, fixTradeAmount, oldClampLimits, newClampLimits));
        }
        if (minTradeAmount != null) {
            amountService.setMinTradeAmount(toUpdatedPassiveAmount(market, priceQuote, minTradeAmount, oldClampLimits, newClampLimits));
        }
        if (maxTradeAmount != null) {
            amountService.setMaxTradeAmount(toUpdatedPassiveAmount(market, priceQuote, maxTradeAmount, oldClampLimits, newClampLimits));
        }

        updateUserSpecificTradeAmountLimitAsSliderValue(offerDirection, amountService.getUserSpecificTradeAmountLimit());
        amountService.updateAmountSliderValues();
    }

    boolean applyUseBaseCurrencyForAmountInputChanged(boolean useBaseCurrencyForAmountInput) {
        amountService.setUseBaseCurrencyForAmountInput(useBaseCurrencyForAmountInput);
        Direction direction = directionService.getDisplayDirection();
        if (!amountService.isDerivedStateInitialized()) {
            return false;
        }

        updateInputAmountLimits(amountService.getTradeAmountLimits());
        updateUserSpecificTradeAmountLimitAsSliderValue(direction, amountService.getUserSpecificTradeAmountLimit());
        amountService.updateAmountSliderValues();
        return true;
    }

    boolean applyUseRangeAmountChanged(boolean useRangeAmount) {
        amountService.setUseRangeAmount(useRangeAmount);
        if (!amountService.isDerivedStateInitialized()) {
            return false;
        }

        amountService.updateAmountSliderValues();
        return true;
    }

    /* --------------------------------------------------------------------- */
    // Amount writes
    /* --------------------------------------------------------------------- */

    public void recalculateTradeAmountConstraintsForSelectedPaymentRail() {
        if (!hasPricingContext()) {
            return;
        }

        Market market = marketService.getMarket();
        Direction offerDirection = getOfferDirection(market);
        PriceQuote offerPriceQuote = priceService.getPriceQuote();

        TradeAmountConstraints tradeAmountConstraints = tradeAmountConstraintsService.compute(market,
                offerDirection,
                offerPriceQuote,
                paymentMethodSpecificAmountLimits.getAmountLimitInUsd());
        applyTradeAmountConstraints(tradeAmountConstraints);
        amountService.clampCurrentTradeAmounts(true);

        updateUserSpecificTradeAmountLimitAsSliderValue(offerDirection, amountService.getUserSpecificTradeAmountLimit());
        amountService.updateAmountSliderValues();
    }

    /* --------------------------------------------------------------------- */
    // Package scope helpers used by workflow facade
    /* --------------------------------------------------------------------- */

    TradeAmount toClampedTradeAmount(Monetary amount) {
        checkNotNull(amount, "amount must not be null");
        Market market = checkNotNull(marketService.getMarket(), "market must not be null");
        PriceQuote priceQuote = checkNotNull(priceService.getPriceQuote(), "priceQuote must not be null");
        TradeAmountRange limits = getClampLimits(true);
        return amountMappingService.toTradeAmountFromInputAmount(market, priceQuote, amount, limits);
    }

    TradeAmount toTradeAmountFromSliderValue(TradeAmount tradeAmount, double sliderValue) {
        checkNotNull(tradeAmount, "tradeAmount must not be null");
        Market market = checkNotNull(marketService.getMarket(), "market must not be null");
        PriceQuote priceQuote = checkNotNull(priceService.getPriceQuote(), "priceQuote must not be null");
        TradeAmountRange limits = getClampLimits(true);
        MonetaryRange inputAmountLimits = checkNotNull(amountService.getInputAmountLimits(), "inputAmountLimits must not be null");
        return amountMappingService.toTradeAmountFromSliderValue(market,
                priceQuote,
                tradeAmount,
                limits,
                inputAmountLimits,
                amountService.getUseBaseCurrencyForAmountInput(),
                sliderValue);
    }

    TradeAmountRange getClampLimits(boolean includeUserSpecificTradeAmountLimit) {
        return amountService.getClampLimits(includeUserSpecificTradeAmountLimit);
    }

    boolean isDerivedStateInitialized() {
        return amountService.isDerivedStateInitialized() && directionService.getDisplayDirection() != null;
    }


    /* --------------------------------------------------------------------- */
    // Internal recalculation helpers
    /* --------------------------------------------------------------------- */

    private boolean hasPricingContext() {
        return marketService.getMarket() != null
                && priceService.getPriceQuote() != null
                && amountService.isDerivedStateInitialized();
    }

    private Direction getOfferDirection(Market market) {
        Direction displayDirection = checkNotNull(directionService.getDisplayDirection(), "displayDirection must not be null");
        return Direction.displayDirectionToOfferDirection(displayDirection, market);
    }

    private void applyTradeAmountConstraints(TradeAmountConstraints tradeAmountConstraints) {
        amountService.setTradeAmountLimits(tradeAmountConstraints.tradeAmountLimits());
        amountService.setUserSpecificTradeAmountLimit(tradeAmountConstraints.userSpecificTradeAmountLimit());
        updateInputAmountLimits(tradeAmountConstraints.tradeAmountLimits());
    }

    private void updateInputAmountLimits(TradeAmountRange tradeAmountLimits) {
        checkNotNull(tradeAmountLimits, "tradeAmountLimits must not be null");
        MonetaryRange inputAmountLimits = amountMappingService.toInputAmountLimits(tradeAmountLimits,
                amountService.getUseBaseCurrencyForAmountInput());
        amountService.setInputAmountLimits(inputAmountLimits);
    }

    private void updateUserSpecificTradeAmountLimitAsSliderValue(Direction direction,
                                                                 Optional<TradeAmount> userSpecificTradeAmountLimit) {
        if (direction.isBuy() && userSpecificTradeAmountLimit.isPresent() && amountService.getInputAmountLimits() != null) {
            double sliderValue = amountService.toSliderValue(userSpecificTradeAmountLimit.get());
            amountService.setUserSpecificTradeAmountLimitAsSliderValue(Optional.of(sliderValue));
        } else {
            amountService.setUserSpecificTradeAmountLimitAsSliderValue(Optional.empty());
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
                amountService.getUseBaseCurrencyForAmountInput());
    }
}
