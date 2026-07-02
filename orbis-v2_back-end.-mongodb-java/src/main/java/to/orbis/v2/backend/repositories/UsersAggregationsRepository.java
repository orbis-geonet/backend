package to.orbis.v2.backend.repositories;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.Language;
import to.orbis.v2.backend.models.entity.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.LookupOperation.newLookup;

@Repository
@RequiredArgsConstructor
public class UsersAggregationsRepository {

    ReactiveMongoTemplate mongoTemplate;

    public Mono<ExtendedUser> findOneByUserKey(String userKey, Optional<String> viewerUserKey) {
        var aggregation = newAggregation(
                Stream.of(Stream.of(Aggregation.match(Criteria.where(User.Fields.userKey.name()).is(userKey))),
                                extendUserClauses(viewerUserKey))
                        .flatMap(Function.identity())
                        .toArray(AggregationOperation[]::new)
        );
        return mongoTemplate.aggregate(
                aggregation,
                        "users",
                        ExtendedUser.class)
                .singleOrEmpty();
    }

    public Flux<ExtendedUser> findByDisplayName(String search, Optional<String> viewerUserKey, Pageable pageable) {
        val criteria = Criteria.where(User.Fields.displayName.name()).regex(".*" + search + ".*", "i");
        val exludingBlocked = viewerUserKey.map(uk -> criteria.and(User.Fields.blockedBy.name()).ne(uk)).orElse(criteria);
        return mongoTemplate.aggregate(
                newAggregation(
                        Stream.of(
                                        Stream.of(
                                                Aggregation.match(exludingBlocked),
                                                Aggregation.skip(pageable.getOffset()),
                                                Aggregation.limit(pageable.getPageSize())),
                                        extendUserClauses(viewerUserKey))
                                .flatMap(Function.identity())
                                .toArray(AggregationOperation[]::new)
                ),
                "users",
                ExtendedUser.class);
    }

    private Stream<AggregationOperation> extendUserClauses(Optional<String> viewerUserKey) {
        return Stream.of(Stream.of(buildLookupFor(Group.Fields.admins),
                        buildLookupFor(Group.Fields.members),

                        new FreeFormOperation("$addFields",
                                "{\"groupAdminCount\": {\"$size\": \"$admins\"}, " +
                                        "\"groupMemberCount\": {\"$size\": \"$members\"}}"),

                        buildLookupFor(Group.Fields.followers)),
                viewerUserKey.map(this::buildFollowsLookup).stream(),
                viewerUserKey.stream().map(this::buildBlockedClause),
                Stream.of(new FreeFormOperation("$addFields", "{ \n" +
                                "    \"pending\": { \"$gt\": [{\"$size\": {\"$ifNull\": [{\"$filter\": {\"input\": \"$following\", \"cond\": {\"$eq\": [\"$$this.accepted\", false]} }}, []]}}, 0]},\n" +
                                "    \"following\": { \"$gt\": [{\"$size\": {\"$ifNull\": [{\"$filter\": {\"input\": \"$following\", \"cond\": {\"$eq\": [\"$$this.accepted\", true]} }}, []]}}, 0]}}}"),
                        new FreeFormOperation("$lookup", "{ \"from\" : \"follows\", \"let\" : { \"userKey\" : \"$userKey\"}, \"pipeline\": [\n" +
                                "  {\"$match\": {\"$expr\": {\"$and\": [{\"$eq\": [\"$followerKey\", \"$$userKey\"]}, {\"$eq\": [\"$accepted\", true]}]}}}\n" +
                                "], \"as\" : \"follows\"}"),
                        new FreeFormOperation("$addFields", "{ " +
                                "\"totalFollowing\": { \"$size\": {\"$filter\": {\"input\": \"$follows\", \"cond\": {\"$gt\": [\"$$this.userKey\", null]}}} }," +
                                "\"followedGroups\": { \"$size\": {\"$filter\": {\"input\": \"$follows\", \"cond\": {\"$gt\": [\"$$this.groupKey\", null]}}} }," +
                                "\"followedPlaces\": { \"$size\": {\"$filter\": {\"input\": \"$follows\", \"cond\": {\"$gt\": [\"$$this.placeKey\", null]}}} }," +
                                "}}"),
                        newLookup().from("follows").localField(User.Fields.userKey.name()).foreignField(Follow.Fields.userKey.name()).as("followedBy"),
                        new FreeFormOperation("$addFields", "{\"totalFollowers\": {\"$size\": \"$followedBy\"}}"),
                        project(ExtendedUser.class))).flatMap(Function.identity());
    }

    private AggregationOperation buildBlockedClause(String viewingUserKey) {
        return new FreeFormOperation("$addFields", String.format("{\"blocked\": {\"$in\": [\"%s\", {\"$ifNull\": [\"$blockedBy\", []]}]}}", viewingUserKey));
    }


    private AggregationOperation buildFollowsLookup(String userKey) {
        return new FreeFormOperation("$lookup", String.format("{\"from\": \"follows\", " +
                "\"let\": {\"fKey\": \"%s\", \"uKey\": \"$userKey\"}, " +
                "\"as\": \"following\", " +
                "\"pipeline\": [  {\"$match\": {\"$expr\":" +
                "    { \"$and\": [" +
                "      {\"$eq\": [\"$followerKey\", \"$$fKey\"]}," +
                "      {\"$eq\": [\"$userKey\", \"$$uKey\"]}]}}}]}", userKey));
    }

    private AggregationOperation buildLookupFor(Group.Fields field) {
        return new PipelineLookupOperation(
                "groups",
                field.name(),
                Variables.builder()
                        .variable(Variable.from("userKey", "$userKey"))
                        .build(),
                new FreeFormOperation("$match",
                        String.format("{ $expr: " +
                                        "{ $and: [" +
                                        "  { $in: [\"$$userKey\", { \"$ifNull\" : [\"$" + field.name() + "\", []]}]}," +
                                        "  { $lte: [\"$deleted\", false]} " +
                                        "  ]}}",
                                field.name())),
                Aggregation.project(SimplifiedGroup.class)
        );
    }

    public Flux<User> sampleUsers(int userCount) {
        return mongoTemplate.aggregate(newAggregation(sample(userCount)), "users", User.class);
    }

    public Mono<Void> updateLocation(String userKey, GeoJsonPoint point) {
        Query q = new Query(Criteria.where(User.Fields.userKey.name()).is(userKey));
        val update = new Update();
        update.set(User.Fields.coordinates.name(), point);
        return mongoTemplate.updateFirst(q, update, User.class).then();
    }

    public Mono<Void> addFcmToken(String userKey, String fcmToken) {
        Query q = new Query(Criteria.where(User.Fields.userKey.name()).is(userKey));
        val update = new Update();
        update.addToSet(User.Fields.fcmTokens.name(), fcmToken);
        return mongoTemplate.updateFirst(q, update, User.class).then();
    }

    public Mono<Void> deleteFcmToken(String userKey, String fcmToken) {
        Query q = new Query(Criteria.where(User.Fields.userKey.name()).is(userKey));
        val update = new Update();
        update.pull(User.Fields.fcmTokens.name(), fcmToken);
        return mongoTemplate.updateFirst(q, update, User.class).then();
    }

    public Flux<ChatUser> lookupChatUsers(List<String> userKeys, String viewingUserKey) {
        return lookupRawChatUsers(userKeys)
                .flatMap(u -> Mono.just(u).zipWith(lookupRawChatUsers(Collections.singletonList(viewingUserKey)).singleOrEmpty()))
                .map(pair -> {
                    val u = pair.getT1();
                    val vu = pair.getT2();

                    return u.setBlocked(u.getBlockedBy().contains(vu.getUserKey()) || vu.getBlockedBy().contains(u.getUserKey()));
                });
    }

    private Flux<ChatUser> lookupRawChatUsers(List<String> userKeys) {
        Query q = new Query(Criteria.where(User.Fields.userKey.name()).in(userKeys));
        return mongoTemplate.find(q, ChatUser.class, "users");
    }

    public Mono<UpdateResult> setReported(String userKey, String reportedMessage) {
        val q = new Query(Criteria.where(User.Fields.userKey.name()).is(userKey));
        val u = new Update()
          .set(Group.Fields.reported.name(), true)
          .set(Group.Fields.reportedMessage.name(), reportedMessage)
          .set(Group.Fields.reportedSolved.name(), false)
          .set(Group.Fields.reportedTime.name(), Instant.now());
        return mongoTemplate.updateFirst(q, u, User.class);
    }

    public Mono<Void> blockUser(String userKey, String blockingUser) {
        val q = new Query(Criteria.where(User.Fields.userKey.name()).is(userKey));
        val u = new Update().push(User.Fields.blockedBy.name()).value(blockingUser);
        return mongoTemplate.updateFirst(q, u, User.class).then();
    }

    public Mono<Void> unblockUser(String userKey, String blockingUser) {
        val q = new Query(Criteria.where(User.Fields.userKey.name()).is(userKey));
        val u = new Update().pull(User.Fields.blockedBy.name(), blockingUser);
        return mongoTemplate.updateFirst(q, u, User.class).then();
    }

    public Flux<ExtendedUser> findBlockedUsers(String userKey, Pageable pageable) {
        return mongoTemplate.aggregate(
                newAggregation(
                        Stream.of(
                                        Stream.of(
                                                Aggregation.match(Criteria.where(User.Fields.blockedBy.name()).is(userKey)),
                                                Aggregation.skip(pageable.getOffset()),
                                                Aggregation.limit(pageable.getPageSize())),
                                        extendUserClauses(Optional.of(userKey)))
                                .flatMap(Function.identity())
                                .toArray(AggregationOperation[]::new)
                ),
                "users",
                ExtendedUser.class);
    }

    public Flux<ExtendedUser> findBlockedByUsers(String userKey, Pageable pageable) {
        return mongoTemplate.aggregate(
                newAggregation(
                        Stream.of(
                                        Stream.of(
                                                Aggregation.match(Criteria.where(User.Fields.userKey.name()).is(userKey)),
                                                Aggregation.unwind(User.Fields.blockedBy.name()),
                                                Aggregation.lookup("users", "blockedBy", "userKey", "user"),
                                                Aggregation.unwind("user"),
                                                Aggregation.replaceRoot("user"),
                                                Aggregation.skip(pageable.getOffset()),
                                                Aggregation.limit(pageable.getPageSize())),
                                        extendUserClauses(Optional.of(userKey)))
                                .flatMap(Function.identity())
                                .toArray(AggregationOperation[]::new)
                ),
                "users",
                ExtendedUser.class);
    }

    public Mono<Void> setLanguage(String userKey, String language) {
        Query q = new Query(Criteria.where(User.Fields.userKey.name()).is(userKey));
        val update = new Update();
        update.set(User.Fields.language.name(), language.toLowerCase(Locale.ROOT));
        return mongoTemplate.updateMulti(q, update, User.class).then();
    }
}
