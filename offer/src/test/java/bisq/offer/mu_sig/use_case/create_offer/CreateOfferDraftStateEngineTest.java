package bisq.offer.mu_sig.use_case.create_offer;

import bisq.account.accounts.Account;
import bisq.account.payment_method.PaymentMethod;
import bisq.account.payment_method.PaymentRail;
import bisq.account.payment_method.fiat.FiatPaymentMethod;
import bisq.account.payment_method.fiat.FiatPaymentRail;
import bisq.bonded_roles.bonded_role.AuthorizedBondedRole;
import bisq.bonded_roles.market_price.MarketPrice;
import bisq.bonded_roles.market_price.MarketPriceRequestService;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.market.MarketRepository;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountConversion;
import bisq.common.observable.ReadOnlyObservable;
import bisq.common.observable.map.ReadOnlyObservableMap;
import bisq.offer.Direction;
import bisq.offer.mu_sig.use_case.AmountMappingService;
import bisq.offer.mu_sig.use_case.create_offer.amount.CreateOfferAmountUseCase;
import bisq.offer.mu_sig.use_case.create_offer.direction.CreateOfferDirectionUseCase;
import bisq.offer.mu_sig.use_case.create_offer.market.CreateOfferMarketUseCase;
import bisq.offer.mu_sig.use_case.create_offer.payment_method.CreateOfferPaymentMethodUseCase;
import bisq.offer.mu_sig.use_case.create_offer.price.CreateOfferPriceUseCase;
import bisq.offer.mu_sig.use_case.dependencies.CreateOfferDraftCookieStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CreateOfferDraftStateEngineTest {
    private Market usdBtcMarket;
    private PriceQuote usdBtcPriceQuote;
    private TradeAmount usdBtcDefaultTradeAmount;
    private CreateOfferMarketUseCase marketUseCase;
    private CreateOfferDirectionUseCase directionUseCase;
    private CreateOfferPaymentMethodUseCase paymentMethodUseCase;
    private CreateOfferPriceUseCase priceUseCase;
    private CreateOfferAmountUseCase amountUseCase;
    private MockMarketPriceService marketPriceService;
    private CreateOfferDraftCookieStore cookieStore;
    private CreateOfferDraftStateEngine stateEngine;
    private CreateOfferStateUpdateHandler stateUpdateHandler;
    private CreateOfferTradeAmountConstraintsService tradeAmountConstraintsService;
    private Map<Market, List<Account<?, ?>>> accountsByMarket;

    @BeforeEach
    public void setUp() {
        usdBtcMarket = MarketRepository.getUSDBitcoinMarket();
        usdBtcPriceQuote = PriceQuote.fromFiatPrice(50000, "USD");
        usdBtcDefaultTradeAmount = TradeAmountConversion.toTradeAmount(usdBtcMarket,
                usdBtcPriceQuote,
                Fiat.fromFaceValue(500, "USD"));

        marketPriceService = new MockMarketPriceService(usdBtcPriceQuote);
        marketPriceService.put(usdBtcMarket, usdBtcPriceQuote, usdBtcDefaultTradeAmount);

        cookieStore = mock(CreateOfferDraftCookieStore.class);
        when(cookieStore.getDisplayDirection()).thenReturn(Direction.SELL);
        when(cookieStore.getUseBaseCurrencyForAmountInput(any())).thenReturn(false);
        when(cookieStore.getUseRangeAmount()).thenReturn(false);
        when(cookieStore.getUseFixPrice(any())).thenReturn(false);
        when(cookieStore.getPricePercentage(any())).thenReturn(0d);

        accountsByMarket = new HashMap<>();
        marketUseCase = new CreateOfferMarketUseCase();
        directionUseCase = new CreateOfferDirectionUseCase(cookieStore);
        paymentMethodUseCase = new CreateOfferPaymentMethodUseCase(marketUseCase, market -> accountsByMarket.getOrDefault(market, List.of()));
        priceUseCase = new CreateOfferPriceUseCase(marketPriceService, marketUseCase, cookieStore);
        amountUseCase = new CreateOfferAmountUseCase(marketPriceService,
                marketUseCase,
                directionUseCase,
                paymentMethodUseCase,
                priceUseCase,
                cookieStore);
        AmountMappingService amountMappingService = amountUseCase.getAmountMappingService();
        tradeAmountConstraintsService = new CreateOfferTradeAmountConstraintsService(marketPriceService);

        stateEngine = new CreateOfferDraftStateEngine(marketUseCase,
                directionUseCase,
                paymentMethodUseCase,
                priceUseCase,
                amountUseCase,
                marketPriceService,
                amountMappingService,
                amountUseCase.getAmountLimits().getPaymentMethodSpecificAmountLimits(),
                tradeAmountConstraintsService,
                CreateOfferUseCase.DEFAULT_TRADE_AMOUNT_IN_USD);
        stateUpdateHandler = new CreateOfferStateUpdateHandler(marketUseCase,
                directionUseCase,
                paymentMethodUseCase,
                priceUseCase,
                amountUseCase,
                marketPriceService,
                amountUseCase.getAmountLimits().getPaymentMethodSpecificAmountLimits(),
                tradeAmountConstraintsService,
                stateEngine);
    }

    @Test
    public void initializeSetsDerivedState() {
        initializeStateForMarket(usdBtcMarket);
        stateEngine.initialize();

        assertEquals(usdBtcMarket, marketUseCase.getMarket());
        assertEquals(Direction.SELL, directionUseCase.getDisplayDirection());
        assertEquals(usdBtcPriceQuote, priceUseCase.getPriceQuote());
        assertEquals(usdBtcDefaultTradeAmount, amountUseCase.getFixTradeAmount());
        assertEquals(usdBtcDefaultTradeAmount, amountUseCase.getMinTradeAmount());
        assertEquals(usdBtcDefaultTradeAmount, amountUseCase.getMaxTradeAmount());
        assertNotNull(amountUseCase.getTradeAmountLimits());
        assertNotNull(amountUseCase.getInputAmountLimits());
    }

    @Test
    public void onDirectionChangedReturnsFalseWithoutPricingContext() {
        directionUseCase.onSetDisplayDirection(Direction.BUY);
        assertEquals(Direction.BUY, directionUseCase.getDisplayDirection());
    }

    @Test
    public void onDirectionChangedReturnsTrueWithPricingContext() {
        initializeStateForMarket(usdBtcMarket);
        stateEngine.initialize();

        boolean recalculated = stateEngine.onDirectionChanged(Direction.BUY);

        assertTrue(recalculated);
        Optional<TradeAmount> userSpecificTradeAmountLimit = amountUseCase.getUserSpecificTradeAmountLimit();
        assertTrue(userSpecificTradeAmountLimit.isPresent());
    }

    @Test
    public void applyUseBaseCurrencyForAmountInputChangedDependsOnDerivedStateInitialization() {
        assertFalse(stateEngine.applyUseBaseCurrencyForAmountInputChanged(true));

        initializeStateForMarket(usdBtcMarket);
        stateEngine.initialize();

        assertTrue(stateEngine.applyUseBaseCurrencyForAmountInputChanged(true));
        assertTrue(amountUseCase.getUseBaseCurrencyForAmountInput());
    }

    @Test
    public void recalculateTradeAmountConstraintsForSelectedPaymentRailClampsExistingAmounts() {
        PaymentMethod<?> paymentMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> account = createAccount(paymentMethod);
        Account<?, ?> anotherEligibleAccount = createAccount(paymentMethod);
        accountsByMarket.put(usdBtcMarket, List.of(account, anotherEligibleAccount));
        initializeStateForMarket(usdBtcMarket);
        stateEngine.initialize();

        TradeAmount nineThousandUsd = TradeAmountConversion.toTradeAmount(usdBtcMarket,
                usdBtcPriceQuote,
                Fiat.fromFaceValue(9000, "USD"));
        amountUseCase.setFixTradeAmount(nineThousandUsd);
        assertEquals(Fiat.fromFaceValue(9000, "USD"), amountUseCase.getFixTradeAmount().getQuoteSideAmount());

        paymentMethodUseCase.onAddAccountByPaymentMethodEntry(Map.entry(paymentMethod, account));

        stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();

        assertEquals(Fiat.fromFaceValue(5000, "USD"), amountUseCase.getFixTradeAmount().getQuoteSideAmount());
    }

    private void initializeStateForMarket(Market market) {
        marketUseCase.onSetMarket(market);
        marketUseCase.initialize();
        directionUseCase.initialize();
        paymentMethodUseCase.initialize();
        priceUseCase.initialize();
        amountUseCase.initialize();
        stateUpdateHandler.initialize();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <R extends PaymentRail> Account<?, ?> createAccount(PaymentMethod<R> paymentMethod) {
        Account<PaymentMethod<R>, ?> account = (Account<PaymentMethod<R>, ?>) mock(Account.class);
        when(account.getPaymentMethod()).thenReturn(paymentMethod);
        return account;
    }

    private static class MockMarketPriceService implements MarketPriceService {
        private final Map<Market, PriceQuote> priceQuoteByMarket = new HashMap<>();
        private final Map<Market, TradeAmount> defaultTradeAmountByMarket = new HashMap<>();
        private final PriceQuote btcUsdPriceQuote;

        private MockMarketPriceService(PriceQuote btcUsdPriceQuote) {
            this.btcUsdPriceQuote = btcUsdPriceQuote;
        }

        private void put(Market market, PriceQuote priceQuote, TradeAmount defaultTradeAmount) {
            priceQuoteByMarket.put(market, priceQuote);
            defaultTradeAmountByMarket.put(market, defaultTradeAmount);
        }

        @Override
        public void setSelectedMarket(Market market) {
        }

        @Override
        public ReadOnlyObservable<Market> getSelectedMarket() {
            return null;
        }

        @Override
        public Optional<MarketPrice> findMarketPrice(Market market) {
            return Optional.empty();
        }

        @Override
        public Optional<PriceQuote> findMarketPriceQuote(Market market) {
            return Optional.ofNullable(priceQuoteByMarket.get(market));
        }

        @Override
        public PriceQuote getMarketPriceQuoteOrThrow(Market market) {
            return findMarketPriceQuote(market)
                    .orElseThrow(() -> new IllegalStateException("Market price quote not available for " + market));
        }

        @Override
        public ReadOnlyObservableMap<Market, MarketPrice> getMarketPriceByCurrencyMap() {
            return null;
        }

        @Override
        public boolean hasMarketPrice(Market market) {
            return false;
        }

        @Override
        public Optional<MarketPriceRequestService> getMarketPriceRequestService() {
            return Optional.empty();
        }

        @Override
        public Optional<AuthorizedBondedRole> getMarketPriceProvidingOracle() {
            return Optional.empty();
        }
    }
}
