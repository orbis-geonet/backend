package to.orbis.v2.backend.repositories;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Metrics;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.FreeFormOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.repositories.queries.PostQuery;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PostsAggregationsRepository {

    ReactiveMongoTemplate mongoTemplate;
    private final static Integer MAX_DISTANCE = 500;

    public Flux<ExtendedPost> findPosts(PostQuery query) {
        val conditions = buildConditions(query);

        val sortingClause = query.getMoment().map(

                        _ignored -> query.isPastEvents() ?
                                sort(Sort.by(Post.Fields.plannedTime.name()).descending())
                                : sort(Sort.by(Post.Fields.plannedTime.name()).ascending()))

                .or(() -> query.isInverseGeoQuery()
                        ? Optional.empty()
                        : Optional.of(sort(Sort.by(Post.Fields.timestamp.name()).descending()))).stream();

        val finalOperations = Stream.of(
                lookupUnwind("places", "placeKey", "placeKey", "place"),
                lookupUnwind("groups", "groupKey", "groupKey", "group"),
                lookupUnwind("users", "userKey", "userKey", "user"),
                filterBlockedUsers("", query.getViewerUserKey()),
                filterDeletedUsers(""),
                filterDeletedGroup(""),
                Stream.of(new FreeFormOperation("$unset",
                        "group.admins", "group.members", "group.followers"
                )),
                commentsCounts(query.getViewerUserKey()),
                attendingClause(query.getViewerUserKey())
        ).flatMap(Function.identity());

        val skipClause = query.getPage().flatMap(p -> query.getLimit().map(l -> skip((long) p * l))).stream();
        val limitClause = query.getLimit().map(Aggregation::limit).stream();

        final AggregationOperation[] operations = Stream.of(
                conditions,
                query.isDedup() ? dedupClause() : Stream.<AggregationOperation>empty(),
                sortingClause,
                skipClause,
                limitClause,
                finalOperations
        ).flatMap(Function.identity()).toArray(AggregationOperation[]::new);


        val aggregation = Aggregation.newAggregation(operations);

//        val startTime = Instant.now();
        return mongoTemplate.aggregate(aggregation, "posts", ExtendedPost.class);
//                .doFinally(_ignored -> {
//                    log.info(
//                            "findPosts: takes time {} seconds. Params distance {} isInverse {} Type: {}",
//                            Duration.between(startTime, Instant.now()).toSeconds(),
//                            query.getDistance(),
//                            query.isInverseGeoQuery(),
//                            query.getPostTypes().map(it -> it.stream().map(Enum::name).collect(Collectors.joining(", ")))
//                    );
//                });

    }

    public static Stream<AggregationOperation> filterBlockedUsers(String prefix, Optional<String> viewerUserKey) {
        return viewerUserKey.stream().map(vk -> match(Criteria.where(prefix + "user."+User.Fields.blockedBy.name()).ne(vk)));
    }

    public static Stream<AggregationOperation> filterDeletedUsers(String prefix) {
        return Stream.of(match(Criteria.where(prefix + "user." + User.Fields.deleted.name()).is(false)));
    }

    public static Stream<AggregationOperation> filterDeletedGroup(String prefix) {
        Criteria criteria = new Criteria();
        criteria.orOperator(
                Criteria.where(prefix + "group").exists(false),
                Criteria.where(prefix + "group." + Group.Fields.deleted.name()).is(false)
        );
        return Stream.of(match(criteria));
    }

    private Stream<AggregationOperation> dedupClause() {
        return Stream.of(
                new FreeFormOperation("$sort", "{ \"userKey\": 1, \"timestamp\" : -1}"),
                new FreeFormOperation("$group", "{\"_id\": \"$userKey\", \"first\": {\"$first\": \"$$ROOT\"}, \"lastSeen\": {\"$last\": \"$timestamp\"}}"),
                new FreeFormOperation("$set", "{\"first.lastSeen\": \"$lastSeen\"}"),
                new FreeFormOperation("$replaceRoot", "{\"newRoot\": \"$first\"}")
        );
    }

    private Stream<AggregationOperation> attendingClause(Optional<String> viewingUserKey) {
        return viewingUserKey.or(() -> Optional.of("anonymous")).stream().flatMap(uk -> Stream.of(
                new FreeFormOperation("$lookup", "{ \"from\" : \"eventAttendees\", \"let\" : { \"postKey\" : \"$postKey\", \"type\" : \"$type\"}, \n" +
                        "  \"pipeline\" : [{ \"$match\" : \n" +
                        "      { \"$expr\" : { \"$and\" : [\n" +
                        "          { \"$eq\" : [\"$$type\", \"EVENT\"]}, \n" +
                        "          { \"$eq\" : [\"$postKey\", \"$$postKey\"]}]}}},\n" +
                        "          {\"$facet\": {\n" +
                        "              \"user\": [{\"$match\": {\"userKey\": \""+ uk +"\"}}],\n" +
                        "              \"cnt\": [{\"$group\": {\"_id\": \"cnt\", \"cnt\": {\"$sum\": 1}}}]\n" +
                        "              }}\n" +
                        "          ], \n" +
                        "          \"as\" : \"attending\"}"),
                unwind("$attending"),
                unwind("$attending.cnt", true),
                new FreeFormOperation("$set", "{ \"confirmedCount\": {\"$ifNull\": [\"$attending.cnt.cnt\", 0]}, \"attending\" : { \"$gte\" : [{ \"$size\" : { \"$ifNull\" : [\"$attending.user\", []]}}, 1]}}")
        ));
    }

    private Stream<AggregationOperation> commentsCounts(Optional<String> viewingUserKey) {
        return Stream.concat(
                Stream.of(
                /*
                { "$lookup" : { "from": "comments", "let": {"pid": "$postKey"}, "pipeline": [
  {"$match": {"$expr": {"$and": [
      { "$eq": ["$postKey", "$$pid"]},
      { "$or": [{"$lte": ["$deleted", null]}, {"$eq": ["$deleted", false]}]}
      ]}}},
  { "$group": {"_id": "cnt", "cnt": {"$sum": 1}}}
], "as": "commentsCount"} },
{ "$set": {"commentsCount": {"$ifNull": [{"$first": "$commentsCount"}, {"cnt": 0}]}}},
{ "$set": {"commentsCount": "$commentsCount.cnt"}}
                 */
                        new FreeFormOperation("$lookup", "{ \"from\": \"comments\", \"let\": {\"pid\": \"$postKey\"}, \"pipeline\": [\n" +
                                "  {\"$match\": {\"$expr\": {\"$and\": [\n" +
                                "      { \"$eq\": [\"$postKey\", \"$$pid\"]},\n" +
                                "      { \"$or\": [{\"$lte\": [\"$deleted\", null]}, {\"$eq\": [\"$deleted\", false]}]}\n" +
                                "      ]}}},\n" +
                                "  { \"$group\": {\"_id\": \"cnt\", \"cnt\": {\"$sum\": 1}}}\n" +
                                "], \"as\": \"commentsCount\"}"),
                        new FreeFormOperation("$set", "{\"commentsCount\": {\"$ifNull\": [{\"$first\": \"$commentsCount\"}, {\"cnt\": 0}]}}"),
                        new FreeFormOperation("$set", "{\"commentsCount\": \"$commentsCount.cnt\"}")
                ),
                viewingUserKey.map(uk -> new FreeFormOperation("$set", "{\"userLiked\": {\"$in\": [\"" + uk + "\", {\"$ifNull\": [\"$liked\", []]}]}}")).stream());
    }

    private Stream<AggregationOperation> buildConditions(PostQuery query) {

        if (query.getPoint().isPresent()) {
            return query.getPoint()
                    .map(p -> {
                        final Criteria[] criteria = getCriteriaStream(query).toArray(Criteria[]::new);
                        final NearQuery near = NearQuery.near(p, Metrics.KILOMETERS);
                        final NearQuery nearQuery = query.isInverseGeoQuery()
                                ? near.minDistance(query.getDistance(), Metrics.KILOMETERS)
                                    .maxDistance(MAX_DISTANCE, Metrics.KILOMETERS)
                                : near.maxDistance(query.getDistance(), Metrics.KILOMETERS);
                        if (criteria.length == 0) {
                            return (AggregationOperation) geoNear(nearQuery, "dist").useIndex("coordinates");
                        }

                        return (AggregationOperation) geoNear(nearQuery
                                .query(Query.query(new Criteria().andOperator(
                                        criteria
                                ))), "dist").useIndex("coordinates");
                    })
                    .stream();
        } else {
            return getCriteriaStream(query).map(Aggregation::match);
        }
    }

    private Stream<Criteria> getCriteriaStream(PostQuery query) {
        return Stream.of(
                        query.getPostKey().map(k -> Criteria.where(Post.Fields.postKey.name()).is(k)).stream(),
                        query.getUserKey().map(k -> Criteria.where(Post.Fields.userKey.name()).is(k)).stream(),
                        query.getPlaceKey().map(k -> Criteria.where(Post.Fields.placeKey.name()).is(k)).stream(),
                        query.getGroupKey().map(k -> Criteria.where(Post.Fields.groupKey.name()).is(k)).stream(),
                        query.getCity().map(k -> Criteria.where(Post.Fields.city.name()).is(k)).stream(),
                        query.getFrom().map(k -> Criteria.where(Post.Fields.timestamp.name()).lt(k)).stream(),
                        query.getAfter().map(k -> Criteria.where(Post.Fields.timestamp.name()).gte(k)).stream(),
                        query.getMoment().map(k -> {
                            if (query.isPastEvents()) {
                                return Criteria.where(Post.Fields.plannedTime.name()).lte(k);
                            }

                            return Criteria.where(Post.Fields.plannedTime.name()).gte(k);
                        }).stream(),
                        query.getPostTypes().map(k -> Criteria.where(Post.Fields.type.name()).in(k.stream().map(Enum::name).collect(Collectors.toList()))).stream(),
                        Stream.of(Criteria.where(Post.Fields.deleted.name()).is(false)))
                .flatMap(Function.identity());
    }

    public static Stream<AggregationOperation> lookupUnwind(String collection, String localField, String foreignField, String as) {
        return Stream.of(
                lookup(collection, localField, foreignField, as),
                unwind(as, true)
        );
    }

    public Flux<ExtendedPost> findUserFeed(Optional<Instant> from, List<Follow> follows, Optional<String> viewingUserKey, EnumSet<PostType> postTypes, boolean dedup) {

        val users = follows.stream().map(Follow::getUserKey).filter(Objects::nonNull).collect(Collectors.toList());
        val groups = follows.stream().map(Follow::getGroupKey).filter(Objects::nonNull).collect(Collectors.toList());
        val places = follows.stream().map(Follow::getPlaceKey).filter(Objects::nonNull).collect(Collectors.toList());

        val ops = Stream.of(
                Stream.of(match(
                                new Criteria().andOperator(
                                        new Criteria().orOperator(
                                                Criteria.where(Post.Fields.userKey.name()).in(users),
                                                Criteria.where(Post.Fields.groupKey.name()).in(groups),
                                                Criteria.where(Post.Fields.placeKey.name()).in(places)
                                        ),
                                        Criteria.where(Post.Fields.deleted.name()).is(false),
                                        Criteria.where(Post.Fields.timestamp.name()).lt(from.orElse(Instant.now())),
                                        Criteria.where(Post.Fields.type.name()).in(postTypes)
                                ))),
                        dedup ? dedupClause() : Stream.<AggregationOperation>empty(),
                        Stream.of(sort(Sort.by(Post.Fields.timestamp.name()).descending())),
                lookupUnwind("places", "placeKey", "placeKey", "place"),
                lookupUnwind("groups", "groupKey", "groupKey", "group"),
                lookupUnwind("users", "userKey", "userKey", "user"),
                filterBlockedUsers("", viewingUserKey),
                filterDeletedUsers(""),
                filterDeletedGroup(""),
                Stream.of(new FreeFormOperation("$unset",
                        "group.admins", "group.members", "group.followers"
                )),
                commentsCounts(viewingUserKey),
                attendingClause(viewingUserKey)
        ).flatMap(Function.identity());

        return mongoTemplate.aggregate(
                newAggregation(ops.toArray(AggregationOperation[]::new)),
                "posts", ExtendedPost.class);
    }

    public Mono<User> findPostAuthor(String postKey) {
        return mongoTemplate.aggregate(
                        Aggregation.newAggregation(
                                match(Criteria.where(Post.Fields.postKey.name()).is(postKey)),
                                lookup("users", Post.Fields.userKey.name(), User.Fields.userKey.name(), "user"),
                                unwind("user"),
                                replaceRoot("user")),
                        Post.class, User.class)
                .singleOrEmpty();

    }

    public Mono<Void> storySeen(String postKey, String userKey) {
        Query q = new Query(Criteria.where(StorySeen.Fields.postKey.name()).is(postKey).and(StorySeen.Fields.userKey.name()).is(userKey));
        Update u = new Update().set(StorySeen.Fields.timestamp.name(), Instant.now());
        return mongoTemplate.upsert(q, u, StorySeen.class).then();
    }

    public Mono<UpdateResult> likePost(String postKey, String userKey) {
        val q = new Query(Criteria.where(Post.Fields.postKey.name()).is(postKey));
        val u = new Update().addToSet(Post.Fields.liked.name()).value(userKey);
        return mongoTemplate.updateFirst(q, u, Post.class);
    }

    public Mono<UpdateResult> unlikePost(String postKey, String userKey) {
        val q = new Query(Criteria.where(Post.Fields.postKey.name()).is(postKey));
        val u = new Update().pull(Post.Fields.liked.name(), userKey);
        return mongoTemplate.updateFirst(q, u, Post.class);
    }

    public Mono<UpdateResult> deletePosts(String groupKey) {
        val q = new Query(Criteria.where(Post.Fields.groupKey.name()).is(groupKey));
        val u = new Update().set(Post.Fields.deleted.name(), true);
        return mongoTemplate.updateMulti(q, u, Post.class);
    }

    public Mono<UpdateResult> deletePost(String postKey) {
        val q = new Query(Criteria.where(Post.Fields.postKey.name()).is(postKey));
        val u = new Update().set(Post.Fields.deleted.name(), true);
        return mongoTemplate.updateFirst(q, u, Post.class);
    }

    public Flux<ExtendedPost> findAttending(boolean pastEvents, String userKey, Pageable pageable) {

        val ops = Stream.of(
                Stream.of(match(Criteria.where(EventAttendee.Fields.userKey.name()).is(userKey))),
                lookupUnwind("posts", "postKey", "postKey", "post"),
                Stream.of(replaceRoot("post"),
                        pastEvents
                                ? match(Criteria.where(Post.Fields.plannedTime.name()).lt(Instant.now()))
                                : match(Criteria.where(Post.Fields.plannedTime.name()).gte(Instant.now())),
                        sort(pastEvents ? Sort.Direction.DESC : Sort.Direction.ASC, Post.Fields.plannedTime.name()),
                        skip(pageable.getOffset()),
                        limit(pageable.getPageSize())),
                lookupUnwind("places", "placeKey", "placeKey", "place"),
                lookupUnwind("groups", "groupKey", "groupKey", "group"),
                lookupUnwind("users", "userKey", "userKey", "user"),
                Stream.of(new FreeFormOperation("$unset",
                        "group.admins", "group.members", "group.followers"
                ), new FreeFormOperation("$set", "{\"attending\": true}")),
                commentsCounts(Optional.of(userKey)),
                attendingClause(Optional.of(userKey))
        ).flatMap(Function.identity());


        return mongoTemplate.aggregate(newAggregation(ops.toArray(AggregationOperation[]::new)), EventAttendee.class, ExtendedPost.class);
    }

    public Mono<UpdateResult> setReported(String postKey, String reportedMessage) {
        val q = new Query(Criteria.where(Post.Fields.postKey.name()).is(postKey));
        val u = new Update()
          .set(Post.Fields.reported.name(), true)
          .set(Post.Fields.reportedMessage.name(), reportedMessage)
          .set(Post.Fields.reportedSolved.name(), false)
          .set(Post.Fields.reportedTime.name(), Instant.now());
        return mongoTemplate.updateFirst(q, u, Post.class);

    }

    public Mono<UpdateResult> deleteAllByPlaceAndGroup(ExtendedPlace place, Group group) {
        val q = new Query(Criteria.where(Post.Fields.placeKey.name()).is(place.getPlaceKey()).and(Post.Fields.groupKey.name()).is(group.getGroupKey()));
        val u = new Update().set(Post.Fields.deleted.name(), true);
        return mongoTemplate.updateMulti(q, u, Post.class);
    }
}
