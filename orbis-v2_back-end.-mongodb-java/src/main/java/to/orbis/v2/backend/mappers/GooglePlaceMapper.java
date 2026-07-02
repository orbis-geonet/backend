package to.orbis.v2.backend.mappers;

import com.google.appengine.repackaged.com.google.common.collect.Lists;
import com.google.code.geocoder.model.GeocoderAddressComponent;
import com.google.code.geocoder.model.GeocoderResult;
import com.google.maps.model.*;
import lombok.val;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.backend.models.PlaceType;
import to.orbis.v2.backend.models.entity.Place;
import to.orbis.v2.backend.models.entity.PlaceAddress;

import java.util.Arrays;
import java.util.List;

@Mapper(componentModel = "spring", uses = {PointMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface GooglePlaceMapper {

    @Mapping(target = "id", expression = "java(new org.bson.types.ObjectId())")
    @Mapping(target = "coordinates", source = "geometry.location")
    @Mapping(target = "placeKey", expression = "java(place.getId().toHexString())")
    @Mapping(target = "type", source = "types")
    @Mapping(target = "userCreatedKey", ignore = true)
    @Mapping(target = "source", constant = "GOOGLE")
    @Mapping(target = "address", source = "formattedAddress")
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "categoryKey", ignore = true)
    @Mapping(target = "cityKey", ignore = true)
    @Mapping(target = "countryKey" ,ignore = true)
    @Mapping(target = "csvHash", ignore = true)
    @Mapping(target = "csvUrl", ignore = true)
    @Mapping(target = "phone", ignore = true)
    @Mapping(target = "website", ignore = true)
    @Mapping(target = "workingHours", ignore = true)
    @Mapping(target = "lastCheckInTimestamp", ignore = true)
    @Mapping(target = "lastSizeChangeTimestamp", ignore = true)
    @Mapping(target = "dominantGroupKey", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "creationServerTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "groupCreatedKey", ignore = true)
    @Mapping(target = "googlePlaceId", source = "placeId")
    @Mapping(target = "lastSize", constant = "0")
    @Mapping(target = "imageName", ignore = true)
    @Mapping(target = "createTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "reported", ignore = true)
    @Mapping(target = "shareLink", ignore = true)
    @Mapping(target = "fullShareLink", ignore = true)
    @Mapping(target = "reportedMessage", ignore = true)
    @Mapping(target = "reportedSolved", ignore = true)
    @Mapping(target = "reportedTime", ignore = true)
    Place googlePlaceToPlace(PlacesSearchResult placesSearchResult);

    @Mapping(target = "id", expression = "java(new org.bson.types.ObjectId())")
    @Mapping(target = "coordinates", source = "geometry.location")
    @Mapping(target = "placeKey", expression = "java(place.getId().toHexString())")
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "userCreatedKey", ignore = true)
    @Mapping(target = "source", constant = "GOOGLE")
    @Mapping(target = "address", source = "formattedAddress")
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "categoryKey", ignore = true)
    @Mapping(target = "cityKey", ignore = true)
    @Mapping(target = "countryKey" ,ignore = true)
    @Mapping(target = "csvHash", ignore = true)
    @Mapping(target = "csvUrl", ignore = true)
    @Mapping(target = "phone", ignore = true)
    @Mapping(target = "website", ignore = true)
    @Mapping(target = "workingHours", ignore = true)
    @Mapping(target = "lastCheckInTimestamp", ignore = true)
    @Mapping(target = "lastSizeChangeTimestamp", ignore = true)
    @Mapping(target = "dominantGroupKey", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "creationServerTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "groupCreatedKey", ignore = true)
    @Mapping(target = "googlePlaceId", source = "placeId")
    @Mapping(target = "lastSize", constant = "0")
    @Mapping(target = "imageName", ignore = true)
    @Mapping(target = "createTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "reported", ignore = true)
    @Mapping(target = "shareLink", ignore = true)
    @Mapping(target = "fullShareLink", ignore = true)
    @Mapping(target = "reportedMessage", ignore = true)
    @Mapping(target = "reportedSolved", ignore = true)
    @Mapping(target = "reportedTime", ignore = true)
    @Mapping(target = "googleAddress", source = "addressComponents")
    Place placeDetailsToPlace(PlaceDetails placeDetails);

    @Mapping(target = "id", expression = "java(new org.bson.types.ObjectId())")
    @Mapping(target = "coordinates", ignore = true)
    @Mapping(target = "placeKey", expression = "java(place.getId().toHexString())")
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "userCreatedKey", ignore = true)
    @Mapping(target = "source", constant = "GOOGLE")
    @Mapping(target = "address", source = "formattedAddress")
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "categoryKey", ignore = true)
    @Mapping(target = "cityKey", ignore = true)
    @Mapping(target = "countryKey" ,ignore = true)
    @Mapping(target = "csvHash", ignore = true)
    @Mapping(target = "csvUrl", ignore = true)
    @Mapping(target = "phone", ignore = true)
    @Mapping(target = "website", ignore = true)
    @Mapping(target = "workingHours", ignore = true)
    @Mapping(target = "lastCheckInTimestamp", ignore = true)
    @Mapping(target = "lastSizeChangeTimestamp", ignore = true)
    @Mapping(target = "dominantGroupKey", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "creationServerTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "groupCreatedKey", ignore = true)
    @Mapping(target = "googlePlaceId", source = "placeId")
    @Mapping(target = "lastSize", constant = "0")
    @Mapping(target = "imageName", ignore = true)
    @Mapping(target = "createTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "reported", ignore = true)
    @Mapping(target = "shareLink", ignore = true)
    @Mapping(target = "reportedMessage", ignore = true)
    @Mapping(target = "reportedSolved", ignore = true)
    @Mapping(target = "reportedTime", ignore = true)
    @Mapping(target = "googleAddress", source = "addressComponents")
    Place geocoderResultToPlace(GeocodingResult geocoderResult);

//    default PlaceAddress googleAddressToPlaceAddress(List<GeocoderAddressComponent> addressComponents) {
//        return PlaceAddress.builder()
//                .country(getAddressPart(addressComponents, List.of("country")))
//                .city(getAddressPart(addressComponents, List.of("locality", "administrative_area_level_2")))
//                .street(getAddressPart(addressComponents, List.of("street_address", "route")))
//                .neighberhood(getAddressPart(addressComponents, List.of("sublocality", "sublocality_level_1", "sublocality_level_2", "sublocality_level_3", "neighborhood")))
//                .number(getAddressPart(addressComponents, List.of("street_number")))
//                .postalCode(getAddressPart(addressComponents, List.of("postal_code")))
//                .build();
//    }

    default PlaceAddress googleAddressToPlaceAddress(AddressComponent[] addressComponents) {
        return PlaceAddress.builder()
                .country(getAddressPart(addressComponents, List.of(AddressComponentType.COUNTRY)))
                .city(getAddressPart(addressComponents, List.of(AddressComponentType.LOCALITY, AddressComponentType.ADMINISTRATIVE_AREA_LEVEL_2)))
                .street(getAddressPart(addressComponents, List.of(AddressComponentType.STREET_ADDRESS, AddressComponentType.ROUTE)))
                .neighberhood(getAddressPart(addressComponents, List.of(AddressComponentType.SUBLOCALITY_LEVEL_1, AddressComponentType.SUBLOCALITY_LEVEL_2, AddressComponentType.SUBLOCALITY_LEVEL_3, AddressComponentType.NEIGHBORHOOD)))
                .number(getAddressPart(addressComponents, List.of(AddressComponentType.STREET_NUMBER)))
                .postalCode(getAddressPart(addressComponents, List.of(AddressComponentType.POSTAL_CODE)))
                .build();
    }

    private String getAddressPart(List<GeocoderAddressComponent> components, List<String> types) {
        return components
                .stream()
                .filter(component -> component.getTypes().stream().anyMatch(types::contains))
                .map(GeocoderAddressComponent::getLongName)
                .findFirst()
                .orElse("");
    }

    private String getAddressPart(AddressComponent[] components, List<AddressComponentType> types) {
        return Arrays.stream(components)
                .filter(component -> Arrays.stream(component.types).anyMatch(types::contains))
                .map(component -> component.longName)
                .findFirst()
                .orElse("");
    }

    default PlaceType googlePlaceTypeToType(String[] types) {
        if(types == null || types.length == 0) {
            return PlaceType.LOCATION;
        }

        val type = types[0];

        switch (type) {

            case "accounting":
            case "city_hall":
            case "courthouse":
            case "embassy":
            case "hospital":
            case "local_government_office":
            case "police":
            case "post_office":
                return PlaceType.TWO_BUILDINGS;
            case "airport":
            case "atm":
            case "fire_station":
            case "gas_station":
            case "parking":
            case "storage":
            case "subway_station":
            case "taxi_stand":
            case "train_station":
            case "transit_station":
                return PlaceType.BUILDING;
            case "amusement_park":
            case "aquarium":
            case "campground":
            case "park":
            case "rv_park":
                return PlaceType.PARK;
            case "art_gallery":
            case "cemetery":
            case "church":
            case "funeral_home":
            case "hindu_temple":
            case "library":
            case "mosque":
            case "museum":
            case "synagogue":
                return PlaceType.CASTLE;
            case "bakery":
            case "cafe":
            case "restaurant":
                return PlaceType.RESTAURANT;
            case "bank":
            case "florist":
                return PlaceType.HOUSE;
            case "bar":
                return PlaceType.BAR;
            case "beauty_salon":
            case "car_dealer":
            case "car_rental":
            case "car_repair":
            case "car_wash":
            case "dentist":
            case "doctor":
            case "electrician":
            case "hair_care":
            case "insurance_agency":
            case "laundry":
            case "lawyer":
            case "moving_company":
            case "painter":
            case "pharmacy":
            case "physiotherapist":
            case "plumber":
            case "real_estate_agency":
            case "roofing_contractor":
            case "travel_agency":
            case "veterinary_care":
                return PlaceType.HOUSE_2;
            case "bicycle_store":
            case "book_store":
            case "bowling_alley":
            case "casino":
            case "clothing_store":
            case "convenience_store":
            case "department_store":
            case "electronics_store":
            case "furniture_store":
            case "hardware_store":
            case "home_goods_store":
            case "jewelry_store":
            case "liquor_store":
            case "pet_store":
            case "shoe_store":
            case "shopping_mall":
            case "store":
            case "supermarket":
                return PlaceType.SHOPPING;
            case "bus_station":
            case "locksmith":
            case "lodging":
            case "movie_rental":
            case "movie_theater":
            case "zoo":
                return PlaceType.LOCATION;
            case "gym":
            case "spa":
            case "stadium":
                return PlaceType.SPORTS_CENTER;
            case "meal_delivery":
            case "meal_takeaway":
                return PlaceType.FAST_FOOD;
            case "night_club":
                return PlaceType.MUSIC;
            case "school":
                return PlaceType.SCHOOL;
        }

        return PlaceType.LOCATION;
    }
}
