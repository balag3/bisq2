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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TakeOfferFeeServiceTest {
    @Test
    public void feeIsDerivedFromTheMaxTradeAmountInSats() {
        TakeOfferFeeService service = new TakeOfferFeeService();
        // 0.1% of 5_000_000 sats = 5_000 sats.
        service.applyMaxTradeAmount(5_000_000L);
        assertEquals(Coin.asBtcFromValue(5_000L), service.getTradeFee());
    }

    @Test
    public void feeHasAFloorForTinyAmounts() {
        TakeOfferFeeService service = new TakeOfferFeeService();
        // 0.1% of 100_000 sats = 100 sats, below the 1_000 sat floor.
        service.applyMaxTradeAmount(100_000L);
        assertEquals(Coin.asBtcFromValue(1_000L), service.getTradeFee());
    }

    @Test
    public void resetClearsTheFee() {
        TakeOfferFeeService service = new TakeOfferFeeService();
        service.applyMaxTradeAmount(5_000_000L);
        service.reset();
        assertNull(service.getTradeFee());
    }
}
