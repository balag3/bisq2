package bisq.offer.mu_sig.draft.create_offer;

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
import bisq.offer.mu_sig.draft.AmountMappingService;
import bisq.offer.mu_sig.draft.create_offer.amount.CreateOfferAmountService;
import bisq.offer.mu_sig.draft.create_offer.direction.CreateOfferDirectionService;
import bisq.offer.mu_sig.draft.create_offer.market.CreateOfferMarketService;
import bisq.offer.mu_sig.draft.create_offer.payment_method.CreateOfferPaymentMethodService;
import bisq.offer.mu_sig.draft.create_offer.price.CreateOfferPriceService;
import bisq.offer.mu_sig.draft.dependencies.CreateOfferDraftCookieStore;
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
    private CreateOfferMarketService createOfferMarketService;
    private CreateOfferDirectionService createOfferDirectionService;
    private CreateOfferPaymentMethodService createOfferPaymentMethodService;
    private CreateOfferPriceService createOfferPriceService;
    private CreateOfferAmountService createOfferAmountService;
    private MockMarketPriceService marketPriceService;
    private CreateOfferDraftCookieStore cookieStore;
    private CreateOfferDraftStateEngine stateEngine;

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
        when(cookieStore.getDirection()).thenReturn(Direction.SELL);
        when(cookieStore.getUseBaseCurrencyForAmountInput(any())).thenReturn(false);
        when(cookieStore.getUseRangeAmount()).thenReturn(false);
        when(cookieStore.getUseFixPrice(any())).thenReturn(false);
        when(cookieStore.getPricePercentage(any())).thenReturn(0d);
        when(cookieStore.getFixPrice(any())).thenReturn(Optional.empty());

        createOfferMarketService = new CreateOfferMarketService();
        createOfferDirectionService = new CreateOfferDirectionService(cookieStore);
        createOfferPriceService = new CreateOfferPriceService(marketPriceService, cookieStore);
        createOfferAmountService = new CreateOfferAmountService(marketPriceService, cookieStore);
        createOfferPaymentMethodService = new CreateOfferPaymentMethodService(market -> List.of());

        stateEngine = new CreateOfferDraftStateEngine(createOfferMarketService,
                createOfferDirectionService,
                createOfferPaymentMethodService,
                createOfferPriceService,
                createOfferAmountService,
                marketPriceService,
                new AmountMappingService(),
                CreateOfferService.DEFAULT_TRADE_AMOUNT_IN_USD);
    }

    @Test
    public void initializeSetsDerivedState() {
        initializeStateForMarket(usdBtcMarket);
        stateEngine.initialize();

        assertEquals(usdBtcMarket, createOfferMarketService.getMarket());
        assertEquals(Direction.SELL, createOfferDirectionService.getDirection());
        assertEquals(usdBtcPriceQuote, createOfferPriceService.getPriceQuote());
        assertEquals(usdBtcDefaultTradeAmount, createOfferAmountService.getFixTradeAmount());
        assertEquals(usdBtcDefaultTradeAmount, createOfferAmountService.getMinTradeAmount());
        assertEquals(usdBtcDefaultTradeAmount, createOfferAmountService.getMaxTradeAmount());
        assertNotNull(createOfferAmountService.getTradeAmountLimits());
        assertNotNull(createOfferAmountService.getInputAmountLimits());
    }

    @Test
    public void applyDirectionChangedReturnsFalseWithoutPricingContext() {
        boolean recalculated = stateEngine.applyDirectionChanged(Direction.BUY);

        assertFalse(recalculated);
        assertEquals(Direction.BUY, createOfferDirectionService.getDirection());
    }

    @Test
    public void applyDirectionChangedReturnsTrueWithPricingContext() {
        initializeStateForMarket(usdBtcMarket);
        stateEngine.initialize();

        boolean recalculated = stateEngine.applyDirectionChanged(Direction.BUY);

        assertTrue(recalculated);
        Optional<TradeAmount> userSpecificTradeAmountLimit = createOfferAmountService.getUserSpecificTradeAmountLimit();
        assertTrue(userSpecificTradeAmountLimit.isPresent());
    }

    @Test
    public void applyUseBaseCurrencyForAmountInputChangedDependsOnDerivedStateInitialization() {
        assertFalse(stateEngine.applyUseBaseCurrencyForAmountInputChanged(true));

        initializeStateForMarket(usdBtcMarket);
        stateEngine.initialize();

        assertTrue(stateEngine.applyUseBaseCurrencyForAmountInputChanged(true));
        assertTrue(createOfferAmountService.getUseBaseCurrencyForAmountInput());
    }

    @Test
    public void recalculateTradeAmountConstraintsForSelectedPaymentRailClampsExistingAmounts() {
        initializeStateForMarket(usdBtcMarket);
        stateEngine.initialize();

        TradeAmount nineThousandUsd = TradeAmountConversion.toTradeAmount(usdBtcMarket,
                usdBtcPriceQuote,
                Fiat.fromFaceValue(9000, "USD"));
        stateEngine.setFixTradeAmount(nineThousandUsd);
        assertEquals(Fiat.fromFaceValue(9000, "USD"), createOfferAmountService.getFixTradeAmount().getQuoteSideAmount());

        PaymentMethod<?> paymentMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> account = createAccount(paymentMethod);
        createOfferPaymentMethodService.putSelectedAccountByPaymentMethod(paymentMethod, account);

        stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();

        assertEquals(Fiat.fromFaceValue(5000, "USD"), createOfferAmountService.getFixTradeAmount().getQuoteSideAmount());
    }

    private void initializeStateForMarket(Market market) {
        createOfferMarketService.initialize(market);
        createOfferDirectionService.initialize();
        createOfferPaymentMethodService.initialize(market);
        createOfferPriceService.initialize(market);
        createOfferAmountService.initialize(market);
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
