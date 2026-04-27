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
    private CreateOfferPaymentMethodService paymentMethodDraftFacade;
    private CreateOfferDirectionService createOfferDirectionService;
    private CreateOfferPriceService createOfferPriceService;
    private CreateOfferAmountService createOfferAmountService;

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
        paymentMethodDraftFacade = createOfferService.getPaymentMethodService();
        createOfferDirectionService = createOfferService.getDirectionService();
        createOfferPriceService = createOfferService.getPriceService();
        createOfferAmountService = createOfferService.getAmountService();
    }

    @Test
    public void initializePopulatesDraftAndUpdatesPaymentMethods() {
        createOfferService.initialize(defaultMarket);

        assertEquals(defaultMarket, createOfferService.getMarket());
        assertEquals(Direction.SELL, createOfferDirectionService.getDirection());
        assertEquals(defaultMarketPriceQuote, createOfferPriceService.getPriceQuote());
        assertEquals(defaultMarketDefaultTradeAmount, createOfferAmountService.getFixTradeAmount());
        assertEquals(defaultMarketDefaultTradeAmount, createOfferAmountService.getMinTradeAmount());
        assertEquals(defaultMarketDefaultTradeAmount, createOfferAmountService.getMaxTradeAmount());
        assertNotNull(createOfferAmountService.getTradeAmountLimits());
        assertNotNull(createOfferAmountService.getInputAmountLimits());
        assertEquals(List.of(defaultMarket), accountsProvider.requestedMarkets);
    }

    @Test
    public void setMarketResetsPriceAndAmountsDeterministically() {
        createOfferService.initialize(defaultMarket);
        createOfferService.setMarket(xmrBtcMarket);

        assertEquals(xmrBtcMarket, createOfferService.getMarket());
        assertEquals(xmrBtcPriceQuote, createOfferPriceService.getPriceQuote());
        assertEquals(xmrBtcDefaultTradeAmount, createOfferAmountService.getFixTradeAmount());
        assertEquals(xmrBtcDefaultTradeAmount, createOfferAmountService.getMinTradeAmount());
        assertEquals(xmrBtcDefaultTradeAmount, createOfferAmountService.getMaxTradeAmount());
        assertNotNull(createOfferAmountService.getTradeAmountLimits());
        assertNotNull(createOfferAmountService.getInputAmountLimits());
        assertEquals(List.of(defaultMarket, xmrBtcMarket), accountsProvider.requestedMarkets);
    }

    @Test
    public void setPriceQuoteKeepsQuoteInputAmountConstant() {
        createOfferService.initialize(usdBtcMarket);
        createOfferService.setUseBaseCurrencyForAmountInput(false);
        createOfferService.setFixTradeAmountFromInputAmount(Fiat.fromFaceValue(500, "USD"));

        TradeAmount fixTradeAmountBefore = createOfferAmountService.getFixTradeAmount();
        createOfferService.setPriceQuote(PriceQuote.fromFiatPrice(40000, "USD"));
        TradeAmount fixTradeAmountAfter = createOfferAmountService.getFixTradeAmount();

        assertEquals(fixTradeAmountBefore.getQuoteSideAmount(), fixTradeAmountAfter.getQuoteSideAmount());
        assertEquals(Coin.asBtcFromFaceValue(0.0125), fixTradeAmountAfter.getBaseSideAmount());
    }

    @Test
    public void setDirectionRecomputesUserSpecificLimitAndKeepsAmountsStable() {
        createOfferService.initialize(usdBtcMarket);
        TradeAmount fixTradeAmountBefore = createOfferAmountService.getFixTradeAmount();

        createOfferService.setDirection(Direction.BUY);
        Optional<TradeAmount> buyLimit = createOfferAmountService.getUserSpecificTradeAmountLimit();

        createOfferService.setDirection(Direction.SELL);

        assertTrue(buyLimit.isPresent());
        assertTrue(createOfferAmountService.getUserSpecificTradeAmountLimit().isEmpty());
        assertEquals(fixTradeAmountBefore, createOfferAmountService.getFixTradeAmount());
        assertEquals(List.of(Direction.BUY, Direction.SELL), cookieStore.persistedDirections);
    }

    @Test
    public void selectedAccountsUseMostRestrictivePaymentRailLimit() {
        createOfferService.initialize(usdBtcMarket);

        PaymentMethod<?> veryLowRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ADVANCED_CASH);
        PaymentMethod<?> moderateRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> veryLowRiskAccount = createAccount(veryLowRiskMethod);
        Account<?, ?> moderateRiskAccount = createAccount(moderateRiskMethod);

        createOfferService.putSelectedAccountByPaymentMethod(veryLowRiskMethod, veryLowRiskAccount);
        assertEquals(Fiat.fromFaceValue(10000, "USD"),
                createOfferAmountService.getTradeAmountLimits().getMax().getQuoteSideAmount());

        createOfferService.putSelectedAccountByPaymentMethod(moderateRiskMethod, moderateRiskAccount);
        assertEquals(Fiat.fromFaceValue(5000, "USD"),
                createOfferAmountService.getTradeAmountLimits().getMax().getQuoteSideAmount());
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

        createOfferService.putSelectedAccountByPaymentMethod(veryLowRiskMethod, veryLowRiskAccount);
        assertEquals(Fiat.fromFaceValue(9000, "USD"), createOfferAmountService.getFixTradeAmount().getQuoteSideAmount());

        createOfferService.putSelectedAccountByPaymentMethod(moderateRiskMethod, moderateRiskAccount);
        assertEquals(Fiat.fromFaceValue(5000, "USD"), createOfferAmountService.getFixTradeAmount().getQuoteSideAmount());
    }

    @Test
    public void setDirectionWithCurrentValueIsNoOp() {
        createOfferService.initialize(usdBtcMarket);

        TradeAmount fixTradeAmountBefore = createOfferAmountService.getFixTradeAmount();
        createOfferService.setDirection(Direction.SELL);

        assertEquals(fixTradeAmountBefore, createOfferAmountService.getFixTradeAmount());
        assertTrue(cookieStore.persistedDirections.isEmpty());
    }

    @Test
    public void setPriceQuoteWithCurrentValueIsNoOp() {
        createOfferService.initialize(usdBtcMarket);
        int recalculationCountBefore = marketPriceService.btcUsdPriceQuoteRequests;

        createOfferService.setPriceQuote(createOfferPriceService.getPriceQuote());

        assertEquals(recalculationCountBefore, marketPriceService.btcUsdPriceQuoteRequests);
    }

    @Test
    public void setMarketWithCurrentValueIsNoOp() {
        createOfferService.initialize(defaultMarket);

        createOfferService.setMarket(defaultMarket);

        assertEquals(List.of(defaultMarket), accountsProvider.requestedMarkets);
    }

    @Test
    public void selectingSameAccountTwiceDoesNotRecalculateConstraints() {
        createOfferService.initialize(usdBtcMarket);

        PaymentMethod<?> moderateRiskMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> moderateRiskAccount = createAccount(moderateRiskMethod);

        createOfferService.putSelectedAccountByPaymentMethod(moderateRiskMethod, moderateRiskAccount);
        int recalculationCountAfterFirstSelection = marketPriceService.btcUsdPriceQuoteRequests;

        createOfferService.putSelectedAccountByPaymentMethod(moderateRiskMethod, moderateRiskAccount);

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
            createOfferService.getAmountSpec();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertEquals("market must not be null", e.getMessage());
        }
    }

    @Test
    public void toInputAmountAndToPassiveAmountAreConsistent() {
        createOfferService.initialize(usdBtcMarket);
        createOfferService.setUseBaseCurrencyForAmountInput(false);

        TradeAmount tradeAmount = createOfferAmountService.getFixTradeAmount();
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
        Fiat minAmount = (Fiat) createOfferAmountService.getFixTradeAmount().getQuoteSideAmount();

        createOfferService.setFixTradeAmountFromSliderValue(1.0);
        Fiat maxAmount = (Fiat) createOfferAmountService.getFixTradeAmount().getQuoteSideAmount();

        assertTrue(minAmount.getValue() < maxAmount.getValue());
    }

    @Test
    public void setMinAndMaxTradeAmountFromSliderValueWorksCorrectly() {
        createOfferService.initialize(usdBtcMarket);
        createOfferService.setUseRangeAmount(true);

        createOfferService.setMinTradeAmountFromSliderValue(0.2);
        createOfferService.setMaxTradeAmountFromSliderValue(0.8);

        TradeAmount minAmount = createOfferAmountService.getMinTradeAmount();
        TradeAmount maxAmount = createOfferAmountService.getMaxTradeAmount();

        assertTrue(minAmount.getQuoteSideAmount().getValue() < maxAmount.getQuoteSideAmount().getValue());
    }

    @Test
    public void clearAccountsByPaymentMethodRemovesAllAccounts() {
        createOfferService.initialize(usdBtcMarket);
        PaymentMethod<?> achMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> achAccount = createAccount(achMethod);

        paymentMethodDraftFacade.putAccountsByPaymentMethod(achMethod, List.of(achAccount));
        assertEquals(1, paymentMethodDraftFacade.getAccountsByPaymentMethod().size());

        paymentMethodDraftFacade.clearAccountsByPaymentMethod();
        assertEquals(0, paymentMethodDraftFacade.getAccountsByPaymentMethod().size());
    }

    @Test
    public void removeAccountsByPaymentMethodRemovesSpecificMethod() {
        createOfferService.initialize(usdBtcMarket);
        PaymentMethod<?> achMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        PaymentMethod<?> advancedCashMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ADVANCED_CASH);
        Account<?, ?> achAccount = createAccount(achMethod);
        Account<?, ?> advancedCashAccount = createAccount(advancedCashMethod);

        paymentMethodDraftFacade.putAccountsByPaymentMethod(achMethod, List.of(achAccount));
        paymentMethodDraftFacade.putAccountsByPaymentMethod(advancedCashMethod, List.of(advancedCashAccount));
        assertEquals(2, paymentMethodDraftFacade.getAccountsByPaymentMethod().size());

        paymentMethodDraftFacade.removeAccountsByPaymentMethod(achMethod);
        assertEquals(1, paymentMethodDraftFacade.getAccountsByPaymentMethod().size());
        assertTrue(paymentMethodDraftFacade.getAccountsByPaymentMethod().containsKey(advancedCashMethod));
    }

    @Test
    public void putAllAccountsByPaymentMethodReplacesAllAccounts() {
        createOfferService.initialize(usdBtcMarket);
        PaymentMethod<?> achMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> achAccount = createAccount(achMethod);

        Map<PaymentMethod<?>, List<Account<?, ?>>> accountsMap = Map.of(achMethod, List.of(achAccount));
        paymentMethodDraftFacade.putAllAccountsByPaymentMethod(accountsMap);

        assertEquals(1, paymentMethodDraftFacade.getAccountsByPaymentMethod().size());
        assertEquals(List.of(achAccount), paymentMethodDraftFacade.getAccountsByPaymentMethod().get(achMethod));
    }

    @Test
    public void clearSelectedAccountByPaymentMethodRemovesAllSelectedAccounts() {
        createOfferService.initialize(usdBtcMarket);
        PaymentMethod<?> achMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> achAccount = createAccount(achMethod);

        paymentMethodDraftFacade.putSelectedAccountByPaymentMethod(achMethod, achAccount);
        assertEquals(1, paymentMethodDraftFacade.getSelectedAccountByPaymentMethod().size());

        paymentMethodDraftFacade.clearSelectedAccountByPaymentMethod();
        assertEquals(0, paymentMethodDraftFacade.getSelectedAccountByPaymentMethod().size());
    }

    @Test
    public void putAllSelectedAccountByPaymentMethodReplacesAllSelectedAccounts() {
        createOfferService.initialize(usdBtcMarket);
        PaymentMethod<?> achMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> achAccount = createAccount(achMethod);

        Map<PaymentMethod<?>, Account<?, ?>> selectedAccountsMap = Map.of(achMethod, achAccount);
        paymentMethodDraftFacade.putAllSelectedAccountByPaymentMethod(selectedAccountsMap);

        assertEquals(1, paymentMethodDraftFacade.getSelectedAccountByPaymentMethod().size());
        assertEquals(achAccount, paymentMethodDraftFacade.getSelectedAccountByPaymentMethod().get(achMethod));
    }

    @Test
    public void setTradeAmountLimitsUpdatesLimits() {
        createOfferService.initialize(usdBtcMarket);
        TradeAmountRange currentLimits = createOfferAmountService.getTradeAmountLimits();

        TradeAmount doubledMax = TradeAmountConversion.toTradeAmount(usdBtcMarket,
                usdBtcPriceQuote,
                currentLimits.getMin().getQuoteSideAmount().multiply(2));
        TradeAmountRange newLimits = new TradeAmountRange(
                currentLimits.getMin(),
                doubledMax
        );
        createOfferAmountService.setTradeAmountLimits(newLimits);

        assertEquals(newLimits, createOfferAmountService.getTradeAmountLimits());
    }

    @Test
    public void setUserSpecificTradeAmountLimitUpdatesLimit() {
        createOfferService.initialize(usdBtcMarket);
        TradeAmount customLimit = TradeAmountConversion.toTradeAmount(usdBtcMarket,
                usdBtcPriceQuote,
                Fiat.fromFaceValue(3000, "USD"));

        createOfferAmountService.setUserSpecificTradeAmountLimit(Optional.of(customLimit));

        assertEquals(Optional.of(customLimit), createOfferAmountService.getUserSpecificTradeAmountLimit());
    }

    @Test
    public void setInputAmountLimitsUpdatesLimits() {
        createOfferService.initialize(usdBtcMarket);
        var currentLimits = createOfferAmountService.getInputAmountLimits();

        var newLimits = new MonetaryRange(
                currentLimits.getMin(),
                currentLimits.getMin().multiply(1.5)
        );
        createOfferAmountService.setInputAmountLimits(newLimits);

        assertEquals(newLimits, createOfferAmountService.getInputAmountLimits());
    }

    @Test
    public void setUseRangeAmountUpdatesSliderValues() {
        createOfferService.initialize(defaultMarket);
        createOfferService.setUseRangeAmount(true);

        assertTrue(createOfferAmountService.getUseRangeAmount());
        assertNotNull(createOfferAmountService.getMinAmountSliderValue());
        assertNotNull(createOfferAmountService.getMaxAmountSliderValue());
        assertEquals(List.of(true), cookieStore.persistedUseRangeAmountValues);
    }

    @Test
    public void onPaymentMethodSelectedReturnsNoAccountIfNoneExists() {
        createOfferService.initialize(usdBtcMarket);
        PaymentMethod<?> method = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);

        PaymentMethodSelectionResult result = paymentMethodDraftFacade.onPaymentMethodSelected(method);

        assertEquals(PaymentMethodSelectionStatus.NO_ACCOUNT_AVAILABLE, result.status());
        assertTrue(result.accountsRequiringSelection().isEmpty());
        assertTrue(paymentMethodDraftFacade.getSelectedAccountByPaymentMethod().isEmpty());
    }

    @Test
    public void onPaymentMethodSelectedAutoSelectsIfSingleAccountExists() {
        createOfferService.initialize(usdBtcMarket);
        PaymentMethod<?> method = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> account = createAccount(method);
        paymentMethodDraftFacade.putAccountsByPaymentMethod(method, List.of(account));

        PaymentMethodSelectionResult result = paymentMethodDraftFacade.onPaymentMethodSelected(method);

        assertEquals(PaymentMethodSelectionStatus.SINGLE_ACCOUNT_SELECTED, result.status());
        assertTrue(result.accountsRequiringSelection().isEmpty());
        assertEquals(account, paymentMethodDraftFacade.getSelectedAccountByPaymentMethod().get(method));
    }

    @Test
    public void onPaymentMethodSelectedRequiresSelectionIfMultipleAccountsExist() {
        createOfferService.initialize(usdBtcMarket);
        PaymentMethod<?> method = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        Account<?, ?> account1 = createAccount(method);
        Account<?, ?> account2 = createAccount(method);
        paymentMethodDraftFacade.putAccountsByPaymentMethod(method, List.of(account1, account2));

        PaymentMethodSelectionResult result = paymentMethodDraftFacade.onPaymentMethodSelected(method);

        assertEquals(PaymentMethodSelectionStatus.ACCOUNT_SELECTION_REQUIRED, result.status());
        assertEquals(List.of(account1, account2), result.accountsRequiringSelection());
        assertTrue(paymentMethodDraftFacade.getSelectedAccountByPaymentMethod().isEmpty());
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
        public Direction getDirection() {
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
        public void persistDirection(Direction direction) {
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
        private final List<Market> requestedMarkets = new ArrayList<>();

        @Override
        public List<Account<?, ?>> findAccountsForMarket(Market market) {
            requestedMarkets.add(market);
            return List.of();
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
