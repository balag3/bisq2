package bisq.offer.mu_sig.draft.create_offer;

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
import bisq.offer.mu_sig.draft.create_offer.amount.CreateOfferAmountService;
import bisq.offer.mu_sig.draft.create_offer.direction.CreateOfferDirectionService;
import bisq.offer.mu_sig.draft.create_offer.market.CreateOfferMarketService;
import bisq.offer.mu_sig.draft.create_offer.payment_method.CreateOfferPaymentMethodService;
import bisq.offer.mu_sig.draft.create_offer.payment_method.PaymentMethodSelectionResult;
import bisq.offer.mu_sig.draft.create_offer.payment_method.PaymentMethodSelectionStatus;
import bisq.offer.mu_sig.draft.create_offer.price.CreateOfferPriceService;
import bisq.offer.mu_sig.draft.dependencies.AccountsProvider;
import bisq.offer.mu_sig.draft.dependencies.CreateOfferDraftCookieStore;
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
    private CreateOfferService createOfferService;
    private CreateOfferPaymentMethodService paymentMethodService;
    private CreateOfferDirectionService directionService;
    private CreateOfferPriceService priceService;
    private CreateOfferAmountService amountService;
    private CreateOfferMarketService marketService;

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
        createOfferService = new CreateOfferService(marketPriceService, cookieStore, accountsProvider);
        marketService = createOfferService.getMarketService();
        directionService = createOfferService.getDirectionService();
        paymentMethodService = createOfferService.getPaymentMethodService();
        priceService = createOfferService.getPriceService();
        amountService = createOfferService.getAmountService();
    }

    @Test
    public void initializePopulatesDraftAndUpdatesPaymentMethods() {
        createOfferService.initialize(defaultMarket);

        assertEquals(defaultMarket, createOfferService.getMarket());
        assertEquals(Direction.SELL, directionService.getDisplayDirection());
        assertEquals(defaultMarketPriceQuote, priceService.getPriceQuote());
        assertEquals(defaultMarketDefaultTradeAmount, amountService.getFixTradeAmount());
        assertEquals(defaultMarketDefaultTradeAmount, amountService.getMinTradeAmount());
        assertEquals(defaultMarketDefaultTradeAmount, amountService.getMaxTradeAmount());
        assertNotNull(amountService.getTradeAmountLimits());
        assertNotNull(amountService.getInputAmountLimits());
        assertEquals(List.of(defaultMarket), accountsProvider.requestedMarkets);
    }

    @Test
    public void setMarketResetsPriceAndAmountsDeterministically() {
        createOfferService.initialize(defaultMarket);
        marketService.onSelectMarket(xmrBtcMarket);

        assertEquals(xmrBtcMarket, createOfferService.getMarket());
        assertEquals(xmrBtcPriceQuote, priceService.getPriceQuote());
        assertEquals(xmrBtcDefaultTradeAmount, amountService.getFixTradeAmount());
        assertEquals(xmrBtcDefaultTradeAmount, amountService.getMinTradeAmount());
        assertEquals(xmrBtcDefaultTradeAmount, amountService.getMaxTradeAmount());
        assertNotNull(amountService.getTradeAmountLimits());
        assertNotNull(amountService.getInputAmountLimits());
        assertEquals(List.of(defaultMarket, xmrBtcMarket), accountsProvider.requestedMarkets);
    }

    @Test
    public void setPriceQuoteKeepsQuoteInputAmountConstant() {
        createOfferService.initialize(usdBtcMarket);
        createOfferService.setUseBaseCurrencyForAmountInput(false);
        createOfferService.setFixTradeAmountFromInputAmount(Fiat.fromFaceValue(500, "USD"));

        TradeAmount fixTradeAmountBefore = amountService.getFixTradeAmount();
        createOfferService.setPriceQuote(PriceQuote.fromFiatPrice(40000, "USD"));
        TradeAmount fixTradeAmountAfter = amountService.getFixTradeAmount();

        assertEquals(fixTradeAmountBefore.getQuoteSideAmount(), fixTradeAmountAfter.getQuoteSideAmount());
        assertEquals(Coin.asBtcFromFaceValue(0.0125), fixTradeAmountAfter.getBaseSideAmount());
    }

    @Test
    public void setDirectionRecomputesUserSpecificLimitAndKeepsAmountsStable() {
        createOfferService.initialize(usdBtcMarket);
        TradeAmount fixTradeAmountBefore = amountService.getFixTradeAmount();
        Direction persistedDirection = directionService.getDisplayDirection();
        directionService.onSelectDisplayDirection(Direction.BUY);
        Optional<TradeAmount> buyLimit = amountService.getUserSpecificTradeAmountLimit();

        directionService.onSelectDisplayDirection(Direction.SELL);

        assertTrue(buyLimit.isPresent());
        assertTrue(amountService.getUserSpecificTradeAmountLimit().isEmpty());
        assertEquals(fixTradeAmountBefore, amountService.getFixTradeAmount());
        assertEquals(List.of(persistedDirection, Direction.BUY, Direction.SELL), cookieStore.persistedDirections);
    }

    @Test
    public void selectedAccountsUseMostRestrictivePaymentRailLimit() {
        createOfferService.initialize(usdBtcMarket);

        PaymentMethod<?> veryLowRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ADVANCED_CASH);
        PaymentMethod<?> moderateRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> veryLowRiskAccount = createAccount(veryLowRiskMethod);
        Account<?, ?> moderateRiskAccount = createAccount(moderateRiskMethod);

        paymentMethodService.onAddAccountByPaymentMethodEntry(Map.entry(veryLowRiskMethod, veryLowRiskAccount));
        assertEquals(Fiat.fromFaceValue(10000, "USD"),
                amountService.getTradeAmountLimits().getMax().getQuoteSideAmount());

        paymentMethodService.onAddAccountByPaymentMethodEntry(Map.entry(moderateRiskMethod, moderateRiskAccount));
        assertEquals(Fiat.fromFaceValue(5000, "USD"),
                amountService.getTradeAmountLimits().getMax().getQuoteSideAmount());
    }

    @Test
    public void selectedAccountLimitChangeClampsExistingAmounts() {
        createOfferService.initialize(usdBtcMarket);
        createOfferService.setUseBaseCurrencyForAmountInput(false);
        createOfferService.setFixTradeAmountFromInputAmount(Fiat.fromFaceValue(9000, "USD"));

        PaymentMethod<?> veryLowRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ADVANCED_CASH);
        PaymentMethod<?> moderateRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> veryLowRiskAccount = createAccount(veryLowRiskMethod);
        Account<?, ?> moderateRiskAccount = createAccount(moderateRiskMethod);

        paymentMethodService.onAddAccountByPaymentMethodEntry(Map.entry(veryLowRiskMethod, veryLowRiskAccount));
        assertEquals(Fiat.fromFaceValue(9000, "USD"), amountService.getFixTradeAmount().getQuoteSideAmount());

        paymentMethodService.onAddAccountByPaymentMethodEntry(Map.entry(moderateRiskMethod, moderateRiskAccount));
        assertEquals(Fiat.fromFaceValue(5000, "USD"), amountService.getFixTradeAmount().getQuoteSideAmount());
    }

    @Test
    public void setDirectionWithCurrentValueIsNoOp() {
        createOfferService.initialize(usdBtcMarket);
        Direction persistedDirection = directionService.getDisplayDirection();
        TradeAmount fixTradeAmountBefore = amountService.getFixTradeAmount();
        directionService.onSelectDisplayDirection(persistedDirection.mirror());

        assertEquals(fixTradeAmountBefore, amountService.getFixTradeAmount());
        assertEquals(List.of(persistedDirection, persistedDirection.mirror()), cookieStore.persistedDirections);
    }

    @Test
    public void setPriceQuoteWithCurrentValueIsNoOp() {
        createOfferService.initialize(usdBtcMarket);
        int recalculationCountBefore = marketPriceService.btcUsdPriceQuoteRequests;

        createOfferService.setPriceQuote(priceService.getPriceQuote());

        assertEquals(recalculationCountBefore, marketPriceService.btcUsdPriceQuoteRequests);
    }

    @Test
    public void setMarketWithCurrentValueIsNoOp() {
        createOfferService.initialize(defaultMarket);

        marketService.onSelectMarket(defaultMarket);

        assertEquals(List.of(defaultMarket), accountsProvider.requestedMarkets);
    }

    @Test
    public void selectingSameAccountTwiceDoesNotRecalculateConstraints() {
        createOfferService.initialize(usdBtcMarket);

        PaymentMethod<?> moderateRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> moderateRiskAccount = createAccount(moderateRiskMethod);

        paymentMethodService.onAddAccountByPaymentMethodEntry(Map.entry(moderateRiskMethod, moderateRiskAccount));
        int recalculationCountAfterFirstSelection = marketPriceService.btcUsdPriceQuoteRequests;

        paymentMethodService.onAddAccountByPaymentMethodEntry(Map.entry(moderateRiskMethod, moderateRiskAccount));

        assertEquals(recalculationCountAfterFirstSelection, marketPriceService.btcUsdPriceQuoteRequests);
    }

    @Test
    public void setUseBaseCurrencyForAmountInputWithCurrentValueIsNoOp() {
        createOfferService.initialize(usdBtcMarket);

        createOfferService.setUseBaseCurrencyForAmountInput(false);

        assertTrue(cookieStore.persistedInputModes.isEmpty());
    }

    @Test
    public void setUseBaseCurrencyForAmountInputWithDifferentValuePersistsPreference() {
        createOfferService.initialize(usdBtcMarket);

        createOfferService.setUseBaseCurrencyForAmountInput(true);

        assertEquals(List.of(new InputModePreference(usdBtcMarket, true)), cookieStore.persistedInputModes);
    }

    @Test
    public void setUseRangeAmountWithCurrentValueIsNoOp() {
        createOfferService.initialize(usdBtcMarket);

        createOfferService.setUseRangeAmount(false);

        assertTrue(cookieStore.persistedUseRangeAmountValues.isEmpty());
    }

    @Test
    public void setUseRangeAmountWithDifferentValuePersistsPreference() {
        createOfferService.initialize(usdBtcMarket);

        createOfferService.setUseRangeAmount(true);

        assertEquals(List.of(true), cookieStore.persistedUseRangeAmountValues);
    }

    @Test
    public void getAmountSpecThrowsWhenMarketIsNull() {
        try {
            amountService.createAndGetAmountSpec(marketService.getMarket());
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertEquals("market must not be null", e.getMessage());
        }
    }

    @Test
    public void toInputAmountAndToPassiveAmountAreConsistent() {
        createOfferService.initialize(usdBtcMarket);
        createOfferService.setUseBaseCurrencyForAmountInput(false);

        TradeAmount tradeAmount = amountService.getFixTradeAmount();
        var inputAmount = createOfferService.toInputAmount(tradeAmount, true);
        var passiveAmount = createOfferService.toPassiveAmount(tradeAmount, true);

        assertEquals(tradeAmount.getQuoteSideAmount(), inputAmount);
        assertEquals(tradeAmount.getBaseSideAmount(), passiveAmount);
    }

    @Test
    public void setFixTradeAmountFromSliderValueUpdatesAmount() {
        createOfferService.initialize(usdBtcMarket);
        createOfferService.setUseBaseCurrencyForAmountInput(false);

        createOfferService.setFixTradeAmountFromSliderValue(0.0);
        Fiat minAmount = (Fiat) amountService.getFixTradeAmount().getQuoteSideAmount();

        createOfferService.setFixTradeAmountFromSliderValue(1.0);
        Fiat maxAmount = (Fiat) amountService.getFixTradeAmount().getQuoteSideAmount();

        assertTrue(minAmount.getValue() < maxAmount.getValue());
    }

    @Test
    public void setMinAndMaxTradeAmountFromSliderValueWorksCorrectly() {
        createOfferService.initialize(usdBtcMarket);
        createOfferService.setUseRangeAmount(true);

        createOfferService.setMinTradeAmountFromSliderValue(0.2);
        createOfferService.setMaxTradeAmountFromSliderValue(0.8);

        TradeAmount minAmount = amountService.getMinTradeAmount();
        TradeAmount maxAmount = amountService.getMaxTradeAmount();

        assertTrue(minAmount.getQuoteSideAmount().getValue() < maxAmount.getQuoteSideAmount().getValue());
    }

    @Test
    public void setTradeAmountLimitsUpdatesLimits() {
        createOfferService.initialize(usdBtcMarket);
        TradeAmountRange currentLimits = amountService.getTradeAmountLimits();

        TradeAmount doubledMax = TradeAmountConversion.toTradeAmount(usdBtcMarket,
                usdBtcPriceQuote,
                currentLimits.getMin().getQuoteSideAmount().multiply(2));
        TradeAmountRange newLimits = new TradeAmountRange(
                currentLimits.getMin(),
                doubledMax
        );
        amountService.setTradeAmountLimits(newLimits);

        assertEquals(newLimits, amountService.getTradeAmountLimits());
    }

    @Test
    public void setUserSpecificTradeAmountLimitUpdatesLimit() {
        createOfferService.initialize(usdBtcMarket);
        TradeAmount customLimit = TradeAmountConversion.toTradeAmount(usdBtcMarket,
                usdBtcPriceQuote,
                Fiat.fromFaceValue(3000, "USD"));

        amountService.setUserSpecificTradeAmountLimit(Optional.of(customLimit));

        assertEquals(Optional.of(customLimit), amountService.getUserSpecificTradeAmountLimit());
    }

    @Test
    public void setInputAmountLimitsUpdatesLimits() {
        createOfferService.initialize(usdBtcMarket);
        var currentLimits = amountService.getInputAmountLimits();

        var newLimits = new MonetaryRange(
                currentLimits.getMin(),
                currentLimits.getMin().multiply(1.5)
        );
        amountService.setInputAmountLimits(newLimits);

        assertEquals(newLimits, amountService.getInputAmountLimits());
    }

    @Test
    public void setUseRangeAmountUpdatesSliderValues() {
        createOfferService.initialize(defaultMarket);
        createOfferService.setUseRangeAmount(true);

        assertTrue(amountService.getUseRangeAmount());
        assertNotNull(amountService.getMinAmountSliderValue());
        assertNotNull(amountService.getMaxAmountSliderValue());
        assertEquals(List.of(true), cookieStore.persistedUseRangeAmountValues);
    }

    @Test
    public void onPaymentMethodSelectedReturnsNoAccountIfNoneExists() {
        createOfferService.initialize(usdBtcMarket);
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
        createOfferService.initialize(usdBtcMarket);
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
        createOfferService.initialize(usdBtcMarket);

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
