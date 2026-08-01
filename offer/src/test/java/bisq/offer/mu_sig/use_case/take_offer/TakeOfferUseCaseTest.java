package bisq.offer.mu_sig.use_case.take_offer;

import bisq.account.accounts.Account;
import bisq.account.payment_method.BitcoinPaymentMethod;
import bisq.account.payment_method.BitcoinPaymentRail;
import bisq.account.payment_method.PaymentMethod;
import bisq.account.payment_method.PaymentMethodSpec;
import bisq.account.payment_method.fiat.FiatPaymentMethod;
import bisq.account.payment_method.fiat.FiatPaymentRail;
import bisq.account.protocol_type.TradeProtocolType;
import bisq.bonded_roles.market_price.MarketPrice;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.market.MarketRepository;
import bisq.common.monetary.PriceQuote;
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
import bisq.offer.price.spec.FixPriceSpec;
import bisq.offer.price.spec.FloatPriceSpec;
import bisq.offer.price.spec.MarketPriceSpec;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TakeOfferUseCaseTest {
    private final Market market = MarketRepository.getUSDBitcoinMarket();
    private final PriceQuote marketPriceQuote = PriceQuote.fromFiatPrice(100_000, "USD");
    private final PaymentMethod<?> wiseMethod = FiatPaymentMethod.fromPaymentRail(FiatPaymentRail.WISE);
    private final PaymentMethod<?> mainChainMethod = BitcoinPaymentMethod.fromPaymentRail(BitcoinPaymentRail.MAIN_CHAIN);

    private final MarketPriceService marketPriceService = mock(MarketPriceService.class);
    private final IdentityService identityService = mock(IdentityService.class);

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
        when(identityService.findActiveIdentity(any(NetworkId.class))).thenReturn(Optional.of(mock(Identity.class)));

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
        assertTrue(useCase.getSelectedAccount().isEmpty());
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
        Account<?, ?> wiseAccount = accountFor(wiseMethod);
        TakeOfferUseCase useCase = createUseCase(market -> List.of(wiseAccount));
        useCase.initialize(validOffer());
        assertEquals(wiseAccount, useCase.getSelectedAccount().orElseThrow());

        MuSigOffer rejectedOffer = validOffer();
        when(rejectedOffer.getProtocolTypes()).thenReturn(List.of(TradeProtocolType.BISQ_EASY));

        assertRejected(useCase, rejectedOffer, Reason.PROTOCOL_TYPE_NOT_SUPPORTED);

        assertTrue(useCase.getSelectedAccount().isEmpty());
        assertTrue(useCase.getPaymentMethodService().getAccountsByPaymentMethod().isEmpty());
        assertTrue(useCase.getPaymentMethodService().getTakerSidePaymentMethodSpecs().isEmpty());
        assertNull(useCase.getPriceService().getPriceQuote());
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
        MarketPrice marketPrice = mock(MarketPrice.class);
        when(marketPrice.getPriceQuote()).thenReturn(marketPriceQuote);
        when(marketPriceService.findMarketPrice(market)).thenReturn(Optional.of(marketPrice));
        when(marketPriceService.findMarketPriceQuote(market)).thenReturn(Optional.of(marketPriceQuote));
        when(identityService.findActiveIdentity(any(NetworkId.class))).thenReturn(Optional.empty());
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
        return offer;
    }

    private static PaymentMethodSpec<?> specOf(PaymentMethod<?> paymentMethod) {
        PaymentMethodSpec<?> spec = mock(PaymentMethodSpec.class);
        when(spec.getPaymentMethod()).thenAnswer(invocation -> paymentMethod);
        return spec;
    }

    private static OfferOption accountOption(PaymentMethod<?> paymentMethod) {
        AccountOption accountOption = mock(AccountOption.class);
        when(accountOption.getPaymentMethod()).thenAnswer(invocation -> paymentMethod);
        when(accountOption.getAcceptedCountryCodes()).thenReturn(List.of());
        when(accountOption.getAcceptedBanks()).thenReturn(List.of());
        return accountOption;
    }

    private static void assertRejected(TakeOfferUseCase useCase, MuSigOffer offer, Reason expectedReason) {
        TakeOfferValidationException exception =
                assertThrows(TakeOfferValidationException.class, () -> useCase.initialize(offer));
        assertEquals(expectedReason, exception.getReason());
    }
}
