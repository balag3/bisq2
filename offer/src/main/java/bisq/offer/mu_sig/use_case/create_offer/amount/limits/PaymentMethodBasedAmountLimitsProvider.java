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

package bisq.offer.mu_sig.use_case.create_offer.amount.limits;

import bisq.account.accounts.Account;
import bisq.account.payment_method.PaymentMethod;
import bisq.account.payment_method.PaymentRail;
import bisq.account.payment_method.fiat.FiatPaymentRail;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.application.UseCase;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.observable.Observable;
import bisq.common.observable.ReadOnlyObservable;
import bisq.offer.mu_sig.use_case.create_offer.market.CreateOfferMarketUseCase;
import bisq.offer.mu_sig.use_case.create_offer.payment_method.CreateOfferPaymentMethodUseCase;
import bisq.offer.mu_sig.use_case.create_offer.price.CreateOfferPriceUseCase;
import com.google.common.collect.ImmutableMap;

import java.util.Comparator;
import java.util.Map;

import static bisq.offer.mu_sig.use_case.create_offer.amount.limits.TradeAmountLimitUtils.toTradeAmountLimit;
import static com.google.common.base.Preconditions.checkNotNull;

public class PaymentMethodBasedAmountLimitsProvider extends UseCase {
    private final Observable<Fiat> amountLimitInUsd = new Observable<>(AbsoluteAmountLimitsProvider.MAX_TRADE_AMOUNT_IN_USD); //todo remove
    private final Observable<TradeAmount> tradeAmountLimit = new Observable<>();

    private final MarketPriceService marketPriceService;
    private final CreateOfferMarketUseCase marketService;
    private final CreateOfferPaymentMethodUseCase paymentMethodUseCase;
    private final CreateOfferPriceUseCase priceService;

    PaymentMethodBasedAmountLimitsProvider(MarketPriceService marketPriceService,
                                           CreateOfferMarketUseCase marketService,
                                           CreateOfferPaymentMethodUseCase paymentMethodUseCase,
                                           CreateOfferPriceUseCase priceService) {
        this.marketPriceService = checkNotNull(marketPriceService, "marketPriceService must not be null");
        this.marketService = marketService;
        this.paymentMethodUseCase = paymentMethodUseCase;
        this.priceService = priceService;
    }

    @Override
    public void initialize() {
        pin(marketService.addMarketListener(market ->
                update(market,
                        priceService.getPriceQuote(),
                        paymentMethodUseCase.getAccountByPaymentMethod())));
        pin(paymentMethodUseCase.accountByPaymentMethodObservable().addObserver(() ->
                update(marketService.getMarket(),
                        priceService.getPriceQuote(),
                        paymentMethodUseCase.getAccountByPaymentMethod())));
        pin(priceService.addPriceQuoteListener(priceQuote ->
                update(marketService.getMarket(),
                        priceQuote,
                        paymentMethodUseCase.getAccountByPaymentMethod())));
    }


    /* --------------------------------------------------------------------- */
    // Update
    /* --------------------------------------------------------------------- */

    private void update(Market market,
                        PriceQuote priceQuote,
                        ImmutableMap<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethod) {
        if (dependenciesValid(market, priceQuote, accountByPaymentMethod)) {
            Fiat limitInUsd = evaluateLimitInUsd(accountByPaymentMethod);
            amountLimitInUsd.set(limitInUsd);

            TradeAmount limit = toTradeAmountLimit(marketPriceService, market, priceQuote, limitInUsd);
            tradeAmountLimit.set(limit);
        }
    }


    /* --------------------------------------------------------------------- */
    // Getters
    /* --------------------------------------------------------------------- */

    ReadOnlyObservable<TradeAmount> tradeAmountLimitObservable() {
        return tradeAmountLimit;
    }

    TradeAmount getTradeAmountLimit() {
        return tradeAmountLimit.get();
    }

    //todo remove
    ReadOnlyObservable<Fiat> amountLimitInUsdObservable() {
        return amountLimitInUsd;
    }

    //todo remove
    Fiat getAmountLimitInUsd() {
        return amountLimitInUsd.get();
    }

    private static boolean dependenciesValid(Market market,
                                             PriceQuote priceQuote,
                                             ImmutableMap<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethod) {
        return market != null &&
                accountByPaymentMethod != null &&
                priceQuote != null &&
                market.equals(priceQuote.getMarket());
    }

    /* --------------------------------------------------------------------- */
    // Static
    /* --------------------------------------------------------------------- */

    static Fiat evaluateLimitInUsd(Map<PaymentMethod<?>, Account<?, ?>> accountByPaymentMethod) {
        return accountByPaymentMethod.values().stream()
                .map(Account::getPaymentMethod)
                .map(PaymentMethod::getPaymentRail)
                .map(PaymentRail.class::cast)
                .min(Comparator.comparing(PaymentMethodBasedAmountLimitsProvider::evaluateLimitInUsd))
                .map(PaymentMethodBasedAmountLimitsProvider::evaluateLimitInUsd)
                .orElse(AbsoluteAmountLimitsProvider.MAX_TRADE_AMOUNT_IN_USD);
    }

    public static Fiat evaluateLimitInUsd(PaymentRail paymentRail) {
        checkNotNull(paymentRail, "paymentRail must not be null");
        Fiat maxTradeLimitByProtocol = AbsoluteAmountLimitsProvider.MAX_TRADE_AMOUNT_IN_USD;
        if (paymentRail instanceof FiatPaymentRail fiatPaymentRail) {
            return switch (fiatPaymentRail.getChargebackRisk()) {
                case VERY_LOW -> maxTradeLimitByProtocol;
                case LOW -> maxTradeLimitByProtocol.multiply(0.8);
                case MEDIUM -> maxTradeLimitByProtocol.multiply(0.65);
                case MODERATE -> maxTradeLimitByProtocol.multiply(0.5);
            };
        }
        return maxTradeLimitByProtocol;
    }
}
