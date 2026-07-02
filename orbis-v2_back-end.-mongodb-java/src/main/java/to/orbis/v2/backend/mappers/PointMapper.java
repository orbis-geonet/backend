package to.orbis.v2.backend.mappers;

import com.google.maps.model.LatLng;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import to.orbis.v2.backend.models.dto.PointDto;

@Mapper(componentModel = "spring")
public interface PointMapper {

    @Mapping(source = "x", target = "longitude")
    @Mapping(source = "y", target = "latitude")
    PointDto geoJsonPointToPoint(GeoJsonPoint geoJsonPoint);

    default GeoJsonPoint latLngToGeoJsonPoint(LatLng latLng) {
        if (latLng == null) {
            return null;
        }

        return new GeoJsonPoint(latLng.lng, latLng.lat);
    }

    default GeoJsonPoint pointToGeiGeoJsonPoint(PointDto pointDto) {
        if (pointDto == null) {
            return null;
        }
        else {
            return new GeoJsonPoint(pointDto.getLongitude(), pointDto.getLatitude());
        }
    }
}
