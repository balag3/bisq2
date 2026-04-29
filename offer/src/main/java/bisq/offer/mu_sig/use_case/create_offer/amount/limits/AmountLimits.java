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

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.application.UseCase;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.common.observable.Observable;
import bisq.common.observable.ReadOnlyObservable;
import bisq.offer.mu_sig.use_case.create_offer.direction.CreateOfferDirectionUseCase;
import bisq.offer.mu_sig.use_case.create_offer.market.CreateOfferMarketUseCase;
import bisq.offer.mu_sig.use_case.create_offer.payment_method.CreateOfferPaymentMethodUseCase;
import bisq.offer.mu_sig.use_case.create_offer.price.CreateOfferPriceUseCase;
import lombok.Getter;

import java.util.Optional;

public class AmountLimits extends UseCase {
    @Getter
    private final AbsoluteAmountLimits absoluteAmountLimits;
    @Getter
    private final PaymentMethodBasedAmountLimits paymentMethodSpecificAmountLimits;
    @Getter
    private final UserSpecificAmountLimits userSpecificAmountLimits;

    private final Observable<Optional<TradeAmount>> userSpecificAmountLimit = new Observable<>(Optional.empty());
    private final Observable<TradeAmountRange> tradeAmountLimits = new Observable<>();

    public AmountLimits(MarketPriceService marketPriceService,
                        CreateOfferMarketUseCase marketService,
                        CreateOfferDirectionUseCase directionService,
                        CreateOfferPaymentMethodUseCase paymentMethodService,
                        CreateOfferPriceUseCase priceService) {
        absoluteAmountLimits = new AbsoluteAmountLimits(marketPriceService, marketService, priceService);
        paymentMethodSpecificAmountLimits = new PaymentMethodBasedAmountLimits(marketPriceService, marketService, paymentMethodService, priceService);
        userSpecificAmountLimits = new UserSpecificAmountLimits(marketPriceService, marketService, directionService, priceService);
    }

    @Override
    public void initialize() {
        absoluteAmountLimits.initialize();
        paymentMethodSpecificAmountLimits.initialize();
        userSpecificAmountLimits.initialize();

        pin(absoluteAmountLimits.tradeAmountLimitsObservable().addObserver(tradeAmountLimits -> {
            update(tradeAmountLimits,
                    paymentMethodSpecificAmountLimits.getTradeAmountLimit(),
                    userSpecificAmountLimits.getTradeAmountLimit());
        }));
        pin(paymentMethodSpecificAmountLimits.tradeAmountLimitObservable().addObserver(tradeAmountLimit -> {
            update(absoluteAmountLimits.getTradeAmountLimits(),
                    tradeAmountLimit,
                    userSpecificAmountLimits.getTradeAmountLimit());
        }));
        pin(userSpecificAmountLimits.tradeAmountLimitObservable().addObserver(tradeAmountLimit -> {
            update(absoluteAmountLimits.getTradeAmountLimits(),
                    paymentMethodSpecificAmountLimits.getTradeAmountLimit(),
                    tradeAmountLimit);
        }));
    }

    @Override
    public void dispose() {
        super.dispose();
        absoluteAmountLimits.dispose();
        paymentMethodSpecificAmountLimits.dispose();
        userSpecificAmountLimits.dispose();
    }

    private void update(TradeAmountRange absoluteTradeAmountLimits,
                        TradeAmount paymentMethodSpecificAmountLimit,
                        Optional<TradeAmount> userSpecificAmountLimit) {
        if (dependenciesValid(absoluteTradeAmountLimits, paymentMethodSpecificAmountLimit, userSpecificAmountLimit)) {
            TradeAmount min = absoluteTradeAmountLimits.getMin();
            TradeAmount max = paymentMethodSpecificAmountLimit.clamp(absoluteTradeAmountLimits);
            TradeAmountRange paymentMethodSpecificAmountLimits = new TradeAmountRange(min, max);
            if (userSpecificAmountLimit.isPresent()) {
                TradeAmount clamped = userSpecificAmountLimit.get().clamp(paymentMethodSpecificAmountLimits);
                this.userSpecificAmountLimit.set(Optional.of(clamped));
                max = clamped;
            } else {
                this.userSpecificAmountLimit.set(Optional.empty());
            }
            tradeAmountLimits.set(new TradeAmountRange(min, max));
        }
    }

    private static boolean dependenciesValid(TradeAmountRange absoluteTradeAmountLimits,
                                             TradeAmount paymentMethodSpecificAmountLimit,
                                             Optional<TradeAmount> userSpecificAmountLimit) {
        if (absoluteTradeAmountLimits == null ||
                paymentMethodSpecificAmountLimit == null ||
                userSpecificAmountLimit == null) {
            return false;
        }

        TradeAmount absoluteMin = absoluteTradeAmountLimits.getMin();
        TradeAmount absoluteMax = absoluteTradeAmountLimits.getMax();
        if (absoluteMin == null || absoluteMax == null) {
            return false;
        }

        if (!matchingMarket(absoluteMin, paymentMethodSpecificAmountLimit) ||
                !matchingMarket(absoluteMax, paymentMethodSpecificAmountLimit)) {
            return false;
        }

        return userSpecificAmountLimit.isEmpty() ||
                (matchingMarket(absoluteMin, userSpecificAmountLimit.get()) &&
                        matchingMarket(absoluteMax, userSpecificAmountLimit.get()));
    }

    private static boolean matchingMarket(TradeAmount left, TradeAmount right) {
        return left.getBaseSideAmount().getCode().equals(right.getBaseSideAmount().getCode()) &&
                left.getQuoteSideAmount().getCode().equals(right.getQuoteSideAmount().getCode());
    }


    /* --------------------------------------------------------------------- */
    // Getters
    /* --------------------------------------------------------------------- */

    public ReadOnlyObservable<TradeAmountRange> amountLimitsObservable() {
        return tradeAmountLimits;
    }

    public TradeAmountRange getAmountLimits() {
        return tradeAmountLimits.get();
    }

    public ReadOnlyObservable<Optional<TradeAmount>> userSpecificAmountLimitObservable() {
        return userSpecificAmountLimit;
    }

    public Optional<TradeAmount> getUserSpecificAmountLimit() {
        return userSpecificAmountLimit.get();
    }
}
