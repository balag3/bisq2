package bisq.offer.mu_sig.draft;

import bisq.bonded_roles.bonded_role.AuthorizedBondedRole;
import bisq.bonded_roles.market_price.MarketPrice;
import bisq.bonded_roles.market_price.MarketPriceRequestService;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.market.MarketRepository;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.PriceQuote;
import bisq.common.monetary.TradeAmount;
import bisq.common.observable.ReadOnlyObservable;
import bisq.common.observable.map.ReadOnlyObservableMap;
import bisq.offer.Direction;
import bisq.offer.amount.spec.QuoteSideRangeAmountSpec;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TakeOfferTradeAmountConstraintsServiceTest {

    @Test
    public void computeClampsMaxAmountToStrictLimitWhenOfferMaxExceedsStrictLimit() {
        Market market = MarketRepository.getUSDBitcoinMarket();
        PriceQuote offerPriceQuote = PriceQuote.fromFiatPrice(50000, "USD");
        PriceQuote marketPriceQuote = PriceQuote.fromFiatPrice(50000, "USD");
        MockMarketPriceService marketPriceService = new MockMarketPriceService(PriceQuote.fromFiatPrice(50000, "USD"));
        TakeOfferTradeAmountConstraintsService service = new TakeOfferTradeAmountConstraintsService(marketPriceService);
        QuoteSideRangeAmountSpec amountSpec = new QuoteSideRangeAmountSpec(
                Fiat.fromFaceValue(100.0, "USD").getValue(),
                Fiat.fromFaceValue(5200.0, "USD").getValue());

        Fiat maxTradeLimitInUsd = Fiat.fromFaceValue(5000.0, "USD");
        TradeAmountConstraints constraints = service.compute(market,
                Direction.BUY,
                amountSpec,
                offerPriceQuote,
                marketPriceQuote,
                maxTradeLimitInUsd);

        assertEquals(Fiat.fromFaceValue(100.0, "USD"), constraints.tradeAmountLimits().getMin().getQuoteSideAmount());
        assertEquals(Fiat.fromFaceValue(5000.0, "USD"), constraints.tradeAmountLimits().getMax().getQuoteSideAmount());
    }

    @Test
    public void computeAcceptsSlightlyHigherAmountWhenStrictClampingIsImpossibleButWithinTolerance() {
        Market market = MarketRepository.getUSDBitcoinMarket();
        PriceQuote offerPriceQuote = PriceQuote.fromFiatPrice(50000, "USD");
        PriceQuote marketPriceQuote = PriceQuote.fromFiatPrice(50000, "USD");
        MockMarketPriceService marketPriceService = new MockMarketPriceService(PriceQuote.fromFiatPrice(50000, "USD"));
        TakeOfferTradeAmountConstraintsService service = new TakeOfferTradeAmountConstraintsService(marketPriceService);
        QuoteSideRangeAmountSpec amountSpec = new QuoteSideRangeAmountSpec(
                Fiat.fromFaceValue(5100.0, "USD").getValue(),
                Fiat.fromFaceValue(5200.0, "USD").getValue());

        Fiat maxTradeLimitInUsd = Fiat.fromFaceValue(5000.0, "USD");
        TradeAmountConstraints constraints = service.compute(market,
                Direction.BUY,
                amountSpec,
                offerPriceQuote,
                marketPriceQuote,
                maxTradeLimitInUsd);

        assertEquals(Fiat.fromFaceValue(5100.0, "USD"), constraints.tradeAmountLimits().getMin().getQuoteSideAmount());
        assertEquals(Fiat.fromFaceValue(5200.0, "USD"), constraints.tradeAmountLimits().getMax().getQuoteSideAmount());
    }

    @Test
    public void computeThrowsWhenStrictClampingIsImpossibleAndAmountExceedsTolerance() {
        Market market = MarketRepository.getUSDBitcoinMarket();
        PriceQuote offerPriceQuote = PriceQuote.fromFiatPrice(50000, "USD");
        PriceQuote marketPriceQuote = PriceQuote.fromFiatPrice(50000, "USD");
        MockMarketPriceService marketPriceService = new MockMarketPriceService(PriceQuote.fromFiatPrice(50000, "USD"));
        TakeOfferTradeAmountConstraintsService service = new TakeOfferTradeAmountConstraintsService(marketPriceService);
        QuoteSideRangeAmountSpec amountSpec = new QuoteSideRangeAmountSpec(
                Fiat.fromFaceValue(5300.0, "USD").getValue(),
                Fiat.fromFaceValue(6000.0, "USD").getValue());

        Fiat maxTradeLimitInUsd = Fiat.fromFaceValue(5000.0, "USD");
        assertThrows(IllegalStateException.class,
                () -> service.compute(market,
                        Direction.BUY,
                        amountSpec,
                        offerPriceQuote,
                        marketPriceQuote,
                        maxTradeLimitInUsd));
    }

    private static class MockMarketPriceService implements MarketPriceService {
        private final PriceQuote btcUsdPriceQuote;

        private MockMarketPriceService(PriceQuote btcUsdPriceQuote) {
            this.btcUsdPriceQuote = btcUsdPriceQuote;
        }

        public PriceQuote getBtcUsdPriceQuote() {
            return btcUsdPriceQuote;
        }

        public TradeAmount getTradeAmountFromUsd(Market market, Fiat usdAmount) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public void setSelectedMarket(Market market) {
        }

        @Override
        public ReadOnlyObservable<Market> getSelectedMarket() {
            return null;
        }

        @Override
        public Optional<MarketPrice> findMarketPrice(Market market) {
            return Optional.empty();
        }

        @Override
        public Optional<PriceQuote> findMarketPriceQuote(Market market) {
            return Optional.ofNullable(btcUsdPriceQuote);
        }

        @Override
        public PriceQuote getMarketPriceQuoteOrThrow(Market market) {
            return btcUsdPriceQuote;
        }

        @Override
        public ReadOnlyObservableMap<Market, MarketPrice> getMarketPriceByCurrencyMap() {
            return null;
        }

        @Override
        public boolean hasMarketPrice(Market market) {
            return false;
        }

        @Override
        public Optional<MarketPriceRequestService> getMarketPriceRequestService() {
            return Optional.empty();
        }

        @Override
        public Optional<AuthorizedBondedRole> getMarketPriceProvidingOracle() {
            return Optional.empty();
        }
    }
}
