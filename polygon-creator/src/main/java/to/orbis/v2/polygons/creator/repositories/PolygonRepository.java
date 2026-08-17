package to.orbis.v2.polygons.creator.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import to.orbis.v2.polygons.creator.models.entity.Polygon;

import java.util.List;

public interface PolygonRepository extends MongoRepository<Polygon, String> {

    @Query("{ 'polygonCenter': { $geoWithin: { $centerSphere: [ [ ?0, ?1 ], ?2 ] } } }")
    List<Polygon> findByPolygonCenterWithinRadius(double longitude, double latitude, double radius);

}
