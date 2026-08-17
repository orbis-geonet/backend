package to.orbis.v2.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CurrencyConversionService {
    private final WebClient webClient;
    private final Duration cacheTtl;
    private final ConcurrentHashMap<Currency, CachedRate> cachedUsdRates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Currency, Mono<BigDecimal>> inFlightUsdRates = new ConcurrentHashMap<>();

    public CurrencyConversionService(
            WebClient.Builder builder,
            @Value("${FRANKFURTER_BASE_URL:https://api.frankfurter.dev/v1}") String frankfurterBaseUrl,
            @Value("${FX_PRICE_CACHE_SECONDS:86400}") long cacheSeconds
    ) {
        this.webClient = builder.baseUrl(frankfurterBaseUrl).build();
        this.cacheTtl = Duration.ofSeconds(Math.max(60, cacheSeconds));
    }

    public Mono<BigDecimal> convertToUsd(BigDecimal amount, Currency currency) {
        return getUsdRate(currency)
                .map(rate -> amount.multiply(rate).setScale(9, RoundingMode.HALF_UP));
    }

    public Mono<BigDecimal> getUsdRate(Currency currency) {
        if (currency == null || currency == Currency.USD) {
            return Mono.just(BigDecimal.ONE);
        }
        var now = Instant.now();
        var cached = cachedUsdRates.get(currency);
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return Mono.just(cached.rate);
        }
        return inFlightUsdRates.computeIfAbsent(currency, key -> fetchUsdRate(key)
                        .doOnNext(rate -> cachedUsdRates.put(key, new CachedRate(rate, Instant.now().plus(cacheTtl))))
                        .onErrorResume(error -> {
                            var stale = cachedUsdRates.get(key);
                            if (stale != null) {
                                log.warn("Using stale FX rate {} to USD after refresh failure: {}", key, error.getMessage());
                                return Mono.just(stale.rate);
                            }
                            return Mono.error(error);
                        })
                        .doFinally(signal -> inFlightUsdRates.remove(key))
                        .cache());
    }

    private Mono<BigDecimal> fetchUsdRate(Currency currency) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/latest")
                        .queryParam("from", currency.name())
                        .queryParam("to", Currency.USD.name())
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(body -> extractRate(body, currency));
    }

    @SuppressWarnings("unchecked")
    private BigDecimal extractRate(Map<String, Object> body, Currency currency) {
        var rates = (Map<String, Object>) body.get("rates");
        if (rates == null || !rates.containsKey(Currency.USD.name())) {
            throw new IllegalStateException("Frankfurter response missing USD rate for " + currency);
        }
        var rate = new BigDecimal(String.valueOf(rates.get(Currency.USD.name())));
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Frankfurter USD rate must be greater than zero for " + currency);
        }
        log.info("Fetched FX rate {} to USD={} cacheSeconds={}", currency, rate.stripTrailingZeros().toPlainString(), cacheTtl.getSeconds());
        return rate;
    }

    private static class CachedRate {
        final BigDecimal rate;
        final Instant expiresAt;

        CachedRate(BigDecimal rate, Instant expiresAt) {
            this.rate = rate;
            this.expiresAt = expiresAt;
        }
    }
}
