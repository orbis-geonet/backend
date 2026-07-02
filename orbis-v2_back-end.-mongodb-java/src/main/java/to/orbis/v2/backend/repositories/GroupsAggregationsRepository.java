package to.orbis.v2.backend.repositories;

import com.google.common.collect.Lists;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Metrics;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.FreeFormOperation;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.UserSubscriptionStatus;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.repositories.queries.GroupQuery;
import to.orbis.v2.backend.utils.AggregationUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Stream.of;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.UnsetOperation.unset;
import static to.orbis.v2.backend.utils.AggregationUtils.ADD_FIELDS;
import static to.orbis.v2.backend.utils.AggregationUtils.LOOK_UP;

@Repository
@RequiredArgsConstructor
public class GroupsAggregationsRepository {

    ReactiveMongoTemplate mongoTemplate;

    public Flux<ExtendedGroup> findAll(GroupQuery groupQuery, Optional<GeoJsonPoint> userLocation) {
        val aggregation = newAggregation(buildOperations(groupQuery, userLocation));

        return mongoTemplate.aggregate(aggregation, "groups", ExtendedGroup.class);
    }

    private AggregationOperation[] buildOperations(GroupQuery groupQuery, Optional<GeoJsonPoint> userLocation) {

        val notRemoved = Lists.newArrayList(
                match(new Criteria().orOperator(Criteria.where(Group.Fields.deleted.name()).is(false),
                        Criteria.where(Group.Fields.deleted.name()).exists(false))));

        val nearClause = groupQuery.getPoint()
                .map(pnt -> NearQuery.near(pnt, Metrics.KILOMETERS));

        val geoOperation = nearClause
                .flatMap(clause -> groupQuery.getDistance()
                        .map(dist -> geoNear(clause.maxDistance(dist), "dst"))
                        .or(() -> Optional.of(geoNear(clause, "dst"))));

        val nameClause = groupQuery.getNameFilter().map(name ->
                match(Criteria.where(Group.Fields.name.name()).regex(".*" + name + ".*", "i")));


        val groupKeyClause = groupQuery.getGroupKey().map(key ->
                match(Criteria.where(Group.Fields.groupKey.name()).is(key)));

        val pagination = groupQuery.getPageable().stream().flatMap(pg ->
                Lists.newArrayList(
                        Aggregation.skip(pg.getOffset()),
                        Aggregation.limit(pg.getPageSize())).stream());

        val userDetails = Lists.newArrayList(buildSizeFor(Group.Fields.admins),
                buildSizeFor(Group.Fields.members),
                buildSizeFor(Group.Fields.followers)).stream();

        val hasStripeAccount = of (
                Aggregation.lookup("stripeAccount", Group.Fields.mainAdmin.name(), StripeAccount.Fields.userKey.name(), "stripeAccount"),
                new FreeFormOperation(ADD_FIELDS, String.format("{ \"%s\": {\"$ne\": [\"$stripeAccount\", []]}}", ExtendedGroup.Fields.hasStripeAccount.name())),
                lookup(AggregationUtils.getCollectionName(StripeAccount.class, false), Group.Fields.mainAdmin.name(), StripeAccount.Fields.userKey.name(), ExtendedGroup.Fields.mainUserStripeAccount.name()),
                unwind(ExtendedGroup.Fields.mainUserStripeAccount.name(), true)
        );

        val membershipClause = groupQuery.getUserKey().map(userKey ->
                        of(
                                membershipInfo(userKey, Group.Fields.admins, 1),
                                membershipInfo(userKey, Group.Fields.members, 1),
                                membershipInfo(userKey, Group.Fields.followers, 1),
                                membershipInfo(userKey, Group.Fields.storiesHidden, 0),
                                blockedInfo(userKey),
                                new FreeFormOperation(ADD_FIELDS, String.format("{ \"%s\": {\"$eq\": [\"$%s\", \"%s\"]}}", ExtendedGroup.Fields.isMainAdmin, Group.Fields.mainAdmin, userKey))
                        )
                )
                .stream().flatMap(Function.identity());

        var isSubscriber = groupQuery.getUserKey()
                .map(this::isSubscriber)
                .stream().flatMap(Function.identity());

        val skipGroupsClause = groupQuery.getSkipGroups().map(sg ->
                match(Criteria.where(Group.Fields.groupKey.name()).nin(sg))).stream();

        return of(geoOperation.stream(),
                notRemoved.stream(),
                nameClause.stream(),
                groupKeyClause.stream(),
                skipGroupsClause,
                pagination,
                userDetails,
                membershipClause,
                placesOwnedClause(),
                postsCountGeoClauses(userLocation),
                postsCountFinalClauses(),
                isSubscriber,
                hasStripeAccount,
                hasSubscriptions()
        )
                .flatMap(Function.identity())
                .toArray(AggregationOperation[]::new);
    }

    private Stream<AggregationOperation> postsCountFinalClauses() {
        return of(
                new FreeFormOperation("$set", "{\"postsThisWeek\": { \"$ifNull\": [\"$postsCount.postsThisWeek.cnt\", 0] }, \n" +
                        "     \"postsLastWeek\": {\"$ifNull\": [\"$postsCount.postsLastWeek.cnt\", 0]}}"),
                unset("postsCount")
        );
    }

    private Stream<AggregationOperation> isSubscriber(String userKey) {
        var subLookup = "subscribers";
        var subLookupCount = "subscribersCount";
        return of(
                new FreeFormOperation(LOOK_UP, String.format(
                        "{\n" +
                                "                from: \"%s\",\n" +
                                "                let: { \"sub_groupKey\": \"$%s\"},\n" +
                                "                pipeline: [\n" +
                                "                    { $match: { $expr: { $eq: [ \"$$sub_groupKey\", \"$%s\" ] } } },\n" +
                                "                    { $facet: { \"%s\" : [ \n" +
                                "                        { $match: { $expr: { $and: [ {\"$eq\": [\"$%s\", \"%s\"]}, {\"$eq\": [\"$%s\", \"%s\"]} ] } } }, \n" +
                                "                                { \"$group\" : {\"_id\": \"count\", \"count\": {\"$sum\": 1}}}\n" +
                                "                            ]}}\n" +
                                "                ],\n" +
                                "                as: \"%s\",\n" +
                                "            }",
                        AggregationUtils.getCollectionName(UserSubscription.class, false),
                        UserSubscription.Fields.groupKey.name(),
                        Group.Fields.groupKey.name(),
                        subLookupCount,
                        UserSubscription.Fields.status.name(),
                        UserSubscriptionStatus.ACTIVATED.name(),
                        UserSubscription.Fields.userKey.name(),
                        userKey,
                        subLookup
                )),
                unwind(subLookup),
                unwind(String.format("%s.%s.count", subLookup, subLookupCount), true),
                new FreeFormOperation(ADD_FIELDS, String.format(
                        "{\"%s\": { \"$ne\": [\"$%s\", [] ]}}",
                        ExtendedGroup.Fields.isSubscriber.name(),
                        String.format("%s.%s", subLookup, subLookupCount)
                ))
        );
    }

    private Stream<AggregationOperation> hasSubscriptions() {
        var subGroupLookup = "groupSubscriptions";
        var subGroupLookupCount = "subGroup";
        return of(
                new FreeFormOperation(LOOK_UP, String.format(
                        "{ from: \"%s\", \n" +
                                "                    let: { \"sub_groupKey\": \"$%s\"},\n" +
                                "                    pipeline: [\n" +
                                "                        { $match: { $expr: { $eq: [ \"$$sub_groupKey\", \"$%s\" ] } } },\n" +
                                "                        { $facet: { \"%s\": [\n" +
                                "                                { $match: { $expr: { $eq: [\"$%s\", %s] } }, },\n" +
                                "                                { \"$group\" : {\"_id\": \"count\", \"count\": {\"$sum\": 1}}}\n" +
                                "                            ]}}\n" +
                                "                    ],\n" +
                                "                as: \"%s\"}",
                        AggregationUtils.getCollectionName(Subscription.class, false),
                        Subscription.Fields.groupKey.name(),
                        Group.Fields.groupKey.name(),
                        subGroupLookupCount,
                        Subscription.Fields.deleted.name(),
                        "false",
                        subGroupLookup
                )),
                unwind(subGroupLookup),
                unwind(String.format("%s.%s.count", subGroupLookup, subGroupLookupCount), true),
                new FreeFormOperation(ADD_FIELDS, String.format(
                        "{\"%s\": { \"$ne\": [\"$%s\", [] ]}}",
                        ExtendedGroup.Fields.hasSubscription.name(),
                        String.format("%s.%s", subGroupLookup, subGroupLookupCount)
                ))
        );
    }

    private Stream<AggregationOperation> postsCountGeoClauses(Optional<GeoJsonPoint> userLocation) {
        val now = Instant.now();
        val weekAgo = now.minus(7, ChronoUnit.DAYS);
        val twoWeeksAgo = now.minus(14, ChronoUnit.DAYS);
        return userLocation.stream().flatMap(ul ->
                {
                    final String lookup = String.format(
                            "{ \"from\" : \"posts\", \"let\" : { \"groupKey\" : \"$groupKey\"}, \n" +
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

    private AggregationOperation blockedInfo(String userKey) {
        val name = "is" + capitalize(Group.Fields.blockedBy.name()).substring(0, Group.Fields.blockedBy.name().length()) + "User";
        return new FreeFormOperation("$addFields", String.format("{\"%s\": {\"$in\": [\"%s\", {\"$ifNull\": [\"$%s\", []]}]}}", name, userKey, Group.Fields.blockedBy.name()));
    }

    private AggregationOperation membershipInfo(String userKey, Group.Fields memberType, int trimLetters) {
        val name = "is" + capitalize(memberType.name()).substring(0, memberType.name().length() - trimLetters);
        return new FreeFormOperation("$addFields", String.format("{\"%s\": {\"$in\": [\"%s\", {\"$ifNull\": [\"$%s\", []]}]}}", name, userKey, memberType.name()));
    }

    private Stream<AggregationOperation> placesOwnedClause() {
        return Lists.newArrayList(
                new FreeFormOperation("$lookup", "{\"from\" : \"places\", let: {groupKey: \"$groupKey\"}, pipeline: [" +
                        "{ $match: {$expr: {$eq: [\"$dominantGroupKey\", \"$$groupKey\"]}}}," +
                        "{ $group: {_id: \"cnt\", cnt: {$sum: 1}}}" +
                        "], \"as\": \"placesCount\"}"),
                unwind("placesCount", true),
                new FreeFormOperation("$addFields", "{placesCount: {$ifNull: [\"$placesCount.cnt\", 0]}}")
        ).stream();
    }

    private FreeFormOperation removeMembershipInfo() {
        return new FreeFormOperation("$unset", "[\"admins\", \"members\", \"followers\"]");
    }

    private AggregationOperation buildSizeFor(Group.Fields field) {
        return new FreeFormOperation("$addFields", String.format("{ \"%sCount\": {\"$size\": \"$%s\"}}", field.name(), field.name()));
    }

    public Flux<ExtendedGroup> findGroupsComposition(Place place) {
        return mongoTemplate.aggregate(newAggregation(
                        match(Criteria.where("placeKey").is(place.getPlaceKey())
                                .and("validTimestamp").gte(Instant.now().minus(1, ChronoUnit.DAYS))),
                        group("groupKey").count().as("validCheckins"),
                        lookup("groups", "_id", "groupKey", "group"),
                        unwind("group"),
                        addFields().addField("group.validCheckins").withValue("$validCheckins").build(),
                        replaceRoot().withValueOf("group"),
                        match(Criteria.where(Group.Fields.deleted.name()).is(false)),
                        removeMembershipInfo()
                ),
                "checkins", ExtendedGroup.class);
    }

    @SafeVarargs
    private static Aggregation aggregation(Stream<AggregationOperation>... aggregations) {
        return newAggregation(Arrays.stream(aggregations).flatMap(Function.identity()).toArray(AggregationOperation[]::new));
    }

    public Flux<SimplifiedGroup> findLastInteractions(String userKey, int size, GeoJsonPoint point) {
        return mongoTemplate.aggregate(aggregation(
                of(match(Criteria.where("userKey").is(userKey).and("deleted").is(false)),
                        sort(Sort.by(Post.Fields.timestamp.name()).descending()),
                        group(Group.Fields.groupKey.name()).count().as("cnt"),
                        sort(Sort.by("cnt").descending()),
                        lookup("groups", "_id", "groupKey", "group"),
                        unwind("group"),
                        replaceRoot().withValueOf("group"),
                        match(Criteria.where(Group.Fields.deleted.name()).is(false)),
                        limit(size)),
                placesOwnedClause(),
                postsCountGeoClauses(Optional.of(point)),
                postsCountFinalClauses(),
                of(project(SimplifiedGroup.class))
        ), "posts", SimplifiedGroup.class);
    }

    public Flux<SimplifiedGroup> findLastActiveAround(GeoJsonPoint point, int size, List<SimplifiedGroup> alreadyFound) {
        Aggregation aggregation = aggregation(
                of(geoNear(NearQuery.near(point).maxDistance(50, Metrics.KILOMETERS).query(Query.query(
                                new Criteria().andOperator(
                                        Criteria.where("groupKey").exists(true),
                                        Criteria.where("groupKey").nin(alreadyFound.stream().map(SimplifiedGroup::getGroupKey).toArray()),
                                        Criteria.where("deleted").is(false),
                                        Criteria.where("timestamp").gte(Instant.now().minus(7, ChronoUnit.DAYS))))), "dist")
                                .useIndex("coordinates"),

                        limit(100 + 10L * size),
                        group(Group.Fields.groupKey.name()).count().as("cnt"),
                        sort(Sort.by("cnt").descending()),
                        limit(size),
                        lookup("groups", "_id", "groupKey", "group"),
                        unwind("group"),
                        replaceRoot().withValueOf("group"),
                        match(Criteria.where(Group.Fields.deleted.name()).is(false))),
                placesOwnedClause(),
                postsCountGeoClauses(Optional.of(point)),
                postsCountFinalClauses()
        );
        return mongoTemplate.aggregate(aggregation, "posts", SimplifiedGroup.class);
    }

    public Mono<GroupStripeAccountInfo> findMainAdminStripeInfo(String groupKey) {
        var lookupUsers = "mainAdminUser";
        var lookupStripeAccount = "mainAdminStripeAccount";
        return mongoTemplate.aggregate(
                        newAggregation(
                                match(Criteria.where(Group.Fields.groupKey.name()).is(groupKey)),
                                lookup(AggregationUtils.getCollectionName(User.class, true), Group.Fields.mainAdmin.name(), User.Fields.userKey.name(), lookupUsers),
                                unwind(lookupUsers),
                                lookup(AggregationUtils.getCollectionName(StripeAccount.class, false), Group.Fields.mainAdmin.name(), StripeAccount.Fields.userKey.name(), lookupStripeAccount),
                                unwind(lookupStripeAccount),
                                new FreeFormOperation("$project", String.format("{_id: 0, \"%s\": 1, \"%s\": 1, \"%s\": \"$%s.%s\", \"%s\": \"$%s.%s\"}", Group.Fields.groupKey.name(), Group.Fields.mainAdmin.name(), GroupStripeAccountInfo.Fields.partnerKey.name(), lookupUsers, User.Fields.partnerKey.name(), GroupStripeAccountInfo.Fields.stripeId.name(), lookupStripeAccount,StripeAccount.Fields.stripeId.name()))
                        ),
                        AggregationUtils.getCollectionName(Group.class, true),
                        GroupStripeAccountInfo.class
                )
                .singleOrEmpty();
    }

    public Flux<SimplifiedGroup> findRatedGroupsForPeriod(GeoJsonPoint point, Instant periodStart, Instant periodEnd, int size) {
        /*
        db.getCollection('posts').aggregate([
{$geoNear: { near:  { type: "Point", coordinates: [ 10, 10 ] }, spherical: true, maxDistance: 50000, distanceField: "dist"}},
{$match: {timestamp: {$gte: ISODate("2021-05-13T11:44Z")}}},
{$addFields: {
      inPeriod: {$gte: ["$timestamp", ISODate("2021-05-14T11:44Z")]}
    }},

{$group: {_id: {groupKey: "$groupKey", inPeriod: "$inPeriod"}, cnt: {$sum: 1}}},
    {$match: {"_id.inPeriod": false}},
    {$sort: {"cnt": -1}},
    {$group: {"_id": "some", groups: {$push: {groupKey: "$_id.groupKey"}}}},
    { $unwind: { "path": "$groups", "includeArrayIndex": "groups.rank" }},
    //{$limit: 20},
    -> { $replaceWith: {_id: "$groups.groupKey", groupKey: "$groups.groupKey", rank: "$groups.rank"}},
    { $lookup: {from: "groups", localField: "_id", foreignField: "groupKey", as: "group"}},
    { $unwind: "$group" },
    {$set: {"group.rank": "$rank"}},
    {$replaceRoot: {newRoot: "$group"}}

])

         */

        Aggregation aggregations = aggregation(
                of(
                        geoNear(NearQuery.near(point).maxDistance(50, Metrics.KILOMETERS), "dist").useIndex("coordinates"),
                        match(Criteria.where("timestamp").gte(periodStart).lt(periodEnd).and("deleted").is(false)),
                        group("groupKey").count().as("cnt"),
                        sort(Sort.by("cnt").descending()),
                        new FreeFormOperation("$group", "{\"_id\": \"some\", groups: {$push: {groupKey: \"$_id\"}}}"),
                        new FreeFormOperation("$unwind", "{ \"path\": \"$groups\", \"includeArrayIndex\": \"groups.rank\" }"),
                        new FreeFormOperation("$replaceWith", "{_id: \"$groups.groupKey\", groupKey: \"$groups.groupKey\", rank: \"$groups.rank\"}"),
                        lookup("groups", "_id", "groupKey", "group"),
                        unwind("group"),
                        new FreeFormOperation("$set", "{\"group.rank\": \"$rank\"}"),
                        replaceRoot("group"),
                        match(Criteria.where(Group.Fields.deleted.name()).is(false)),
                        limit(size)),
                placesOwnedClause(),
                postsCountGeoClauses(Optional.of(point)),
                postsCountFinalClauses()
        );
        return mongoTemplate.aggregate(aggregations, "posts", SimplifiedGroup.class);
    }

    public Flux<SimplifiedGroup> findGroupsWithUser(String userKey, Group.Fields memberType, Optional<GeoJsonPoint> viewerLocation, Pageable pageable) {
        val aggregation = newAggregation(
                of(
                        of(match(Criteria.where(memberType.name()).is(userKey).and(Group.Fields.deleted.name()).is(false)),
                                sort(Sort.Direction.ASC, Group.Fields.groupKey.name()),
                                skip(pageable.getOffset()),
                                limit(pageable.getPageSize())),
                        placesOwnedClause(),
                        postsCountGeoClauses(viewerLocation),
                        postsCountFinalClauses()
                        )
                        .flatMap(Function.identity())
                        .toArray(AggregationOperation[]::new)
        );

        return mongoTemplate.aggregate(aggregation, "groups", SimplifiedGroup.class);
    }

    public Flux<SimplifiedUser> findGroupUsers(String groupKey, Group.Fields userField, Pageable pageable) {
        var aggregation = aggregation(of(
                match(Criteria.where(Group.Fields.groupKey.name()).is(groupKey).and(Group.Fields.deleted.name()).is(false)),
                unwind(userField.name()),
                new FreeFormOperation("$replaceRoot", "{newRoot: {_id: \"$" + userField.name() + "\"}}"),
                skip(pageable.getOffset()),
                limit(pageable.getPageSize()),
                lookup("users", "_id", "userKey", "user"),
                unwind("user"),
                replaceRoot("user"),
                project(SimplifiedUser.class)
        ));
        return mongoTemplate.aggregate(aggregation, "groups", SimplifiedUser.class);
    }

    public Mono<UpdateResult> setReported(String groupKey, String reportedMessage) {
        val q = new Query(Criteria.where(Group.Fields.groupKey.name()).is(groupKey));
        val u = new Update()
          .set(Group.Fields.reported.name(), true)
          .set(Group.Fields.reportedMessage.name(), reportedMessage)
          .set(Group.Fields.reportedSolved.name(), false)
          .set(Group.Fields.reportedTime.name(), Instant.now());
        return mongoTemplate.updateFirst(q, u, Group.class);
    }

    public Mono<UpdateResult> setSubscriptionActivated(String mainAdminUserKey) {
        val q = new Query(Criteria.where(Group.Fields.mainAdmin.name()).is(mainAdminUserKey));
        val u = new Update()
                .set(Group.Fields.isSubscriptionActivate.name(), true)
                .set(Group.Fields.timestamp.name(), Instant.now());
        return mongoTemplate.updateMulti(q, u, Group.class);
    }

    public Flux<SimplifiedGroup> sampleGroups(GeoJsonPoint point, int size, List<SimplifiedGroup> foundGroups) {
        return mongoTemplate.aggregate(aggregation(
                of(match(new Criteria().andOperator(
                                        Criteria.where(Group.Fields.groupKey.name()).exists(true),
                                        Criteria.where(Group.Fields.groupKey.name()).nin(foundGroups.stream().map(SimplifiedGroup::getGroupKey).toArray()),
                                        Criteria.where(Group.Fields.deleted.name()).is(false))),

                        sample(size),
                        match(Criteria.where(Group.Fields.deleted.name()).is(false))),
                placesOwnedClause(),
                postsCountGeoClauses(Optional.of(point)),
                postsCountFinalClauses()
        ), "groups", SimplifiedGroup.class);
    }

    public Mono<Void> blockGroup(String groupKey, String userKey) {
        val q = new Query(Criteria.where(Group.Fields.groupKey.name()).is(groupKey));
        val u = new Update().push(Group.Fields.blockedBy.name()).value(userKey);
        return mongoTemplate.updateFirst(q, u, Group.class).then();
    }

    public Mono<Void> unblockGroup(String groupKey, String userKey) {
        val q = new Query(Criteria.where(Group.Fields.groupKey.name()).is(groupKey));
        val u = new Update().pull(Group.Fields.blockedBy.name(), userKey);
        return mongoTemplate.updateFirst(q, u, Group.class).then();
    }
}
