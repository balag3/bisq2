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

package bisq.offer.mu_sig.draft;

import bisq.account.AccountService;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.offer.mu_sig.MuSigOffer;
import bisq.offer.mu_sig.draft.dependencies.AccountsProvider;
import bisq.offer.mu_sig.draft.dependencies.CreateOfferDraftCookieStore;
import bisq.offer.mu_sig.draft.dependencies.CreateOfferDraftMarketData;
import bisq.offer.mu_sig.draft.dependencies.DefaultAccountsProvider;
import bisq.offer.mu_sig.draft.dependencies.DefaultCreateOfferDraftCookieStore;
import bisq.offer.mu_sig.draft.dependencies.DefaultCreateOfferDraftMarketData;
import bisq.settings.SettingsService;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakeOfferDraftWorkflowOLD extends OfferDraftWorkflow<TakeOfferDraft> {
    private final CreateOfferDraftCookieStore cookieStore;
    private final AmountMappingService amountMappingService;
    private final PaymentMethodSelectionService paymentMethodSelectionService;
    // private final CreateOfferDraftStateEngine stateEngine;
    @Delegate
    protected TakeOfferDraft takeOfferDraft;

    public TakeOfferDraftWorkflowOLD(MarketPriceService marketPriceService,
                                     SettingsService settingsService,
                                     AccountService accountService) {
        this(new DefaultCreateOfferDraftMarketData(marketPriceService),
                new DefaultCreateOfferDraftCookieStore(settingsService),
                new DefaultAccountsProvider(accountService));
    }

    TakeOfferDraftWorkflowOLD(CreateOfferDraftMarketData marketData,
                              CreateOfferDraftCookieStore cookieStore,
                              AccountsProvider accountsProvider) {
        super(new TakeOfferDraft());

        this.cookieStore = checkNotNull(cookieStore, "cookieStore must not be null");
        checkNotNull(accountsProvider, "accountsProvider must not be null");

        amountMappingService = new AmountMappingService();
        TradeAmountConstraintsService tradeAmountConstraintsService = new TradeAmountConstraintsService(checkNotNull(marketData,
                "marketData must not be null"));
        paymentMethodSelectionService = new PaymentMethodSelectionService(accountsProvider);

        takeOfferDraft = offerDraft;
       /* stateEngine = new CreateOfferDraftStateEngine(createOfferDraft,
                marketData,
                tradeAmountConstraintsService,
                amountMappingService,
                this::getSelectedPaymentRail,
                this::updatePaymentMethods,
                DEFAULT_TRADE_AMOUNT_IN_USD);*/
    }

  /*  public TakeOfferDraftWorkflow() {
        super(new TakeOfferDraft());
        takeOfferDraft = offerDraft;
    }*/

    public void initialize(MuSigOffer offer) {
        this.offerDraft.setOffer(offer);
    }

    public void setFixTradeAmountFromSliderValue(double sliderValue) {
       // TradeAmount fixTradeAmount = checkNotNull(getFixTradeAmount(), "fixTradeAmount must not be null");
      //  TradeAmount tradeAmount = stateEngine.toTradeAmountFromSliderValue(fixTradeAmount, sliderValue);
       // setFixTradeAmount(tradeAmount);
    }
}
