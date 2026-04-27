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

import bisq.account.AccountService;
import bisq.account.payment_method.PaymentRail;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.Monetary;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.offer.amount.spec.AmountSpec;
import bisq.offer.mu_sig.MuSigOffer;
import bisq.offer.mu_sig.draft.AmountMappingService;
import bisq.offer.mu_sig.draft.OfferDraftWorkflow;
import bisq.offer.mu_sig.draft.PaymentMethodSelectionService;
import bisq.offer.mu_sig.draft.dependencies.AccountsProvider;
import bisq.offer.mu_sig.draft.dependencies.DefaultAccountsProvider;
import bisq.offer.mu_sig.draft.dependencies.DefaultTakeOfferDraftCookieStore;
import bisq.offer.mu_sig.draft.dependencies.TakeOfferDraftCookieStore;
import bisq.offer.mu_sig.draft.take_offer.amount.TakeOfferAmountService;
import bisq.offer.mu_sig.draft.take_offer.direction.TakeOfferDirectionService;
import bisq.offer.mu_sig.draft.take_offer.market.TakeOfferMarketService;
import bisq.offer.mu_sig.draft.take_offer.payment_method.TakeOfferPaymentMethodService;
import bisq.offer.mu_sig.draft.take_offer.price.TakeOfferPriceService;
import bisq.settings.SettingsService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * User-facing workflow facade for creating an offer draft.
 * <p>
 * Design: exposes stable UI/API mutation methods and persistence side effects (cookies), while
 * delegating transition ordering and derived-state recalculation to {@link TakeOfferDraftStateEngine}
 * and isolated domain services.
 */
@Slf4j
public class TakeOfferDraftWorkflow extends OfferDraftWorkflow {
    public static final Fiat DEFAULT_TRADE_AMOUNT_IN_USD = Fiat.fromFaceValue(500, "USD");
    @Getter
    private final TakeOfferMarketService marketService;
    @Getter
    private final TakeOfferDirectionService directionService;
    @Getter
    private final TakeOfferPriceService priceService;
    @Getter
    private final TakeOfferAmountService amountService;

    private final TakeOfferDraftCookieStore cookieStore;
    private final AmountMappingService amountMappingService;
    @Getter
    private final TakeOfferPaymentMethodService paymentMethodService;
    private final TakeOfferDraftStateEngine stateEngine;


    /* --------------------------------------------------------------------- */
    // Construction
    /* --------------------------------------------------------------------- */

    public TakeOfferDraftWorkflow(MarketPriceService marketPriceService,
                                  SettingsService settingsService,
                                  AccountService accountService) {
        this(marketPriceService,
                new DefaultTakeOfferDraftCookieStore(settingsService),
                new DefaultAccountsProvider(accountService));
    }

    TakeOfferDraftWorkflow(MarketPriceService marketPriceService,
                           TakeOfferDraftCookieStore cookieStore,
                           AccountsProvider accountsProvider) {
        marketService = new TakeOfferMarketService();
        directionService = new TakeOfferDirectionService();
        priceService = new TakeOfferPriceService();
        amountService = new TakeOfferAmountService();

        this.cookieStore = checkNotNull(cookieStore, "cookieStore must not be null");
        checkNotNull(accountsProvider, "accountsProvider must not be null");
        checkNotNull(marketPriceService, "marketPriceProvider must not be null");

        amountMappingService = new AmountMappingService();
        TakeOfferTradeAmountConstraintsService tradeAmountConstraintsService = new TakeOfferTradeAmountConstraintsService(marketPriceService);
        PaymentMethodSelectionService paymentMethodSelectionService = new PaymentMethodSelectionService(accountsProvider);

        stateEngine = new TakeOfferDraftStateEngine(marketService,
                directionService,
                priceService,
                amountService,
                marketPriceService,
                tradeAmountConstraintsService,
                amountMappingService,
                this::getSelectedPaymentRail,
                this::updatePaymentMethods,
                DEFAULT_TRADE_AMOUNT_IN_USD);

        paymentMethodService = new TakeOfferPaymentMethodService(paymentMethodSelectionService, stateEngine);
    }

    private void updatePaymentMethods() {
        paymentMethodService.updatePaymentMethods(getMarket());
    }

    private PaymentRail getSelectedPaymentRail() {
        return paymentMethodService.getSelectedPaymentRail();
    }


    /* --------------------------------------------------------------------- */
    // Lifecycle
    /* --------------------------------------------------------------------- */

    public void initialize(MuSigOffer muSigOffer) {
        checkNotNull(muSigOffer, "muSigOffer must not be null");

        Market market = muSigOffer.getMarket();
        boolean useBaseCurrencyForAmountInput = cookieStore.getUseBaseCurrencyForAmountInput(market);

        stateEngine.initialize(muSigOffer, useBaseCurrencyForAmountInput);
    }

    /* --------------------------------------------------------------------- */
    // Amount input entry points
    /* --------------------------------------------------------------------- */

    public void setFixTradeAmountFromInputAmount(Monetary amount) {
        TradeAmount tradeAmount = stateEngine.toClampedTradeAmount(amount);
        setFixTradeAmount(tradeAmount);
    }

    public void setFixTradeAmountFromSliderValue(double sliderValue) {
        TradeAmount fixTradeAmount = checkNotNull(amountService.getFixTradeAmount(), "fixTradeAmount must not be null");
        TradeAmount tradeAmount = stateEngine.toTradeAmountFromSliderValue(fixTradeAmount, sliderValue);
        setFixTradeAmount(tradeAmount);
    }


    /* --------------------------------------------------------------------- */
    // Amount conversion
    /* --------------------------------------------------------------------- */

    public Monetary toInputAmount(TradeAmount tradeAmount, boolean includeUserSpecificTradeAmountLimit) {
        boolean useBaseCurrencyForAmountInput = amountService.getUseBaseCurrencyForAmountInput();
        TradeAmountRange limits = getClampLimits(includeUserSpecificTradeAmountLimit);
        return amountMappingService.toInputAmount(tradeAmount, limits, useBaseCurrencyForAmountInput);
    }

    public Monetary toPassiveAmount(TradeAmount tradeAmount, boolean includeUserSpecificTradeAmountLimit) {
        boolean useBaseCurrencyForAmountInput = amountService.getUseBaseCurrencyForAmountInput();
        TradeAmountRange limits = getClampLimits(includeUserSpecificTradeAmountLimit);
        return amountMappingService.toPassiveAmount(tradeAmount, limits, useBaseCurrencyForAmountInput);
    }


    /* --------------------------------------------------------------------- */
    // Mutation API
    /* --------------------------------------------------------------------- */

    public void setUseBaseCurrencyForAmountInput(boolean value) {
        if (value == amountService.getUseBaseCurrencyForAmountInput()) {
            return;
        }

        Market market = getMarket();
        if (market == null) {
            amountService.setUseBaseCurrencyForAmountInput(value);
            return;
        }

        if (stateEngine.applyUseBaseCurrencyForAmountInputChanged(value)) {
            cookieStore.persistUseBaseCurrencyForAmountInput(market, value);
        }
    }

    public void setFixTradeAmount(TradeAmount tradeAmount) {
        stateEngine.setFixTradeAmount(tradeAmount);
    }


    /* --------------------------------------------------------------------- */
    // Derived read model
    /* --------------------------------------------------------------------- */

    public AmountSpec getAmountSpec() {
        return amountService.getAmountSpec();
    }

    @Override
    public Market getMarket() {
        return marketService.getMarket();
    }


    /* --------------------------------------------------------------------- */
    // Internal helpers
    /* --------------------------------------------------------------------- */

    private TradeAmountRange getClampLimits(boolean includeUserSpecificTradeAmountLimit) {
        return stateEngine.getClampLimits(includeUserSpecificTradeAmountLimit);
    }
}
