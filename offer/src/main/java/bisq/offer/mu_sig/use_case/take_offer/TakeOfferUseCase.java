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

package bisq.offer.mu_sig.use_case.take_offer;

import bisq.account.AccountService;
import bisq.account.payment_method.PaymentMethod;
import bisq.account.payment_method.PaymentRail;
import bisq.account.protocol_type.TradeProtocolType;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.Monetary;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.identity.IdentityService;
import bisq.offer.amount.spec.AmountSpec;
import bisq.offer.mu_sig.MuSigOffer;
import bisq.offer.mu_sig.use_case.DraftOfferUseCase;
import bisq.offer.mu_sig.use_case.create_offer.payment_method.PaymentMethodSelection;
import bisq.offer.mu_sig.use_case.create_offer.price.limits.PriceLimits;
import bisq.offer.mu_sig.use_case.take_offer.TakeOfferValidationException.Reason;
import bisq.offer.mu_sig.use_case.take_offer.payment_method.PaymentMethodSelectionService;
import bisq.offer.options.AccountOption;
import bisq.offer.options.CollateralOption;
import bisq.offer.options.OfferOption;
import bisq.account.payment_method.BitcoinPaymentRail;
import bisq.account.payment_method.PaymentMethodSpec;
import bisq.offer.price.PriceUtil;
import bisq.offer.price.spec.FixPriceSpec;
import bisq.offer.price.spec.FloatPriceSpec;
import bisq.offer.price.spec.MarketPriceSpec;
import bisq.offer.price.spec.PriceSpec;
import bisq.offer.mu_sig.use_case.dependencies.AccountsProvider;
import bisq.offer.mu_sig.use_case.dependencies.DefaultAccountsProvider;
import bisq.offer.mu_sig.use_case.dependencies.DefaultTakeOfferDraftCookieStore;
import bisq.offer.mu_sig.use_case.dependencies.TakeOfferDraftCookieStore;
import bisq.offer.mu_sig.use_case.take_offer.amount.TakeOfferAmountService;
import bisq.offer.mu_sig.use_case.take_offer.direction.TakeOfferDirectionService;
import bisq.offer.mu_sig.use_case.take_offer.market.TakeOfferMarketService;
import bisq.offer.mu_sig.use_case.take_offer.payment_method.TakeOfferPaymentMethodService;
import bisq.offer.mu_sig.use_case.take_offer.price.TakeOfferPriceService;
import bisq.settings.SettingsService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO
@Slf4j
public class TakeOfferUseCase extends DraftOfferUseCase {
    public static final Fiat DEFAULT_TRADE_AMOUNT_IN_USD = Fiat.fromFaceValue(500, "USD");
    @Getter
    private final TakeOfferMarketService marketService;
    @Getter
    private final TakeOfferDirectionService directionService;
    @Getter
    private final TakeOfferPriceService priceService;
    @Getter
    private final TakeOfferAmountService amountService;

    private final TakeOfferDraftCookieStore cookieStore;
    @Getter
    private final TakeOfferPaymentMethodService paymentMethodService;
    private final MarketPriceService marketPriceService;
    private final IdentityService identityService;


    /* --------------------------------------------------------------------- */
    // Construction
    /* --------------------------------------------------------------------- */

    public TakeOfferUseCase(MarketPriceService marketPriceService,
                            IdentityService identityService,
                            SettingsService settingsService,
                            AccountService accountService) {
        this(marketPriceService,
                identityService,
                new DefaultTakeOfferDraftCookieStore(settingsService),
                new DefaultAccountsProvider(accountService));
    }

    TakeOfferUseCase(MarketPriceService marketPriceService,
                     IdentityService identityService,
                     TakeOfferDraftCookieStore cookieStore,
                     AccountsProvider accountsProvider) {
        marketService = new TakeOfferMarketService();
        directionService = new TakeOfferDirectionService();
        priceService = new TakeOfferPriceService();
        amountService = new TakeOfferAmountService();

        this.cookieStore = checkNotNull(cookieStore, "cookieStore must not be null");
        checkNotNull(accountsProvider, "accountsProvider must not be null");
        this.marketPriceService = checkNotNull(marketPriceService, "marketPriceService must not be null");
        this.identityService = checkNotNull(identityService, "identityService must not be null");

        PaymentMethodSelectionService paymentMethodSelectionService = new PaymentMethodSelectionService(accountsProvider);


        paymentMethodService = new TakeOfferPaymentMethodService(paymentMethodSelectionService);
    }

    private void updatePaymentMethods() {
        paymentMethodService.updatePaymentMethods(getMarket());
    }

    private PaymentRail getSelectedPaymentRail() {
        return paymentMethodService.getSelectedPaymentRail();
    }


    /* --------------------------------------------------------------------- */
    // Lifecycle
    /* --------------------------------------------------------------------- */

    @Override
    public void initialize() {
        throw new UnsupportedOperationException("Use initialize(MuSigOffer)");
    }

    public void initialize(MuSigOffer muSigOffer) {
        checkNotNull(muSigOffer, "muSigOffer must not be null");
        validate(muSigOffer);
        // Single market price lookup: the presence requirement and the quote resolution must not
        // diverge, and no service state is touched before all checks have passed.
        PriceQuote marketPriceQuote = PriceUtil.findMarketPriceQuote(marketPriceService, muSigOffer.getMarket())
                .orElseThrow(() -> new TakeOfferValidationException(Reason.NO_MARKET_PRICE,
                        "No market price available for market " + muSigOffer.getMarket().getMarketCodes()));
        PriceQuote priceQuote = resolvePriceQuote(muSigOffer, marketPriceQuote);

        marketService.initialize(muSigOffer);
        directionService.initialize(muSigOffer);
        priceService.setPriceQuote(priceQuote);
    }

    private static PriceQuote resolvePriceQuote(MuSigOffer offer, PriceQuote marketPriceQuote) {
        PriceSpec priceSpec = offer.getPriceSpec();
        if (priceSpec instanceof FixPriceSpec fixPriceSpec) {
            return fixPriceSpec.getPriceQuote();
        } else if (priceSpec instanceof FloatPriceSpec floatPriceSpec) {
            return PriceUtil.fromMarketPriceMarkup(marketPriceQuote, floatPriceSpec.getPercentage());
        } else if (priceSpec instanceof MarketPriceSpec) {
            return marketPriceQuote;
        } else {
            throw new IllegalStateException("Not supported priceSpec. priceSpec=" + priceSpec);
        }
    }


    /* --------------------------------------------------------------------- */
    // Trust-boundary validation (specification.md, "Offer as root input")
    /* --------------------------------------------------------------------- */

    private void validate(MuSigOffer offer) {
        if (!offer.getProtocolTypes().contains(TradeProtocolType.MU_SIG)) {
            throw new TakeOfferValidationException(Reason.PROTOCOL_TYPE_NOT_SUPPORTED,
                    "Offer " + offer.getId() + " does not support the MuSig trade protocol. protocolTypes="
                            + offer.getProtocolTypes());
        }
        if (identityService.findActiveIdentity(offer.getMakerNetworkId()).isPresent()) {
            throw new TakeOfferValidationException(Reason.OWN_OFFER,
                    "Offer " + offer.getId() + " was created by one of our own identities");
        }
        Market market = offer.getMarket();
        PriceSpec priceSpec = offer.getPriceSpec();
        if (priceSpec instanceof FloatPriceSpec floatPriceSpec) {
            double percentage = floatPriceSpec.getPercentage();
            if (percentage < PriceLimits.MIN_PERCENTAGE_FROM_MARKET_PRICE
                    || percentage > PriceLimits.MAX_PERCENTAGE_FROM_MARKET_PRICE) {
                throw new TakeOfferValidationException(Reason.FLOAT_PRICE_OUT_OF_BOUNDS,
                        "Floating price percentage " + percentage + " of offer " + offer.getId()
                                + " is outside the create-offer bounds; such an offer could not have been created legitimately");
            }
        } else if (priceSpec instanceof FixPriceSpec fixPriceSpec) {
            if (!market.equals(fixPriceSpec.getPriceQuote().getMarket())) {
                throw new TakeOfferValidationException(Reason.FIXED_PRICE_MARKET_MISMATCH,
                        "Fixed price quote market " + fixPriceSpec.getPriceQuote().getMarket().getMarketCodes()
                                + " does not match the offer market " + market.getMarketCodes());
            }
        }
        List<PaymentMethodSpec<?>> takerSideSpecs = getTakerSidePaymentMethodSpecs(offer);
        if (takerSideSpecs.isEmpty() || takerSideSpecs.size() > PaymentMethodSelection.MAX_NUM_PAYMENT_METHODS) {
            throw new TakeOfferValidationException(Reason.INVALID_PAYMENT_METHOD_SPECS,
                    "The taker-selectable side of offer " + offer.getId() + " must contain between 1 and "
                            + PaymentMethodSelection.MAX_NUM_PAYMENT_METHODS + " payment method specifications but contains "
                            + takerSideSpecs.size());
        }
        long distinctTakerSideMethods = takerSideSpecs.stream()
                .map(PaymentMethodSpec::getPaymentMethod)
                .distinct()
                .count();
        if (distinctTakerSideMethods != takerSideSpecs.size()) {
            throw new TakeOfferValidationException(Reason.INVALID_PAYMENT_METHOD_SPECS,
                    "The taker-selectable side of offer " + offer.getId()
                            + " contains duplicate payment methods");
        }
        List<PaymentMethodSpec<?>> bitcoinSideSpecs = getBitcoinSidePaymentMethodSpecs(offer);
        if (bitcoinSideSpecs.size() != 1
                || bitcoinSideSpecs.get(0).getPaymentMethod().getPaymentRail() != BitcoinPaymentRail.MAIN_CHAIN) {
            throw new TakeOfferValidationException(Reason.INVALID_PAYMENT_METHOD_SPECS,
                    "The Bitcoin side of offer " + offer.getId()
                            + " must contain exactly one Bitcoin main-chain payment method specification");
        }
        validateOfferOptions(offer, takerSideSpecs);
    }

    private static List<PaymentMethodSpec<?>> getTakerSidePaymentMethodSpecs(MuSigOffer offer) {
        return offer.getMarket().isBaseCurrencyBitcoin()
                ? offer.getQuoteSidePaymentMethodSpecs()
                : offer.getBaseSidePaymentMethodSpecs();
    }

    private static List<PaymentMethodSpec<?>> getBitcoinSidePaymentMethodSpecs(MuSigOffer offer) {
        return offer.getMarket().isBaseCurrencyBitcoin()
                ? offer.getBaseSidePaymentMethodSpecs()
                : offer.getQuoteSidePaymentMethodSpecs();
    }

    private static void validateOfferOptions(MuSigOffer offer, List<PaymentMethodSpec<?>> takerSideSpecs) {
        List<OfferOption> offerOptions = offer.getOfferOptions();
        List<CollateralOption> collateralOptions = offerOptions.stream()
                .filter(CollateralOption.class::isInstance)
                .map(CollateralOption.class::cast)
                .collect(Collectors.toList());
        if (collateralOptions.size() != 1) {
            throw new TakeOfferValidationException(Reason.INVALID_OFFER_OPTIONS,
                    "Offer " + offer.getId() + " must contain exactly one CollateralOption but contains "
                            + collateralOptions.size());
        }
        CollateralOption collateralOption = collateralOptions.get(0);
        double buyerSecurityDeposit = collateralOption.getBuyerSecurityDeposit();
        double sellerSecurityDeposit = collateralOption.getSellerSecurityDeposit();
        if (buyerSecurityDeposit != sellerSecurityDeposit) {
            // Asymmetric deposits are not supported by the current protocol
            // (OfferOptionUtil.findSymmetricSecurityDepositPercent throws downstream).
            throw new TakeOfferValidationException(Reason.INVALID_OFFER_OPTIONS,
                    "Offer " + offer.getId() + " has asymmetric security deposit percentages: buyer="
                            + buyerSecurityDeposit + ", seller=" + sellerSecurityDeposit);
        }
        if (buyerSecurityDeposit < 0 || buyerSecurityDeposit > 1) {
            throw new TakeOfferValidationException(Reason.INVALID_OFFER_OPTIONS,
                    "Offer " + offer.getId() + " has a security deposit percentage outside 0-100%: "
                            + buyerSecurityDeposit);
        }
        // Count on the raw list: OfferOptionUtil.findAccountOptions returns a Set, which would
        // collapse exactly equal duplicates before they can be rejected.
        Map<PaymentMethod<?>, Long> accountOptionCountByPaymentMethod = offerOptions.stream()
                .filter(AccountOption.class::isInstance)
                .map(AccountOption.class::cast)
                .collect(Collectors.groupingBy(AccountOption::getPaymentMethod, Collectors.counting()));
        for (PaymentMethodSpec<?> paymentMethodSpec : takerSideSpecs) {
            long count = accountOptionCountByPaymentMethod.getOrDefault(paymentMethodSpec.getPaymentMethod(), 0L);
            if (count != 1) {
                throw new TakeOfferValidationException(Reason.INVALID_OFFER_OPTIONS,
                        "Offer " + offer.getId() + " must contain exactly one AccountOption for payment method "
                                + paymentMethodSpec.getPaymentMethod().getPaymentRailName() + " but contains " + count);
            }
        }
        Set<PaymentMethod<?>> takerSideMethods = takerSideSpecs.stream()
                .map(spec -> (PaymentMethod<?>) spec.getPaymentMethod())
                .collect(Collectors.toSet());
        if (!takerSideMethods.containsAll(accountOptionCountByPaymentMethod.keySet())) {
            throw new TakeOfferValidationException(Reason.INVALID_OFFER_OPTIONS,
                    "Offer " + offer.getId() + " contains AccountOptions for payment methods it does not offer");
        }
    }

    /* --------------------------------------------------------------------- */
    // Amount input entry points
    /* --------------------------------------------------------------------- */

    public void setFixTradeAmountFromInputAmount(Monetary amount) {
    }

    public void setFixTradeAmountFromSliderValue(double sliderValue) {
    }


    /* --------------------------------------------------------------------- */
    // Amount conversion
    /* --------------------------------------------------------------------- */

    public Monetary toInputAmount(TradeAmount tradeAmount, boolean includeUserSpecificTradeAmountLimit) {
        return null;
    }

    public Monetary toPassiveAmount(TradeAmount tradeAmount, boolean includeUserSpecificTradeAmountLimit) {
        return null;
    }


    /* --------------------------------------------------------------------- */
    // Mutation API
    /* --------------------------------------------------------------------- */

    public void setUseBaseCurrencyForAmountInput(boolean value) {
    }

    public void setFixTradeAmount(TradeAmount tradeAmount) {
    }


    /* --------------------------------------------------------------------- */
    // Derived read model
    /* --------------------------------------------------------------------- */

    public AmountSpec getAmountSpec() {
        return amountService.getAmountSpec();
    }

    @Override
    public Market getMarket() {
        return marketService.getMarket();
    }

}
