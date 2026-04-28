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

package bisq.offer.mu_sig.use_case.create_offer.market;

import bisq.common.market.Market;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

public class CreateOfferMarketUseCase {
    @Getter(AccessLevel.PACKAGE)
    @Delegate
    private final CreateOfferMarketModel model;
    private final Set<Consumer<Market>> listeners = new CopyOnWriteArraySet<>();

    public CreateOfferMarketUseCase() {
        this.model = new CreateOfferMarketModel();
    }

    public void initialize(Market market) {
        model.setMarket(market);
        applyMarket(market, false);
    }


    /* --------------------------------------------------------------------- */
    // User input
    /* --------------------------------------------------------------------- */

    public void onSelectMarket(Market market) {
        applyMarket(market, true);
    }


    private void applyMarket(Market market, boolean notifyListeners) {
        if (market != model.getMarket()) {
            model.setMarket(market);
            if (notifyListeners) {
                listeners.forEach(listener -> listener.accept(market));
            }
        }
    }

    public void addListener(Consumer<Market> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<Market> listener) {
        listeners.remove(listener);
    }
}
