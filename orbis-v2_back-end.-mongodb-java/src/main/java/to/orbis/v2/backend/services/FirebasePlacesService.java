package to.orbis.v2.backend.services;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.configuration.FirebaseConfigurationOptions;
import to.orbis.v2.backend.mappers.PlaceMapper;
import to.orbis.v2.backend.models.entity.Place;
import to.orbis.v2.backend.utils.GeoHashUtils;

@Service
@RequiredArgsConstructor
public class FirebasePlacesService {

    PlaceMapper placeMapper;
    FirebaseConfigurationOptions options;
    FirebaseIndexService indexService;

    public Mono<Place> save(Place place) {

        val db = FirebaseDatabase.getInstance(options.getDatabaseUrl());

        final String geohash = GeoHashUtils.encodeHash(place.getCoordinates());
        final DatabaseReference placeReference = db.getReference(String.format("/placeSizes/%s/%s",
                geohash, place.getPlaceKey()));

        placeReference.setValueAsync(placeMapper.placeToFirebasePlace(place));

        return indexService.ensureIndex(geohash).thenReturn(place);
    }
}
