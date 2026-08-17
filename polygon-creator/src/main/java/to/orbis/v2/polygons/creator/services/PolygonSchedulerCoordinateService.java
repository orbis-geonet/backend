package to.orbis.v2.polygons.creator.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateStatus;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateType;
import to.orbis.v2.polygons.creator.models.entity.PolygonSchedulerCoordinate;
import to.orbis.v2.polygons.creator.repositories.PolygonSchedulerCoordinateAggregationRepository;
import to.orbis.v2.polygons.creator.repositories.PolygonSchedulerCoordinateRepository;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolygonSchedulerCoordinateService {
    private final PolygonSchedulerCoordinateRepository polygonSchedulerCoordinateRepository;
    private final PolygonSchedulerCoordinateAggregationRepository polygonSchedulerCoordinateAggregationRepository;

    private final Set<String> coordinateInProgressSet = Collections.synchronizedSet(new HashSet<>());
    private final ReentrantLock lock = new ReentrantLock();

//    @PreDestroy
    public void preDestroy() {
        if (!coordinateInProgressSet.isEmpty()) {
            polygonSchedulerCoordinateAggregationRepository.updateStatusPreDestroy(coordinateInProgressSet, PolygonSchedulerCoordinateStatus.NEW);
        }
    }

    @Scheduled(cron = "0 0 0 * * ?", zone = "America/Sao_Paulo")
    public void deleteOldPoints() {
        polygonSchedulerCoordinateRepository.deleteAllByStatusAndCreatedAtBefore(
                PolygonSchedulerCoordinateStatus.DONE,
                Instant.now().minus(1, ChronoUnit.DAYS)
        );
        polygonSchedulerCoordinateRepository.deleteAllByStatusAndCreatedAtBefore(
                PolygonSchedulerCoordinateStatus.ERROR, Instant.now()
        );
    }

    public void updateCoordinateAround(PolygonSchedulerCoordinate coordinate) {
        boolean isNeedToUpdateCoordinateAround = coordinate.getType().equals(PolygonSchedulerCoordinateType.TRIGGER);
        if (isNeedToUpdateCoordinateAround) {
            polygonSchedulerCoordinateAggregationRepository.updateStatusForCoordinateAround(coordinate.getCoordinates(), PolygonSchedulerCoordinateStatus.DONE);
        }
    }

    public List<PolygonSchedulerCoordinate> getPointsToProcess(
            PolygonSchedulerCoordinateType type,
            PageRequest pageRequest
    ) {
        List<PolygonSchedulerCoordinate> coordinates = polygonSchedulerCoordinateRepository.findAllByIsEnabledAndAndTypeAndStatus(
                        true, type, PolygonSchedulerCoordinateStatus.NEW, pageRequest)
                .stream()
                .toList();
        Set<String> coordinateInProgress = setCalculationInProcess(coordinates);
        if (coordinateInProgress.isEmpty()) {
            return Collections.emptyList();
        } else {
            coordinateInProgressSet.addAll(coordinateInProgress);
            return coordinates;
        }
    }

    private Set<String> setCalculationInProcess(List<PolygonSchedulerCoordinate> coordinates) {
        if (coordinates.isEmpty()) {
            return Collections.emptySet();
        } else  {
            Set<String> coordinateKeys = coordinates.stream()
                    .map(PolygonSchedulerCoordinate::getPolygonSchedulerCoordinateKey)
                    .collect(Collectors.toSet());

            long count = polygonSchedulerCoordinateAggregationRepository.updateStatus(coordinateKeys, PolygonSchedulerCoordinateStatus.IN_PROCESS);
            if (count > 0) {
                return coordinateKeys;
            } else {
                return Collections.emptySet();
            }
        }
    }

    public void removeCoordinatesInProgress(List<PolygonSchedulerCoordinate> coordinates) {
        Set<String> coordinatesInProgress = coordinates.stream().map(PolygonSchedulerCoordinate::getPolygonSchedulerCoordinateKey)
                .collect(Collectors.toSet());
        coordinatesInProgress.removeAll(coordinateInProgressSet);
    }
}
