package to.orbis.v2.backend.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.FreeFormOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.*;
import to.orbis.v2.backend.models.Currency;
import to.orbis.v2.backend.models.dto.*;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.utils.AggregationUtils;

import java.util.*;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static to.orbis.v2.backend.utils.AggregationUtils.ADD_FIELDS;
import static to.orbis.v2.backend.utils.AggregationUtils.LOOK_UP;

@Repository
@RequiredArgsConstructor
public class SubscriptionAggregationRepository {
    ReactiveMongoTemplate mongoTemplate;

    public Flux<UserSubscriptionDto> findAllUserSubscriptionByUserKey(String groupKey, String userKey) {
        var lookupGroup = "group";
        var lookupSubscription = "sub";
        return mongoTemplate.aggregate(
                newAggregation(
                        lookup(AggregationUtils.getCollectionName(Group.class, true), UserSubscription.Fields.groupKey.name(), Group.Fields.groupKey.name(), lookupGroup),
                        unwind(lookupGroup),
                        lookup(AggregationUtils.getCollectionName(Subscription.class, false), UserSubscription.Fields.subscriptionKey.name(), Subscription.Fields.subscriptionKey.name(), lookupSubscription),
                        unwind(lookupSubscription),
                        addFields()
                                .addField(UserSubscriptionDto.Fields.groupName.name())
                                .withValue(AggregationUtils.makeValueName(lookupGroup, Group.Fields.name.name()))
                                .addField(UserSubscriptionDto.Fields.name.name())
                                .withValue(AggregationUtils.makeValueName(lookupSubscription, Subscription.Fields.name.name()))
                                .addField(UserSubscriptionDto.Fields.price.name())
                                .withValue(AggregationUtils.makeValueName(lookupSubscription, Subscription.Fields.price.name()))
                                .addField(UserSubscriptionDto.Fields.currency.name())
                                .withValue(AggregationUtils.makeValueName(lookupSubscription, Subscription.Fields.currency.name()))
                                .addField(UserSubscriptionDto.Fields.benefit.name())
                                .withValue(AggregationUtils.makeValueName(lookupSubscription, Subscription.Fields.benefit.name()))
                                .build(),
                        match(getCriteria(userKey, groupKey))
                ),
                AggregationUtils.getCollectionName(UserSubscription.class, false),
                UserSubscriptionDto.class
        );
    }

    public Mono<CountResult> countSubscriptionWithOtherCurrency(Currency currency, String userKey) {
        var lookupSubscription = "sub";
        return mongoTemplate.aggregate(
                newAggregation(
                        lookup(AggregationUtils.getCollectionName(Subscription.class, false), UserSubscription.Fields.subscriptionKey.name(), Subscription.Fields.subscriptionKey.name(), lookupSubscription),
                        unwind(lookupSubscription),
                        match(Criteria.where(AggregationUtils.makeMatchName(lookupSubscription, Subscription.Fields.currency.name())).ne(currency.name())
                                .and(UserSubscription.Fields.userKey.name()).is(userKey)
                                .and(UserSubscription.Fields.status.name()).is(UserSubscriptionStatus.ACTIVATED)),
                        count().as("result")
                ),
                        AggregationUtils.getCollectionName(UserSubscription.class, false),
                        CountResult.class
                )
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(new CountResult(0)));
    }

    public Flux<SimplifiedUserDto> findGroupUserStatistic(String groupKey, String subscriptionKey, PageRequest pageable) {
        var lookupUserSubscription = "userSub";
        var aggregation = newAggregation(
                lookup(AggregationUtils.getCollectionName(UserSubscription.class, false), User.Fields.userKey.name(), UserSubscription.Fields.userKey.name(), lookupUserSubscription),
                unwind(lookupUserSubscription),
                match(getMembersCriteria(lookupUserSubscription, groupKey, subscriptionKey)),
                sort(Sort.Direction.DESC, User.Fields.displayName.name()),
                addFields()
                        .addField(SimplifiedUserDto.Fields.codes.name())
                        .withValue(AggregationUtils.makeValueName(lookupUserSubscription, UserSubscription.Fields.codes.name()))
                        .build(),
                skip(pageable.getOffset()), limit(pageable.getPageSize())
        );
        return mongoTemplate.aggregate(
                aggregation,
                AggregationUtils.getCollectionName(User.class, true),
                SimplifiedUserDto.class
        );
    }

    public Flux<SubscriptionDto> findAll(String groupKey, String userKey, PageRequest pageable) {
        var subLookup = "subscribers";
        var subCountLookup = "subCount";
        return mongoTemplate.aggregate(
                newAggregation(
                    new FreeFormOperation(LOOK_UP, String.format(
                            "{from: \"%s\",\n" +
                                    "            let: { \"sub_key\": \"$%s\"},\n" +
                                    "            pipeline: [\n" +
                                    "                { $match: { $expr: { $eq: [ \"$$sub_key\", \"$%s\" ] } } },\n" +
                                    "                { $facet: { \"%s\": [\n" +
                                    "                            { $match: { $expr: { $and: [ { $eq: [\"$%s\", \"%s\"] }, { $eq: [\"$%s\", \"%s\"] } ] } }, },\n" +
                                    "                            { \"$group\" : {\"_id\": \"count\", \"count\": {\"$sum\": 1}}}\n" +
                                    "                        ]}}\n" +
                                    "            ],\n" +
                                    "            as: \"%s\"}",
                            AggregationUtils.getCollectionName(UserSubscription.class, false),
                            UserSubscription.Fields.subscriptionKey.name(),
                            Subscription.Fields.subscriptionKey.name(),
                            subCountLookup,
                            UserSubscription.Fields.status.name(),
                            UserSubscriptionStatus.ACTIVATED,
                            UserSubscription.Fields.userKey.name(),
                            userKey,
                            subLookup)),
                        unwind(subLookup),
                        unwind(String.format("%s.%s.count", subLookup, subCountLookup), true),
                        new FreeFormOperation(ADD_FIELDS, String.format(
                                "{\"%s\": { \"$ne\": [\"$%s\", [] ]}}",
                                SubscriptionDto.Fields.isSubscriber.name(),
                                String.format("%s.%s", subLookup, subCountLookup)
                        )),
                        match(Criteria.where(Subscription.Fields.groupKey.name()).is(groupKey)
                                .and(Subscription.Fields.deleted.name()).is(false)),
                        Aggregation.skip(pageable.getOffset()),
                        Aggregation.limit(pageable.getPageSize())
                ),
                AggregationUtils.getCollectionName(Subscription.class, false),
                SubscriptionDto.class
        );
    }

    private Criteria getCriteria(String userKey, String groupKey) {
        var criteria = Criteria.where(UserSubscription.Fields.userKey.name()).is(userKey)
                .and(UserSubscription.Fields.status.name()).is(UserSubscriptionStatus.ACTIVATED);
        if (Objects.nonNull(groupKey) && !groupKey.isEmpty()) {
            criteria.and(UserSubscription.Fields.groupKey.name()).is(groupKey);
        }
        return criteria;
    }

    private Criteria getMembersCriteria(String lookupUserSubscription, String groupKey, String subscriptionKey) {
        var criteria = Criteria.where(AggregationUtils.makeMatchName(lookupUserSubscription, UserSubscription.Fields.status.name())).is(UserSubscriptionStatus.ACTIVATED)
                .and(AggregationUtils.makeMatchName(lookupUserSubscription, UserSubscription.Fields.groupKey.name())).is(groupKey);
        if (Objects.nonNull(subscriptionKey) && !subscriptionKey.isEmpty()) {
            criteria.and(AggregationUtils.makeMatchName(lookupUserSubscription, UserSubscription.Fields.subscriptionKey.name())).is(subscriptionKey);
        }
        return criteria;
    }

    public Mono<List<StatisticDto>> getCountForStatistic(StatisticRequestDto requestDto) {
        return mongoTemplate.aggregate(
                        newAggregation(
                                match(
                                        Criteria.where(UserSubscription.Fields.createTimestamp.name()).gte(requestDto.getStartDate()).lte(requestDto.getEndDate())
                                                .and(UserSubscription.Fields.status.name()).is(UserSubscriptionStatus.ACTIVATED)
                                                .and(UserSubscription.Fields.subscriptionKey.name()).is(requestDto.getKey())
                                ),
                                new FreeFormOperation("$group", String.format("{_id: {groupColumnName: {$dateToString:{format: \"%s\", date: \"$%s\"}}}, count: {$sum: 1}}", requestDto.getDataPattern(), UserSubscription.Fields.createTimestamp.name())),
                                new FreeFormOperation("$replaceRoot", String.format("{newRoot: { $mergeObjects: [{%s: \"$_id.groupColumnName\"}, {%s: \"$count\" }]}}", StatisticDto.Fields.columnName.name(), StatisticDto.Fields.number.name())),
                                new FreeFormOperation("$sort", String.format("{ \"%s\": 1}", StatisticDto.Fields.columnName.name()))
                        ),
                        AggregationUtils.getCollectionName(UserSubscription.class, false),
                        StatisticDto.class
                ).buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    public Mono<List<StatisticDto>> getAmountForStatistic(StatisticRequestDto requestDto) {
        var lookupSubscription = "sub";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(UserSubscription.class, false), Payment.Fields.userSubscriptionKey.name(), UserSubscription.Fields.userSubscriptionKey.name(), lookupSubscription),
                                unwind(lookupSubscription),
                                match(
                                        Criteria.where(Payment.Fields.createTimestamp.name()).gte(requestDto.getStartDate()).lte(requestDto.getEndDate())
                                                .and(Payment.Fields.status.name()).is(PaymentStatus.SUCCEEDED)
                                                .and(Payment.Fields.paymentType.name()).is(PaymentType.SUBSCRIPTION_PAYMENT)
                                                .and(AggregationUtils.makeMatchName(lookupSubscription, UserSubscription.Fields.subscriptionKey.name())).is(requestDto.getKey())
                                ),
                                new FreeFormOperation("$group", String.format("{_id: {dayOfSubscription: {$dateToString:{format: \"%s\", date: \"$%s\"}}}, amount: {$sum: {$toDouble: \"$%s\"}}}", requestDto.getDataPattern(), Payment.Fields.createTimestamp.name(), Payment.Fields.amountAfterStripCommission.name())),
                                new FreeFormOperation("$replaceRoot", String.format("{newRoot: { $mergeObjects: [{%s: \"$_id.dayOfSubscription\"}, { %s: \"$amount\" }]} }", StatisticDto.Fields.columnName.name(), StatisticDto.Fields.amount.name())),
                                new FreeFormOperation("$sort", String.format("{ \"%s\": 1}", StatisticDto.Fields.columnName.name()))
                        ),
                        AggregationUtils.getCollectionName(Payment.class, false),
                        StatisticDto.class
                ).buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }
}
