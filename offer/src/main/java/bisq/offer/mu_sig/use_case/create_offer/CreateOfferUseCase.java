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
import bisq.account.accounts.Account;
import bisq.account.payment_method.PaymentMethod;
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
import bisq.offer.mu_sig.use_case.create_offer.amount.limits.AbsoluteAmountLimits;
import bisq.offer.mu_sig.use_case.create_offer.amount.limits.AmountLimits;
import bisq.offer.mu_sig.use_case.create_offer.amount.limits.PaymentMethodBasedAmountLimits;
import bisq.offer.mu_sig.use_case.create_offer.amount.limits.UserSpecificAmountLimits;
import bisq.offer.mu_sig.use_case.create_offer.direction.CreateOfferDirectionUseCase;
import bisq.offer.mu_sig.use_case.create_offer.market.CreateOfferMarketUseCase;
import bisq.offer.mu_sig.use_case.create_offer.payment_method.CreateOfferPaymentMethodUseCase;
import bisq.offer.mu_sig.use_case.create_offer.price.CreateOfferPriceUseCase;
import bisq.offer.mu_sig.use_case.dependencies.AccountsProvider;
import bisq.offer.mu_sig.use_case.dependencies.CreateOfferDraftCookieStore;
import bisq.offer.mu_sig.use_case.dependencies.DefaultAccountsProvider;
import bisq.offer.mu_sig.use_case.dependencies.DefaultCreateOfferDraftCookieStore;
import bisq.settings.SettingsService;
import com.google.common.collect.ImmutableMap;
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
    private final UserSpecificAmountLimits userSpecificAmountLimits;
    private final AmountLimits amountLimits;
    private final PaymentMethodBasedAmountLimits paymentMethodSpecificAmountLimits;
    private final AbsoluteAmountLimits absoluteAmountLimits;


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

        absoluteAmountLimits = new AbsoluteAmountLimits(marketPriceService);
        paymentMethodSpecificAmountLimits = new PaymentMethodBasedAmountLimits(marketPriceService);
        userSpecificAmountLimits = new UserSpecificAmountLimits(marketPriceService);
        amountLimits = new AmountLimits(absoluteAmountLimits, paymentMethodSpecificAmountLimits, userSpecificAmountLimits);

        marketService = new CreateOfferMarketUseCase();
        directionService = new CreateOfferDirectionUseCase(cookieStore);
        paymentMethodService = new CreateOfferPaymentMethodUseCase(accountsProvider);
        priceService = new CreateOfferPriceUseCase(marketPriceService, cookieStore);
        amountUseCase = new CreateOfferAmountUseCase(marketPriceService, cookieStore, amountLimits, amountMappingService);

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
                paymentMethodSpecificAmountLimits,
                tradeAmountConstraintsService,
                DEFAULT_TRADE_AMOUNT_IN_USD);

        stateUpdateHandler = new CreateOfferStateUpdateHandler(marketService,
                directionService,
                paymentMethodService,
                priceService,
                amountUseCase,
                marketPriceService,
                paymentMethodSpecificAmountLimits,
                tradeAmountConstraintsService,
                stateEngine);
    }


    /* --------------------------------------------------------------------- */
    // Lifecycle
    /* --------------------------------------------------------------------- */

    public void initialize(Market market) {
        checkNotNull(market, "Market must not be null");

        amountLimits.initialize();
        marketService.initialize(market);
        directionService.initialize();
        paymentMethodService.initialize(market);
        priceService.initialize(market);
        amountUseCase.initialize(market);

        stateUpdateHandler.initialize();

        stateEngine.initialize();

        registerObservers();
        updateAmountLimitSources();
    }

    private void registerObservers() {
        pin(marketService.addMarketListener(market -> {
            updateAbsoluteAmountLimits(market, priceService.getPriceQuote());
            updateUserSpecificAmountLimits(market,
                    directionService.getDisplayDirection(),
                    priceService.getPriceQuote());
        }));
        pin(directionService.addDisplayDirectionListener(displayDirection -> {
            updateUserSpecificAmountLimits(marketService.getMarket(),
                    displayDirection,
                    priceService.getPriceQuote());
        }));
        pin(priceService.addPriceQuoteListener(priceQuote -> {
            updateAbsoluteAmountLimits(marketService.getMarket(), priceQuote);
            updateUserSpecificAmountLimits(marketService.getMarket(),
                    directionService.getDisplayDirection(),
                    priceQuote);

            //todo
            stateEngine.applyPriceQuoteChanged(priceQuote);
        }));

        pin(paymentMethodService.accountByPaymentMethodObservable().addObserver(() -> {
            updatePaymentMethodSpecificAmountLimits(marketService.getMarket(),
                    priceService.getPriceQuote(),
                    paymentMethodService.getAccountByPaymentMethod());
        }));
    }

    private void updateAmountLimitSources() {
        Market market = marketService.getMarket();
        PriceQuote priceQuote = priceService.getPriceQuote();
        updateAbsoluteAmountLimits(market, priceQuote);
        updateUserSpecificAmountLimits(market,
                directionService.getDisplayDirection(),
                priceQuote);
        updatePaymentMethodSpecificAmountLimits(market,
                priceQuote,
                paymentMethodService.getAccountByPaymentMethod());
    }

    private void updatePaymentMethodSpecificAmountLimits(Market market,
                                                         PriceQuote priceQuote,
                                                         ImmutableMap<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethod) {
        if (market != null && priceQuote != null && accountByPaymentMethod != null) {
            paymentMethodSpecificAmountLimits.update(market, priceQuote, accountByPaymentMethod);
        }
    }

    private void updateUserSpecificAmountLimits(Market market, Direction displayDirection, PriceQuote priceQuote) {
        if (market != null && displayDirection != null && priceQuote != null) {
            userSpecificAmountLimits.update(market, displayDirection, priceQuote);
        }
    }

    private void updateAbsoluteAmountLimits(Market market, PriceQuote priceQuote) {
        if (market != null && priceQuote != null) {
            absoluteAmountLimits.update(market, priceQuote);
        }
    }

    @Override
    public void dispose() {
        super.dispose();

        amountLimits.dispose();
        marketService.dispose();
        directionService.dispose();
        paymentMethodService.dispose();
        priceService.dispose();
        amountUseCase.dispose();

        stateUpdateHandler.dispose();
        stateEngine.dispose();
    }


    /* --------------------------------------------------------------------- */
    // Mutation API
    /* --------------------------------------------------------------------- */


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
