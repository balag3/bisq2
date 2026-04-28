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

import bisq.account.AccountService;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.Monetary;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.offer.Direction;
import bisq.offer.mu_sig.use_case.AmountMappingService;
import bisq.offer.mu_sig.use_case.DraftOfferUseCase;
import bisq.offer.mu_sig.use_case.create_offer.amount.CreateOfferAmountUseCase;
import bisq.offer.mu_sig.use_case.create_offer.direction.CreateOfferDirectionUseCase;
import bisq.offer.mu_sig.use_case.create_offer.market.CreateOfferMarketUseCase;
import bisq.offer.mu_sig.use_case.create_offer.payment_method.CreateOfferPaymentMethodUseCase;
import bisq.offer.mu_sig.use_case.create_offer.price.CreateOfferPriceUseCase;
import bisq.offer.mu_sig.use_case.dependencies.AccountsProvider;
import bisq.offer.mu_sig.use_case.dependencies.CreateOfferDraftCookieStore;
import bisq.offer.mu_sig.use_case.dependencies.DefaultAccountsProvider;
import bisq.offer.mu_sig.use_case.dependencies.DefaultCreateOfferDraftCookieStore;
import bisq.settings.SettingsService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * User-facing workflow facade for creating an offer draft.
 * <p>
 * Design: exposes stable UI/API mutation methods and persistence side effects (cookies), while
 * delegating transition ordering and derived-state recalculation to {@link CreateOfferDraftStateEngine}
 * and isolated domain services.
 */
@Slf4j
public class CreateOfferUseCase extends DraftOfferUseCase {
    public static final Fiat DEFAULT_TRADE_AMOUNT_IN_USD = Fiat.fromFaceValue(500, "USD");
    @Getter
    private final CreateOfferMarketUseCase marketService;
    @Getter
    private final CreateOfferDirectionUseCase directionService;
    @Getter
    private final CreateOfferPriceUseCase priceService;
    @Getter
    private final CreateOfferAmountUseCase amountUseCase;

    private final CreateOfferDraftCookieStore cookieStore;
    private final AmountMappingService amountMappingService;
    @Getter
    private final CreateOfferPaymentMethodUseCase paymentMethodService;
    private final CreateOfferDraftStateEngine stateEngine;
    private final CreateOfferStateUpdateHandler stateUpdateHandler;
    private final CreateOfferTradeAmountConstraintsService tradeAmountConstraintsService;


    /* --------------------------------------------------------------------- */
    // Construction
    /* --------------------------------------------------------------------- */

    public CreateOfferUseCase(MarketPriceService marketPriceService,
                              SettingsService settingsService,
                              AccountService accountService) {
        this(marketPriceService,
                new DefaultCreateOfferDraftCookieStore(settingsService),
                new DefaultAccountsProvider(accountService));
    }

    CreateOfferUseCase(MarketPriceService marketPriceService,
                       CreateOfferDraftCookieStore cookieStore,
                       AccountsProvider accountsProvider) {
        checkNotNull(accountsProvider, "accountsProvider must not be null");
        checkNotNull(marketPriceService, "marketPriceProvider must not be null");
        this.cookieStore = checkNotNull(cookieStore, "cookieStore must not be null");

        amountMappingService = new AmountMappingService();
        tradeAmountConstraintsService = new CreateOfferTradeAmountConstraintsService(marketPriceService);

        marketService = new CreateOfferMarketUseCase();
        directionService = new CreateOfferDirectionUseCase(cookieStore);
        paymentMethodService = new CreateOfferPaymentMethodUseCase(accountsProvider);
        priceService = new CreateOfferPriceUseCase(marketPriceService, cookieStore);
        amountUseCase = new CreateOfferAmountUseCase(marketPriceService, cookieStore, amountMappingService);


        marketService.marketObservable().addObserver(market -> {
            if (market != null) {
                paymentMethodService.handleMarketChanged(market);
            }
        });


        stateEngine = new CreateOfferDraftStateEngine(marketService,
                directionService,
                paymentMethodService,
                priceService,
                amountUseCase,
                marketPriceService,
                amountMappingService,
                tradeAmountConstraintsService,
                DEFAULT_TRADE_AMOUNT_IN_USD);

        stateUpdateHandler = new CreateOfferStateUpdateHandler(marketService,
                directionService,
                paymentMethodService,
                priceService,
                amountUseCase,
                marketPriceService,
                tradeAmountConstraintsService,
                stateEngine);
    }


    /* --------------------------------------------------------------------- */
    // Lifecycle
    /* --------------------------------------------------------------------- */

    public void initialize(Market market) {
        checkNotNull(market, "Market must not be null");

        marketService.initialize(market);
        directionService.initialize();
        paymentMethodService.initialize(market);
        priceService.initialize(market);
        amountUseCase.initialize(market);
        Direction direction = directionService.getDisplayDirection();

        stateUpdateHandler.initialize();

        stateEngine.initialize();
    }

    @Override
    public void dispose() {
        super.dispose();
        paymentMethodService.dispose();
        stateUpdateHandler.dispose();
        stateEngine.dispose();
    }


    /* --------------------------------------------------------------------- */
    // Mutation API
    /* --------------------------------------------------------------------- */

    public void setPriceQuote(PriceQuote priceQuote) {
        checkNotNull(priceQuote, "PriceQuote must not be null");
        if (priceQuote.equals(priceService.getPriceQuote())) {
            return;
        }
        stateEngine.applyPriceQuoteChanged(priceQuote);
    }

    public void setUseFixPrice(boolean useFixPrice) {
        if (useFixPrice == priceService.getUseFixPrice()) {
            return;
        }
        Market market = checkNotNull(getMarket(), "market must not be null");
        cookieStore.persistUseFixPrice(market, useFixPrice);
        stateEngine.applyUseFixPriceChanged(useFixPrice);
    }

    public void setPricePercentage(double pricePercentage) {
        if (Double.compare(pricePercentage, priceService.getPricePercentage()) == 0) {
            return;
        }
        priceService.setPricePercentage(pricePercentage);
        Market market = checkNotNull(getMarket(), "market must not be null");
        cookieStore.persistPricePercentage(market, pricePercentage);
    }

   /* public void setFixPrice(PriceQuote fixPriceQuote) {
        checkNotNull(fixPriceQuote, "fixPriceQuote must not be null");
        if (priceService.getPriceQuote().equals(fixPriceQuote)) {
            return;
        }
        priceService.setPriceQuote(fixPriceQuote);
    }*/

    public void setUseBaseCurrencyForAmountInput(boolean value) {
        if (value == amountUseCase.getUseBaseCurrencyForAmountInput()) {
            return;
        }

        Market market = getMarket();
        if (market == null) {
            amountUseCase.setUseBaseCurrencyForAmountInput(value);
            return;
        }

        if (stateEngine.applyUseBaseCurrencyForAmountInputChanged(value)) {
            cookieStore.persistUseBaseCurrencyForAmountInput(market, value);
        }
    }

    public void setUseRangeAmount(boolean useRangeAmount) {
        if (useRangeAmount == amountUseCase.getUseRangeAmount()) {
            return;
        }

        if (stateEngine.applyUseRangeAmountChanged(useRangeAmount)) {
            cookieStore.persistUseRangeAmount(useRangeAmount);
        }
    }

    /* --------------------------------------------------------------------- */
    // Amount input entry points
    /* --------------------------------------------------------------------- */

    public void setFixTradeAmountFromInputAmount(Monetary amount) {
        TradeAmount tradeAmount = stateEngine.toClampedTradeAmount(amount);
        amountUseCase.setFixTradeAmount(tradeAmount);
    }

    public void setMinTradeAmountFromInputAmount(Monetary amount) {
        TradeAmount tradeAmount = stateEngine.toClampedTradeAmount(amount);
        amountUseCase.setMinTradeAmount(tradeAmount);
    }

    public void setMaxTradeAmountFromInputAmount(Monetary amount) {
        TradeAmount tradeAmount = stateEngine.toClampedTradeAmount(amount);
        amountUseCase.setMaxTradeAmount(tradeAmount);
    }

    public void setFixTradeAmountFromSliderValue(double sliderValue) {
        TradeAmount fixTradeAmount = checkNotNull(amountUseCase.getFixTradeAmount(), "fixTradeAmount must not be null");
        TradeAmount tradeAmount = stateEngine.toTradeAmountFromSliderValue(fixTradeAmount, sliderValue);
        amountUseCase.setFixTradeAmount(tradeAmount);
    }

    public void setMinTradeAmountFromSliderValue(double sliderValue) {
        TradeAmount minTradeAmount = checkNotNull(amountUseCase.getMinTradeAmount(), "minTradeAmount must not be null");
        TradeAmount tradeAmount = stateEngine.toTradeAmountFromSliderValue(minTradeAmount, sliderValue);
        amountUseCase.setMinTradeAmount(tradeAmount);
    }

    public void setMaxTradeAmountFromSliderValue(double sliderValue) {
        TradeAmount maxTradeAmount = checkNotNull(amountUseCase.getMaxTradeAmount(), "maxTradeAmount must not be null");
        TradeAmount tradeAmount = stateEngine.toTradeAmountFromSliderValue(maxTradeAmount, sliderValue);
        amountUseCase.setMaxTradeAmount(tradeAmount);
    }


    /* --------------------------------------------------------------------- */
    // Amount conversion
    /* --------------------------------------------------------------------- */

    public Monetary toInputAmount(TradeAmount tradeAmount, boolean includeUserSpecificTradeAmountLimit) {
        boolean useBaseCurrencyForAmountInput = amountUseCase.getUseBaseCurrencyForAmountInput();
        TradeAmountRange limits = getClampLimits(includeUserSpecificTradeAmountLimit);
        return amountMappingService.toInputAmount(tradeAmount, limits, useBaseCurrencyForAmountInput);
    }

    public Monetary toPassiveAmount(TradeAmount tradeAmount, boolean includeUserSpecificTradeAmountLimit) {
        boolean useBaseCurrencyForAmountInput = amountUseCase.getUseBaseCurrencyForAmountInput();
        TradeAmountRange limits = getClampLimits(includeUserSpecificTradeAmountLimit);
        return amountMappingService.toPassiveAmount(tradeAmount, limits, useBaseCurrencyForAmountInput);
    }


    /* --------------------------------------------------------------------- */
    // Delegate read methods
    /* --------------------------------------------------------------------- */

    @Override
    public Market getMarket() {
        return marketService.getMarket();
    }


    private TradeAmountRange getClampLimits(boolean includeUserSpecificTradeAmountLimit) {
        return stateEngine.getClampLimits(includeUserSpecificTradeAmountLimit);
    }
}
