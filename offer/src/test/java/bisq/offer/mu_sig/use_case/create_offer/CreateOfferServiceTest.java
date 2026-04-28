package bisq.offer.mu_sig.use_case.create_offer;

import bisq.account.accounts.Account;
import bisq.account.payment_method.PaymentMethod;
import bisq.account.payment_method.fiat.FiatPaymentMethod;
import bisq.account.payment_method.fiat.FiatPaymentRail;
import bisq.bonded_roles.bonded_role.AuthorizedBondedRole;
import bisq.bonded_roles.market_price.MarketPrice;
import bisq.bonded_roles.market_price.MarketPriceRequestService;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.market.MarketRepository;
import bisq.common.monetary.Coin;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.MonetaryRange;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountConversion;
import bisq.common.monetary.TradeAmountRange;
import bisq.common.observable.ReadOnlyObservable;
import bisq.common.observable.map.ReadOnlyObservableMap;
import bisq.offer.Direction;
import bisq.offer.mu_sig.use_case.create_offer.amount.CreateOfferAmountUseCase;
import bisq.offer.mu_sig.use_case.create_offer.direction.CreateOfferDirectionUseCase;
import bisq.offer.mu_sig.use_case.create_offer.market.CreateOfferMarketUseCase;
import bisq.offer.mu_sig.use_case.create_offer.payment_method.CreateOfferPaymentMethodUseCase;
import bisq.offer.mu_sig.use_case.create_offer.payment_method.PaymentMethodSelectionResult;
import bisq.offer.mu_sig.use_case.create_offer.payment_method.PaymentMethodSelectionStatus;
import bisq.offer.mu_sig.use_case.create_offer.price.CreateOfferPriceUseCase;
import bisq.offer.mu_sig.use_case.dependencies.AccountsProvider;
import bisq.offer.mu_sig.use_case.dependencies.CreateOfferDraftCookieStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CreateOfferServiceTest {
    private Market defaultMarket;
    private Market usdBtcMarket;
    private Market xmrBtcMarket;
    private PriceQuote defaultMarketPriceQuote;
    private PriceQuote usdBtcPriceQuote;
    private PriceQuote xmrBtcPriceQuote;
    private TradeAmount defaultMarketDefaultTradeAmount;
    private TradeAmount usdBtcDefaultTradeAmount;
    private TradeAmount xmrBtcDefaultTradeAmount;
    private MockMarketPriceService marketPriceService;
    private FakeCookieStore cookieStore;
    private FakeAccountsProvider accountsProvider;
    private CreateOfferUseCase createOfferUseCase;
    private CreateOfferPaymentMethodUseCase paymentMethodService;
    private CreateOfferDirectionUseCase directionUseCase;
    private CreateOfferPriceUseCase priceUseCase;
    private CreateOfferAmountUseCase amountUseCase;
    private CreateOfferMarketUseCase marketUseCase;

    @BeforeEach
    public void setUp() {
        defaultMarket = MarketRepository.getDefaultBtcFiatMarket();
        usdBtcMarket = MarketRepository.getUSDBitcoinMarket();
        xmrBtcMarket = MarketRepository.getXmrBtcMarket();

        defaultMarketPriceQuote = PriceQuote.fromFiatPrice(50000, defaultMarket.getQuoteCurrencyCode());
        usdBtcPriceQuote = PriceQuote.fromFiatPrice(50000, "USD");
        xmrBtcPriceQuote = PriceQuote.fromPrice(0.005, "XMR", "BTC");

        defaultMarketDefaultTradeAmount = TradeAmountConversion.toTradeAmount(defaultMarket,
                defaultMarketPriceQuote,
                Fiat.fromFaceValue(500, defaultMarket.getQuoteCurrencyCode()));
        usdBtcDefaultTradeAmount = TradeAmountConversion.toTradeAmount(usdBtcMarket,
                usdBtcPriceQuote,
                Fiat.fromFaceValue(500, "USD"));
        xmrBtcDefaultTradeAmount = TradeAmountConversion.toTradeAmount(xmrBtcMarket,
                xmrBtcPriceQuote,
                Coin.asBtcFromFaceValue(0.01));

        marketPriceService = new MockMarketPriceService(usdBtcPriceQuote);
        marketPriceService.put(defaultMarket, defaultMarketPriceQuote, defaultMarketDefaultTradeAmount);
        marketPriceService.put(usdBtcMarket, usdBtcPriceQuote, usdBtcDefaultTradeAmount);
        marketPriceService.put(xmrBtcMarket, xmrBtcPriceQuote, xmrBtcDefaultTradeAmount);

        cookieStore = new FakeCookieStore(Direction.SELL, false, true, false);
        accountsProvider = new FakeAccountsProvider();
        createOfferUseCase = new CreateOfferUseCase(marketPriceService, cookieStore, accountsProvider);
        marketUseCase = createOfferUseCase.getMarketService();
        directionUseCase = createOfferUseCase.getDirectionService();
        paymentMethodService = createOfferUseCase.getPaymentMethodService();
        priceUseCase = createOfferUseCase.getPriceService();
        amountUseCase = createOfferUseCase.getAmountUseCase();
    }

    @Test
    public void initializePopulatesDraftAndUpdatesPaymentMethods() {
        createOfferUseCase.initialize(defaultMarket);

        assertEquals(defaultMarket, createOfferUseCase.getMarket());
        assertEquals(Direction.SELL, directionUseCase.getDisplayDirection());
        assertEquals(defaultMarketPriceQuote, priceUseCase.getPriceQuote());
        assertEquals(defaultMarketDefaultTradeAmount, amountUseCase.getFixTradeAmount());
        assertEquals(defaultMarketDefaultTradeAmount, amountUseCase.getMinTradeAmount());
        assertEquals(defaultMarketDefaultTradeAmount, amountUseCase.getMaxTradeAmount());
        assertNotNull(amountUseCase.getTradeAmountLimits());
        assertNotNull(amountUseCase.getInputAmountLimits());
        assertEquals(List.of(defaultMarket), accountsProvider.requestedMarkets);
    }

    @Test
    public void setMarketResetsPriceAndAmountsDeterministically() {
        createOfferUseCase.initialize(defaultMarket);
        marketUseCase.onSelectMarket(xmrBtcMarket);

        assertEquals(xmrBtcMarket, createOfferUseCase.getMarket());
        assertEquals(xmrBtcPriceQuote, priceUseCase.getPriceQuote());
        assertEquals(xmrBtcDefaultTradeAmount, amountUseCase.getFixTradeAmount());
        assertEquals(xmrBtcDefaultTradeAmount, amountUseCase.getMinTradeAmount());
        assertEquals(xmrBtcDefaultTradeAmount, amountUseCase.getMaxTradeAmount());
        assertNotNull(amountUseCase.getTradeAmountLimits());
        assertNotNull(amountUseCase.getInputAmountLimits());
        assertEquals(List.of(defaultMarket, xmrBtcMarket), accountsProvider.requestedMarkets);
    }

    @Test
    public void setPriceQuoteKeepsQuoteInputAmountConstant() {
        createOfferUseCase.initialize(usdBtcMarket);
        createOfferUseCase.setUseBaseCurrencyForAmountInput(false);
        createOfferUseCase.setFixTradeAmountFromInputAmount(Fiat.fromFaceValue(500, "USD"));

        TradeAmount fixTradeAmountBefore = amountUseCase.getFixTradeAmount();
        createOfferUseCase.setPriceQuote(PriceQuote.fromFiatPrice(40000, "USD"));
        TradeAmount fixTradeAmountAfter = amountUseCase.getFixTradeAmount();

        assertEquals(fixTradeAmountBefore.getQuoteSideAmount(), fixTradeAmountAfter.getQuoteSideAmount());
        assertEquals(Coin.asBtcFromFaceValue(0.0125), fixTradeAmountAfter.getBaseSideAmount());
    }

    @Test
    public void setDirectionRecomputesUserSpecificLimitAndKeepsAmountsStable() {
        createOfferUseCase.initialize(usdBtcMarket);
        TradeAmount fixTradeAmountBefore = amountUseCase.getFixTradeAmount();
        Direction persistedDirection = directionUseCase.getDisplayDirection();
        directionUseCase.onSelectDisplayDirection(Direction.BUY);
        Optional<TradeAmount> buyLimit = amountUseCase.getUserSpecificTradeAmountLimit();

        directionUseCase.onSelectDisplayDirection(Direction.SELL);

        assertTrue(buyLimit.isPresent());
        assertTrue(amountUseCase.getUserSpecificTradeAmountLimit().isEmpty());
        assertEquals(fixTradeAmountBefore, amountUseCase.getFixTradeAmount());
        assertEquals(List.of(persistedDirection, Direction.BUY, Direction.SELL), cookieStore.persistedDirections);
    }

    @Test
    public void selectedAccountsUseMostRestrictivePaymentRailLimit() {
        createOfferUseCase.initialize(usdBtcMarket);

        PaymentMethod<?> veryLowRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ADVANCED_CASH);
        PaymentMethod<?> moderateRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> veryLowRiskAccount = createAccount(veryLowRiskMethod);
        Account<?, ?> moderateRiskAccount = createAccount(moderateRiskMethod);

        paymentMethodService.onAddAccountByPaymentMethodEntry(Map.entry(veryLowRiskMethod, veryLowRiskAccount));
        assertEquals(Fiat.fromFaceValue(10000, "USD"),
                amountUseCase.getTradeAmountLimits().getMax().getQuoteSideAmount());

        paymentMethodService.onAddAccountByPaymentMethodEntry(Map.entry(moderateRiskMethod, moderateRiskAccount));
        assertEquals(Fiat.fromFaceValue(5000, "USD"),
                amountUseCase.getTradeAmountLimits().getMax().getQuoteSideAmount());
    }

    @Test
    public void selectedAccountLimitChangeClampsExistingAmounts() {
        createOfferUseCase.initialize(usdBtcMarket);
        createOfferUseCase.setUseBaseCurrencyForAmountInput(false);
        createOfferUseCase.setFixTradeAmountFromInputAmount(Fiat.fromFaceValue(9000, "USD"));

        PaymentMethod<?> veryLowRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ADVANCED_CASH);
        PaymentMethod<?> moderateRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> veryLowRiskAccount = createAccount(veryLowRiskMethod);
        Account<?, ?> moderateRiskAccount = createAccount(moderateRiskMethod);

        paymentMethodService.onAddAccountByPaymentMethodEntry(Map.entry(veryLowRiskMethod, veryLowRiskAccount));
        assertEquals(Fiat.fromFaceValue(9000, "USD"), amountUseCase.getFixTradeAmount().getQuoteSideAmount());

        paymentMethodService.onAddAccountByPaymentMethodEntry(Map.entry(moderateRiskMethod, moderateRiskAccount));
        assertEquals(Fiat.fromFaceValue(5000, "USD"), amountUseCase.getFixTradeAmount().getQuoteSideAmount());
    }

    @Test
    public void setDirectionWithCurrentValueIsNoOp() {
        createOfferUseCase.initialize(usdBtcMarket);
        Direction persistedDirection = directionUseCase.getDisplayDirection();
        TradeAmount fixTradeAmountBefore = amountUseCase.getFixTradeAmount();
        directionUseCase.onSelectDisplayDirection(persistedDirection.mirror());

        assertEquals(fixTradeAmountBefore, amountUseCase.getFixTradeAmount());
        assertEquals(List.of(persistedDirection, persistedDirection.mirror()), cookieStore.persistedDirections);
    }

    @Test
    public void setPriceQuoteWithCurrentValueIsNoOp() {
        createOfferUseCase.initialize(usdBtcMarket);
        int recalculationCountBefore = marketPriceService.btcUsdPriceQuoteRequests;

        createOfferUseCase.setPriceQuote(priceUseCase.getPriceQuote());

        assertEquals(recalculationCountBefore, marketPriceService.btcUsdPriceQuoteRequests);
    }

    @Test
    public void setMarketWithCurrentValueIsNoOp() {
        createOfferUseCase.initialize(defaultMarket);

        marketUseCase.onSelectMarket(defaultMarket);

        assertEquals(List.of(defaultMarket), accountsProvider.requestedMarkets);
    }

    @Test
    public void selectingSameAccountTwiceDoesNotRecalculateConstraints() {
        createOfferUseCase.initialize(usdBtcMarket);

        PaymentMethod<?> moderateRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> moderateRiskAccount = createAccount(moderateRiskMethod);

        paymentMethodService.onAddAccountByPaymentMethodEntry(Map.entry(moderateRiskMethod, moderateRiskAccount));
        int recalculationCountAfterFirstSelection = marketPriceService.btcUsdPriceQuoteRequests;

        paymentMethodService.onAddAccountByPaymentMethodEntry(Map.entry(moderateRiskMethod, moderateRiskAccount));

        assertEquals(recalculationCountAfterFirstSelection, marketPriceService.btcUsdPriceQuoteRequests);
    }

    @Test
    public void setUseBaseCurrencyForAmountInputWithCurrentValueIsNoOp() {
        createOfferUseCase.initialize(usdBtcMarket);

        createOfferUseCase.setUseBaseCurrencyForAmountInput(false);

        assertTrue(cookieStore.persistedInputModes.isEmpty());
    }

    @Test
    public void setUseBaseCurrencyForAmountInputWithDifferentValuePersistsPreference() {
        createOfferUseCase.initialize(usdBtcMarket);

        createOfferUseCase.setUseBaseCurrencyForAmountInput(true);

        assertEquals(List.of(new InputModePreference(usdBtcMarket, true)), cookieStore.persistedInputModes);
    }

    @Test
    public void setUseRangeAmountWithCurrentValueIsNoOp() {
        createOfferUseCase.initialize(usdBtcMarket);

        createOfferUseCase.setUseRangeAmount(false);

        assertTrue(cookieStore.persistedUseRangeAmountValues.isEmpty());
    }

    @Test
    public void setUseRangeAmountWithDifferentValuePersistsPreference() {
        createOfferUseCase.initialize(usdBtcMarket);

        createOfferUseCase.setUseRangeAmount(true);

        assertEquals(List.of(true), cookieStore.persistedUseRangeAmountValues);
    }

    @Test
    public void getAmountSpecThrowsWhenMarketIsNull() {
        try {
            amountUseCase.createAndGetAmountSpec(marketUseCase.getMarket());
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertEquals("market must not be null", e.getMessage());
        }
    }

    @Test
    public void toInputAmountAndToPassiveAmountAreConsistent() {
        createOfferUseCase.initialize(usdBtcMarket);
        createOfferUseCase.setUseBaseCurrencyForAmountInput(false);

        TradeAmount tradeAmount = amountUseCase.getFixTradeAmount();
        var inputAmount = createOfferUseCase.toInputAmount(tradeAmount, true);
        var passiveAmount = createOfferUseCase.toPassiveAmount(tradeAmount, true);

        assertEquals(tradeAmount.getQuoteSideAmount(), inputAmount);
        assertEquals(tradeAmount.getBaseSideAmount(), passiveAmount);
    }

    @Test
    public void setFixTradeAmountFromSliderValueUpdatesAmount() {
        createOfferUseCase.initialize(usdBtcMarket);
        createOfferUseCase.setUseBaseCurrencyForAmountInput(false);

        createOfferUseCase.setFixTradeAmountFromSliderValue(0.0);
        Fiat minAmount = (Fiat) amountUseCase.getFixTradeAmount().getQuoteSideAmount();

        createOfferUseCase.setFixTradeAmountFromSliderValue(1.0);
        Fiat maxAmount = (Fiat) amountUseCase.getFixTradeAmount().getQuoteSideAmount();

        assertTrue(minAmount.getValue() < maxAmount.getValue());
    }

    @Test
    public void setMinAndMaxTradeAmountFromSliderValueWorksCorrectly() {
        createOfferUseCase.initialize(usdBtcMarket);
        createOfferUseCase.setUseRangeAmount(true);

        createOfferUseCase.setMinTradeAmountFromSliderValue(0.2);
        createOfferUseCase.setMaxTradeAmountFromSliderValue(0.8);

        TradeAmount minAmount = amountUseCase.getMinTradeAmount();
        TradeAmount maxAmount = amountUseCase.getMaxTradeAmount();

        assertTrue(minAmount.getQuoteSideAmount().getValue() < maxAmount.getQuoteSideAmount().getValue());
    }

    @Test
    public void setTradeAmountLimitsUpdatesLimits() {
        createOfferUseCase.initialize(usdBtcMarket);
        TradeAmountRange currentLimits = amountUseCase.getTradeAmountLimits();

        TradeAmount doubledMax = TradeAmountConversion.toTradeAmount(usdBtcMarket,
                usdBtcPriceQuote,
                currentLimits.getMin().getQuoteSideAmount().multiply(2));
        TradeAmountRange newLimits = new TradeAmountRange(
                currentLimits.getMin(),
                doubledMax
        );
        amountUseCase.setTradeAmountLimits(newLimits);

        assertEquals(newLimits, amountUseCase.getTradeAmountLimits());
    }

    @Test
    public void setUserSpecificTradeAmountLimitUpdatesLimit() {
        createOfferUseCase.initialize(usdBtcMarket);
        TradeAmount customLimit = TradeAmountConversion.toTradeAmount(usdBtcMarket,
                usdBtcPriceQuote,
                Fiat.fromFaceValue(3000, "USD"));

        amountUseCase.setUserSpecificTradeAmountLimit(Optional.of(customLimit));

        assertEquals(Optional.of(customLimit), amountUseCase.getUserSpecificTradeAmountLimit());
    }

    @Test
    public void setInputAmountLimitsUpdatesLimits() {
        createOfferUseCase.initialize(usdBtcMarket);
        var currentLimits = amountUseCase.getInputAmountLimits();

        var newLimits = new MonetaryRange(
                currentLimits.getMin(),
                currentLimits.getMin().multiply(1.5)
        );
        amountUseCase.setInputAmountLimits(newLimits);

        assertEquals(newLimits, amountUseCase.getInputAmountLimits());
    }

    @Test
    public void setUseRangeAmountUpdatesSliderValues() {
        createOfferUseCase.initialize(defaultMarket);
        createOfferUseCase.setUseRangeAmount(true);

        assertTrue(amountUseCase.getUseRangeAmount());
        assertNotNull(amountUseCase.getMinAmountSliderValue());
        assertNotNull(amountUseCase.getMaxAmountSliderValue());
        assertEquals(List.of(true), cookieStore.persistedUseRangeAmountValues);
    }

    @Test
    public void onPaymentMethodSelectedReturnsNoAccountIfNoneExists() {
        createOfferUseCase.initialize(usdBtcMarket);
        PaymentMethod<?> method = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);

        PaymentMethodSelectionResult result = paymentMethodService.evaluatePaymentMethodSelectionResult(method);

        assertEquals(PaymentMethodSelectionStatus.NO_ACCOUNT_AVAILABLE, result.status());
        assertTrue(result.accountsRequiringSelection().isEmpty());
        assertTrue(paymentMethodService.getAccountByPaymentMethod().isEmpty());
    }

    @Test
    public void onPaymentMethodSelectedAutoSelectsIfSingleAccountExists() {
        PaymentMethod<?> method = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> account = createAccount(method);
        accountsProvider.put(usdBtcMarket, List.of(account));
        createOfferUseCase.initialize(usdBtcMarket);
        PaymentMethodSelectionResult result = paymentMethodService.evaluatePaymentMethodSelectionResult(method);
        paymentMethodService.onAddAccountByPaymentMethodEntry(result.methodAccountEntry().orElseThrow());

        assertEquals(PaymentMethodSelectionStatus.SINGLE_ACCOUNT_SELECTED, result.status());
        assertTrue(result.accountsRequiringSelection().isEmpty());
        assertEquals(account, paymentMethodService.getAccountByPaymentMethod().get(method));
    }

    @Test
    public void onPaymentMethodSelectedRequiresSelectionIfMultipleAccountsExist() {
        PaymentMethod<?> method = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> account1 = createAccount(method);
        Account<?, ?> account2 = createAccount(method);
        accountsProvider.put(usdBtcMarket, List.of(account1, account2));
        createOfferUseCase.initialize(usdBtcMarket);

        PaymentMethodSelectionResult result = paymentMethodService.evaluatePaymentMethodSelectionResult(method);

        assertEquals(PaymentMethodSelectionStatus.ACCOUNT_SELECTION_REQUIRED, result.status());
        assertEquals(List.of(account1, account2), result.accountsRequiringSelection());
        assertTrue(paymentMethodService.getAccountByPaymentMethod().isEmpty());
    }

    private static class MockMarketPriceService implements MarketPriceService {
        private final Map<Market, PriceQuote> priceQuoteByMarket = new HashMap<>();
        private final Map<Market, TradeAmount> defaultTradeAmountByMarket = new HashMap<>();
        private final PriceQuote btcUsdPriceQuote;
        private int btcUsdPriceQuoteRequests;

        private MockMarketPriceService(PriceQuote btcUsdPriceQuote) {
            this.btcUsdPriceQuote = btcUsdPriceQuote;
        }

        private void put(Market market, PriceQuote priceQuote, TradeAmount defaultTradeAmount) {
            priceQuoteByMarket.put(market, priceQuote);
            defaultTradeAmountByMarket.put(market, defaultTradeAmount);
        }

        public PriceQuote getBtcUsdPriceQuote() {
            btcUsdPriceQuoteRequests++;
            return btcUsdPriceQuote;
        }

        public TradeAmount getTradeAmountFromUsd(Market market, Fiat usdAmount) {
            return Optional.ofNullable(defaultTradeAmountByMarket.get(market))
                    .orElseThrow(() -> new IllegalStateException("Default trade amount not available for " + market));
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
            return findMarketPriceQuote(market).orElseThrow(() -> new IllegalStateException("No price quote found for market: " + market));
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

    private static class FakeCookieStore implements CreateOfferDraftCookieStore {
        private final Direction defaultDirection;
        private final boolean defaultUseBaseForFiatMarkets;
        private final boolean defaultUseBaseForOtherMarkets;
        private final boolean defaultUseRangeAmount;
        private final List<Direction> persistedDirections = new ArrayList<>();
        private final List<InputModePreference> persistedInputModes = new ArrayList<>();
        private final List<Boolean> persistedUseRangeAmountValues = new ArrayList<>();

        private FakeCookieStore(Direction defaultDirection,
                                boolean defaultUseBaseForFiatMarkets,
                                boolean defaultUseBaseForOtherMarkets,
                                boolean defaultUseRangeAmount) {
            this.defaultDirection = defaultDirection;
            this.defaultUseBaseForFiatMarkets = defaultUseBaseForFiatMarkets;
            this.defaultUseBaseForOtherMarkets = defaultUseBaseForOtherMarkets;
            this.defaultUseRangeAmount = defaultUseRangeAmount;
        }

        @Override
        public Direction getDisplayDirection() {
            return defaultDirection;
        }

        @Override
        public boolean getUseBaseCurrencyForAmountInput(Market market) {
            return market.isBtcFiatMarket() ? defaultUseBaseForFiatMarkets : defaultUseBaseForOtherMarkets;
        }

        @Override
        public boolean getUseRangeAmount() {
            return defaultUseRangeAmount;
        }

        @Override
        public void persistDisplayDirection(Direction direction) {
            persistedDirections.add(direction);
        }

        @Override
        public void persistUseBaseCurrencyForAmountInput(Market market, boolean useBaseCurrencyForAmountInput) {
            persistedInputModes.add(new InputModePreference(market, useBaseCurrencyForAmountInput));
        }

        @Override
        public void persistUseRangeAmount(boolean useRangeAmount) {
            persistedUseRangeAmountValues.add(useRangeAmount);
        }

        @Override
        public boolean getUseFixPrice(Market market) {
            return false;
        }

        @Override
        public void persistUseFixPrice(Market market, boolean useFixPrice) {

        }

        @Override
        public double getPricePercentage(Market market) {
            return 0;
        }

        @Override
        public void persistPricePercentage(Market market, double pricePercentage) {

        }

        @Override
        public Optional<PriceQuote> getFixPrice(Market market) {
            return Optional.empty();
        }

        @Override
        public void persistFixPrice(Market market, PriceQuote fixPrice) {

        }
    }

    private static class FakeAccountsProvider implements AccountsProvider {
        private final Map<Market, List<Account<?, ?>>> accountsByMarket = new HashMap<>();
        private final List<Market> requestedMarkets = new ArrayList<>();

        private void put(Market market, List<Account<?, ?>> accounts) {
            accountsByMarket.put(market, accounts);
        }

        @Override
        public List<Account<?, ?>> findAccountsForMarket(Market market) {
            requestedMarkets.add(market);
            return accountsByMarket.getOrDefault(market, List.of());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Account<?, ?> createAccount(PaymentMethod<?> paymentMethod) {
        Account account = mock(Account.class);
        when(account.getPaymentMethod()).thenReturn(paymentMethod);
        return account;
    }

    private record InputModePreference(Market market, boolean useBaseCurrencyForAmountInput) {
    }
}
