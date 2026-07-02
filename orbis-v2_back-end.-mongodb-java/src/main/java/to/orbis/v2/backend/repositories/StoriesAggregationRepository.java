package to.orbis.v2.backend.repositories;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.FreeFormOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import to.orbis.v2.backend.models.entity.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static to.orbis.v2.backend.repositories.PostsAggregationsRepository.*;

@Repository
@RequiredArgsConstructor
public class StoriesAggregationRepository {

    ReactiveMongoTemplate mongoTemplate;

    public Flux<ExtendedStory> findUserStories(List<Follow> follows, boolean seen, String userKey, Pageable pageable) {
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
                                        Criteria.where(Post.Fields.groupKey.name()).exists(true),
                                        Criteria.where(Post.Fields.groupKey.name()).ne(null)
                                )),
                        group(Post.Fields.groupKey.name()),
                        lookup("groups", "_id", "groupKey", "group"),
                        match(Criteria.where("group." + Group.Fields.storiesHidden.name()).ne(userKey)),
                        lookup("stories", "_id", "groupKey", "story"),
                        unwind("story"),
                        replaceRoot("story")),
                getFinalOperations(seen, Optional.of(userKey), pageable, Optional.of(sort(Sort.by("timestamp").descending())))
        ).flatMap(Function.identity());

        return mongoTemplate.aggregate(
                newAggregation(ops.toArray(AggregationOperation[]::new)),
                "posts", ExtendedStory.class);
    }

    private Stream<AggregationOperation> getFinalOperations(boolean seen, Optional<String> userKey, Pageable pageable, Optional<SortOperation> sortOperation) {
        return Stream.of(
                Stream.of(skip(pageable.getOffset()),
                        limit(pageable.getPageSize())
                ),
                lookupUnwind("groups", "groupKey", "groupKey", "group"),
                filterDeletedGroup(""),
                Stream.of(
                        new FreeFormOperation("$unset", "group.admins", "group.followers", "group.members"),
                        new FreeFormOperation("$unwind", "{\"path\": \"$posts\"}")
                ),
                lookupUnwind("users", "$posts.userKey", "userKey", "posts.user"),
                lookupUnwind("places", "$posts.placeKey", "placeKey", "posts.place"),
                filterBlockedUsers("posts.", userKey),
                filterDeletedUsers("posts."),
                lookupUnwind("posts", "$posts.postKey", "postKey", "originalPost"),
                Stream.of(match(Criteria.where("originalPost."+ Post.Fields.deleted.name()).is(false))),
                userKey.stream().map(uk -> new FreeFormOperation("$set", "{\"posts.userLiked\": {\"$in\": [\"" + uk + "\", {\"$ifNull\": [\"$originalPost.liked\", []]}]}}")),
                Stream.of(
                        new FreeFormOperation("$set", "{\"posts.liked\": \"$originalPost.liked\"}"),
                        new FreeFormOperation("$lookup", "{ \"from\": \"comments\", \"let\": {\"pid\": \"$posts.postKey\"}, \"pipeline\": [\n" +
                                "  {\"$match\": {\"$expr\": {\"$and\": [\n" +
                                "      { \"$eq\": [\"$postKey\", \"$$pid\"]},\n" +
                                "      { \"$or\": [{\"$lte\": [\"$deleted\", null]}, {\"$eq\": [\"$deleted\", false]}]}\n" +
                                "      ]}}},\n" +
                                "  { \"$group\": {\"_id\": \"cnt\", \"cnt\": {\"$sum\": 1}}}\n" +
                                "], \"as\": \"posts.commentsCount\"}"),
                        new FreeFormOperation("$set", "{\"posts.commentsCount\": {\"$ifNull\": [{\"$first\": \"$posts.commentsCount\"}, {\"cnt\": 0}]}}"),
                        new FreeFormOperation("$set", "{\"posts.commentsCount\": \"$posts.commentsCount.cnt\"}")
                ),
                userKey.map(uk ->
                        new FreeFormOperation("$lookup", "{\"from\": \"storiesSeen\", \"let\": {\"pid\": \"$posts.postKey\"}, \"pipeline\": [\n" +
                                        "     {\"$match\": {\"$expr\": {\"$and\": [\n" +
                                        "         { \"$eq\": [\"$$pid\", \"$postKey\"]},\n" +
                                        "         { \"$eq\": [\"$userKey\", \"" + uk + "\"]}\n" +
                                        "         ]}}},\n" +
                                        "     {\"$limit\": 1}\n" +
                                        "   ], \"as\": \"posts.seen\"}"))
                        .or(() -> Optional.of(
                                new FreeFormOperation("$set", "{\"posts.seen\": []}")
                        )).stream(),
                Stream.of(
                        new FreeFormOperation("$set", "{\"posts.seen\": {\"$gt\": [{\"$size\": \"$posts.seen\"}, 0]}}"),
                        new FreeFormOperation("$group", "{\"_id\": \"$_id\", \"posts\": {\"$push\": \"$posts\"}, \"story\":{\"$first\": \"$$ROOT\"}}"),
                        new FreeFormOperation("$set", "{\"story.posts\": \"$posts\"}"),
                        replaceRoot("story"),
                        new FreeFormOperation("$set", "{\"seen\": {\"$reduce\": {\"input\":\"$posts\", \"initialValue\": true, \"in\": {\"$and\": [\"$$value\", \"$$this.seen\"]}}}}"),
                        match(Criteria.where("seen").is(seen))),
                sortOperation.stream()
        ).flatMap(Function.identity());
    }

    public Flux<ExtendedStory> findCityStories(String city, boolean seen, Optional<String> userKey, Pageable pageable) {
        val cityMatch = Aggregation.match(
                Criteria.where(Story.Fields.cities.name()).in(city)
        );

        val ops = Stream.of(
                Stream.of(cityMatch),
                getFinalOperations(seen, userKey, pageable, Optional.of(sort(Sort.by(Sort.Direction.DESC, "timestamp"))))
        ).flatMap(Function.identity());

        val aggregation = newAggregation(ops.toArray(AggregationOperation[]::new));
        return mongoTemplate.aggregate(aggregation, "stories", ExtendedStory.class);
    }

    public Flux<ExtendedStory> findNearStories(GeoJsonPoint geoJsonPoint, double distance, boolean seen, Optional<String> userKey, Pageable pageable) {

        val query = NearQuery.near(geoJsonPoint).inKilometers();

        val withLimit = query.maxDistance(new Distance(distance, Metrics.KILOMETERS));

        val ops = Stream.of(
                Stream.of(geoNear(withLimit, "dist")),
                getFinalOperations(seen, userKey, pageable, Optional.of(sort(Sort.by("dist"))))
        ).flatMap(Function.identity());

        val aggregation = newAggregation(ops.toArray(AggregationOperation[]::new));
        return mongoTemplate.aggregate(aggregation, "stories", ExtendedStory.class);
    }

    public Flux<Story> findByPostsContains(String postKey) {
        return mongoTemplate.find(Query.query(Criteria.where("posts.postKey").is(postKey)), Story.class);
    }
}
