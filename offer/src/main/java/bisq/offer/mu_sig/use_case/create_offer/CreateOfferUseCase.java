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

package bisq.offer.mu_sig.use_case.create_offer;

import bisq.account.AccountService;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.monetary.Fiat;
import bisq.common.observable.Observable;
import bisq.offer.mu_sig.use_case.DraftOfferUseCase;
import bisq.offer.mu_sig.use_case.create_offer.amount.CreateOfferAmountUseCase;
import bisq.offer.mu_sig.use_case.create_offer.direction.CreateOfferDirectionUseCase;
import bisq.offer.mu_sig.use_case.create_offer.market.CreateOfferMarketUseCase;
import bisq.offer.mu_sig.use_case.create_offer.payment_method.CreateOfferPaymentMethodUseCase;
import bisq.offer.mu_sig.use_case.create_offer.price.CreateOfferPriceUseCase;
import bisq.offer.mu_sig.use_case.dependencies.AccountsProvider;
import bisq.offer.mu_sig.use_case.dependencies.CreateOfferDraftCookieStore;
import bisq.offer.mu_sig.use_case.dependencies.DefaultAccountsProvider;
import bisq.offer.mu_sig.use_case.dependencies.DefaultCreateOfferDraftCookieStore;
import bisq.settings.SettingsService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Getter
public class CreateOfferUseCase extends DraftOfferUseCase {
    public static final Fiat DEFAULT_TRADE_AMOUNT_IN_USD = Fiat.fromFaceValue(500, "USD");

    private final CreateOfferMarketUseCase marketUseCase;
    private final CreateOfferDirectionUseCase directionService;
    private final CreateOfferPaymentMethodUseCase paymentMethodService;
    private final CreateOfferPriceUseCase priceService;
    private final CreateOfferAmountUseCase amountUseCase;
    private final Observable<Boolean> initialized = new Observable<>(false);


    /* --------------------------------------------------------------------- */
    // Construction
    /* --------------------------------------------------------------------- */

    public CreateOfferUseCase(MarketPriceService marketPriceService,
                              SettingsService settingsService,
                              AccountService accountService) {
        this(checkNotNull(marketPriceService, "marketPriceService must not be null"),
                new DefaultCreateOfferDraftCookieStore(checkNotNull(settingsService, "settingsService must not be null")),
                new DefaultAccountsProvider(checkNotNull(accountService, "accountService must not be null")));
    }

    CreateOfferUseCase(MarketPriceService marketPriceService,
                       CreateOfferDraftCookieStore cookieStore,
                       AccountsProvider accountsProvider) {

        marketUseCase = new CreateOfferMarketUseCase();
        directionService = new CreateOfferDirectionUseCase(cookieStore);
        paymentMethodService = new CreateOfferPaymentMethodUseCase(marketUseCase, accountsProvider);
        priceService = new CreateOfferPriceUseCase(marketPriceService, marketUseCase, cookieStore);
        amountUseCase = new CreateOfferAmountUseCase(marketPriceService,
                marketUseCase,
                directionService,
                paymentMethodService,
                priceService,
                cookieStore);
    }


    /* --------------------------------------------------------------------- */
    // Lifecycle
    /* --------------------------------------------------------------------- */

    @Override
    public void initialize() {
        marketUseCase.initialize();
        directionService.initialize();
        paymentMethodService.initialize();
        priceService.initialize();
        amountUseCase.initialize();

        pin(amountUseCase.initializedObservable().addObserver(initialized -> {
            if (initialized != null) {
                extracted();
            }
        }));
    }

    private void extracted() {
        if (amountUseCase.isInitialized()) {
            setInitialized(true);
        }
    }

    @Override
    public void dispose() {
        super.dispose();

        marketUseCase.dispose();
        directionService.dispose();
        paymentMethodService.dispose();
        priceService.dispose();
        amountUseCase.dispose();
    }


    /* --------------------------------------------------------------------- */
    // initialized
    /* --------------------------------------------------------------------- */

    private void setInitialized(boolean value) {
        initialized.set(value);
    }

    public Observable<Boolean> initializedObservable() {
        return initialized;
    }

    public boolean isInitialized() {
        return initialized.get();
    }



    /* --------------------------------------------------------------------- */
    // Delegate read methods
    /* --------------------------------------------------------------------- */

    @Override
    // not used anymore
    public Market getMarket() {
        return marketUseCase.getMarket();
    }
}
