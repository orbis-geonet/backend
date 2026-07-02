package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.PolygonSchedulerCoordinateType;
import to.orbis.v2.backend.models.dto.PolygonSchedulerCoordinateStatusDto;
import to.orbis.v2.backend.models.entity.Place;
import to.orbis.v2.backend.models.entity.PolygonSchedulerCoordinate;
import to.orbis.v2.backend.repositories.PolygonSchedulerCoordinateRepository;
import to.orbis.v2.backend.utils.PolygonCalculationUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolygonSchedulerService {

    private final PolygonSchedulerCoordinateRepository polygonSchedulerCoordinateRepository;

    public Mono<String> trigger() {
        return polygonSchedulerCoordinateRepository.deleteAll()
                .thenMany(Flux.defer(() -> {
                    List<PolygonSchedulerCoordinate> coordinates = PolygonCalculationUtils.getSouthAmericaCoordinates();
                    return polygonSchedulerCoordinateRepository.saveAll(coordinates);
                }))
                .count()  // Count the number of saved elements
                .map(count -> "Number of saved coordinates: " + count);  // Return the count as a string
    }

    public Mono<String> addPolygonSchedulerCoordinateAfterCheckin(
            Place place
    ) {
        PolygonSchedulerCoordinate polygonSchedulerCoordinate = PolygonCalculationUtils.createPolygonSchedulerCoordinate(
                place.getCoordinates().getX(),
                place.getCoordinates().getY(),
                PolygonCalculationUtils.RADIUS_KM_CHECK_IN,
                PolygonSchedulerCoordinateType.CHECKIN
        );
        log.info("Triggering polygon calculation with coordinates after checking: {}", place.getCoordinates());

        return polygonSchedulerCoordinateRepository.save(polygonSchedulerCoordinate)
                .map(PolygonSchedulerCoordinate::getPolygonSchedulerCoordinateKey);
    }

    public Mono<String> addPolygonSchedulerForOnePoint(
            double latitude, double longitude
    ) {
        PolygonSchedulerCoordinate polygonSchedulerCoordinate = PolygonCalculationUtils.createPolygonSchedulerCoordinate(
                longitude,
                latitude,
                PolygonCalculationUtils.RADIUS_KM,
                PolygonSchedulerCoordinateType.TRIGGER
        );
        return polygonSchedulerCoordinateRepository.save(polygonSchedulerCoordinate)
                .map(point -> "Point was added. Key: " + point.getPolygonSchedulerCoordinateKey());
    }

    public Mono<PolygonSchedulerCoordinateStatusDto> getStatusPolygonCalculation(String key) {
        return polygonSchedulerCoordinateRepository.findByPolygonSchedulerCoordinateKey(key)
                .map(it -> new PolygonSchedulerCoordinateStatusDto(it.getStatus()));
    }
}
