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
import bisq.common.observable.Observable;
import bisq.common.observable.ReadOnlyObservable;

public class CreateOfferMarketModel implements CreateOfferMarketReadOnlyModel {
    protected final Observable<Market> market = new Observable<>();
    protected final Observable<Boolean> marketChanged = new Observable<>(false);

    public CreateOfferMarketModel() {
    }

    /* --------------------------------------------------------------------- */
    // Market
    /* --------------------------------------------------------------------- */

    void setMarket(Market market) {
        this.market.set(market);
    }

    @Override
    public ReadOnlyObservable<Market> marketObservable() {
        return market;
    }

    @Override
    public Market getMarket() {
        return market.get();
    }

    void setMarketChanged(Boolean marketChanged) {
        this.marketChanged.set(marketChanged);
    }

    @Override
    public ReadOnlyObservable<Boolean> marketChangedObservable() {
        return marketChanged;
    }
}
