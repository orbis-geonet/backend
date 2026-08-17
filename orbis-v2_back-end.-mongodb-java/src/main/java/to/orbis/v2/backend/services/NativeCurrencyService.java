package to.orbis.v2.backend.services;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.Currency;
import to.orbis.v2.backend.models.entity.User;
import to.orbis.v2.backend.repositories.UsersRepository;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Map.entry;

@Slf4j
@Service
public class NativeCurrencyService {
    private static final Map<String, Currency> COUNTRY_CURRENCIES = Map.ofEntries(
            entry("australia", Currency.AUD),
            entry("austria", Currency.EUR),
            entry("belgium", Currency.EUR),
            entry("brazil", Currency.BRL),
            entry("bulgaria", Currency.BGN),
            entry("canada", Currency.CAD),
            entry("china", Currency.CNY),
            entry("czechia", Currency.CZK),
            entry("czech republic", Currency.CZK),
            entry("denmark", Currency.DKK),
            entry("estonia", Currency.EUR),
            entry("finland", Currency.EUR),
            entry("france", Currency.EUR),
            entry("germany", Currency.EUR),
            entry("greece", Currency.EUR),
            entry("hong kong", Currency.HKD),
            entry("hungary", Currency.HUF),
            entry("iceland", Currency.ISK),
            entry("india", Currency.INR),
            entry("indonesia", Currency.IDR),
            entry("ireland", Currency.EUR),
            entry("israel", Currency.ILS),
            entry("italy", Currency.EUR),
            entry("japan", Currency.JPY),
            entry("latvia", Currency.EUR),
            entry("lithuania", Currency.EUR),
            entry("luxembourg", Currency.EUR),
            entry("malaysia", Currency.MYR),
            entry("malta", Currency.EUR),
            entry("mexico", Currency.MXN),
            entry("netherlands", Currency.EUR),
            entry("new zealand", Currency.NZD),
            entry("norway", Currency.NOK),
            entry("philippines", Currency.PHP),
            entry("poland", Currency.PLN),
            entry("portugal", Currency.EUR),
            entry("romania", Currency.RON),
            entry("singapore", Currency.SGD),
            entry("slovakia", Currency.EUR),
            entry("slovenia", Currency.EUR),
            entry("south africa", Currency.ZAR),
            entry("south korea", Currency.KRW),
            entry("spain", Currency.EUR),
            entry("sweden", Currency.SEK),
            entry("switzerland", Currency.CHF),
            entry("thailand", Currency.THB),
            entry("turkey", Currency.TRY),
            entry("turkiye", Currency.TRY),
            entry("united kingdom", Currency.GBP),
            entry("united states", Currency.USD),
            entry("united states of america", Currency.USD)
    );

    private final UsersRepository usersRepository;
    private final PlacesInfoService placesInfoService;
    private final CurrencyConversionService currencyConversionService;
    private final Duration locationCacheTtl = Duration.ofHours(24);
    private final Duration fallbackLocationCacheTtl = Duration.ofMinutes(5);
    private final ConcurrentHashMap<String, CachedNativeCurrencyInfo> cachedLocationCurrencies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Mono<NativeCurrencyInfo>> inFlightLocationCurrencies = new ConcurrentHashMap<>();

    public NativeCurrencyService(UsersRepository usersRepository, PlacesInfoService placesInfoService, CurrencyConversionService currencyConversionService) {
        this.usersRepository = usersRepository;
        this.placesInfoService = placesInfoService;
        this.currencyConversionService = currencyConversionService;
    }

    public Mono<NativeCurrencyInfo> findForUser(String userKey) {
        return usersRepository.findOneByUserKey(userKey)
                .flatMap(user -> resolvePoint(user.getCoordinates()))
                .switchIfEmpty(usd(false))
                .onErrorResume(error -> {
                    log.warn("Cannot resolve native currency for user {}: {}", userKey, error.getMessage());
                    return usd(false);
                });
    }

    public Mono<NativeCurrencyInfo> findForLocationOrUser(GeoJsonPoint point, String userKey) {
        Mono<NativeCurrencyInfo> userCurrency = usersRepository.findOneByUserKey(userKey)
                .flatMap(user -> resolvePoint(user.getCoordinates()));
        return resolvePoint(point)
                .filter(NativeCurrencyInfo::isNativeCurrencyAvailable)
                .switchIfEmpty(userCurrency)
                .switchIfEmpty(usd(false))
                .onErrorResume(error -> {
                    log.warn("Cannot resolve native currency for user {}: {}", userKey, error.getMessage());
                    return usd(false);
                });
    }

    private Mono<NativeCurrencyInfo> resolvePoint(GeoJsonPoint point) {
        if (point == null) {
            return Mono.empty();
        }
        var key = locationKey(point);
        var cached = cachedLocationCurrencies.get(key);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return Mono.just(cached.info);
        }
        return inFlightLocationCurrencies.computeIfAbsent(key, ignored -> resolvePointUncached(point)
                .onErrorResume(error -> {
                    log.warn("Cannot resolve native currency for location {}: {}", key, error.getMessage());
                    return usd(false);
                })
                .switchIfEmpty(usd(false))
                .doOnNext(info -> cachedLocationCurrencies.put(key, new CachedNativeCurrencyInfo(info, Instant.now().plus(info.isNativeCurrencyAvailable() ? locationCacheTtl : fallbackLocationCacheTtl))))
                .doFinally(signal -> inFlightLocationCurrencies.remove(key))
                .cache());
    }

    private Mono<NativeCurrencyInfo> resolvePointUncached(GeoJsonPoint point) {
        return placesInfoService.findPlace(point.getY(), point.getX())
                .flatMap(place -> Mono.justOrEmpty(currencyForCountry(place.getCountry())))
                .flatMap(currency -> currencyConversionService.getUsdRate(currency)
                        .map(rate -> new NativeCurrencyInfo(currency, rate, true)));
    }

    private Mono<NativeCurrencyInfo> usd(boolean available) {
        return currencyConversionService.getUsdRate(Currency.USD)
                .map(rate -> new NativeCurrencyInfo(Currency.USD, rate, available));
    }

    private Optional<Currency> currencyForCountry(String country) {
        if (country == null || country.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(COUNTRY_CURRENCIES.get(normalize(country)));
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }

    private String locationKey(GeoJsonPoint point) {
        return String.format(java.util.Locale.US, "%.2f;%.2f", point.getY(), point.getX());
    }

    private static class CachedNativeCurrencyInfo {
        final NativeCurrencyInfo info;
        final Instant expiresAt;

        CachedNativeCurrencyInfo(NativeCurrencyInfo info, Instant expiresAt) {
            this.info = info;
            this.expiresAt = expiresAt;
        }
    }

    @Value
    public static class NativeCurrencyInfo {
        Currency nativeCurrency;
        BigDecimal nativeCurrencyUsdRate;
        boolean nativeCurrencyAvailable;
    }
}
