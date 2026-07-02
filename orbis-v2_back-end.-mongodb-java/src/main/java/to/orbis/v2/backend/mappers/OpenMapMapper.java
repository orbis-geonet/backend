package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.backend.models.dto.openstreetmap.*;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface OpenMapMapper {

    @Mapping(target = "city", source = "city")
    @Mapping(target = "cityDistrict", source = "district")
    @Mapping(target = "suburb", source = "district")
    @Mapping(target = "municipality", source = "district")
    @Mapping(target = "county", source = "country")
    @Mapping(target = "country", source = "country")
    @Mapping(target = "stateDistrict", source = "state")
    PlaceInfoDto toPlaceInfo(PhotonProperties source);
}
