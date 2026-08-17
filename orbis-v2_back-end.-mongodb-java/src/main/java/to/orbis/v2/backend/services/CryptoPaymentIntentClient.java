package to.orbis.v2.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.Currency;
import to.orbis.v2.backend.models.dto.crypto.CryptoPaymentDto;
import to.orbis.v2.backend.models.dto.crypto.CryptoPaymentStatusDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class CryptoPaymentIntentClient {
    private final WebClient webClient;
    private final WebClient priceWebClient;
    private final CurrencyConversionService currencyConversionService;
    private final String masterKey;
    private final String solTokenAddress;
    private final Duration solPriceCacheTtl;
    private final Object solPriceLock = new Object();
    private final AtomicReference<CachedPrice> cachedSolUsdPrice = new AtomicReference<>();
    private final AtomicReference<Mono<BigDecimal>> inFlightSolUsdPrice = new AtomicReference<>();

    public CryptoPaymentIntentClient(
            CurrencyConversionService currencyConversionService,
            @Value("${NODEJS_WORKER_URL:http://localhost:3000}") String cloneProxyUrl,
            @Value("${ORBIS_API_SECRET}") String masterKey,
            @Value("${GECKOTERMINAL_BASE_URL:https://api.geckoterminal.com/api/v2}") String geckoTerminalBaseUrl,
            @Value("${GECKOTERMINAL_SOL_TOKEN_ADDRESS:So11111111111111111111111111111111111111112}") String solTokenAddress,
            @Value("${GECKOTERMINAL_SOL_PRICE_CACHE_SECONDS:60}") long solPriceCacheSeconds
    ) {
        this.webClient = WebClient.builder().baseUrl(cloneProxyUrl).build();
        this.priceWebClient = WebClient.builder().baseUrl(geckoTerminalBaseUrl).build();
        this.currencyConversionService = currencyConversionService;
        this.masterKey = masterKey;
        this.solTokenAddress = solTokenAddress;
        this.solPriceCacheTtl = Duration.ofSeconds(Math.max(1, solPriceCacheSeconds));
    }

    public Mono<CryptoPaymentDto> createIntent(
            String userKey,
            String kind,
            String itemKey,
            Integer quantity,
            String payerWallet,
            String ownerWallet,
            BigDecimal amount,
            Currency currency,
            String coin
    ) {
        String normalizedCoin = coin == null ? "ORBIS" : coin.toUpperCase();
        Currency normalizedCurrency = currency == null ? Currency.USD : currency;
        Map<String, Object> body = new HashMap<>();
        body.put("userKey", userKey == null ? "" : userKey);
        body.put("kind", kind);
        body.put("itemKey", itemKey == null ? "" : itemKey);
        body.put("quantity", quantity == null ? 1 : quantity);
        body.put("payerWallet", payerWallet);
        body.put("ownerWallet", ownerWallet);
        body.put("coin", normalizedCoin);
        if ("SOL".equals(normalizedCoin)) {
            return currencyConversionService.convertToUsd(amount, normalizedCurrency)
                    .zipWith(getSolUsdPrice())
                    .flatMap(prices -> {
                        String amountSol = toSolAmount(prices.getT1(), prices.getT2());
                        body.put("amountSol", amountSol);
                        log.info("crypto intent converted kind={} itemKey={} coin={} amount={} currency={} amountUsd={} amountSol={}",
                                kind,
                                itemKey,
                                normalizedCoin,
                                amount.stripTrailingZeros().toPlainString(),
                                normalizedCurrency,
                                prices.getT1().stripTrailingZeros().toPlainString(),
                                amountSol);
                        return postIntent(body);
                    });
        }
        return currencyConversionService.convertToUsd(amount, normalizedCurrency)
                .flatMap(amountUsd -> {
                    body.put("priceUsd", amountUsd.stripTrailingZeros().toPlainString());
                    log.info("crypto intent converted kind={} itemKey={} coin={} amount={} currency={} amountUsd={}",
                            kind,
                            itemKey,
                            normalizedCoin,
                            amount.stripTrailingZeros().toPlainString(),
                            normalizedCurrency,
                            amountUsd.stripTrailingZeros().toPlainString());
                    return postIntent(body);
                });
    }

    private Mono<CryptoPaymentDto> postIntent(Map<String, Object> body) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/payments/intent").queryParam("_internal", "true").build())
                .header("X-Master-Key", masterKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(CryptoPaymentDto.class);
    }

    private Mono<BigDecimal> getSolUsdPrice() {
        Instant now = Instant.now();
        CachedPrice cached = cachedSolUsdPrice.get();
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return Mono.just(cached.price);
        }
        synchronized (solPriceLock) {
            cached = cachedSolUsdPrice.get();
            now = Instant.now();
            if (cached != null && cached.expiresAt.isAfter(now)) {
                return Mono.just(cached.price);
            }
            Mono<BigDecimal> inFlight = inFlightSolUsdPrice.get();
            if (inFlight != null) {
                return inFlight;
            }
            Mono<BigDecimal> request = fetchSolUsdPrice()
                    .doOnNext(price -> cachedSolUsdPrice.set(new CachedPrice(price, Instant.now().plus(solPriceCacheTtl))))
                    .doFinally(signal -> {
                        synchronized (solPriceLock) {
                            inFlightSolUsdPrice.set(null);
                        }
                    })
                    .cache();
            inFlightSolUsdPrice.set(request);
            return request;
        }
    }

    private Mono<BigDecimal> fetchSolUsdPrice() {
        return priceWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/simple/networks/solana/token_price/{address}").build(solTokenAddress))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(this::extractSolUsdPrice);
    }

    private BigDecimal extractSolUsdPrice(Map<String, Object> body) {
        Map<String, Object> data = asMap(body.get("data"), "data");
        Map<String, Object> attributes = asMap(data.get("attributes"), "attributes");
        Map<String, Object> tokenPrices = asMap(attributes.get("token_prices"), "token_prices");
        Object priceValue = tokenPrices.get(solTokenAddress);
        if (priceValue == null) {
            for (Map.Entry<String, Object> entry : tokenPrices.entrySet()) {
                if (solTokenAddress.equalsIgnoreCase(entry.getKey())) {
                    priceValue = entry.getValue();
                    break;
                }
            }
        }
        if (priceValue == null) {
            throw new IllegalStateException("GeckoTerminal SOL price response missing token " + solTokenAddress);
        }
        BigDecimal price = parsePositiveDecimal(String.valueOf(priceValue), "GeckoTerminal SOL price");
        log.info("Fetched live SOL price from GeckoTerminal usd={} cacheSeconds={}", price.stripTrailingZeros().toPlainString(), solPriceCacheTtl.getSeconds());
        return price;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value, String name) {
        if (!(value instanceof Map)) {
            throw new IllegalStateException("GeckoTerminal SOL price response missing " + name);
        }
        return (Map<String, Object>) value;
    }

    private static BigDecimal parsePositiveDecimal(String value, String name) {
        BigDecimal decimal = new BigDecimal(value);
        if (decimal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(name + " must be greater than zero");
        }
        return decimal;
    }

    private String toSolAmount(BigDecimal amountUsd, BigDecimal solUsdPrice) {
        if (solUsdPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("SOL price must be greater than zero");
        }
        return amountUsd.divide(solUsdPrice, 9, RoundingMode.UP).stripTrailingZeros().toPlainString();
    }

    private static class CachedPrice {
        final BigDecimal price;
        final Instant expiresAt;

        CachedPrice(BigDecimal price, Instant expiresAt) {
            this.price = price;
            this.expiresAt = expiresAt;
        }
    }

    public Mono<CryptoPaymentStatusDto> getStatus(String ref) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/payments/status/{ref}").queryParam("_internal", "true").build(ref))
                .header("X-Master-Key", masterKey)
                .retrieve()
                .bodyToMono(CryptoPaymentStatusDto.class);
    }
}
