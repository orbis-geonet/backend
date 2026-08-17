package to.orbis.v2.polygons.creator.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.polygons.creator.models.dto.PlacePalindromeCreationDto;
import to.orbis.v2.polygons.creator.models.entity.Polygon;

@Mapper(componentModel = "spring", uses = {PointMapper.class}, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PolygonMapper {

    @Mapping(target = "id", expression = "java(new org.bson.types.ObjectId())")
    // Content-addressed identity: the same place-set yields the same key on every clone
    @Mapping(target = "polygonKey", expression = "java(to.orbis.v2.polygons.creator.utils.PolygonKeyUtils.deterministicKey(placePalindromeDto.getPlaceKeys()))")
    @Mapping(target = "groupKey", source = "dominantGroupKey")
    @Mapping(target = "placeKeys", source = "placeKeys")
    @Mapping(target = "polygonCenter", source = "polygonCenter")
    @Mapping(target = "polygonPoints", source = "polygonPoints")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    Polygon placePalindromeDtoToPolygon(PlacePalindromeCreationDto placePalindromeDto);
}