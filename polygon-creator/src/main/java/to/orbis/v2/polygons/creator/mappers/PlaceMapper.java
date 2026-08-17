package to.orbis.v2.polygons.creator.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.polygons.creator.models.dto.ExtendedPlaceDto;
import to.orbis.v2.polygons.creator.models.dto.PlaceForPalindromeCreationDto;
import to.orbis.v2.polygons.creator.models.entity.ExtendedPlace;

@Mapper(componentModel = "spring", uses = {PointMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PlaceMapper {
    @Mapping(target = "size", expression = "java(place.currentSize())")
    @Mapping(target = "tangentPoints", ignore = true)
    @Mapping(target = "connectingPlacesByPair", ignore = true)
    @Mapping(target = "circleDto", ignore = true)
    @Mapping(target = "dominantGroup", ignore = true)
    @Mapping(target = "circle", ignore = true)
    @Mapping(target = "palindromeGroupKeys", ignore = true)
    ExtendedPlaceDto extendedPlaceToExtendedPlaceDto(ExtendedPlace place);

    @Mapping(target = "placeKey", source = "placeKey")
    @Mapping(target = "size", source = "size")
    @Mapping(target = "coordinates", source = "coordinates")
    @Mapping(target = "timestamp", source = "timestamp")
    @Mapping(target = "dominantGroupKey", source = "dominantGroupKey")
    @Mapping(target = "dominantGroup", source = "dominantGroup")
    @Mapping(target = "tangentPoints", expression = "java(new java.util.HashSet<>())")
    @Mapping(target = "connectingPlacesByPair", expression = "java(new java.util.ArrayList<>())")
    PlaceForPalindromeCreationDto toPlaceForPalindromeCreationDto(ExtendedPlaceDto placeDtoList);
}
