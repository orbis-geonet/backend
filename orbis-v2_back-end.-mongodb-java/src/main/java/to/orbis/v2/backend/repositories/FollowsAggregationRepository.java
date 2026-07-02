package to.orbis.v2.backend.repositories;

import com.google.appengine.repackaged.com.google.common.io.Files;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.FreeFormOperation;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import to.orbis.v2.backend.models.FollowType;
import to.orbis.v2.backend.models.entity.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Stream.of;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.UnsetOperation.unset;

@Repository
@RequiredArgsConstructor
public class FollowsAggregationRepository {

    ReactiveMongoTemplate mongoTemplate;

    public Flux<ExtendedFollow> getFollowing(Optional<FollowType> type, Optional<String> name, String followerUserKey, Optional<GeoJsonPoint> point, Pageable pageable) {
        val followerKeyCriteria = Criteria.where(Follow.Fields.followerKey.name()).is(followerUserKey)
                .and(Follow.Fields.accepted.name()).is(true);

        val withTypeFilter = type.map(t -> addTypeCriteria(followerKeyCriteria, t))
                .orElse(followerKeyCriteria);

        return findFollows(withTypeFilter, name, point, pageable);
    }

    private Criteria addTypeCriteria(Criteria followerKeyCriteria, FollowType followType) {

        switch (followType) {
            case USER:
                return followerKeyCriteria.and(Follow.Fields.userKey.name()).exists(true);
            case PLACE:
                return followerKeyCriteria.and(Follow.Fields.placeKey.name()).exists(true);
            case GROUP:
                return followerKeyCriteria.and(Follow.Fields.groupKey.name()).exists(true);
        }

        return followerKeyCriteria;
    }

    private Flux<ExtendedFollow> findFollows(Criteria criteria, Optional<String> name, Optional<GeoJsonPoint> userLocation, Pageable pageable) {
        return mongoTemplate.aggregate(newAggregation(
                Stream.of(
                        Stream.of(match(criteria),
                lookup("users", "userKey", "userKey", "user"),
                unwind("user", true),
                lookup("groups", "groupKey", "groupKey", "group"),
                unwind("group", true),
                lookup("places", "placeKey", "placeKey", "place"),
                unwind("place", true)),
                        name.stream().map(this::filterNameClause),
                        Stream.of(skip(pageable.getOffset()), limit(pageable.getPageSize())),
                        postsCountGeoClauses(userLocation),
                        postsCountFinalClauses()).flatMap(Function.identity()).toArray(AggregationOperation[]::new)
        ), "follows", ExtendedFollow.class);
    }

    private AggregationOperation filterNameClause(String name) {
        final String pattern = ".*" + name + ".*";
        return match(
                new Criteria().orOperator(
                        Criteria.where(Follow.Fields.placeKey.name()).exists(true).and("place." + Place.Fields.name.name()).regex(pattern, "i"),
                        Criteria.where(Follow.Fields.groupKey.name()).exists(true).and("group." + Group.Fields.name.name()).regex(pattern, "i"),
                        Criteria.where(Follow.Fields.userKey.name()).exists(true).and("user." + User.Fields.displayName.name()).regex(pattern, "i")
                ));
    }

    private Stream<AggregationOperation> postsCountFinalClauses() {
        return of(
                new FreeFormOperation("$set", "{\"group.postsThisWeek\": { \"$ifNull\": [\"$postsCount.postsThisWeek.cnt\", 0] }, \n" +
                        "     \"group.postsLastWeek\": {\"$ifNull\": [\"$postsCount.postsLastWeek.cnt\", 0]}}"),
                unset("postsCount"),
                new FreeFormOperation("$addFields", "{\n" +
                        "    \"group\": {\n" +
                        "      \"$cond\": [\n" +
                        "        { \"$lte\": [\"$groupKey\", \"\"] },\n" +
                        "        \"$$REMOVE\",\n" +
                        "        \"$group\"\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  }")
        );
    }

    private Stream<AggregationOperation> postsCountGeoClauses(Optional<GeoJsonPoint> userLocation) {
        val now = Instant.now();
        val weekAgo = now.minus(7, ChronoUnit.DAYS);
        val twoWeeksAgo = now.minus(14, ChronoUnit.DAYS);
        return userLocation.stream().flatMap(ul ->
                {
                    final String lookup = String.format(
                            "{ \"from\" : \"posts\", \"let\" : { \"groupKey\" : \"$group.groupKey\"}, \n" +
                                    "  \"pipeline\" : [\n" +
                                    "    { \"$geoNear\": {\n" +
                                    "        \"near\": { \"type\": \"Point\", \"coordinates\": [%s, %s] },\n" + // longitude, latitude
                                    "        \"distanceField\": \"dist\",\n" +
                                    "        \"maxDistance\": 25000,\n" +
                                    "        \"key\": \"coordinates\",\n" +
                                    "        \"spherical\": true\n" +
                                    "        }},\n" +
                                    "      { \"$match\" : { \"$expr\" : { \"$eq\" : [\"$groupKey\", \"$$groupKey\"]}}}, \n" +
                                    "      { \"$facet\": {\n" +
                                    "          \"postsThisWeek\": [\n" +
                                    "            { \"$match\": { \"$expr\": { \"$and\": [\n" +
                                    "                  {\"$lt\": [{\"$dateFromString\": {\"dateString\": \"%s\"}}, \"$timestamp\"]},\n" + // week ago
                                    "                  {\"$lt\": [\"$timestamp\", {\"$dateFromString\": {\"dateString\": \"%s\"}}]}\n" + // now
                                    "                ] } }},\n" +
                                    "            { \"$group\" : { \"_id\" : \"cnt\", \"cnt\" : { \"$sum\" : 1}}}\n" +
                                    "          ],\n" +
                                    "          \"postsLastWeek\": [\n" +
                                    "            { \"$match\": { \"$expr\": { \"$and\": [\n" +
                                    "                  {\"$lt\": [{\"$dateFromString\": {\"dateString\": \"%s\"}}, \"$timestamp\"]},\n" + // two weeks ago
                                    "                  {\"$lt\": [\"$timestamp\", {\"$dateFromString\": {\"dateString\": \"%s\"}}]}\n" + // week ago
                                    "                ] } }},\n" +
                                    "              { \"$group\" : { \"_id\" : \"cnt\", \"cnt\" : { \"$sum\" : 1}}}\n" +
                                    "                ]\n" +
                                    "          }}\n" +
                                    "  ], \"as\" : \"postsCount\"}", ul.getX(), ul.getY(), weekAgo.toString(), now.toString(), twoWeeksAgo.toString(), weekAgo.toString());
                    return of(
                            new FreeFormOperation("$lookup", lookup),
                            unwind("postsCount"),
                            unwind("postsCount.postsThisWeek", true),
                            unwind("postsCount.postsLastWeek", true)
                    );
                }
        );
    }


    private Flux<SimplifiedUser> findFollowers(Criteria criteria, Pageable pageable) {
        var aggregation = newAggregation(
                match(criteria),
                sort(Sort.Direction.DESC, "id"),
                skip(pageable.getOffset()), limit(pageable.getPageSize()),
                lookup("users", "followerKey", "userKey", "followerUser"),
                unwind("followerUser"),
                new FreeFormOperation("$set", "{\"followerUser.seen\": \"$seen\"}"),
                replaceRoot("followerUser")
        );
        return mongoTemplate.aggregate(aggregation, "follows", SimplifiedUser.class);
    }

    public Flux<SimplifiedUser> getUserFollowers(String userKey, boolean pending, Pageable pageable) {
        return findFollowers(Criteria.where(Follow.Fields.userKey.name()).is(userKey).and(Follow.Fields.accepted.name()).is(!pending), pageable);
    }

    public Flux<SimplifiedUser> getGroupFollowers(String groupKey, Pageable pageable) {
        return findFollowers(Criteria.where(Follow.Fields.groupKey.name()).is(groupKey), pageable);
    }

    public Flux<SimplifiedUser> getPlaceFollowers(String placeKey, Pageable pageable) {
        return findFollowers(Criteria.where(Follow.Fields.placeKey.name()).is(placeKey), pageable);
    }

    public Flux<User> findAllToNotify(Post post) {
        val postCriteria = new Criteria().orOperator(
                Stream.of(
                                Optional.ofNullable(post.getGroupKey()).stream().map(gk -> Criteria.where(Follow.Fields.groupKey.name()).is(gk)),
                                Optional.ofNullable(post.getUserKey()).stream().map(uk -> Criteria.where(Follow.Fields.userKey.name()).is(uk).and(Follow.Fields.accepted.name()).is(true)),
                                Optional.ofNullable(post.getPlaceKey()).stream().map(pk -> Criteria.where(Follow.Fields.placeKey.name()).is(pk))
                        ).flatMap(Function.identity())
                        .toArray(Criteria[]::new)
        );

        val notNotifyMyself = Criteria.where(Follow.Fields.followerKey.name()).ne(post.getUserKey());

        val fullCriteria = new Criteria().andOperator(postCriteria, notNotifyMyself);

        return mongoTemplate.aggregate(
                newAggregation(
                        match(fullCriteria),
                        group(Follow.Fields.followerKey.name()),
                        lookup("users", "_id", "userKey", "user"),
                        unwind("user"),
                        replaceRoot("user")
                ), "follows", User.class);
    }
}
