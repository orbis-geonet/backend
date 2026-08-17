package to.orbis.v2.polygons.creator.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import to.orbis.v2.polygons.creator.models.entity.Polygon;
import to.orbis.v2.polygons.creator.repositories.PolygonRepository;
import to.orbis.v2.polygons.creator.utils.PolygonCalculationUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolygonService {
    private final PolygonRepository polygonRepository;

    public List<Polygon> getPolygons(
            double longitude, double latitude, double radius
    ) {
        return polygonRepository.findByPolygonCenterWithinRadius(
                longitude, latitude, radius / PolygonCalculationUtils.EARTH_RADIUS_KM
        );
    }

}
