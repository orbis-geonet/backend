package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.backend.models.dto.ExtendedPlaceDto;
import to.orbis.v2.backend.models.dto.PlacePalindromeCreationDto;
import to.orbis.v2.backend.models.dto.PlaceDto;
import to.orbis.v2.backend.models.dto.PrimitivePlaceDto;
import to.orbis.v2.backend.models.entity.ExtendedPlace;
import to.orbis.v2.backend.models.entity.Place;
import to.orbis.v2.backend.models.entity.PrimitivePlace;
import to.orbis.v2.backend.models.firebase.FirebasePlace;
import to.orbis.v2.backend.models.requests.places.CreatePlaceRequest;
import to.orbis.v2.backend.models.requests.places.UpdatePlaceRequest;

import java.time.Instant;
import java.util.Objects;

@Mapper(componentModel = "spring", uses = {PointMapper.class, GroupMapper.class, UserMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PlaceMapper {

    @Mapping(target = "size", expression = "java(place.currentSize())")
    PlaceDto placeToPlaceDto(Place place);

    PrimitivePlaceDto primitivePlaceToPrimitivePlaceDto(PrimitivePlace primitivePlace);

    @Mapping(target = "competingGroups", ignore = true)
    @Mapping(target = "dominantGroup", ignore = true)
    @Mapping(target = "dist", ignore = true)
    @Mapping(target = "following", ignore = true)
    ExtendedPlace placeToExtendedPlace(Place place);

    @Mapping(target = "size", expression = "java(place.currentSize())")
    ExtendedPlaceDto extendedPlaceToExtendedPlaceDto(ExtendedPlace place);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "placeKey", ignore = true)
    @Mapping(target = "userCreatedKey", ignore = true)
    @Mapping(target = "csvHash", ignore = true)
    @Mapping(target = "csvUrl", ignore = true)
    @Mapping(target = "lastCheckInTimestamp", ignore = true)
    @Mapping(target = "lastSizeChangeTimestamp", ignore = true)
    @Mapping(target = "deleted", expression = "java(false)")
    @Mapping(target = "creationServerTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "createTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "lastSize", constant = "0.0")
    @Mapping(target = "dominantGroupKey", ignore = true)
    @Mapping(target = "reported", ignore = true)
    @Mapping(target = "shareLink", ignore = true)
    @Mapping(target = "fullShareLink", ignore = true)
    @Mapping(target = "reportedMessage", ignore = true)
    @Mapping(target = "reportedSolved", ignore = true)
    @Mapping(target = "reportedTime", ignore = true)
    @Mapping(target = "totalRate", constant = "0.0")
    @Mapping(target = "countRates", constant = "0")
    Place cratePlaceRequestToPlace(CreatePlaceRequest place);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "placeKey", ignore = true)
    @Mapping(target = "userCreatedKey", ignore = true)
    @Mapping(target = "csvHash", ignore = true)
    @Mapping(target = "csvUrl", ignore = true)
    @Mapping(target = "lastCheckInTimestamp", ignore = true)
    @Mapping(target = "lastSizeChangeTimestamp", ignore = true)
    @Mapping(target = "creationServerTimestamp", ignore = true)
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "groupCreatedKey", ignore = true)
    @Mapping(target = "lastSize", ignore = true)
    @Mapping(target = "createTimestamp", ignore = true)
    @Mapping(target = "reported", ignore = true)
    @Mapping(target = "shareLink", ignore = true)
    @Mapping(target = "fullShareLink", ignore = true)
    @Mapping(target = "totalRate", ignore = true)
    @Mapping(target = "countRates", ignore = true)
    Place updatePlaceRequestToPlace(UpdatePlaceRequest place);

    FirebasePlace placeToFirebasePlace(Place place);

    default Double calculateAverageRate(Place place) {
        return Objects.isNull(place.getCountRates()) || place.getCountRates() == 0 || Objects.isNull(place.getTotalRate()) ?
                0 : place.getTotalRate() / place.getCountRates();
    }

    default Long toTimestampMillis(Instant instant) {

        if (instant == null) {
            // epoch millis of 2019-01-01
            return 1546300800000L;
        }

        return instant.toEpochMilli();
    }

    PlacePalindromeCreationDto extendedPlaceDtoToExtendedPlacePalindromeDto(ExtendedPlaceDto extendedPlaceDto);
}
