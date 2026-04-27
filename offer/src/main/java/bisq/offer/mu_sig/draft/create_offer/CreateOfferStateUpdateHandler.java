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

package bisq.offer.mu_sig.draft.create_offer;

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.observable.Pin;
import bisq.offer.mu_sig.draft.create_offer.amount.CreateOfferAmountService;
import bisq.offer.mu_sig.draft.create_offer.direction.CreateOfferDirectionService;
import bisq.offer.mu_sig.draft.create_offer.market.CreateOfferMarketService;
import bisq.offer.mu_sig.draft.create_offer.payment_method.CreateOfferPaymentMethodService;
import bisq.offer.mu_sig.draft.create_offer.price.CreateOfferPriceService;

import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class CreateOfferStateUpdateHandler {
    private final CreateOfferMarketService marketService;
    private final CreateOfferDirectionService directionService;
    private final CreateOfferPaymentMethodService paymentMethodService;
    private final CreateOfferPriceService priceService;
    private final CreateOfferAmountService amountService;
    private final CreateOfferTradeAmountConstraintsService tradeAmountConstraintsService;
    private final CreateOfferDraftStateEngine stateEngine;
    private final Set<Pin> pins = new HashSet<>();
    private boolean paymentRailObserverInitialized;

    CreateOfferStateUpdateHandler(CreateOfferMarketService marketService,
                                  CreateOfferDirectionService directionService,
                                  CreateOfferPaymentMethodService paymentMethodService,
                                  CreateOfferPriceService priceService,
                                  CreateOfferAmountService amountService,
                                  MarketPriceService marketPriceService,
                                  CreateOfferTradeAmountConstraintsService tradeAmountConstraintsService,
                                  CreateOfferDraftStateEngine stateEngine) {
        this.marketService = checkNotNull(marketService, "createOfferMarketService must not be null");
        this.directionService = checkNotNull(directionService, "createOfferDirectionService must not be null");
        this.paymentMethodService = checkNotNull(paymentMethodService, "paymentMethodService must not be null");
        this.priceService = checkNotNull(priceService, "createOfferPriceService must not be null");
        this.amountService = checkNotNull(amountService, "createOfferAmountService must not be null");
        this.tradeAmountConstraintsService = tradeAmountConstraintsService;
        this.stateEngine = checkNotNull(stateEngine, "stateEngine must not be null");
    }

    public void initialize() {
        paymentRailObserverInitialized = false;
        directionService.directionObservable().addObserver(direction -> {
            if (direction != null) {
                // Direction direction,
                //                                   PriceQuote offerPriceQuote,
                //                                   PriceQuote marketPriceQuote,
                tradeAmountConstraintsService.compute(marketService.getMarket(),
                        directionService.getDirection(),
                        priceService.getPriceQuote(),
                        paymentMethodService.getPaymentRailBasedTradeLimitInUsd());
            }
        });
        marketService.marketObservable().addObserver(market -> {
            if (market != null) {

            }
        });

        pins.add(paymentMethodService.paymentRailBasedTradeLimitInUsdObservable().addObserver(paymentRailBasedTradeLimitInUsd -> {
            if (!paymentRailObserverInitialized) {
                paymentRailObserverInitialized = true;
                return;
            }
            if (paymentRailBasedTradeLimitInUsd != null) {
                stateEngine.recalculateTradeAmountConstraintsForSelectedPaymentRail();
            }
        }));
    }

    public void dispose() {
        pins.forEach(Pin::unbind);
        pins.clear();
    }
}
