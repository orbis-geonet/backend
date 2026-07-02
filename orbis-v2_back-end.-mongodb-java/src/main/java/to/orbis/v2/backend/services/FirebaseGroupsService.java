package to.orbis.v2.backend.services;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.configuration.FirebaseConfigurationOptions;
import to.orbis.v2.backend.mappers.GroupMapper;
import to.orbis.v2.backend.models.dto.PointDto;
import to.orbis.v2.backend.models.entity.ExtendedGroup;
import to.orbis.v2.backend.models.entity.Place;
import to.orbis.v2.backend.utils.GeoHashUtils;

@Service
@RequiredArgsConstructor
public class FirebaseGroupsService {

    FirebaseConfigurationOptions options;
    GroupMapper groupMapper;

    public Mono<ExtendedGroup> save(Place place, ExtendedGroup group) {

        val db = FirebaseDatabase.getInstance(options.getDatabaseUrl());

        final DatabaseReference placeReference = db.getReference(String.format("/groupOwnership/%s/%s",
                GeoHashUtils.encodeHash(place.getCoordinates()),
                place.getPlaceKey()));

        placeReference.setValueAsync(groupMapper.groupToFirebaseGroup(group)
                .setPlaceKey(place.getPlaceKey())
                .setPlaceCoordinates(
                        PointDto.builder()
                                .longitude(place.getCoordinates().getX())
                                .latitude(place.getCoordinates().getY())
                                .build()
                )
        );


        return Mono.just(group);
    }
}
