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

package bisq.offer.mu_sig.use_case.create_offer.price;

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.PriceQuote;
import bisq.offer.mu_sig.use_case.dependencies.CreateOfferDraftCookieStore;
import bisq.offer.price.spec.FixPriceSpec;
import bisq.offer.price.spec.FloatPriceSpec;
import bisq.offer.price.spec.MarketPriceSpec;
import bisq.offer.price.spec.PriceSpec;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

public class CreateOfferPriceUseCase {
    @Getter(AccessLevel.PACKAGE)
    @Delegate
    private final CreateOfferPriceModel model;
    private final MarketPriceService marketPriceService;
    private final CreateOfferDraftCookieStore cookieStore;

    public CreateOfferPriceUseCase(MarketPriceService marketPriceService, CreateOfferDraftCookieStore cookieStore) {
        this.marketPriceService = marketPriceService;
        this.cookieStore = cookieStore;
        this.model = new CreateOfferPriceModel();
    }

    public void initialize(Market market) {
        boolean useFixPrice = cookieStore.getUseFixPrice(market);
        setUseFixPrice(useFixPrice);

        double pricePercentage = cookieStore.getPricePercentage(market);
        setPricePercentage(pricePercentage);

        PriceQuote priceQuote = fromMarketChange(market);
        setPriceQuote(priceQuote);
    }

    public PriceQuote fromMarketChange(Market market) {
        boolean useFixPrice = getUseFixPrice();
        PriceQuote marketPriceQuote = marketPriceService.getMarketPriceQuoteOrThrow(market);
        if (useFixPrice) {
            Optional<PriceQuote> fixPrice = cookieStore.getFixPrice(market);
            //todo clamp price to limits
            PriceQuote priceQuote = fixPrice
                    .filter(e -> e.getValue() > 0)
                    .orElse(marketPriceQuote);
            cookieStore.persistFixPrice(market, priceQuote);
            return priceQuote;
        } else {
            double pricePercentage = cookieStore.getPricePercentage(market);
            // cookieStore.persistPricePercentage(market, pricePercentage);
            return marketPriceQuote;
        }
    }

    public void setPriceQuote(PriceQuote priceQuote) {
        model.setPriceQuote(priceQuote);
    }

    public void setUseFixPrice(boolean useFixPrice) {
        model.setUseFixPrice(useFixPrice);
    }

    public void applyUseFixPriceChanged(boolean useFixPrice) {
        setUseFixPrice(useFixPrice);
        PriceQuote priceQuote = getPriceQuote();
        if (priceQuote != null && !useFixPrice) {
            setPricePercentage(0L);
        }
    }

    public void setPricePercentage(double pricePercentage) {
        model.setPricePercentage(pricePercentage);
    }

    public PriceSpec createAndGetPriceSpec() {
        if (getUseFixPrice()) {
            return new FixPriceSpec(checkNotNull(getPriceQuote(), "priceQuote must not be null"));
        }
        double pricePercentage = getPricePercentage();
        if (pricePercentage == 0d) {
            return new MarketPriceSpec();
        }
        return new FloatPriceSpec(pricePercentage);
    }
}
