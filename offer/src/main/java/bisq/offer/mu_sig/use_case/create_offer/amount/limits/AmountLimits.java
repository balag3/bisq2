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

import bisq.common.application.UseCase;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.common.observable.Observable;
import bisq.common.observable.ReadOnlyObservable;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

public class AmountLimits extends UseCase {
    private final AbsoluteAmountLimits absoluteAmountLimits;
    private final PaymentMethodBasedAmountLimits paymentMethodSpecificAmountLimits;
    private final UserSpecificAmountLimits userSpecificAmountLimits;
    protected final Observable<TradeAmountRange> tradeAmountLimits = new Observable<>();
    protected final Observable<Optional<TradeAmount>> userSpecificAmountLimit = new Observable<>(Optional.empty());

    public AmountLimits(AbsoluteAmountLimits absoluteAmountLimits,
                        PaymentMethodBasedAmountLimits paymentMethodSpecificAmountLimits,
                        UserSpecificAmountLimits userSpecificAmountLimits) {
        this.absoluteAmountLimits = checkNotNull(absoluteAmountLimits, "absoluteAmountLimits must not be null");
        this.paymentMethodSpecificAmountLimits = checkNotNull(paymentMethodSpecificAmountLimits, "paymentMethodSpecificAmountLimits must not be null");
        this.userSpecificAmountLimits = checkNotNull(userSpecificAmountLimits, "userSpecificAmountLimits must not be null");
    }

    public void initialize() {
        pin(absoluteAmountLimits.tradeAmountLimits.addObserver(tradeAmountLimits -> {
            update(tradeAmountLimits,
                    paymentMethodSpecificAmountLimits.getTradeAmountLimit(),
                    userSpecificAmountLimits.getTradeAmountLimit());
        }));
        pin(paymentMethodSpecificAmountLimits.tradeAmountLimit.addObserver(tradeAmountLimit -> {
            update(absoluteAmountLimits.getAmountLimits(),
                    tradeAmountLimit,
                    userSpecificAmountLimits.getTradeAmountLimit());
        }));
        pin(userSpecificAmountLimits.tradeAmountLimit.addObserver(tradeAmountLimit -> {
            update(absoluteAmountLimits.getAmountLimits(),
                    paymentMethodSpecificAmountLimits.getTradeAmountLimit(),
                    tradeAmountLimit);
        }));
    }

    private void update(TradeAmountRange absoluteTradeAmountLimits,
                        TradeAmount paymentMethodSpecificAmountLimit,
                        Optional<TradeAmount> userSpecificAmountLimit) {
        if (absoluteTradeAmountLimits != null &&
                paymentMethodSpecificAmountLimit != null &&
                userSpecificAmountLimit != null) {

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
