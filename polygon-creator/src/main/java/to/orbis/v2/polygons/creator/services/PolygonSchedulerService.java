package to.orbis.v2.polygons.creator.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import to.orbis.v2.polygons.creator.configuration.config.PolygonCreatorConfiguration;
import to.orbis.v2.polygons.creator.job.CustomThreadFactory;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateStatus;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateType;
import to.orbis.v2.polygons.creator.models.dto.PalindromeCreationResult;
import to.orbis.v2.polygons.creator.models.dto.polygon.GroupedPolygonDto;
import to.orbis.v2.polygons.creator.models.entity.PolygonSchedulerCoordinate;
import to.orbis.v2.polygons.creator.repositories.PolygonRepository;
import to.orbis.v2.polygons.creator.repositories.PolygonSchedulerCoordinateRepository;
import to.orbis.v2.polygons.creator.utils.PolygonCalculationUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolygonSchedulerService {

    private final PolygonSchedulerCoordinateRepository polygonSchedulerCoordinateRepository;
    private final PolygonSchedulerCoordinateService polygonSchedulerCoordinateService;
    private final PolygonCalculationService polygonCalculationService;
    private final MongoTemplate mongoTemplate;
    private final PolygonRepository polygonRepository;
    private final PolygonCreatorConfiguration configuration;

    public String trigger() {
        polygonSchedulerCoordinateRepository.deleteAll();

        List<PolygonSchedulerCoordinate> coordinates = PolygonCalculationUtils.getGlobalCoordinates();
        mongoTemplate.insert(coordinates, PolygonSchedulerCoordinate.class);
//        polygonSchedulerCoordinateRepository.saveAll(coordinates);

        log.info("trigger: saved coordinates {}", coordinates.size());
        return "Number of saved coordinates: " + coordinates.size();
    }

    public String addPolygonSchedulerForOnePoint(
            double latitude, double longitude
    ) {
        PolygonSchedulerCoordinate polygonSchedulerCoordinate = PolygonCalculationUtils.createPolygonSchedulerCoordinate(
                longitude,
                latitude,
                PolygonCalculationUtils.RADIUS_KM,
                PolygonSchedulerCoordinateType.TRIGGER
        );
        PolygonSchedulerCoordinate savedPoint = polygonSchedulerCoordinateRepository.save(polygonSchedulerCoordinate);

        log.info("addPolygonSchedulerForOnePoint: saved coordinates {}", savedPoint.getPolygonSchedulerCoordinateKey());
        return "Number of saved coordinates: " + savedPoint.getPolygonSchedulerCoordinateKey();
    }

    public void calculateCheckinPoints() {
        calculatePolygons(PolygonSchedulerCoordinateType.CHECKIN);
    }

    public void calculateTriggerPoints() {
        calculatePolygons(PolygonSchedulerCoordinateType.TRIGGER);
    }

    private void calculatePolygons(
            PolygonSchedulerCoordinateType type) {
        int page = CustomThreadFactory.getThreadNumber(Thread.currentThread().getName()) + configuration.getStartPage();
        PageRequest pageRequest = PageRequest.of(page, configuration.getPageSize());

        List<PolygonSchedulerCoordinate> coordinates = polygonSchedulerCoordinateService.getPointsToProcess(type, pageRequest);
        if (!coordinates.isEmpty()) {
            log.info("Type: {}. Page: {}. Polygon calculation: There are {} coordinates", page, type, coordinates.size());

            Instant startTime = Instant.now();
            Duration duration = Duration.between(startTime, Instant.now());

            calculatePolygons(coordinates, type);
            removeDuplicatePolygons(type);

            polygonSchedulerCoordinateService.removeCoordinatesInProgress(coordinates);
            log.info("Type: {}. Take time: {} sec", type, duration.toSeconds());
        } else {
            log.info("Type: {}. Page: {}. There is no polygon calculation points", type, page);
        }
    }

    private void removeDuplicatePolygons(PolygonSchedulerCoordinateType type) {
        // Aggregation to find duplicate placeKeys and sort by createdAt
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "createdAt")), // Sort by createdAt descending
                Aggregation.group("placeKeys")
                        .first("_id").as("firstId") // Keep the newest polygon's id
                        .push("_id").as("ids"),    // Collect all ids for deletion
                Aggregation.match(Criteria.where("ids.1").exists(true)) // Ensure there is more than one polygon
        );

        List<GroupedPolygonDto> groupedPolygons = mongoTemplate.aggregate(aggregation, "polygons", GroupedPolygonDto.class)
                .getMappedResults();

        Integer totalDeleted = groupedPolygons.stream()
                .map(groupedPolygon -> {
                            // Extract ids to delete (all but the first in each group)
                            List<String> idsToDelete = groupedPolygon.getIds().stream()
                                    .skip(1) // Skip the first id (keep the newest copy)
                                    .collect(Collectors.toList());

                            // Perform deletion for the duplicate ids
                            polygonRepository.deleteAllById(idsToDelete);
                            return idsToDelete.size();
                        })
                .reduce(0, Integer::sum);

        log.info("Type: {}. removeDuplicatePolygons: removed {} polygons", type, totalDeleted);
    }

    private void calculatePolygons(
            List<PolygonSchedulerCoordinate> coordinates, PolygonSchedulerCoordinateType type) {
        for (PolygonSchedulerCoordinate coordinate : coordinates) {
            if (coordinate.isEnabled()) {
                try {
                    Instant startAt = Instant.now();
                    PalindromeCreationResult palindromeCreationResult =
                            polygonCalculationService.processCoordinate(coordinate, type);

                    Long timeInSec = Duration.between(startAt, Instant.now()).getSeconds();

                    coordinate.setCalculated(true);
                    coordinate.setStatus(PolygonSchedulerCoordinateStatus.DONE);
                    coordinate.setFinishedAt(Instant.now());
                    coordinate.setNumberOfPlaces(palindromeCreationResult.getNumberOfPlaces());
                    coordinate.setNumberOfPolygons(palindromeCreationResult.getNumberOfPolygons());
                    coordinate.setTimeInSec(timeInSec);

                    //Delete trigger coordinate around
//                    polygonSchedulerCoordinateService.updateCoordinateAround(coordinate);

//                    addExtraCalculationForCheckin(coordinate);
                } catch (Exception e) {
                    log.error("Error occurred during polygon calculation: ", e);
                    coordinate.setStatus(PolygonSchedulerCoordinateStatus.ERROR);
                    coordinate.setFinishedAt(Instant.now());
                }

                polygonSchedulerCoordinateRepository.save(coordinate);
            }
        }
    }



    private void addExtraCalculationForCheckin(
            PolygonSchedulerCoordinate coordinate) {
        boolean isNeedExtraCheckin = coordinate.getType().equals(PolygonSchedulerCoordinateType.CHECKIN) && coordinate.getRadius() != PolygonCalculationUtils.RADIUS_KM_CHECK_IN;
        if (isNeedExtraCheckin) {
            PolygonSchedulerCoordinate polygonSchedulerCoordinate = PolygonCalculationUtils.createPolygonSchedulerCoordinate(
                    coordinate.getLongitude(),
                    coordinate.getLatitude(),
                    PolygonCalculationUtils.RADIUS_KM_CHECK_IN,
                    PolygonSchedulerCoordinateType.TRIGGER
            );
            PolygonSchedulerCoordinate savedPoint = polygonSchedulerCoordinateRepository.save(polygonSchedulerCoordinate);
            log.info("Type: {}. saved coordinates {} for extra check in", coordinate.getType(), savedPoint.getPolygonSchedulerCoordinateKey());
        }
    }
}
