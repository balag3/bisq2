package bisq.offer.mu_sig.use_case.take_offer;

import bisq.account.accounts.Account;
import bisq.account.accounts.fiat.CountryBasedAccountPayload;
import bisq.account.payment_method.BitcoinPaymentMethod;
import bisq.account.payment_method.BitcoinPaymentRail;
import bisq.account.payment_method.PaymentMethod;
import bisq.account.payment_method.PaymentMethodSpec;
import bisq.account.payment_method.fiat.FiatPaymentMethod;
import bisq.account.payment_method.fiat.FiatPaymentRail;
import bisq.account.protocol_type.TradeProtocolType;
import bisq.bonded_roles.market_price.MarketPrice;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.locale.Country;
import bisq.common.market.Market;
import bisq.common.market.MarketRepository;
import bisq.common.monetary.Coin;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.PriceQuote;
import bisq.common.observable.map.ObservableHashMap;
import bisq.network.identity.NetworkId;
import bisq.identity.Identity;
import bisq.identity.IdentityService;
import bisq.offer.Direction;
import bisq.offer.mu_sig.MuSigOffer;
import bisq.offer.mu_sig.use_case.dependencies.TakeOfferDraftCookieStore;
import bisq.offer.mu_sig.use_case.take_offer.TakeOfferValidationException.Reason;
import bisq.offer.options.AccountOption;
import bisq.offer.options.CollateralOption;
import bisq.offer.options.OfferOption;
import bisq.offer.amount.spec.BaseSideRangeAmountSpec;
import bisq.offer.amount.spec.QuoteSideFixedAmountSpec;
import bisq.offer.amount.spec.QuoteSideRangeAmountSpec;
import bisq.offer.price.spec.FixPriceSpec;
import bisq.offer.price.spec.FloatPriceSpec;
import bisq.offer.price.spec.MarketPriceSpec;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TakeOfferUseCaseTest {
    private final Market market = MarketRepository.getUSDBitcoinMarket();
    private final PriceQuote marketPriceQuote = PriceQuote.fromFiatPrice(100_000, "USD");
    private final PaymentMethod<?> wiseMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.WISE);
    private final PaymentMethod<?> advancedCashMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ADVANCED_CASH);
    private final PaymentMethod<?> achMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
    private final PaymentMethod<?> mainChainMethod = BitcoinPaymentMethod.fromPaymentRail(BitcoinPaymentRail.MAIN_CHAIN);

    private final MarketPriceService marketPriceService = mock(MarketPriceService.class);
    private final IdentityService identityService = mock(IdentityService.class);
    private final ObservableHashMap<Market, MarketPrice> marketPriceByCurrencyMap = new ObservableHashMap<>();

    private void stubMarketPrice(PriceQuote quote) {
        MarketPrice marketPrice = mock(MarketPrice.class);
        when(marketPrice.getPriceQuote()).thenReturn(quote);
        when(marketPriceService.findMarketPrice(market)).thenReturn(Optional.of(marketPrice));
        when(marketPriceService.findMarketPriceQuote(market)).thenReturn(Optional.of(quote));
        // The USD-defined amount limits convert through the market price of the offer market and
        // the USD market (the same market in this harness).
        when(marketPriceService.getMarketPriceQuoteOrThrow(market)).thenReturn(quote);
    }

    private void fireMarketPriceUpdate(PriceQuote quote) {
        stubMarketPrice(quote);
        MarketPrice marketPrice = mock(MarketPrice.class);
        when(marketPrice.getPriceQuote()).thenReturn(quote);
        marketPriceByCurrencyMap.put(market, marketPrice);
    }

    @Test
    public void validOfferInitializesMarketDirectionAndPrice() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();

        useCase.initialize(offer);

        assertEquals(market, useCase.getMarket());
        assertEquals(Direction.BUY, useCase.getDirectionService().getDirection());
        assertEquals(marketPriceQuote, useCase.getPriceService().getPriceQuote());
    }

    @Test
    public void ownOfferIsRejected() {
        TakeOfferUseCase useCase = createUseCase();
        when(identityService.findAnyIdentityByNetworkId(any(NetworkId.class))).thenReturn(Optional.of(mock(Identity.class)));

        assertRejected(useCase, validOffer(), Reason.OWN_OFFER);
    }

    @Test
    public void ownOfferOfAnyLocalIdentityIsRejected() {
        // The own-offer rule covers ANY local identity, including retired ones: deleting a user
        // profile retires the identity but its offers survive, so the check must use the
        // any-identity lookup (active + retired + default), not the active-only one.
        TakeOfferUseCase useCase = createUseCase();
        when(identityService.findActiveIdentity(any(NetworkId.class))).thenReturn(Optional.empty());
        when(identityService.findAnyIdentityByNetworkId(any(NetworkId.class))).thenReturn(Optional.of(mock(Identity.class)));

        assertRejected(useCase, validOffer(), Reason.OWN_OFFER);
    }

    @Test
    public void offerWithoutMuSigProtocolTypeIsRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        when(offer.getProtocolTypes()).thenReturn(List.of(TradeProtocolType.BISQ_EASY));

        assertRejected(useCase, offer, Reason.PROTOCOL_TYPE_NOT_SUPPORTED);
    }

    @Test
    public void missingMarketPriceRejectsEvenFixedPriceOffers() {
        TakeOfferUseCase useCase = createUseCase();
        when(marketPriceService.findMarketPrice(market)).thenReturn(Optional.empty());
        when(marketPriceService.findMarketPriceQuote(market)).thenReturn(Optional.empty());
        MuSigOffer offer = validOffer();
        when(offer.getPriceSpec()).thenReturn(new FixPriceSpec(PriceQuote.fromFiatPrice(90_000, "USD")));

        assertRejected(useCase, offer, Reason.NO_MARKET_PRICE);
    }

    @Test
    public void floatingPercentageOutsideCreateBoundsIsRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        when(offer.getPriceSpec()).thenReturn(new FloatPriceSpec(0.6));

        assertRejected(useCase, offer, Reason.FLOAT_PRICE_OUT_OF_BOUNDS);
    }

    @Test
    public void fixedPriceQuoteOfDifferentMarketIsRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        when(offer.getPriceSpec()).thenReturn(new FixPriceSpec(PriceQuote.fromFiatPrice(90_000, "EUR")));

        assertRejected(useCase, offer, Reason.FIXED_PRICE_MARKET_MISMATCH);
    }

    @Test
    public void takerSideWithoutPaymentMethodSpecsIsRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        when(offer.getQuoteSidePaymentMethodSpecs()).thenReturn(Collections.emptyList());

        assertRejected(useCase, offer, Reason.INVALID_PAYMENT_METHOD_SPECS);
    }

    @Test
    public void takerSideWithMoreThanFourPaymentMethodSpecsIsRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        List<PaymentMethodSpec<?>> fiveSpecs = List.of(
                specOf(wiseMethod), specOf(wiseMethod), specOf(wiseMethod), specOf(wiseMethod), specOf(wiseMethod));
        when(offer.getQuoteSidePaymentMethodSpecs()).thenReturn(fiveSpecs);

        assertRejected(useCase, offer, Reason.INVALID_PAYMENT_METHOD_SPECS);
    }

    @Test
    public void bitcoinSideWithoutSingleMainChainSpecIsRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        List<PaymentMethodSpec<?>> lightningSpec = List.of(
                specOf(BitcoinPaymentMethod.fromPaymentRail(BitcoinPaymentRail.LN)));
        when(offer.getBaseSidePaymentMethodSpecs()).thenReturn(lightningSpec);

        assertRejected(useCase, offer, Reason.INVALID_PAYMENT_METHOD_SPECS);
    }

    @Test
    public void missingCollateralOptionIsRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        List<OfferOption> options = List.of(accountOption(wiseMethod));
        when(offer.getOfferOptions()).thenReturn(options);

        assertRejected(useCase, offer, Reason.INVALID_OFFER_OPTIONS);
    }

    @Test
    public void asymmetricSecurityDepositsAreRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        List<OfferOption> options = List.of(new CollateralOption(0.2, 0.3), accountOption(wiseMethod));
        when(offer.getOfferOptions()).thenReturn(options);

        assertRejected(useCase, offer, Reason.INVALID_OFFER_OPTIONS);
    }

    @Test
    public void securityDepositOutsideSaneBoundsIsRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        List<OfferOption> options = List.of(new CollateralOption(1.5, 1.5), accountOption(wiseMethod));
        when(offer.getOfferOptions()).thenReturn(options);

        assertRejected(useCase, offer, Reason.INVALID_OFFER_OPTIONS);
    }

    @Test
    public void missingAccountOptionForSelectableMethodIsRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        when(offer.getOfferOptions()).thenReturn(List.of(new CollateralOption(0.25, 0.25)));

        assertRejected(useCase, offer, Reason.INVALID_OFFER_OPTIONS);
    }

    @Test
    public void duplicateAccountOptionsForAMethodAreRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        List<OfferOption> options = List.of(
                new CollateralOption(0.25, 0.25), accountOption(wiseMethod), accountOption(wiseMethod));
        when(offer.getOfferOptions()).thenReturn(options);

        assertRejected(useCase, offer, Reason.INVALID_OFFER_OPTIONS);
    }

    @Test
    public void duplicateTakerSidePaymentMethodsAreRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        List<PaymentMethodSpec<?>> duplicateMethods = List.of(specOf(wiseMethod), specOf(wiseMethod));
        when(offer.getQuoteSidePaymentMethodSpecs()).thenReturn(duplicateMethods);

        assertRejected(useCase, offer, Reason.INVALID_PAYMENT_METHOD_SPECS);
    }

    @Test
    public void accountOptionForNonOfferedMethodIsRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        PaymentMethod<?> nonOfferedMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.ACH_TRANSFER);
        List<OfferOption> options = List.of(new CollateralOption(0.25, 0.25),
                accountOption(wiseMethod), accountOption(nonOfferedMethod));
        when(offer.getOfferOptions()).thenReturn(options);

        assertRejected(useCase, offer, Reason.INVALID_OFFER_OPTIONS);
    }

    @Test
    public void exactlyEqualDuplicateAccountOptionsAreRejected() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        List<OfferOption> options = List.of(new CollateralOption(0.25, 0.25),
                realAccountOption(FiatPaymentRail.WISE), realAccountOption(FiatPaymentRail.WISE));
        when(offer.getOfferOptions()).thenReturn(options);

        assertRejected(useCase, offer, Reason.INVALID_OFFER_OPTIONS);
    }

    @Test
    public void rejectedInitializationLeavesNoPartialState() {
        TakeOfferUseCase useCase = createUseCase();
        when(marketPriceService.findMarketPrice(market)).thenReturn(Optional.empty());
        MuSigOffer offer = validOffer();

        assertRejected(useCase, offer, Reason.NO_MARKET_PRICE);

        assertThrows(NullPointerException.class, useCase::getMarket);
        assertNull(useCase.getPriceService().getPriceQuote());
    }

    @Test
    public void disposeClearsRetainedState() {
        // The wizard disposes the use case on deactivation, but UI callbacks queued before that
        // still run afterwards and re-read the price state; disposal must not leave the closed
        // session's values readable.
        TakeOfferUseCase useCase = createUseCase();
        useCase.initialize(validOffer());
        assertNotNull(useCase.getPriceService().getPriceQuote());
        assertNotNull(useCase.getPriceService().getMarketPriceQuote());
        assertNotNull(useCase.getPriceService().getPriceDeviation());
        assertNotNull(useCase.getFeeService().getTradeFee());

        useCase.dispose();

        assertNull(useCase.getPriceService().getPriceQuote());
        assertNull(useCase.getPriceService().getMarketPriceQuote());
        assertNull(useCase.getPriceService().getPriceDeviation());
        assertNull(useCase.getFeeService().getTradeFee());
        assertNull(useCase.getAmountService().getFixTradeAmount());
        assertThrows(NullPointerException.class, useCase::getMarket);
    }

    private static OfferOption realAccountOption(FiatPaymentRail paymentRail) {
        return new AccountOption(FiatPaymentMethod.fromPaymentRail(paymentRail),
                "a".repeat(40),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                List.of(),
                new byte[20]);
    }

    @Test
    public void initializeLoadsEligibleAccountsAndPreselectsTheSingleAccount() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();

        useCase.initialize(offer);

        assertEquals(List.of(wiseAccount),
                useCase.getPaymentMethodService().getAccountsByPaymentMethod().get(wiseMethod));
        assertEquals(wiseAccount,
                useCase.getPaymentMethodService().getSelectedAccountByPaymentMethod().get(wiseMethod));
    }

    @Test
    public void singleMethodWithSingleEligibleAccountSkipsPaymentStepAndAppliesSelection() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();

        useCase.initialize(offer);

        assertFalse(useCase.shouldShowPaymentStep());
        assertEquals(wiseAccount, useCase.getSelectedAccount().orElseThrow());
        PaymentMethodSpec<?> selectedSpec = useCase.getSelectedPaymentMethodSpec().orElseThrow();
        assertEquals(offer.getQuoteSidePaymentMethodSpecs().get(0), selectedSpec);
    }

    @Test
    public void singleMethodWithoutEligibleAccountShowsPaymentStep() {
        TakeOfferUseCase useCase = createUseCase(market -> List.of());
        MuSigOffer offer = validOffer();

        useCase.initialize(offer);

        assertTrue(useCase.shouldShowPaymentStep());
        // The review confirmation gate blocks the take when either is empty; they must be empty
        // together so a spec can never be handed off without its account.
        assertTrue(useCase.getSelectedAccount().isEmpty());
        assertTrue(useCase.getSelectedPaymentMethodSpec().isEmpty());
    }

    @Test
    public void multipleOfferedMethodsShowPaymentStepButPreselectTheSingleEligibleAccount() {
        PaymentMethod<?> sepaMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.SEPA);
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        List<PaymentMethodSpec<?>> twoMethods = List.of(specOf(wiseMethod), specOf(sepaMethod));
        List<OfferOption> options = List.of(new CollateralOption(0.25, 0.25),
                accountOption(wiseMethod), accountOption(sepaMethod));
        when(offer.getQuoteSidePaymentMethodSpecs()).thenReturn(twoMethods);
        when(offer.getOfferOptions()).thenReturn(options);

        useCase.initialize(offer);

        assertTrue(useCase.shouldShowPaymentStep());
        assertEquals(wiseAccount, useCase.getSelectedAccount().orElseThrow());
    }

    @Test
    public void reinitializationDoesNotKeepAStalePreselection() {
        PaymentMethod<?> sepaMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.SEPA);
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        Account<?, ?> sepaAccount = accountFor(sepaMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount, sepaAccount));
        MuSigOffer offerWithOneMethod = validOffer();
        useCase.initialize(offerWithOneMethod);
        assertEquals(wiseAccount, useCase.getSelectedAccount().orElseThrow());

        MuSigOffer offerWithTwoMethods = validOffer();
        List<PaymentMethodSpec<?>> twoMethods = List.of(specOf(wiseMethod), specOf(sepaMethod));
        List<OfferOption> options = List.of(new CollateralOption(0.25, 0.25),
                accountOption(wiseMethod), accountOption(sepaMethod));
        when(offerWithTwoMethods.getQuoteSidePaymentMethodSpecs()).thenReturn(twoMethods);
        when(offerWithTwoMethods.getOfferOptions()).thenReturn(options);

        useCase.initialize(offerWithTwoMethods);

        assertTrue(useCase.getSelectedAccount().isEmpty());
    }

    @Test
    public void rejectedReinitializationResetsPreviousState() {
        PaymentMethod<?> sepaMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.SEPA);
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        Account<?, ?> sepaAccountFr = countryAccountFor(sepaMethod, "FR");
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount, sepaAccountFr));

        MuSigOffer offer = validOffer();
        List<PaymentMethodSpec<?>> twoMethods = List.of(specOf(wiseMethod), specOf(sepaMethod));
        List<OfferOption> options = List.of(new CollateralOption(0.25, 0.25),
                accountOption(wiseMethod), accountOption(sepaMethod, List.of("DE")));
        when(offer.getQuoteSidePaymentMethodSpecs()).thenReturn(twoMethods);
        when(offer.getOfferOptions()).thenReturn(options);
        useCase.initialize(offer);
        assertEquals(wiseAccount, useCase.getSelectedAccount().orElseThrow());
        assertFalse(useCase.getPaymentMethodService().getIncompatibleAccountsByPaymentMethod().isEmpty());

        MuSigOffer rejectedOffer = validOffer();
        when(rejectedOffer.getProtocolTypes()).thenReturn(List.of(TradeProtocolType.BISQ_EASY));

        assertRejected(useCase, rejectedOffer, Reason.PROTOCOL_TYPE_NOT_SUPPORTED);

        assertTrue(useCase.getSelectedAccount().isEmpty());
        assertTrue(useCase.getPaymentMethodService().getAccountsByPaymentMethod().isEmpty());
        assertTrue(useCase.getPaymentMethodService().getTakerSidePaymentMethodSpecs().isEmpty());
        assertTrue(useCase.getPaymentMethodService().getIncompatibleAccountsByPaymentMethod().isEmpty());
        assertNull(useCase.getPriceService().getPriceQuote());
        assertNull(useCase.getPriceService().getPriceDeviation());
        assertNull(useCase.getAmountService().getAmountSpec());
        assertNull(useCase.getAmountService().getFixTradeAmount());
        assertNull(useCase.getAmountService().getTradeAmountLimits());
        assertNull(useCase.getAmountService().getInputAmountLimits());
        assertTrue(useCase.getAmountService().getUserSpecificTradeAmountLimit().isEmpty());
        assertTrue(useCase.getAmountService().isAmountValid());

        fireMarketPriceUpdate(PriceQuote.fromFiatPrice(105_000, "USD"));
        assertNull(useCase.getPriceService().getPriceQuote());
        assertNull(useCase.getPriceService().getPriceDeviation());
    }

    @Test
    public void rejectedInitializationLeavesNoPaymentState() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        when(marketPriceService.findMarketPrice(market)).thenReturn(Optional.empty());
        MuSigOffer offer = validOffer();

        assertRejected(useCase, offer, Reason.NO_MARKET_PRICE);

        assertTrue(useCase.getPaymentMethodService().getAccountsByPaymentMethod().isEmpty());
        assertTrue(useCase.getPaymentMethodService().getTakerSidePaymentMethodSpecs().isEmpty());
        assertTrue(useCase.getPaymentMethodService().getIncompatibleAccountsByPaymentMethod().isEmpty());
    }

    @Test
    public void deviationIsComputedForFixedPriceOffers() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        when(offer.getPriceSpec()).thenReturn(new FixPriceSpec(PriceQuote.fromFiatPrice(110_000, "USD")));

        useCase.initialize(offer);

        assertEquals(0.10, useCase.getPriceService().getPriceDeviation(), 1e-9);
    }

    @Test
    public void deviationIsZeroForMarketPriceOffers() {
        TakeOfferUseCase useCase = createUseCase();

        useCase.initialize(validOffer());

        assertEquals(0.0, useCase.getPriceService().getPriceDeviation(), 1e-9);
    }

    @Test
    public void marketPriceUpdateRefreshesQuoteForMarketPriceOffers() {
        TakeOfferUseCase useCase = createUseCase();
        useCase.initialize(validOffer());
        assertEquals(marketPriceQuote, useCase.getPriceService().getPriceQuote());

        PriceQuote updatedQuote = PriceQuote.fromFiatPrice(105_000, "USD");
        fireMarketPriceUpdate(updatedQuote);

        assertEquals(updatedQuote, useCase.getPriceService().getPriceQuote());
        assertEquals(0.0, useCase.getPriceService().getPriceDeviation(), 1e-9);
    }

    @Test
    public void marketPriceUpdateKeepsFixedQuoteButRefreshesDeviation() {
        TakeOfferUseCase useCase = createUseCase();
        MuSigOffer offer = validOffer();
        PriceQuote fixedQuote = PriceQuote.fromFiatPrice(100_000, "USD");
        when(offer.getPriceSpec()).thenReturn(new FixPriceSpec(fixedQuote));
        useCase.initialize(offer);
        assertEquals(0.0, useCase.getPriceService().getPriceDeviation(), 1e-9);

        fireMarketPriceUpdate(PriceQuote.fromFiatPrice(80_000, "USD"));

        assertEquals(fixedQuote, useCase.getPriceService().getPriceQuote());
        assertEquals(0.25, useCase.getPriceService().getPriceDeviation(), 1e-9);
    }

    @Test
    public void rejectedInitializationDoesNotReactToMarketPriceUpdates() {
        TakeOfferUseCase useCase = createUseCase();
        when(marketPriceService.findMarketPrice(market)).thenReturn(Optional.empty());
        assertRejected(useCase, validOffer(), Reason.NO_MARKET_PRICE);

        fireMarketPriceUpdate(PriceQuote.fromFiatPrice(105_000, "USD"));

        assertNull(useCase.getPriceService().getPriceQuote());
        assertNull(useCase.getPriceService().getPriceDeviation());
    }

    /* --------------------------------------------------------------------- */
    // Amount (take-offer.md, "Amount" and "Amount limits")

    /* --------------------------------------------------------------------- */
    // Converted amount sanity (zero and overflowing conversions)
    /* --------------------------------------------------------------------- */

    @Test
    public void fixedAmountConvertingToZeroOnTheBaseSideIsRejected() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyFixedAmount(offer, 500);
        // An internally consistent fixed price can still be absurd: 500 USD at 8e14 USD/BTC
        // rounds to 0 sats on the base side while the quote side stays within all limits.
        when(offer.getPriceSpec()).thenReturn(new FixPriceSpec(PriceQuote.fromFiatPrice(800_000_000_000_000L, "USD")));

        assertRejected(useCase, offer, Reason.AMOUNT_OUTSIDE_LIMITS);
    }

    @Test
    public void baseSideRangeWhoseConversionOverflowsIsRejected() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        // The conversion of these base side amounts overflows a long; an unchecked conversion
        // wraps them into a plausible looking quote range instead of failing.
        BaseSideRangeAmountSpec amountSpec =
                new BaseSideRangeAmountSpec(1_844_674_407_371_955_162L, 1_844_674_407_373_955_162L);
        when(offer.getAmountSpec()).thenReturn(amountSpec);
        when(offer.hasAmountRange()).thenReturn(true);

        assertRejected(useCase, offer, Reason.INVALID_OFFER);
    }

    @Test
    public void amountInputWhoseConversionOverflowsIsIgnored() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyRangeAmount(offer, 1000, 3000);
        useCase.initialize(offer);
        var midpoint = useCase.getAmountService().getFixTradeAmount();

        // The typed amount wraps on conversion; clamping the wrapped pair would publish a base
        // and quote side that no longer belong to the same price.
        useCase.setFixTradeAmountFromInputAmount(Coin.asBtcFromValue(1_844_674_407_371_955_162L));

        assertEquals(midpoint, useCase.getAmountService().getFixTradeAmount());
        assertTrue(useCase.getAmountService().isAmountValid());
    }

    @Test
    public void backgroundPriceUpdateZeroingThePassiveSideBlocksTheHandoff() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyRangeAmount(offer, 1000, 3000);
        useCase.initialize(offer);
        assertTrue(useCase.getHandoff().isPresent());
        var limitsBefore = useCase.getAmountService().getTradeAmountLimits();

        // The quote side intersection is price independent, so only the recomputed passive side
        // reveals the absurd price; a 0 sat base amount must never reach the handoff, and the
        // zero-sided recomputed limits must not replace the published ones.
        fireMarketPriceUpdate(PriceQuote.fromFiatPrice(800_000_000_000_000L, "USD"));

        assertFalse(useCase.getAmountService().isAmountValid());
        assertTrue(useCase.getHandoff().isEmpty());
        assertEquals(limitsBefore, useCase.getAmountService().getTradeAmountLimits());
    }

    /* --------------------------------------------------------------------- */

    @Test
    public void tradeFeeIsSetFromTheMaxTradeAmountAtInitialization() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyRangeAmount(offer, 1000, 3000);

        useCase.initialize(offer);

        // The mock keys the fee off the maximum BTC-side trade amount (3000 USD at 100k = 0.03 BTC).
        long maxSats = useCase.getAmountService().getTradeAmountLimits().getMax().getBitcoinSideAmount().getValue();
        assertEquals(feeForMaxSats(maxSats), useCase.getFeeService().getTradeFee());
    }

    @Test
    public void nonPositiveMarketPriceIsRejectedAtInitialization() {
        TakeOfferUseCase useCase = createUseCase();
        PriceQuote zeroQuote = new PriceQuote(0, Coin.asBtcFromValue(1L), Fiat.fromFaceValue(100_000, "USD"));
        MarketPrice zeroPrice = mock(MarketPrice.class);
        when(zeroPrice.getPriceQuote()).thenReturn(zeroQuote);
        when(marketPriceService.findMarketPrice(market)).thenReturn(Optional.of(zeroPrice));
        when(marketPriceService.findMarketPriceQuote(market)).thenReturn(Optional.of(zeroQuote));

        assertRejected(useCase, validOffer(), Reason.NO_MARKET_PRICE);
    }

    @Test
    public void nonPositiveMarketPriceUpdateBlocksTheHandoff() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        useCase.initialize(validOffer());
        assertTrue(useCase.getHandoff().isPresent());

        // A zero-value price arriving on the poller must not be published or persisted; it is
        // treated as no price, blocking confirmation (no marketPrice=0 contract).
        PriceQuote zeroQuote = new PriceQuote(0, Coin.asBtcFromValue(1L), Fiat.fromFaceValue(100_000, "USD"));
        MarketPrice zeroPrice = mock(MarketPrice.class);
        when(zeroPrice.getPriceQuote()).thenReturn(zeroQuote);
        when(marketPriceService.findMarketPrice(market)).thenReturn(Optional.of(zeroPrice));
        when(marketPriceService.findMarketPriceQuote(market)).thenReturn(Optional.of(zeroQuote));
        marketPriceByCurrencyMap.put(market, zeroPrice);

        assertNull(useCase.getPriceService().getMarketPriceQuote());
        assertTrue(useCase.getHandoff().isEmpty());
    }

    @Test
    public void handoffSnapshotPairsTheAmountsWithTheMarketPrice() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        useCase.initialize(offer);

        TakeOfferUseCase.Handoff handoff = useCase.getHandoff().orElseThrow();
        TradeAmount fixTradeAmount = useCase.getAmountService().getFixTradeAmount();
        assertEquals(fixTradeAmount.getBaseSideAmount(), handoff.baseSideAmount());
        assertEquals(fixTradeAmount.getQuoteSideAmount(), handoff.quoteSideAmount());
        assertEquals(marketPriceQuote.getValue(), handoff.marketPrice());

        // A background price update moves both the amounts' passive side and the handed price;
        // the snapshot must pair the two from the same update, never a torn mix.
        PriceQuote newQuote = PriceQuote.fromFiatPrice(120_000, "USD");
        fireMarketPriceUpdate(newQuote);
        TakeOfferUseCase.Handoff updated = useCase.getHandoff().orElseThrow();
        TradeAmount refreshed = useCase.getAmountService().getFixTradeAmount();
        assertEquals(refreshed.getBaseSideAmount(), updated.baseSideAmount());
        assertEquals(newQuote.getValue(), updated.marketPrice());
        assertEquals(newQuote.toBaseSideMonetary(refreshed.getQuoteSideAmount()), updated.baseSideAmount());
    }

    @Test
    public void handoffIsEmptyWhenTheAmountIsInvalid() {
        Market eurMarket = new Market("BTC", "EUR", "Bitcoin", "Euro");
        PriceQuote btcEurQuote = PriceQuote.fromFiatPrice(100_000, "EUR");
        stubEurMarketPrice(eurMarket, btcEurQuote);

        MuSigOffer offer = offerWithMethods(Direction.BUY, advancedCashMethod);
        Account<?, ?> acAccount = accountFor(advancedCashMethod);
        TakeOfferUseCase eurUseCase = createUseCase(market -> List.of(acAccount));
        when(offer.getMarket()).thenReturn(eurMarket);
        when(offer.getPriceSpec()).thenReturn(new FixPriceSpec(btcEurQuote));
        QuoteSideFixedAmountSpec amountSpec = new QuoteSideFixedAmountSpec(Fiat.fromFaceValue(9_500, "EUR").getValue());
        when(offer.getAmountSpec()).thenReturn(amountSpec);
        when(offer.hasAmountRange()).thenReturn(false);
        eurUseCase.initialize(offer);
        assertTrue(eurUseCase.getHandoff().isPresent());

        // The absolute maximum drops below the fixed amount; the amount is invalid, so no handoff.
        fireEurMarketPriceUpdate(eurMarket, PriceQuote.fromFiatPrice(90_000, "EUR"));
        assertFalse(eurUseCase.getAmountService().isAmountValid());
        assertTrue(eurUseCase.getHandoff().isEmpty());
    }

    @Test
    public void fixedOfferFeeStaysOnTheFixedAmountThroughRecomputation() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyFixedAmount(offer, 500);
        useCase.initialize(offer);

        TradeAmount fixTradeAmount = useCase.getAmountService().getFixTradeAmount();
        Coin feeAtInit = feeForMaxSats(fixTradeAmount.getBitcoinSideAmount().getValue());
        assertEquals(feeAtInit, useCase.getFeeService().getTradeFee());

        // A background price update recomputes limits; the fee must stay keyed on the fixed
        // amount, not the wider effective range.
        fireMarketPriceUpdate(PriceQuote.fromFiatPrice(120_000, "USD"));
        TradeAmount refreshed = useCase.getAmountService().getFixTradeAmount();
        assertEquals(feeForMaxSats(refreshed.getBitcoinSideAmount().getValue()), useCase.getFeeService().getTradeFee());
    }

    @Test
    public void tradeFeeTracksTheEffectiveMaxWhenAMethodChangesIt() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        Account<?, ?> acAccount = accountFor(advancedCashMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount, acAccount));
        MuSigOffer offer = offerWithMethods(Direction.BUY, wiseMethod, advancedCashMethod);
        applyRangeAmount(offer, 1000, 8000);
        useCase.initialize(offer);

        // No rail selected yet: effective max = offer max 8000 USD.
        long maxAtInit = useCase.getAmountService().getTradeAmountLimits().getMax().getBitcoinSideAmount().getValue();
        assertEquals(feeForMaxSats(maxAtInit), useCase.getFeeService().getTradeFee());

        // WISE's 5000 USD rail limit lowers the effective max; the fee must follow.
        useCase.getPaymentMethodService().onPaymentMethodSelected(wiseMethod);
        long maxAfterWise = useCase.getAmountService().getTradeAmountLimits().getMax().getBitcoinSideAmount().getValue();
        assertEquals(feeForMaxSats(maxAfterWise), useCase.getFeeService().getTradeFee());
        assertNotEquals(maxAtInit, maxAfterWise);
    }

    private static Coin feeForMaxSats(long maxSats) {
        return Coin.asBtcFromValue(Math.max(1_000L, Math.round(maxSats * 0.001)));
    }

    @Test
    public void rejectedInitializationClearsTheTradeFee() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        useCase.initialize(validOffer());
        assertNotNull(useCase.getFeeService().getTradeFee());

        MuSigOffer rejectedOffer = validOffer();
        when(rejectedOffer.getProtocolTypes()).thenReturn(List.of(TradeProtocolType.BISQ_EASY));
        assertRejected(useCase, rejectedOffer, Reason.PROTOCOL_TYPE_NOT_SUPPORTED);

        assertNull(useCase.getFeeService().getTradeFee());
    }

    @Test
    public void fixedAmountOfferInitializesTradeAmountAndSkipsAmountStep() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();

        useCase.initialize(offer);

        TradeAmount fixTradeAmount = useCase.getAmountService().getFixTradeAmount();
        assertNotNull(fixTradeAmount);
        assertEquals(usd(500), fixTradeAmount.getQuoteSideAmount());
        assertEquals(marketPriceQuote.toBaseSideMonetary(usd(500)), fixTradeAmount.getBaseSideAmount());
        assertFalse(useCase.shouldShowAmountStep());
        assertTrue(useCase.getAmountService().isAmountValid());
        assertNotNull(useCase.getAmountService().getTradeAmountLimits());
    }

    @Test
    public void fixedAmountBelowAbsoluteMinimumRejects() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyFixedAmount(offer, 5);

        assertRejected(useCase, offer, Reason.AMOUNT_OUTSIDE_LIMITS);
    }

    @Test
    public void fixedAmountAboveUserCapRejectsOnlyWhenTakerBuysBitcoin() {
        Account<?, ?> acAccount = accountFor(advancedCashMethod);
        TakeOfferUseCase sellUseCase = createUseCase(market -> List.of(acAccount));
        MuSigOffer sellOffer = offerWithMethods(Direction.SELL, advancedCashMethod);
        applyFixedAmount(sellOffer, 5000);

        assertRejected(sellUseCase, sellOffer, Reason.AMOUNT_OUTSIDE_LIMITS);

        TakeOfferUseCase buyUseCase = createUseCase(market -> List.of(acAccount));
        MuSigOffer buyOffer = offerWithMethods(Direction.BUY, advancedCashMethod);
        applyFixedAmount(buyOffer, 5000);

        buyUseCase.initialize(buyOffer);
        assertEquals(usd(5000), buyUseCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());
    }

    @Test
    public void rangeOfferComputesEffectiveRangeAndMidpointDefault() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyRangeAmount(offer, 1000, 3000);

        useCase.initialize(offer);

        assertTrue(useCase.shouldShowAmountStep());
        assertEquals(usd(1000), useCase.getAmountService().getTradeAmountLimits().getMin().getQuoteSideAmount());
        assertEquals(usd(3000), useCase.getAmountService().getTradeAmountLimits().getMax().getQuoteSideAmount());
        assertEquals(usd(1000), useCase.getAmountService().getInputAmountLimits().getMin());
        assertEquals(usd(3000), useCase.getAmountService().getInputAmountLimits().getMax());
        assertEquals(usd(2000), useCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());
        assertEquals(0.5, useCase.getAmountService().getFixAmountSliderValue(), 1e-9);
    }

    @Test
    public void rangeOfferMethodLimitBindsEffectiveMaximum() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyRangeAmount(offer, 1000, 8000);

        useCase.initialize(offer);

        // WISE is a MODERATE chargeback-risk rail: 50% of the 10k USD absolute maximum.
        assertEquals(usd(5000), useCase.getAmountService().getTradeAmountLimits().getMax().getQuoteSideAmount());
        assertEquals(usd(5000), useCase.getAmountService().getInputAmountLimits().getMax());
    }

    @Test
    public void rangeOfferOutsideAbsoluteLimitsRejects() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyRangeAmount(offer, 15000, 20000);

        assertRejected(useCase, offer, Reason.AMOUNT_OUTSIDE_LIMITS);
    }

    @Test
    public void rangeCollapsingToPointSkipsAmountStep() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyRangeAmount(offer, 5000, 8000);

        useCase.initialize(offer);

        // The WISE limit squeezes the range to the single point 5000 USD.
        assertFalse(useCase.shouldShowAmountStep());
        assertEquals(usd(5000), useCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());
        assertTrue(useCase.getAmountService().isAmountValid());
    }

    @Test
    public void userCapBecomesEffectiveMaximumAndMarkerForBitcoinBuyer() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = offerWithMethods(Direction.SELL, wiseMethod);
        applyRangeAmount(offer, 1000, 8000);

        useCase.initialize(offer);

        // Effective maximum = 4000 USD user cap; slider base stays the pre-user range 1000-5000.
        assertEquals(usd(4000), useCase.getAmountService().getTradeAmountLimits().getMax().getQuoteSideAmount());
        assertEquals(usd(5000), useCase.getAmountService().getInputAmountLimits().getMax());
        assertEquals(usd(4000), useCase.getAmountService().getUserSpecificTradeAmountLimit().orElseThrow().getQuoteSideAmount());
        assertEquals(0.75, useCase.getAmountService().getUserSpecificTradeAmountLimitAsSliderValue().orElseThrow(), 1e-9);
        // Midpoint of the EFFECTIVE range 1000-4000.
        assertEquals(usd(2500), useCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());
    }

    @Test
    public void inputAmountEntryClampsToEffectiveRange() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyRangeAmount(offer, 1000, 8000);
        useCase.initialize(offer);

        useCase.setFixTradeAmountFromInputAmount(usd(3000));
        assertEquals(usd(3000), useCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());
        assertEquals(0.5, useCase.getAmountService().getFixAmountSliderValue(), 1e-9);

        useCase.setFixTradeAmountFromInputAmount(usd(7000));
        assertEquals(usd(5000), useCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());
        assertEquals(1.0, useCase.getAmountService().getFixAmountSliderValue(), 1e-9);
    }

    @Test
    public void sliderEntryMapsOverBaseRangeAndCapsAtUserLimit() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = offerWithMethods(Direction.SELL, wiseMethod);
        applyRangeAmount(offer, 1000, 8000);
        useCase.initialize(offer);

        useCase.setFixTradeAmountFromSliderValue(1.0);

        // Slider end = 5000 USD (pre-user maximum), capped at the 4000 USD user limit and re-emitted.
        assertEquals(usd(4000), useCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());
        assertEquals(0.75, useCase.getAmountService().getFixAmountSliderValue(), 1e-9);
    }

    @Test
    public void paymentMethodSwitchClampsSelectionVisibly() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        Account<?, ?> acAccount = accountFor(advancedCashMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount, acAccount));
        MuSigOffer offer = offerWithMethods(Direction.BUY, wiseMethod, advancedCashMethod);
        applyRangeAmount(offer, 1000, 8000);
        useCase.initialize(offer);

        useCase.getPaymentMethodService().onPaymentMethodSelected(advancedCashMethod);
        useCase.setFixTradeAmountFromInputAmount(usd(7000));
        assertEquals(usd(7000), useCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());

        useCase.getPaymentMethodService().onPaymentMethodSelected(wiseMethod);

        // User-initiated change: the selection is clamped visibly into the new effective range.
        assertEquals(usd(5000), useCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());
        assertEquals(1.0, useCase.getAmountService().getFixAmountSliderValue(), 1e-9);
        assertTrue(useCase.getAmountService().isAmountValid());
    }

    @Test
    public void laterMethodSelectionInvalidatesFixedAmountWithoutClamp() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        Account<?, ?> acAccount = accountFor(advancedCashMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount, acAccount));
        MuSigOffer offer = offerWithMethods(Direction.BUY, wiseMethod, advancedCashMethod);
        applyFixedAmount(offer, 6000);

        useCase.initialize(offer);
        assertTrue(useCase.getAmountService().isAmountValid());

        useCase.getPaymentMethodService().onPaymentMethodSelected(wiseMethod);

        // A fixed offer amount is never clamped; it becomes invalid instead.
        assertFalse(useCase.getAmountService().isAmountValid());
        assertEquals(usd(6000), useCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());

        useCase.getPaymentMethodService().onPaymentMethodSelected(advancedCashMethod);
        assertTrue(useCase.getAmountService().isAmountValid());
    }

    @Test
    public void backgroundPriceUpdateNeverClampsAndBlocksViaValidity() {
        Market eurMarket = new Market("BTC", "EUR", "Bitcoin", "Euro");
        PriceQuote btcEurQuote = PriceQuote.fromFiatPrice(100_000, "EUR");
        Fiat offerAmount = Fiat.fromFaceValue(9_500, "EUR");
        stubEurMarketPrice(eurMarket, btcEurQuote);

        MuSigOffer offer = offerWithMethods(Direction.BUY, advancedCashMethod);
        Account<?, ?> acAccount = accountFor(advancedCashMethod);
        TakeOfferUseCase eurUseCase = createUseCase(market -> List.of(acAccount));
        when(offer.getMarket()).thenReturn(eurMarket);
        when(offer.getPriceSpec()).thenReturn(new FixPriceSpec(btcEurQuote));
        QuoteSideFixedAmountSpec amountSpec = new QuoteSideFixedAmountSpec(offerAmount.getValue());
        when(offer.getAmountSpec()).thenReturn(amountSpec);
        when(offer.hasAmountRange()).thenReturn(false);

        eurUseCase.initialize(offer);
        assertTrue(eurUseCase.getAmountService().isAmountValid());

        // EUR weakens against USD: the 10k USD absolute maximum is now 9000 EUR < the fixed 9500 EUR.
        fireEurMarketPriceUpdate(eurMarket, PriceQuote.fromFiatPrice(90_000, "EUR"));
        assertFalse(eurUseCase.getAmountService().isAmountValid());
        assertEquals(offerAmount, eurUseCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());

        // Recovery lifts the block.
        fireEurMarketPriceUpdate(eurMarket, PriceQuote.fromFiatPrice(100_000, "EUR"));
        assertTrue(eurUseCase.getAmountService().isAmountValid());
    }

    @Test
    public void backgroundPriceUpdateKeepsInputSideOfRangeSelection() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyRangeAmount(offer, 1000, 3000);
        useCase.initialize(offer);
        useCase.setFixTradeAmountFromInputAmount(usd(3000));

        PriceQuote newQuote = PriceQuote.fromFiatPrice(120_000, "USD");
        fireMarketPriceUpdate(newQuote);

        TradeAmount fixTradeAmount = useCase.getAmountService().getFixTradeAmount();
        assertEquals(usd(3000), fixTradeAmount.getQuoteSideAmount());
        assertEquals(newQuote.toBaseSideMonetary(usd(3000)), fixTradeAmount.getBaseSideAmount());
        assertTrue(useCase.getAmountService().isAmountValid());
    }

    @Test
    public void inputSideSwitchRecomputesInputRangeAndConversions() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = validOffer();
        applyRangeAmount(offer, 1000, 3000);
        useCase.initialize(offer);

        useCase.setUseBaseCurrencyForAmountInput(true);

        assertEquals("BTC", useCase.getAmountService().getInputAmountLimits().getMin().getCode());
        TradeAmount fixTradeAmount = useCase.getAmountService().getFixTradeAmount();
        assertEquals(fixTradeAmount.getBaseSideAmount(), useCase.toInputAmount(fixTradeAmount));
        assertEquals(fixTradeAmount.getQuoteSideAmount(), useCase.toPassiveAmount(fixTradeAmount));

        useCase.setUseBaseCurrencyForAmountInput(false);
        assertEquals("USD", useCase.getAmountService().getInputAmountLimits().getMin().getCode());
        assertEquals(fixTradeAmount.getQuoteSideAmount(), useCase.toInputAmount(fixTradeAmount));
    }

    @Test
    public void incomputableLimitsBlockConfirmationUntilRecovery() {
        Market eurMarket = new Market("BTC", "EUR", "Bitcoin", "Euro");
        PriceQuote btcEurQuote = PriceQuote.fromFiatPrice(100_000, "EUR");
        stubEurMarketPrice(eurMarket, btcEurQuote);

        MuSigOffer offer = offerWithMethods(Direction.BUY, advancedCashMethod);
        Account<?, ?> acAccount = accountFor(advancedCashMethod);
        TakeOfferUseCase eurUseCase = createUseCase(market -> List.of(acAccount));
        when(offer.getMarket()).thenReturn(eurMarket);
        when(offer.getPriceSpec()).thenReturn(new FixPriceSpec(btcEurQuote));
        QuoteSideFixedAmountSpec amountSpec = new QuoteSideFixedAmountSpec(Fiat.fromFaceValue(5_000, "EUR").getValue());
        when(offer.getAmountSpec()).thenReturn(amountSpec);
        when(offer.hasAmountRange()).thenReturn(false);

        eurUseCase.initialize(offer);
        assertTrue(eurUseCase.getAmountService().isAmountValid());

        // The BTC/USD price needed for the USD-defined limits vanishes while the offer market's
        // price is still present: the limits are not computable, confirmation must be blocked.
        doThrow(new IllegalStateException("No BTC/USD market price"))
                .when(marketPriceService).getMarketPriceQuoteOrThrow(market);
        fireEurMarketPriceUpdate(eurMarket, PriceQuote.fromFiatPrice(99_000, "EUR"));
        assertFalse(eurUseCase.getAmountService().isAmountValid());

        // Recovery lifts the block with the next successful recomputation.
        doReturn(marketPriceQuote).when(marketPriceService).getMarketPriceQuoteOrThrow(market);
        fireEurMarketPriceUpdate(eurMarket, PriceQuote.fromFiatPrice(100_000, "EUR"));
        assertTrue(eurUseCase.getAmountService().isAmountValid());
    }

    @Test
    public void methodWhoseLimitCannotCoverTheOfferIsInadmissible() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        Account<?, ?> acAccount = accountFor(advancedCashMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount, acAccount));
        MuSigOffer offer = offerWithMethods(Direction.BUY, wiseMethod, advancedCashMethod);
        applyRangeAmount(offer, 6000, 8000);
        useCase.initialize(offer);

        // The WISE 5000 USD rail limit empties the 6000-8000 range; ADVANCED_CASH covers it.
        assertFalse(useCase.isPaymentMethodAdmissible(wiseMethod));
        assertTrue(useCase.isPaymentMethodAdmissible(advancedCashMethod));

        TakeOfferUseCase fixedUseCase = createUseCase(market -> List.of(wiseAccount, acAccount));
        MuSigOffer fixedOffer = offerWithMethods(Direction.BUY, wiseMethod, advancedCashMethod);
        applyFixedAmount(fixedOffer, 6000);
        fixedUseCase.initialize(fixedOffer);

        assertFalse(fixedUseCase.isPaymentMethodAdmissible(wiseMethod));
        assertTrue(fixedUseCase.isPaymentMethodAdmissible(advancedCashMethod));
    }

    @Test
    public void lateSelectionCollapsingTheRangeHidesTheAmountStep() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        Account<?, ?> acAccount = accountFor(advancedCashMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount, acAccount));
        MuSigOffer offer = offerWithMethods(Direction.BUY, wiseMethod, advancedCashMethod);
        applyRangeAmount(offer, 5000, 8000);
        useCase.initialize(offer);
        assertTrue(useCase.shouldShowAmountStep());

        // WISE's 5000 USD limit collapses the range to a point: treated as fixed, step hidden.
        useCase.getPaymentMethodService().onPaymentMethodSelected(wiseMethod);
        assertFalse(useCase.shouldShowAmountStep());
        assertEquals(usd(5000), useCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());

        useCase.getPaymentMethodService().onPaymentMethodSelected(advancedCashMethod);
        assertTrue(useCase.shouldShowAmountStep());
    }

    @Test
    public void editingWhileLimitsAreStaleDoesNotLiftTheConfirmationBlock() {
        Market eurMarket = new Market("BTC", "EUR", "Bitcoin", "Euro");
        PriceQuote btcEurQuote = PriceQuote.fromFiatPrice(100_000, "EUR");
        stubEurMarketPrice(eurMarket, btcEurQuote);

        MuSigOffer offer = offerWithMethods(Direction.BUY, advancedCashMethod);
        Account<?, ?> acAccount = accountFor(advancedCashMethod);
        TakeOfferUseCase eurUseCase = createUseCase(market -> List.of(acAccount));
        when(offer.getMarket()).thenReturn(eurMarket);
        when(offer.getPriceSpec()).thenReturn(new FixPriceSpec(btcEurQuote));
        QuoteSideRangeAmountSpec amountSpec = new QuoteSideRangeAmountSpec(
                Fiat.fromFaceValue(9_200, "EUR").getValue(), Fiat.fromFaceValue(9_800, "EUR").getValue());
        when(offer.getAmountSpec()).thenReturn(amountSpec);
        when(offer.hasAmountRange()).thenReturn(true);

        eurUseCase.initialize(offer);
        assertTrue(eurUseCase.getAmountService().isAmountValid());

        // EUR weakens: the absolute maximum falls to 9000 EUR, below the offer minimum - the
        // intersection is empty and the published limits are stale.
        fireEurMarketPriceUpdate(eurMarket, PriceQuote.fromFiatPrice(90_000, "EUR"));
        assertFalse(eurUseCase.getAmountService().isAmountValid());

        // Editing against the stale published limits must not lift the block.
        eurUseCase.setFixTradeAmountFromSliderValue(0.5);
        assertFalse(eurUseCase.getAmountService().isAmountValid());
        eurUseCase.setFixTradeAmountFromInputAmount(Fiat.fromFaceValue(9_300, "EUR"));
        assertFalse(eurUseCase.getAmountService().isAmountValid());

        // Recovery recomputes the limits; editing validates again.
        fireEurMarketPriceUpdate(eurMarket, PriceQuote.fromFiatPrice(100_000, "EUR"));
        assertTrue(eurUseCase.getAmountService().isAmountValid());
        eurUseCase.setFixTradeAmountFromSliderValue(0.5);
        assertTrue(eurUseCase.getAmountService().isAmountValid());
    }

    @Test
    public void offerNoMethodCanCoverIsRejectedAtInitialization() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        Account<?, ?> achAccount = accountFor(achMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount, achAccount));
        MuSigOffer offer = offerWithMethods(Direction.BUY, wiseMethod, achMethod);
        // Both offered rails cap at 5000 USD; no method can cover 6000-8000.
        applyRangeAmount(offer, 6000, 8000);

        assertRejected(useCase, offer, Reason.AMOUNT_OUTSIDE_LIMITS);
    }

    @Test
    public void inadmissiblePreselectionIsDroppedInsteadOfRejecting() {
        // WISE has the only eligible account and gets preselected, but its 5000 USD rail limit
        // cannot cover the fixed 6000 USD amount; ADVANCED_CASH stays selectable via account
        // creation, so the offer must not be rejected and the preselection must be dropped.
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        MuSigOffer offer = offerWithMethods(Direction.BUY, wiseMethod, advancedCashMethod);
        applyFixedAmount(offer, 6000);

        useCase.initialize(offer);

        assertTrue(useCase.getSelectedAccount().isEmpty());
        assertTrue(useCase.getAmountService().isAmountValid());
        assertEquals(usd(6000), useCase.getAmountService().getFixTradeAmount().getQuoteSideAmount());
    }

    @Test
    public void missingUsdPriceAtInitializationRejectsCleanly() {
        Market eurMarket = new Market("BTC", "EUR", "Bitcoin", "Euro");
        PriceQuote btcEurQuote = PriceQuote.fromFiatPrice(100_000, "EUR");
        stubEurMarketPrice(eurMarket, btcEurQuote);

        MuSigOffer offer = offerWithMethods(Direction.BUY, advancedCashMethod);
        Account<?, ?> acAccount = accountFor(advancedCashMethod);
        TakeOfferUseCase eurUseCase = createUseCase(market -> List.of(acAccount));
        when(offer.getMarket()).thenReturn(eurMarket);
        QuoteSideFixedAmountSpec amountSpec = new QuoteSideFixedAmountSpec(Fiat.fromFaceValue(5_000, "EUR").getValue());
        when(offer.getAmountSpec()).thenReturn(amountSpec);
        when(offer.hasAmountRange()).thenReturn(false);
        // The BTC/USD price needed for the USD-defined limits is missing while the offer
        // market's price exists: a clean rejection, not an uncaught runtime exception.
        doThrow(new IllegalStateException("No BTC/USD market price"))
                .when(marketPriceService).getMarketPriceQuoteOrThrow(market);

        assertRejected(eurUseCase, offer, Reason.NO_MARKET_PRICE);
        assertNull(eurUseCase.getAmountService().getAmountSpec());
    }

    @Test
    public void collapseStateIsCurrentWhenTheLimitsArePublished() {
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        Account<?, ?> acAccount = accountFor(advancedCashMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount, acAccount));
        MuSigOffer offer = offerWithMethods(Direction.BUY, wiseMethod, advancedCashMethod);
        applyRangeAmount(offer, 5000, 8000);
        useCase.initialize(offer);

        // Observers of the limits (the wizard's step visibility) must read a consistent
        // shouldShowAmountStep at fire time: collapse state updates before the ranges publish.
        List<Boolean> shouldShowAtFireTime = new java.util.ArrayList<>();
        useCase.getAmountService().tradeAmountLimitsObservable().addObserver(limits ->
                shouldShowAtFireTime.add(useCase.shouldShowAmountStep()));
        shouldShowAtFireTime.clear();

        useCase.getPaymentMethodService().onPaymentMethodSelected(wiseMethod);
        assertEquals(List.of(false), shouldShowAtFireTime);

        shouldShowAtFireTime.clear();
        useCase.getPaymentMethodService().onPaymentMethodSelected(advancedCashMethod);
        assertEquals(List.of(true), shouldShowAtFireTime);
    }

    private void stubEurMarketPrice(Market eurMarket, PriceQuote quote) {
        MarketPrice marketPrice = mock(MarketPrice.class);
        when(marketPrice.getPriceQuote()).thenReturn(quote);
        when(marketPriceService.findMarketPrice(eurMarket)).thenReturn(Optional.of(marketPrice));
        when(marketPriceService.findMarketPriceQuote(eurMarket)).thenReturn(Optional.of(quote));
        when(marketPriceService.getMarketPriceQuoteOrThrow(eurMarket)).thenReturn(quote);
    }

    private void fireEurMarketPriceUpdate(Market eurMarket, PriceQuote quote) {
        stubEurMarketPrice(eurMarket, quote);
        MarketPrice marketPrice = mock(MarketPrice.class);
        when(marketPrice.getPriceQuote()).thenReturn(quote);
        marketPriceByCurrencyMap.put(eurMarket, marketPrice);
    }

    private MuSigOffer offerWithMethods(Direction direction, PaymentMethod<?>... methods) {
        MuSigOffer offer = validOffer();
        when(offer.getDirection()).thenReturn(direction);
        List<PaymentMethodSpec<?>> specs = new java.util.ArrayList<>();
        List<OfferOption> options = new java.util.ArrayList<>();
        options.add(new CollateralOption(0.25, 0.25));
        for (PaymentMethod<?> method : methods) {
            specs.add(specOf(method));
            options.add(accountOption(method));
        }
        when(offer.getQuoteSidePaymentMethodSpecs()).thenReturn(specs);
        when(offer.getOfferOptions()).thenReturn(options);
        return offer;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Account<?, ?> accountFor(PaymentMethod<?> paymentMethod) {
        Account account = mock(Account.class);
        when(account.getPaymentMethod()).thenReturn(paymentMethod);
        return account;
    }

    private TakeOfferUseCase createUseCase() {
        return createUseCase(market -> List.of());
    }

    private TakeOfferUseCase createUseCase(bisq.offer.mu_sig.use_case.dependencies.AccountsProvider accountsProvider) {
        stubMarketPrice(marketPriceQuote);
        when(marketPriceService.getMarketPriceByCurrencyMap()).thenReturn(marketPriceByCurrencyMap);
        when(identityService.findAnyIdentityByNetworkId(any(NetworkId.class))).thenReturn(Optional.empty());
        return new TakeOfferUseCase(marketPriceService,
                identityService,
                mock(TakeOfferDraftCookieStore.class),
                accountsProvider);
    }

    private MuSigOffer validOffer() {
        NetworkId makerNetworkId = mock(NetworkId.class);
        List<PaymentMethodSpec<?>> quoteSideSpecs = List.of(specOf(wiseMethod));
        List<PaymentMethodSpec<?>> baseSideSpecs = List.of(specOf(mainChainMethod));
        List<OfferOption> offerOptions = List.of(new CollateralOption(0.25, 0.25), accountOption(wiseMethod));
        MuSigOffer offer = mock(MuSigOffer.class);
        when(offer.getId()).thenReturn("test-offer-id");
        when(offer.getMakerNetworkId()).thenReturn(makerNetworkId);
        when(offer.getMarket()).thenReturn(market);
        when(offer.getProtocolTypes()).thenReturn(List.of(TradeProtocolType.MU_SIG));
        when(offer.getDirection()).thenReturn(Direction.BUY);
        when(offer.getPriceSpec()).thenReturn(new MarketPriceSpec());
        when(offer.getQuoteSidePaymentMethodSpecs()).thenReturn(quoteSideSpecs);
        when(offer.getBaseSidePaymentMethodSpecs()).thenReturn(baseSideSpecs);
        when(offer.getOfferOptions()).thenReturn(offerOptions);
        applyFixedAmount(offer, 500);
        return offer;
    }

    private static void applyFixedAmount(MuSigOffer offer, long usdFaceValue) {
        QuoteSideFixedAmountSpec amountSpec = new QuoteSideFixedAmountSpec(usd(usdFaceValue).getValue());
        when(offer.getAmountSpec()).thenReturn(amountSpec);
        when(offer.hasAmountRange()).thenReturn(false);
    }

    private static void applyRangeAmount(MuSigOffer offer, long minUsdFaceValue, long maxUsdFaceValue) {
        QuoteSideRangeAmountSpec amountSpec =
                new QuoteSideRangeAmountSpec(usd(minUsdFaceValue).getValue(), usd(maxUsdFaceValue).getValue());
        when(offer.getAmountSpec()).thenReturn(amountSpec);
        when(offer.hasAmountRange()).thenReturn(true);
    }

    private static Fiat usd(long faceValue) {
        return Fiat.fromFaceValue(faceValue, "USD");
    }

    private static PaymentMethodSpec<?> specOf(PaymentMethod<?> paymentMethod) {
        PaymentMethodSpec<?> spec = mock(PaymentMethodSpec.class);
        when(spec.getPaymentMethod()).thenAnswer(invocation -> paymentMethod);
        return spec;
    }

    private static OfferOption accountOption(PaymentMethod<?> paymentMethod) {
        return accountOption(paymentMethod, List.of());
    }

    private static OfferOption accountOption(PaymentMethod<?> paymentMethod, List<String> acceptedCountryCodes) {
        AccountOption accountOption = mock(AccountOption.class);
        when(accountOption.getPaymentMethod()).thenAnswer(invocation -> paymentMethod);
        when(accountOption.getAcceptedCountryCodes()).thenReturn(acceptedCountryCodes);
        when(accountOption.getAcceptedBanks()).thenReturn(List.of());
        return accountOption;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Account<?, ?> countryAccountFor(PaymentMethod<?> paymentMethod, String countryCode) {
        Country country = mock(Country.class);
        when(country.getCode()).thenReturn(countryCode);
        CountryBasedAccountPayload payload = mock(CountryBasedAccountPayload.class);
        when(payload.getCountry()).thenReturn(country);
        Account account = mock(Account.class);
        when(account.getPaymentMethod()).thenReturn(paymentMethod);
        when(account.getAccountPayload()).thenAnswer(invocation -> payload);
        return account;
    }

    private static void assertRejected(TakeOfferUseCase useCase, MuSigOffer offer, Reason expectedReason) {
        TakeOfferValidationException exception =
                assertThrows(TakeOfferValidationException.class, () -> useCase.initialize(offer));
        assertEquals(expectedReason, exception.getReason());
    }
}
