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

package bisq.offer.mu_sig.draft.create_offer.market;

import bisq.common.market.Market;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;

public class CreateOfferMarketService {
    @Getter(AccessLevel.PACKAGE)
    @Delegate
    private final CreateOfferMarketModel model;

    public CreateOfferMarketService() {
        this.model = new CreateOfferMarketModel();
    }

    public void initialize(Market market) {
        model.setMarket(market);
    }

    public void setMarket(Market market) {
        if (market != model.getMarket()) {
            model.setMarket(market);
            model.setMarketChanged(true);
        }
    }

    public void resetMarketChanged() {
        model.setMarketChanged(false);
    }
}
