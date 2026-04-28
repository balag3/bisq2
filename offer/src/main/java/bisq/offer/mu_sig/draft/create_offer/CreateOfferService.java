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

import bisq.account.AccountService;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.Monetary;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.offer.Direction;
import bisq.offer.mu_sig.draft.AmountMappingService;
import bisq.offer.mu_sig.draft.DraftOfferService;
import bisq.offer.mu_sig.draft.create_offer.amount.CreateOfferAmountService;
import bisq.offer.mu_sig.draft.create_offer.direction.CreateOfferDirectionService;
import bisq.offer.mu_sig.draft.create_offer.market.CreateOfferMarketService;
import bisq.offer.mu_sig.draft.create_offer.payment_method.CreateOfferPaymentMethodService;
import bisq.offer.mu_sig.draft.create_offer.price.CreateOfferPriceService;
import bisq.offer.mu_sig.draft.dependencies.AccountsProvider;
import bisq.offer.mu_sig.draft.dependencies.CreateOfferDraftCookieStore;
import bisq.offer.mu_sig.draft.dependencies.DefaultAccountsProvider;
import bisq.offer.mu_sig.draft.dependencies.DefaultCreateOfferDraftCookieStore;
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
public class CreateOfferService extends DraftOfferService {
    public static final Fiat DEFAULT_TRADE_AMOUNT_IN_USD = Fiat.fromFaceValue(500, "USD");
    @Getter
    private final CreateOfferMarketService marketService;
    @Getter
    private final CreateOfferDirectionService directionService;
    @Getter
    private final CreateOfferPriceService priceService;
    @Getter
    private final CreateOfferAmountService amountService;

    private final CreateOfferDraftCookieStore cookieStore;
    private final AmountMappingService amountMappingService;
    @Getter
    private final CreateOfferPaymentMethodService paymentMethodService;
    private final CreateOfferDraftStateEngine stateEngine;
    private final CreateOfferStateUpdateHandler stateUpdateHandler;
    private final CreateOfferTradeAmountConstraintsService tradeAmountConstraintsService;


    /* --------------------------------------------------------------------- */
    // Construction
    /* --------------------------------------------------------------------- */

    public CreateOfferService(MarketPriceService marketPriceService,
                              SettingsService settingsService,
                              AccountService accountService) {
        this(marketPriceService,
                new DefaultCreateOfferDraftCookieStore(settingsService),
                new DefaultAccountsProvider(accountService));
    }

    CreateOfferService(MarketPriceService marketPriceService,
                       CreateOfferDraftCookieStore cookieStore,
                       AccountsProvider accountsProvider) {
        checkNotNull(accountsProvider, "accountsProvider must not be null");
        checkNotNull(marketPriceService, "marketPriceProvider must not be null");
        this.cookieStore = checkNotNull(cookieStore, "cookieStore must not be null");

        amountMappingService = new AmountMappingService();
        tradeAmountConstraintsService = new CreateOfferTradeAmountConstraintsService(marketPriceService);

        marketService = new CreateOfferMarketService();
        directionService = new CreateOfferDirectionService(cookieStore);
        paymentMethodService = new CreateOfferPaymentMethodService(accountsProvider);
        priceService = new CreateOfferPriceService(marketPriceService, cookieStore);
        amountService = new CreateOfferAmountService(marketPriceService, cookieStore, amountMappingService);


        marketService.marketObservable().addObserver(market -> {
            if (market != null) {
                paymentMethodService.handleMarketChanged(market);
            }
        });


        stateEngine = new CreateOfferDraftStateEngine(marketService,
                directionService,
                paymentMethodService,
                priceService,
                amountService,
                marketPriceService,
                amountMappingService,
                tradeAmountConstraintsService,
                DEFAULT_TRADE_AMOUNT_IN_USD);

        stateUpdateHandler = new CreateOfferStateUpdateHandler(marketService,
                directionService,
                paymentMethodService,
                priceService,
                amountService,
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
        amountService.initialize(market);
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

    public void setUseRangeAmount(boolean useRangeAmount) {
        if (useRangeAmount == amountService.getUseRangeAmount()) {
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
        amountService.setFixTradeAmount(tradeAmount);
    }

    public void setMinTradeAmountFromInputAmount(Monetary amount) {
        TradeAmount tradeAmount = stateEngine.toClampedTradeAmount(amount);
        amountService.setMinTradeAmount(tradeAmount);
    }

    public void setMaxTradeAmountFromInputAmount(Monetary amount) {
        TradeAmount tradeAmount = stateEngine.toClampedTradeAmount(amount);
        amountService.setMaxTradeAmount(tradeAmount);
    }

    public void setFixTradeAmountFromSliderValue(double sliderValue) {
        TradeAmount fixTradeAmount = checkNotNull(amountService.getFixTradeAmount(), "fixTradeAmount must not be null");
        TradeAmount tradeAmount = stateEngine.toTradeAmountFromSliderValue(fixTradeAmount, sliderValue);
        amountService.setFixTradeAmount(tradeAmount);
    }

    public void setMinTradeAmountFromSliderValue(double sliderValue) {
        TradeAmount minTradeAmount = checkNotNull(amountService.getMinTradeAmount(), "minTradeAmount must not be null");
        TradeAmount tradeAmount = stateEngine.toTradeAmountFromSliderValue(minTradeAmount, sliderValue);
        amountService.setMinTradeAmount(tradeAmount);
    }

    public void setMaxTradeAmountFromSliderValue(double sliderValue) {
        TradeAmount maxTradeAmount = checkNotNull(amountService.getMaxTradeAmount(), "maxTradeAmount must not be null");
        TradeAmount tradeAmount = stateEngine.toTradeAmountFromSliderValue(maxTradeAmount, sliderValue);
        amountService.setMaxTradeAmount(tradeAmount);
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
