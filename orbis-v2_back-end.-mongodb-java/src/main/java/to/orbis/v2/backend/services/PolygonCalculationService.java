package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.mappers.GroupMapper;
import to.orbis.v2.backend.mappers.PlaceMapper;
import to.orbis.v2.backend.mappers.PolygonMapper;
import to.orbis.v2.backend.models.dto.PlacePalindromeDto;
import to.orbis.v2.backend.models.dto.ExtendedPlaceDto;
import to.orbis.v2.backend.models.entity.ExtendedPlace;
import to.orbis.v2.backend.models.entity.Polygon;
import to.orbis.v2.backend.repositories.GroupsRepository;
import to.orbis.v2.backend.repositories.PlacesRepository;
import to.orbis.v2.backend.repositories.PolygonRepository;
import to.orbis.v2.backend.repositories.PolygonsAggregationRepository;
import to.orbis.v2.backend.utils.PolygonCalculationUtils;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PolygonCalculationService {

    PolygonRepository polygonRepository;
    PolygonMapper polygonMapper;
    PlacesRepository placesRepository;
    PlaceMapper placeMapper;
    GroupsRepository groupsRepository;
    GroupMapper groupMapper;
    PolygonsAggregationRepository polygonsAggregationRepository;

    /**
     * Fetches Polygons from the database and returns them mapped to List<PlacePalindromeDto>
     */
    public Mono<List<PlacePalindromeDto>> findPolygons(
            double latitude, double longitude, String groupKey, double distanceInKm) {
        double radiusInRadians = distanceInKm / PolygonCalculationUtils.EARTH_RADIUS_KM;

        Mono<List<Polygon>> polygonsMono;
        if (groupKey != null && !groupKey.isEmpty()) {
            long start = System.currentTimeMillis();
            polygonsMono = polygonRepository.findPolygonsByLocationAndGroupKey(longitude, latitude, radiusInRadians, groupKey).collectList()
                    .doFinally(signal -> {
                        long executionTime = System.currentTimeMillis() - start;
                        log.info("findPolygonsByLocationAndGroupKey executed in {} ms", executionTime);
                    });
        } else {
            long start = System.currentTimeMillis();
            polygonsMono = polygonRepository.findPolygonsByLocation(longitude, latitude, radiusInRadians).collectList()
                    .doFinally(signal -> {
                        long executionTime = System.currentTimeMillis() - start;
                        log.info("findPolygonsByLocation executed in {} ms", executionTime);
                    });
        }

        return polygonsMono.flatMap(polygons ->
                Flux.fromIterable(polygons)
                        .flatMap(polygon ->
                                mapPlaceKeysToExtendedPlaceDtos(polygon.getPlaceKeys())
                                        .collectList() // Collect ExtendedPlaceDtos into a List
                                        .map(places -> {
                                            PlacePalindromeDto dto = polygonMapper.polygonToPlacePalindromeDto(polygon);
                                            dto.setPlaces(places);
                                            polygonMapper.setAdditionalFieldsForPlacePalindromeDto(dto, polygon);
                                            return dto;
                                        })
                        )
                        .collectList()
        );
    }

    /**
     * Pageable version of findPolygons
     */
    public Flux<PlacePalindromeDto> findPolygonsPageable(
            GeoJsonPoint point, Double distance, Pageable pageable) {
        return polygonsAggregationRepository.findPolygonsByLocation(point, distance, pageable)
                .flatMap(polygon -> mapPlaceKeysToExtendedPlaceDtos(polygon.getPlaceKeys())
                        .collectList()
                        .map(places -> {
                            PlacePalindromeDto dto = polygonMapper.polygonToPlacePalindromeDto(polygon);
                            dto.setPlaces(places);
                            dto.setPalindromeKey(polygon.getId().toHexString());
                            polygonMapper.setAdditionalFieldsForPlacePalindromeDto(dto, polygon);
                            return dto;
                        }));
    }

    public Mono<PlacePalindromeDto> findByPlaceKey(String placeKey) {
        return polygonRepository.findByPlaceKey(placeKey).next()
                .flatMap(polygon -> mapPlaceKeysToExtendedPlaceDtos(polygon.getPlaceKeys())
                        .collectList()
                        .map(places -> {
                            PlacePalindromeDto dto = polygonMapper.polygonToPlacePalindromeDto(polygon);
                            dto.setPlaces(places);
                            polygonMapper.setAdditionalFieldsForPlacePalindromeDto(dto, polygon);
                            return dto;
                        }));
    }

    private Flux<ExtendedPlaceDto> mapPlaceKeysToExtendedPlaceDtos(List<String> placeKeys) {
        return Flux.fromIterable(placeKeys)
                .flatMap(placeKey ->
                        placesRepository.findOneByPlaceKey(placeKey)
                                .flatMap(place -> {
                                    ExtendedPlace extendedPlace = placeMapper.placeToExtendedPlace(place);

                                    // Get the dominant group as a Mono<Group>
                                    return groupsRepository.findOneByGroupKeyAndDeletedFalse(extendedPlace.getDominantGroupKey())
                                            .map(dominantGroup -> {
                                                extendedPlace.setDominantGroup(groupMapper.groupToSimplifiedGroup(dominantGroup));
                                                return placeMapper.extendedPlaceToExtendedPlaceDto(extendedPlace);
                                            });
                                })
                );
    }
}
