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

package bisq.offer.mu_sig.draft.dependencies;

import bisq.common.market.Market;
import bisq.common.monetary.PriceQuote;
import bisq.offer.Direction;

import java.util.Optional;

public interface TakeOfferDraftCookieStore {
    void persistDirection(Direction direction);

    Direction getDirection();

    boolean getUseBaseCurrencyForAmountInput(Market market);

    void persistUseBaseCurrencyForAmountInput(Market market, boolean useBaseCurrencyForAmountInput);

    boolean getUseRangeAmount();

    void persistUseRangeAmount(boolean useRangeAmount);

    boolean getUseFixPrice(Market market);

    void persistUseFixPrice(Market market, boolean useFixPrice);

    double getPricePercentage(Market market);

    void persistPricePercentage(Market market,double pricePercentage);

    Optional<PriceQuote> getFixPrice(Market market);

    void persistFixPrice(Market market, PriceQuote fixPrice);
}
