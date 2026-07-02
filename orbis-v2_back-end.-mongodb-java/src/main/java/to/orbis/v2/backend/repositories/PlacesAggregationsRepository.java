package to.orbis.v2.backend.repositories;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.ExtendedPlace;
import to.orbis.v2.backend.models.entity.Group;
import to.orbis.v2.backend.models.entity.Place;
import to.orbis.v2.backend.models.entity.PlaceRate;
import to.orbis.v2.backend.repositories.queries.GroupQuery;
import to.orbis.v2.backend.utils.AggregationUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

    ReactiveMongoTemplate mongoTemplate;

    public Mono<ExtendedPlace> findOneByPlaceKeyWithCompeting(String placeKey, Optional<String> userKey) {
        /*
        db.getCollection('places').aggregate([
! {$match: {placeKey: "b01cd846bea9887f0b534d2c4aec166d"}},
{$lookup: {from: "checkins", let: {pk: "$placeKey", dt: ISODate("2021-04-01T00:00:00Z")}, pipeline: [
      {$match:
          {$expr: {$and: [
              {$eq: ["$placeKey", "$$pk"]},
              {$gte: ["$validTimestamp", "$$dt"]}]}}},
      {$group: {_id: "$groupKey", validCheckins: {$sum: 1}}},
      {$lookup: {from: "groups", localField: "_id", foreignField: "groupKey", as: "group"}},
      {$unwind: "$group"},
      {$addFields: {"group.validCheckins": "$validCheckins"}},
      {$replaceRoot: {newRoot: "$group"}}
    ], as: "checkins"}},
{$addFields: {
    "competingGroups":  "$checkins"}},
])
        */

        List<AggregationOperation> aggregationOperationList = new ArrayList<>();
        aggregationOperationList.add(match(Criteria.where("placeKey").is(placeKey)));
        if (userKey.isPresent()) {
            aggregationOperationList.add(
                    new FreeFormOperation(
                            AggregationUtils.LOOK_UP, "{\n" +
                            "                            \"from\" : \"placeRates\",\n" +
                            "                            \"let\" : { \"placeKey\" : \"$placeKey\"},\n" +
                            "                            \"pipeline\" : [\n" +
                            "                                { \"$match\" : { \"$expr\" :\n" +
                            "                                            {\"$and\" : [{ \"$eq\" : [\"$userKey\", \"" + userKey.get() + "\"]}, { \"$eq\" : [\"$placeKey\", \"" + placeKey +"\"]}]}" +
                            "                                }}\n" +
                            "                            ],\n" +
                            "                            \"as\" : \"rate\"}"
                    )
            );
            aggregationOperationList.add(unwind("rate", true));
            aggregationOperationList.add(
                    new FreeFormOperation(
                            AggregationUtils.ADD_FIELDS, "{\"userRate\": \"$rate.userRate\"}"
                    )
            );
            aggregationOperationList.add(
                    new FreeFormOperation(
                            AggregationUtils.ADD_FIELDS, "{\"canEdit\":\"true\"}"
                    )
            );
        }
        aggregationOperationList.add(
                new PipelineLookupOperation("checkins", "competingGroups", Variables.builder()
                        .variable(Variable.from("pk", "$placeKey"))
                        .build(),
                        new FreeFormOperation("$match", "{$expr: {$and: [" +
                                "{$eq: [\"$placeKey\", \"$$pk\"]}," +
                                "{$gte: [\"$validTimestamp\", {$toDate: \"" + Instant.now().minus(1, ChronoUnit.DAYS).toString() + "\"}]}]}}}"),
                        group("groupKey").count().as("validCheckins"),
                        lookup("groups", "_id", "groupKey", "group"),
                        unwind("group"),
                        new FreeFormOperation("$addFields", "{ \"group.validCheckins\": \"$validCheckins\"}"),
                        replaceRoot("group"),
                        match(Criteria.where(Group.Fields.deleted.name()).is(false)),
                        sort(Sort.Direction.DESC, "validCheckins"),
                        limit(4))
        );
        aggregationOperationList.add(lookup("groups", "dominantGroupKey", "groupKey", "dominantGroup"));
        aggregationOperationList.add(unwind("dominantGroup", true));
        aggregationOperationList.add(
                new FreeFormOperation(
                        AggregationUtils.ADD_FIELDS,
                        "{ \"averageRate\" : { \"$cond\": [ { \"$eq\": [ \"$countRates\", 0 ] }, 0, {\"$divide\":[\"$totalRate\", \"$countRates\"]} ] }}"
                )
        );
        var aggregation = newAggregation(aggregationOperationList);
        return mongoTemplate.aggregate(
                        aggregation,
                        "places",
                        ExtendedPlace.class
                )
                .singleOrEmpty();
    }

    public Flux<ExtendedPlace> findByCoordinatesNear(GeoJsonPoint point, Double distance, Optional<String> name,
                                                     Optional<String> ownedByGroupKey, boolean onlyVisible,
                                                     Optional<String> auth, Pageable pageable) {

        final AggregationOperation[] ops = prepareAggregationOps(point, distance, name, ownedByGroupKey, onlyVisible, auth, pageable);

        return fetchExtendedPlaces(ops);
    }

    public Flux<Place> findPotentiallyTouched(GeoJsonPoint point, Double distance) {
        return mongoTemplate.aggregate(
                newAggregation(prepareAggregationOps(point, distance, Optional.empty(), Optional.empty(), true,
                        Optional.empty(), Pageable.unpaged())),
                "places", Place.class);
    }

    private AggregationOperation[] prepareAggregationOps(
            GeoJsonPoint point,
            Double distance,
            Optional<String> name,
            Optional<String> ownedByGroupKey,
            boolean onlyVisible,
            Optional<String> auth,
            Pageable pageable) {

        val query = NearQuery.near(point).inKilometers();

        val withLimit = distance == null ? query
                : query.maxDistance(new Distance(distance, Metrics.KILOMETERS));

        val criteria = Stream.of(
                name.stream().map(n -> Criteria.where("name").regex(".*" + n + ".*", "i")),
                ownedByGroupKey.stream().map(ogk -> Criteria.where(Place.Fields.dominantGroupKey.name()).is(ogk)),
                Optional.of(onlyVisible).filter(v -> v).stream().flatMap(z -> Stream.of(
                        Criteria.where("dominantGroupKey").exists(true),
                        Criteria.where("lastSize").gt(0),
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
                                limit(p.getPageSize()))),
                        auth.stream().flatMap(this::buildFollowingClause)
                ).flatMap(Function.identity())
                .toArray(AggregationOperation[]::new);
    }

    private Stream<AggregationOperation> buildFollowingClause(String userKey) {
        return Stream.of(
                new FreeFormOperation("$lookup", "{\"from\": \"follows\", \"let\": {\"fKey\": \"" + userKey + "\", \"pKey\": \"$placeKey\"}, " +
                        "\"as\": \"following\", \"pipeline\": [" +
                        "  {\"$match\": {\"$expr\": " +
                        "    { \"$and\": [{\"$eq\": [\"$followerKey\", \"$$fKey\"]}," +
                        "                 {\"$eq\": [\"$placeKey\", \"$$pKey\"]}]}}}]}"),
                new FreeFormOperation("$addFields", "{\"following\": { \"$gt\": [{\"$size\": \"$following\"}, 0]}}")
        );
    }

    public Flux<ExtendedPlace> findAll(Optional<String> name, Optional<String> ownedByGroupKey, Optional<String> auth, Pageable pageable) {
        val ops = Stream.of(
                        name.stream().map(n -> match(Criteria.where("name").regex(".*" + n + ".*", "i"))),
                        ownedByGroupKey.stream().map(n -> match(Criteria.where(Place.Fields.dominantGroupKey.name()).is(n))),
                        auth.stream().flatMap(this::buildFollowingClause),
                        Stream.of(skip(pageable.getOffset()),
                                limit(pageable.getPageSize()))
                ).flatMap(Function.identity())
                .toArray(AggregationOperation[]::new);

        return fetchExtendedPlaces(ops);
    }

    private Flux<ExtendedPlace> fetchExtendedPlaces(AggregationOperation... ops) {
        val aggregation = newAggregation(
                Stream.concat(Arrays.stream(ops),
                        Stream.of(
                                lookup("groups", "dominantGroupKey", "groupKey", "dominantGroup"),
                                unwind("dominantGroup", true),
                                project(ExtendedPlace.class),
                                new FreeFormOperation(
                                        AggregationUtils.ADD_FIELDS,
                                        "{ \"averageRate\" : { \"$cond\": [ { \"$eq\": [ \"$countRates\", 0 ] }, 0, {\"$divide\":[\"$totalRate\", \"$countRates\"]} ] }}"
                                )
                        )).toArray(AggregationOperation[]::new)
        );
        return mongoTemplate.aggregate(aggregation, "places", ExtendedPlace.class);
    }

    public Mono<UpdateResult> setReported(String placeKey, String reportedMessage) {
        val q = new Query(Criteria.where(Place.Fields.placeKey.name()).is(placeKey));
        val u = new Update()
          .set(Group.Fields.reported.name(), true)
          .set(Group.Fields.reportedMessage.name(), reportedMessage)
          .set(Group.Fields.reportedSolved.name(), false)
          .set(Group.Fields.reportedTime.name(), Instant.now());
        return mongoTemplate.updateFirst(q, u, Place.class);
    }

    public Mono<UpdateResult> replaceDominantGroup(ExtendedPlace place, Group group) {
        if (place.getDominantGroup().getGroupKey() == null || !place.getDominantGroup().getGroupKey().equals(group.getGroupKey())) {
            // no need to modify anything as group being removed is not dominant
            return Mono.just(UpdateResult.acknowledged(0, 0L, null));
        }

        val nextGroup = place.getCompetingGroups().stream().filter(cg -> !cg.getGroupKey().equals(group.getGroupKey())).findFirst();

        val q = new Query(Criteria.where(Place.Fields.placeKey.name()).is(place.getPlaceKey()));
        val u = nextGroup.map(cg -> new Update().set(Place.Fields.dominantGroupKey.name(), cg.getGroupKey()))
                .orElse(new Update().unset(Place.Fields.dominantGroupKey.name()));
        return mongoTemplate.updateFirst(q, u, Place.class);
    }

    public Mono<UpdateResult> unsetDominantGroup(String groupKey) {
        val q = new Query(Criteria.where(Place.Fields.dominantGroupKey.name()).is(groupKey));
        val u = new Update().unset(Place.Fields.dominantGroupKey.name());
        return mongoTemplate.updateFirst(q, u, Place.class);

    }

    public Flux<String> findGroupsForMap(GeoJsonPoint point, Distance distance, Pageable pageable) {
        val query = NearQuery.near(point).inKilometers();

        val withLimit = query.maxDistance(distance);

        val criteria = List.of(
                Criteria.where("dominantGroupKey").exists(true),
                Criteria.where("lastSize").gt(0),
                Criteria.where("lastCheckInTimestamp").gte(Instant.now().minus(365, ChronoUnit.DAYS))
        );

        val withQuery = withLimit.query(Query.query(new Criteria().andOperator(criteria)));

        AggregationOperation[] aggregationOperation = Stream.of(
                        Stream.of(geoNear(withQuery, "dist").useIndex("coordinates")),
                        Stream.of(Aggregation.group("dominantGroupKey").first(Aggregation.ROOT).as("uniqueDominantGroupKey")),
                        Stream.of(Aggregation.replaceRoot("uniqueDominantGroupKey")),
                        Stream.of(skip(pageable.getOffset()), limit(pageable.getPageSize()))
                ).flatMap(Function.identity())
                .toArray(AggregationOperation[]::new);

        return mongoTemplate.aggregate(
                newAggregation(aggregationOperation), "places", Place.class)
                .map(Place::getDominantGroupKey);
    }
}
