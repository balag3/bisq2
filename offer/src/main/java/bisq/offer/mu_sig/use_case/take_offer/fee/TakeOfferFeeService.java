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

package bisq.offer.mu_sig.use_case.take_offer.fee;

import bisq.common.monetary.Coin;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;

/**
 * The single source of the MuSig trade fee for the take-offer flow. The fee schedule is not
 * decided yet, so the amount is a mock derived from the maximum trade amount; a later change
 * replaces the mock with the real schedule and wires the same value into the trade protocol
 * (today the protocol still uses a hard-coded placeholder - see the take-offer specification,
 * "Review").
 */
public class TakeOfferFeeService {
    // Placeholder rate until the fee schedule is decided: 0.1% of the maximum BTC-side trade
    // amount, with a floor so tiny trades still carry a visible fee.
    private static final double MOCK_FEE_RATE = 0.001;
    private static final long MOCK_MIN_FEE_IN_SATS = 1_000;

    @Getter(AccessLevel.PACKAGE)
    @Delegate
    private final TakeOfferFeeModel model;

    public TakeOfferFeeService() {
        this.model = new TakeOfferFeeModel();
    }

    // maxTradeAmountInSats is the maximum BTC-side trade amount, so range and fixed offers on
    // either denomination feed a consistent unit.
    public void applyMaxTradeAmount(long maxTradeAmountInSats) {
        long feeInSats = Math.max(MOCK_MIN_FEE_IN_SATS, Math.round(maxTradeAmountInSats * MOCK_FEE_RATE));
        model.setTradeFee(Coin.asBtcFromValue(feeInSats));
    }

    public void reset() {
        model.reset();
    }
}
