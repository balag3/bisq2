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

import bisq.common.monetary.MonetaryRange;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.monetary.TradeAmountRange;
import bisq.common.observable.ReadOnlyObservable;
import bisq.offer.mu_sig.draft.ReadOnlyOfferDraft;

import java.util.Optional;

public interface ReadOnlyCreateOfferDraft extends ReadOnlyOfferDraft {

    ReadOnlyObservable<PriceQuote> priceQuoteObservable();

    PriceQuote getPriceQuote();


    ReadOnlyObservable<Boolean> useFixPriceObservable();

    boolean getUseFixPrice();


    ReadOnlyObservable<Double> pricePercentageObservable();

    double getPricePercentage();


    ReadOnlyObservable<Boolean> useBaseCurrencyForAmountInputObservable();

    boolean getUseBaseCurrencyForAmountInput();


    ReadOnlyObservable<TradeAmount> fixTradeAmountObservable();

    TradeAmount getFixTradeAmount();


    ReadOnlyObservable<TradeAmount> minTradeAmountObservable();

    TradeAmount getMinTradeAmount();


    ReadOnlyObservable<TradeAmount> maxTradeAmountObservable();

    TradeAmount getMaxTradeAmount();


    TradeAmountRange getTradeAmountLimits();

    ReadOnlyObservable<Optional<TradeAmount>> userSpecificTradeAmountLimitObservable();

    Optional<TradeAmount> getUserSpecificTradeAmountLimit();

    ReadOnlyObservable<Optional<Double>> userSpecificTradeAmountLimitAsSliderValueObservable();

    Optional<Double> getUserSpecificTradeAmountLimitAsSliderValue();

    ReadOnlyObservable<Boolean> useRangeAmountObservable();

    boolean getUseRangeAmount();

    ReadOnlyObservable<TradeAmountRange> tradeAmountLimitsObservable();

    ReadOnlyObservable<MonetaryRange> inputAmountLimitsObservable();

    MonetaryRange getInputAmountLimits();

    ReadOnlyObservable<Double> fixAmountSliderValueObservable();

    Double getFixAmountSliderValue();

    ReadOnlyObservable<Double> minAmountSliderValueObservable();

    Double getMinAmountSliderValue();

    ReadOnlyObservable<Double> maxAmountSliderValueObservable();

    Double getMaxAmountSliderValue();

}
