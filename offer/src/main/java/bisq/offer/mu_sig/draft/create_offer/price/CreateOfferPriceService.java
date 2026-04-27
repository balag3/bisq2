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

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.PriceQuote;
import bisq.offer.mu_sig.draft.dependencies.CreateOfferDraftCookieStore;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.util.Optional;

public class CreateOfferPriceService {
    @Getter(AccessLevel.PACKAGE)
    @Delegate
    private final CreateOfferPriceModel model;
    private final MarketPriceService marketPriceService;
    private final CreateOfferDraftCookieStore cookieStore;

    public CreateOfferPriceService(MarketPriceService marketPriceService, CreateOfferDraftCookieStore cookieStore) {
        this.marketPriceService = marketPriceService;
        this.cookieStore = cookieStore;
        this.model = new CreateOfferPriceModel();
    }

    public void initialize(Market market) {
        boolean useFixPrice = cookieStore.getUseFixPrice(market);
        setUseFixPrice(useFixPrice);

        double pricePercentage = cookieStore.getPricePercentage(market);
        setPricePercentage(pricePercentage);

        Optional<PriceQuote> fixPrice = cookieStore.getFixPrice(market);
        PriceQuote marketPriceQuote = marketPriceService.getMarketPriceQuoteOrThrow(market);
        //todo clamp price to limits
        PriceQuote priceQuote = fixPrice
                .filter(e -> e.getValue() > 0)
                .orElse(marketPriceQuote);
        setPriceQuote(priceQuote);
    }

    public void setPriceQuote(PriceQuote priceQuote) {
        model.setPriceQuote(priceQuote);
    }

    public void setUseFixPrice(boolean useFixPrice) {
        model.setUseFixPrice(useFixPrice);
    }

    public void setPricePercentage(double pricePercentage) {
        model.setPricePercentage(pricePercentage);
    }

}
