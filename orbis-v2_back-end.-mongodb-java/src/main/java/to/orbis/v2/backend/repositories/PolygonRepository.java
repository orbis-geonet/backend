package to.orbis.v2.backend.repositories;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Polygon;

import java.util.List;

public interface PolygonRepository extends ReactiveMongoRepository<Polygon, String> {

    @Query("{ 'groupKey': ?3, 'polygonCenter': { $geoWithin: { $centerSphere: [ [ ?0, ?1 ], ?2 ] } } }")
    Flux<Polygon> findPolygonsByLocationAndGroupKey(double longitude, double latitude, double radiusInRadians, String groupKey);

    @Query("{ 'polygonCenter': { $geoWithin: { $centerSphere: [ [ ?0, ?1 ], ?2 ] } } }")
    Flux<Polygon> findPolygonsByLocation(double longitude, double latitude, double radiusInRadians);

    @Query("{ 'placeKeys': { $in: [?0] } }")
    Flux<Polygon> findByPlaceKey(String placeKey);

    @Query("{ 'placeKeys': { $in: ?0 } }")
    Mono<Void> deleteAllByPlaceKeys(List<String> placeKeys);

    /**
     * Returns a polygon by center with some kind of tolerance (e.g 0.000001)
     */
    @Query("{ 'polygonCenter.latitude': { $gte: ?0, $lte: ?1 }, 'polygonCenter.longitude': { $gte: ?2, $lte: ?3 } }")
    Mono<Polygon> findByPolygonCenterWithTolerance(double minLat, double maxLat, double minLon, double maxLon);

    @Query("{ 'polygonCenter': { $geoWithin: { $centerSphere: [ [ ?0, ?1 ], ?2 ] } } }")
    Flux<Polygon> findByPolygonCenterWithinRadius(double longitude, double latitude, double radius);

    @Query(value = "{ 'polygonCenter': { $geoWithin: { $centerSphere: [ [ ?0, ?1 ], ?2 ] } } }", delete = true)
    Mono<Void> deleteByPolygonCenterWithinRadius(double longitude, double latitude, double radiusInRadians);

}
