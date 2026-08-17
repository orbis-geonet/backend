package to.orbis.v2.polygons.creator.repositories;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import to.orbis.v2.polygons.creator.models.entity.ExtendedPlace;
import to.orbis.v2.polygons.creator.models.entity.Place;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
@RequiredArgsConstructor
public class PlacesAggregationsRepository {

    private final MongoTemplate mongoTemplate;

    public List<ExtendedPlace> findByCoordinatesNear(
            GeoJsonPoint point, Double distance, boolean onlyVisible, Pageable pageable) {

        final AggregationOperation[] ops = prepareAggregationOps(point, distance, onlyVisible, pageable);

        return fetchExtendedPlaces(ops);
    }

    private AggregationOperation[] prepareAggregationOps(
            GeoJsonPoint point,
            Double distance,
            boolean onlyVisible,
            Pageable pageable) {

        val query = NearQuery.near(point).inKilometers();

        val withLimit = distance == null ? query
                : query.maxDistance(new Distance(distance, Metrics.KILOMETERS));

        val criteria = Stream.of(
                Optional.of(onlyVisible).filter(v -> v).stream().flatMap(z -> Stream.of(
                        Criteria.where("dominantGroupKey").exists(true),
//                        Criteria.where("lastSize").gt(0),
                        Criteria.where("lastCheckInTimestamp").gte(Instant.now().minus(365, ChronoUnit.DAYS))
                )))
                .flatMap(Function.identity()).collect(Collectors.toList());


        val withQuery = !criteria.isEmpty()
                ? withLimit.query(Query.query(new Criteria().andOperator(criteria)))
                : withLimit;

        val mayBePageable = Optional.ofNullable(pageable).filter(Pageable::isPaged);

        return Stream.of(
                        Stream.of(geoNear(withQuery, "dist").useIndex("coordinates")),
                        mayBePageable.stream().flatMap(p -> Stream.of(skip(p.getOffset()),
                                limit(p.getPageSize())))
                ).flatMap(Function.identity())
                .toArray(AggregationOperation[]::new);
    }

    public void updateSizeForPlaces(List<String> placeKeys) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Place.Fields.placeKey.name()).in(placeKeys));

        Update update = new Update();
        update.set(Place.Fields.lastSize.name(), 0);
        update.set(Place.Fields.timestamp.name(), Instant.now());

        mongoTemplate.updateMulti(query, update, Place.class);
    }

    private List<ExtendedPlace> fetchExtendedPlaces(AggregationOperation... ops) {
        val aggregation = newAggregation(
                Stream.concat(Arrays.stream(ops),
                        Stream.of(
                                lookup("groups", "dominantGroupKey", "groupKey", "dominantGroup"),
                                unwind("dominantGroup", true),
                                project(ExtendedPlace.class)
                        )).toArray(AggregationOperation[]::new)
        );
        return mongoTemplate.aggregate(aggregation, "places", ExtendedPlace.class)
                .getMappedResults();
    }
}
