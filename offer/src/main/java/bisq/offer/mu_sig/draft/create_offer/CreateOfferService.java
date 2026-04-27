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
import bisq.offer.amount.spec.AmountSpec;
import bisq.offer.amount.spec.AmountSpecFactory;
import bisq.offer.mu_sig.draft.AmountMappingService;
import bisq.offer.mu_sig.draft.DraftOfferService;
import bisq.offer.mu_sig.draft.create_offer.amount.CreateOfferAmountService;
import bisq.offer.mu_sig.draft.create_offer.direction.CreateOfferDirectionService;
import bisq.offer.mu_sig.draft.create_offer.market.CreateOfferMarketService;
import bisq.offer.mu_sig.draft.create_offer.payment_method.CreateOfferPaymentMethodService;
import bisq.offer.mu_sig.draft.create_offer.payment_method.PaymentMethodSelectionResult;
import bisq.offer.mu_sig.draft.create_offer.payment_method.PaymentMethodSelectionStatus;
import bisq.offer.mu_sig.draft.create_offer.price.CreateOfferPriceService;
import bisq.offer.mu_sig.draft.dependencies.AccountsProvider;
import bisq.offer.mu_sig.draft.dependencies.CreateOfferDraftCookieStore;
import bisq.offer.mu_sig.draft.dependencies.DefaultAccountsProvider;
import bisq.offer.mu_sig.draft.dependencies.DefaultCreateOfferDraftCookieStore;
import bisq.offer.price.spec.FixPriceSpec;
import bisq.offer.price.spec.FloatPriceSpec;
import bisq.offer.price.spec.MarketPriceSpec;
import bisq.offer.price.spec.PriceSpec;
import bisq.settings.SettingsService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

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

        marketService = new CreateOfferMarketService();
        directionService = new CreateOfferDirectionService(cookieStore);
        paymentMethodService = new CreateOfferPaymentMethodService(accountsProvider);
        priceService = new CreateOfferPriceService(marketPriceService, cookieStore);
        amountService = new CreateOfferAmountService(marketPriceService, cookieStore);

        amountMappingService = new AmountMappingService();

        stateEngine = new CreateOfferDraftStateEngine(marketService,
                directionService,
                paymentMethodService,
                priceService,
                amountService,
                marketPriceService,
                amountMappingService,
                DEFAULT_TRADE_AMOUNT_IN_USD);
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
        Direction direction = directionService.getDirection();

        stateEngine.initialize();

        boolean selectedAccountsChanged = paymentMethodService.updatePaymentMethods(market);
        if (selectedAccountsChanged) {
            stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }
    }


    /* --------------------------------------------------------------------- */
    // Amount input entry points
    /* --------------------------------------------------------------------- */

    public void setFixTradeAmountFromInputAmount(Monetary amount) {
        TradeAmount tradeAmount = stateEngine.toClampedTradeAmount(amount);
        setFixTradeAmount(tradeAmount);
    }

    public void setMinTradeAmountFromInputAmount(Monetary amount) {
        TradeAmount tradeAmount = stateEngine.toClampedTradeAmount(amount);
        setMinTradeAmount(tradeAmount);
    }

    public void setMaxTradeAmountFromInputAmount(Monetary amount) {
        TradeAmount tradeAmount = stateEngine.toClampedTradeAmount(amount);
        setMaxTradeAmount(tradeAmount);
    }

    public void setFixTradeAmountFromSliderValue(double sliderValue) {
        TradeAmount fixTradeAmount = checkNotNull(amountService.getFixTradeAmount(), "fixTradeAmount must not be null");
        TradeAmount tradeAmount = stateEngine.toTradeAmountFromSliderValue(fixTradeAmount, sliderValue);
        setFixTradeAmount(tradeAmount);
    }

    public void setMinTradeAmountFromSliderValue(double sliderValue) {
        TradeAmount minTradeAmount = checkNotNull(amountService.getMinTradeAmount(), "minTradeAmount must not be null");
        TradeAmount tradeAmount = stateEngine.toTradeAmountFromSliderValue(minTradeAmount, sliderValue);
        setMinTradeAmount(tradeAmount);
    }

    public void setMaxTradeAmountFromSliderValue(double sliderValue) {
        TradeAmount maxTradeAmount = checkNotNull(amountService.getMaxTradeAmount(), "maxTradeAmount must not be null");
        TradeAmount tradeAmount = stateEngine.toTradeAmountFromSliderValue(maxTradeAmount, sliderValue);
        setMaxTradeAmount(tradeAmount);
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
    // Mutation API
    /* --------------------------------------------------------------------- */

    // Core market/pricing state
    public void setMarket(Market market) {
        checkNotNull(market, "Market must not be null");
        if (market.equals(getMarket())) {
            return;
        }
        stateEngine.applyMarketChanged(market);
        boolean selectedAccountsChanged = paymentMethodService.updatePaymentMethods(market);
        if (selectedAccountsChanged) {
            stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }
    }

    public void setDirection(Direction direction) {
        checkNotNull(direction, "Direction must not be null");
        if (direction.equals(directionService.getDirection())) {
            return;
        }

        if (stateEngine.applyDirectionChanged(direction)) {
            cookieStore.persistDirection(direction);
        }
    }

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
        Market market = getMarket();
        if (market != null) {
            cookieStore.persistUseFixPrice(market, useFixPrice);
        }
        stateEngine.applyUseFixPriceChanged(useFixPrice);
    }

   /* public void setPricePercentage(double pricePercentage) {
        if (Double.compare(pricePercentage, getPricePercentage()) == 0) {
            return;
        }
        createOfferDraft.setPricePercentage(pricePercentage);
        cookieStore.persistPricePercentage(pricePercentage);
    }

    public void setUseFixPrice(PriceQuote fixPriceQuote) {
        checkNotNull(fixPriceQuote, "fixPriceQuote must not be null");
        if (getPriceQuote().equals(fixPriceQuote)) {
            return;
        }
        createOfferDraft.setUseFixPrice(fixPriceQuote);
        cookieStore.persistPricePercentage(fixPriceQuote);
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

    // Amount state
    public void setFixTradeAmount(TradeAmount tradeAmount) {
        stateEngine.setFixTradeAmount(tradeAmount);
    }

    public void setMinTradeAmount(TradeAmount tradeAmount) {
        stateEngine.setMinTradeAmount(tradeAmount);
    }

    public void setMaxTradeAmount(TradeAmount tradeAmount) {
        stateEngine.setMaxTradeAmount(tradeAmount);
    }

    // Payment account state
    public void putSelectedAccountByPaymentMethod(PaymentMethod<?> paymentMethod, Account<?, ?> account) {
        checkNotNull(paymentMethod, "paymentMethod must not be null");
        checkNotNull(account, "account must not be null");
        if (paymentMethodService.putSelectedAccountByPaymentMethod(paymentMethod, account)) {
            stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }
    }

    public void removeSelectedAccountByPaymentMethod(PaymentMethod<?> paymentMethod) {
        checkNotNull(paymentMethod, "paymentMethod must not be null");
        if (paymentMethodService.removeSelectedAccountByPaymentMethod(paymentMethod)) {
            stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }
    }

    public void putAllSelectedAccountByPaymentMethod(Map<PaymentMethod<?>, Account<?, ?>> selectedAccountByPaymentMethod) {
        checkNotNull(selectedAccountByPaymentMethod, "selectedAccountByPaymentMethod must not be null");
        if (paymentMethodService.putAllSelectedAccountByPaymentMethod(selectedAccountByPaymentMethod)) {
            stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }
    }

    public void clearSelectedAccountByPaymentMethod() {
        if (paymentMethodService.clearSelectedAccountByPaymentMethod()) {
            stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }
    }

    public PaymentMethodSelectionResult onPaymentMethodSelected(PaymentMethod<?> paymentMethod) {
        checkNotNull(paymentMethod, "paymentMethod must not be null");
        PaymentMethodSelectionResult selectionResult = paymentMethodService.onPaymentMethodSelected(paymentMethod);
        if (selectionResult.status() == PaymentMethodSelectionStatus.SINGLE_ACCOUNT_SELECTED) {
            stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
        }
        return selectionResult;
    }


    /* --------------------------------------------------------------------- */
    // Derived read model
    /* --------------------------------------------------------------------- */

    public AmountSpec getAmountSpec() {
        Market market = checkNotNull(getMarket(), "market must not be null");
        boolean isBtcFiatMarket = market.isBtcFiatMarket();
        boolean useRangeAmount = amountService.getUseRangeAmount();
        return AmountSpecFactory.createAmountSpec(isBtcFiatMarket,
                useRangeAmount,
                amountService.getMinTradeAmount(),
                amountService.getMaxTradeAmount(),
                amountService.getFixTradeAmount());
    }

    public PriceSpec getPriceSpec() {
        if (priceService.getUseFixPrice()) {
            return new FixPriceSpec(checkNotNull(priceService.getPriceQuote(), "priceQuote must not be null"));
        }
        double pricePercentage = priceService.getPricePercentage();
        if (pricePercentage == 0d) {
            return new MarketPriceSpec();
        }
        return new FloatPriceSpec(pricePercentage);
    }


    //todo
    @Override
    public Market getMarket() {
        return marketService.getMarket();
    }

    /* --------------------------------------------------------------------- */
    // Internal helpers
    /* --------------------------------------------------------------------- */

    /* --------------------------------------------------------------------- */
    // PaymentMethods
    /* --------------------------------------------------------------------- */


    private TradeAmountRange getClampLimits(boolean includeUserSpecificTradeAmountLimit) {
        return stateEngine.getClampLimits(includeUserSpecificTradeAmountLimit);
    }
}
