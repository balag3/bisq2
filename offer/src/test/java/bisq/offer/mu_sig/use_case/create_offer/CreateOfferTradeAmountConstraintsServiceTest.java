package bisq.offer.mu_sig.use_case.create_offer;

import bisq.account.payment_method.PaymentRail;
import bisq.account.payment_method.fiat.FiatPaymentRail;
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
import bisq.offer.mu_sig.use_case.TradeAmountConstraints;
import bisq.offer.mu_sig.use_case.TradeAmountLimits;
import bisq.offer.mu_sig.use_case.create_offer.amount.limits.PaymentMethodBasedAmountLimits;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateOfferTradeAmountConstraintsServiceTest {

    @Test
    public void computeUsesPaymentRailSpecificMaxLimit() {
        Market market = MarketRepository.getUSDBitcoinMarket();
        PriceQuote offerPriceQuote = PriceQuote.fromFiatPrice(50000, "USD");
        PriceQuote marketPriceQuote = PriceQuote.fromFiatPrice(50000, "USD");
        MockMarketPriceService marketPriceService = new MockMarketPriceService(PriceQuote.fromFiatPrice(50000, "USD"));
        CreateOfferTradeAmountConstraintsService service = new CreateOfferTradeAmountConstraintsService(marketPriceService);

        PaymentRail paymentRail = FiatPaymentRail.ACH_TRANSFER;
        Fiat paymentRailBasedTradeLimitInUsd = PaymentMethodBasedAmountLimits.evaluateLimit(paymentRail);
        TradeAmountConstraints constraints = service.compute(market,
                Direction.BUY,
                offerPriceQuote,
                paymentRailBasedTradeLimitInUsd);

        assertEquals(Fiat.fromFaceValue(5000, "USD"), constraints.tradeAmountLimits().getMax().getQuoteSideAmount());
        assertEquals(Fiat.fromFaceValue(TradeAmountLimits.USER_SPECIFIC_LIMIT_IN_USD, "USD"),
                constraints.userSpecificTradeAmountLimit().orElseThrow().getQuoteSideAmount());
    }

    @Test
    public void computeWithNoPaymentRailFallsBackToProtocolLimit() {
        Market market = MarketRepository.getUSDBitcoinMarket();
        PriceQuote offerPriceQuote = PriceQuote.fromFiatPrice(50000, "USD");
        PriceQuote marketPriceQuote = PriceQuote.fromFiatPrice(50000, "USD");
        MockMarketPriceService marketPriceService = new MockMarketPriceService(PriceQuote.fromFiatPrice(50000, "USD"));
        CreateOfferTradeAmountConstraintsService service = new CreateOfferTradeAmountConstraintsService(marketPriceService);

        PaymentRail paymentRail = null;
        Fiat paymentRailBasedTradeLimitInUsd = PaymentMethodBasedAmountLimits.evaluateLimit(paymentRail);
        TradeAmountConstraints constraints = service.compute(market,
                Direction.SELL,
                offerPriceQuote,
                paymentRailBasedTradeLimitInUsd);



      /*  TradeAmountConstraints constraints = service.compute(market,
                Direction.SELL,
                offerPriceQuote,
                marketPriceQuote,
                null);*/

        assertEquals(Fiat.fromFaceValue(10000, "USD"), constraints.tradeAmountLimits().getMax().getQuoteSideAmount());
        assertTrue(constraints.userSpecificTradeAmountLimit().isEmpty());
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
