package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import to.orbis.v2.backend.models.dto.ExtendedPlaceDto;
import to.orbis.v2.backend.models.dto.PlacePalindromeCreationDto;
import to.orbis.v2.backend.models.dto.PlacePalindromeDto;
import to.orbis.v2.backend.models.entity.Polygon;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {PointMapper.class, GroupMapper.class, UserMapper.class}, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PolygonMapper {

    @Mapping(target = "id", expression = "java(new org.bson.types.ObjectId())")
    @Mapping(target = "polygonKey", expression = "java(polygon.getId().toHexString())")
    @Mapping(target = "groupKey", source = "dominantGroupKey")
    @Mapping(target = "placeKeys", source = "placeKeys")
    @Mapping(target = "polygonCenter", source = "polygonCenter")
    @Mapping(target = "polygonPoints", source = "polygonPoints")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    Polygon placePalindromeDtoToPolygon(PlacePalindromeCreationDto placePalindromeDto);

    @Mapping(target = "places", source = "placeKeys", ignore = true) // We'll handle this manually
    PlacePalindromeDto polygonToPlacePalindromeDto(Polygon polygon);

    /**
     * Should ba called after the PlacePalindromeDto.places have been calculated manually.
     */
    default void setAdditionalFieldsForPlacePalindromeDto(PlacePalindromeDto dto, Polygon polygon) {
        if (dto.getPlaces() != null && !dto.getPlaces().isEmpty()) {
            ExtendedPlaceDto firstPlace = dto.getPlaces().get(0);

            dto.setCoordinates(polygon.getPolygonCenter());
            dto.setName(firstPlace.getName());
            dto.setPlaceKey(firstPlace.getPlaceKey());
            dto.setLastCheckInTimestamp(firstPlace.getLastCheckInTimestamp());
            dto.setLastSizeChangeTimestamp(firstPlace.getLastSizeChangeTimestamp());
            dto.setDominantGroupKey(firstPlace.getDominantGroupKey());
            dto.setSize(firstPlace.getSize());
            dto.setDominantGroup(firstPlace.getDominantGroup());
        }
    }

    @Named("mapGroupKey")
    default String mapGroupKey(PlacePalindromeCreationDto placePalindromeCreationDto) {
        if (placePalindromeCreationDto.getPlaces() != null && !placePalindromeCreationDto.getPlaces().isEmpty()) {
            return placePalindromeCreationDto.getPlaces().get(0).getDominantGroup().getGroupKey();
        }
        return null; // or throw an exception, or use a default value
    }

    @Named("mapPlaceKeys")
    default List<String> mapPlaceKeys(List<ExtendedPlaceDto> places) {
        return places.stream()
                .map(ExtendedPlaceDto::getPlaceKey)
                .collect(Collectors.toList());
    }
}