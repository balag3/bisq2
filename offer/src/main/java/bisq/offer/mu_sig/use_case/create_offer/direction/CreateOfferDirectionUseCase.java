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

package bisq.offer.mu_sig.use_case.create_offer.direction;

import bisq.common.application.UseCase;
import bisq.common.observable.Pin;
import bisq.offer.Direction;
import bisq.offer.mu_sig.use_case.dependencies.CreateOfferDraftCookieStore;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

public class CreateOfferDirectionUseCase extends UseCase {
    @Getter(AccessLevel.PACKAGE)
    @Delegate
    private final CreateOfferDirectionModel model;
    private final CreateOfferDraftCookieStore cookieStore;
    private final Set<Consumer<Direction>> listeners = new CopyOnWriteArraySet<>();

    public CreateOfferDirectionUseCase(CreateOfferDraftCookieStore cookieStore) {
        this.cookieStore = cookieStore;
        this.model = new CreateOfferDirectionModel();
    }

    public void initialize() {
        Direction displayDirection = cookieStore.getDisplayDirection();
        applyDisplayDirection(displayDirection, false);
    }


    /* --------------------------------------------------------------------- */
    // User input
    /* --------------------------------------------------------------------- */

    public void onSelectDisplayDirection(Direction displayDirection) {
        applyDisplayDirection(displayDirection, true);
    }

    private void applyDisplayDirection(Direction displayDirection, boolean notifyListeners) {
        if (displayDirection != model.getDisplayDirection()) {
            model.setDisplayDirection(displayDirection);
            cookieStore.persistDisplayDirection(displayDirection);
            if (notifyListeners) {
                listeners.forEach(listener -> listener.accept(displayDirection));
            }
        }
    }

    public Pin addDisplayDirectionListener(Consumer<Direction> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
