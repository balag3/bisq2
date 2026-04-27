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

package bisq.offer.mu_sig.draft.create_offer.price;

import bisq.common.monetary.PriceQuote;
import lombok.experimental.Delegate;

public class CreateOfferPriceService {
    @Delegate
    private final CreateOfferPriceModel createOfferPriceModel;

    public CreateOfferPriceService(CreateOfferPriceModel createOfferPriceModel) {
        this.createOfferPriceModel = createOfferPriceModel;
    }

    public void setPriceQuote(PriceQuote priceQuote) {
        createOfferPriceModel.setPriceQuote(priceQuote);
    }

    public void setUseFixPrice(boolean useFixPrice) {
        createOfferPriceModel.setUseFixPrice(useFixPrice);
    }

    public void setPricePercentage(double pricePercentage) {
        createOfferPriceModel.setPricePercentage(pricePercentage);
    }
}
