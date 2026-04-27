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

package bisq.desktop.main.content.mu_sig.offer.draft.create_offer.amount_and_price.amount.container.range;

import bisq.common.monetary.Monetary;
import bisq.common.monetary.TradeAmount;
import bisq.common.observable.Pin;
import bisq.desktop.common.threading.UIThread;
import bisq.desktop.common.view.Controller;
import bisq.desktop.main.content.mu_sig.offer.draft.amount_components.passive.MuSigPassiveAmountController;
import bisq.desktop.main.content.mu_sig.offer.draft.amount_components.text_input.MuSigAmountTextInputController;
import bisq.desktop.main.content.mu_sig.offer.draft.create_offer.amount_and_price.amount.container.range.slider.MuSigRangeAmountSliderController;
import bisq.offer.mu_sig.draft.create_offer.CreateOfferDraftWorkflow;
import bisq.offer.mu_sig.draft.create_offer.amount.CreateOfferAmountService;
import javafx.beans.property.ReadOnlyBooleanProperty;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.HashSet;
import java.util.Set;

@Slf4j
public class MuSigRangeAmountController implements Controller {
    private final MuSigRangeAmountModel model;
    @Getter
    private final MuSigRangeAmountView view;
    private final MuSigAmountTextInputController minAmountInputController;
    private final MuSigPassiveAmountController minPassiveAmountController;
    private final MuSigAmountTextInputController maxAmountInputController;
    private final MuSigPassiveAmountController maxPassiveAmountController;
    private final CreateOfferDraftWorkflow createOfferDraftWorkflow;
    private final CreateOfferAmountService createOfferAmountService;
    private final Set<Subscription> subscriptions = new HashSet<>();
    private final Set<Pin> pins = new HashSet<>();

    public MuSigRangeAmountController(CreateOfferDraftWorkflow createOfferDraftWorkflow) {
        this.createOfferDraftWorkflow = createOfferDraftWorkflow;
        createOfferAmountService = createOfferDraftWorkflow.getAmountService();
        model = new MuSigRangeAmountModel();

        minAmountInputController = new MuSigAmountTextInputController(false, true);
        maxAmountInputController = new MuSigAmountTextInputController(false, false);
        minPassiveAmountController = new MuSigPassiveAmountController(true);
        maxPassiveAmountController = new MuSigPassiveAmountController(false);
        MuSigRangeAmountSliderController amountSliderController = new MuSigRangeAmountSliderController(createOfferDraftWorkflow);

        view = new MuSigRangeAmountView(model, this,
                minAmountInputController.getView().getRoot(),
                maxAmountInputController.getView().getRoot(),
                minPassiveAmountController.getView().getRoot(),
                maxPassiveAmountController.getView().getRoot(),
                amountSliderController.getView().getRoot()
        );
    }


    /* --------------------------------------------------------------------- */
    // Lifecycle
    /* --------------------------------------------------------------------- */

    @Override
    public void onActivate() {
        // Domain specific
        pins.add(createOfferAmountService.useBaseCurrencyForAmountInputObservable().addObserver(useBaseCurrencyForAmountInput -> {
            UIThread.run(this::applyAllAmounts);
        }));

        pins.add(createOfferAmountService.minTradeAmountObservable().addObserver(tradeAmount -> {
            UIThread.run(this::applyAllAmounts);
        }));
        pins.add(createOfferAmountService.maxTradeAmountObservable().addObserver(tradeAmount -> {
            UIThread.run(this::applyAllAmounts);
        }));

        subscriptions.add(EasyBind.subscribe(minAmountInputController.amountProperty(),
                amount -> {
                    createOfferDraftWorkflow.setMinTradeAmountFromInputAmount(amount);
                    applyMinInputAmount();

                }));


        subscriptions.add(EasyBind.subscribe(maxAmountInputController.amountProperty(),
                amount -> {
                    createOfferDraftWorkflow.setMaxTradeAmountFromInputAmount(amount);
                    applyMaxInputAmount();
                }));

        // UI specific
        subscriptions.add(EasyBind.subscribe(minAmountInputController.inputTextProperty(),
                inputText -> {
                    model.getMinAmountInputText().set(inputText);
                    applySumNumChars();
                }));
        subscriptions.add(EasyBind.subscribe(maxAmountInputController.inputTextProperty(),
                inputText -> {
                    model.getMaxAmountInputText().set(inputText);
                    applySumNumChars();
                }));

        subscriptions.add(EasyBind.subscribe(model.getMinAmountInputFieldWidth(), width -> {
            if (width != null) {
                minAmountInputController.setAmountFieldWidth(width.doubleValue());
            }
        }));
        subscriptions.add(EasyBind.subscribe(model.getDashWidth(), width -> {
            if (width != null) {
                minAmountInputController.setDashFieldWidth(width.doubleValue());
            }
        }));
        subscriptions.add(EasyBind.subscribe(model.getMaxAmountInputFieldWidth(), width -> {
            if (width != null) {
                maxAmountInputController.setAmountFieldWidth(width.doubleValue());
            }
        }));


        subscriptions.add(EasyBind.subscribe(minAmountInputController.focusedProperty(),
                focused -> {
                    applyIsTextInputFocused();
                }));

        subscriptions.add(EasyBind.subscribe(maxAmountInputController.focusedProperty(),
                focused -> {
                    applyIsTextInputFocused();
                }));
    }

    @Override
    public void onDeactivate() {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
        pins.forEach(Pin::unbind);
        pins.clear();
    }


    /* --------------------------------------------------------------------- */
    // Public API
    /* --------------------------------------------------------------------- */

    public ReadOnlyBooleanProperty getIsTextInputFocused() {
        return model.getIsTextInputFocused();
    }


    /* --------------------------------------------------------------------- */
    // UI handlers
    /* --------------------------------------------------------------------- */

    void onToggleInputMode() {
        boolean value = !createOfferAmountService.getUseBaseCurrencyForAmountInput();
        createOfferDraftWorkflow.setUseBaseCurrencyForAmountInput(value);
    }


    /* --------------------------------------------------------------------- */
    // Private
    /* --------------------------------------------------------------------- */

    private void applyAllAmounts() {
        applyMinInputAmount();
        applyMaxInputAmount();
        applyMinPassiveAmount();
        applyMaxPassiveAmount();
    }

    private void applyMinInputAmount() {
        TradeAmount tradeAmount = createOfferAmountService.getMinTradeAmount();
        Monetary inputAmount = createOfferDraftWorkflow.toInputAmount(tradeAmount, true);
        minAmountInputController.setAmount(inputAmount);
    }

    private void applyMaxInputAmount() {
        TradeAmount tradeAmount = createOfferAmountService.getMaxTradeAmount();
        Monetary inputAmount = createOfferDraftWorkflow.toInputAmount(tradeAmount, true);
        maxAmountInputController.setAmount(inputAmount);
    }

    private void applyMinPassiveAmount() {
        TradeAmount tradeAmount = createOfferAmountService.getMinTradeAmount();
        Monetary passiveAmount = createOfferDraftWorkflow.toPassiveAmount(tradeAmount, true);
        minPassiveAmountController.setAmount(passiveAmount);
    }

    private void applyMaxPassiveAmount() {
        TradeAmount tradeAmount = createOfferAmountService.getMaxTradeAmount();
        Monetary passiveAmount = createOfferDraftWorkflow.toPassiveAmount(tradeAmount, true);
        maxPassiveAmountController.setAmount(passiveAmount);
    }

    private void applySumNumChars() {
        String minAmountInputText = minAmountInputController.inputTextProperty().get();
        int minAmountInputTextLength = minAmountInputText != null ? minAmountInputText.length() : 0;
        String maxAmountInputText = maxAmountInputController.inputTextProperty().get();
        int maxAmountInputTextLength = maxAmountInputText != null ? maxAmountInputText.length() : 0;
        int sumOfNumChars = minAmountInputTextLength + maxAmountInputTextLength + 1; // for dash
        minAmountInputController.setSumOfNumChars(sumOfNumChars);
        maxAmountInputController.setSumOfNumChars(sumOfNumChars);
        model.getSumOfNumChars().set(sumOfNumChars);
    }

    private void applyIsTextInputFocused() {
        boolean focused = minAmountInputController.focusedProperty().get() || maxAmountInputController.focusedProperty().get();
        model.getIsTextInputFocused().set(focused);
    }

}
