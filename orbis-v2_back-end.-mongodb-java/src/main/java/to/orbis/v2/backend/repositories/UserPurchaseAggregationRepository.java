package to.orbis.v2.backend.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.FreeFormOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.PaymentStatus;
import to.orbis.v2.backend.models.PaymentType;
import to.orbis.v2.backend.models.UserSubscriptionStatus;
import to.orbis.v2.backend.models.dto.StatisticRequestDto;
import to.orbis.v2.backend.models.dto.StatisticDto;
import to.orbis.v2.backend.models.dto.UserPurchaseDto;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.utils.AggregationUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
@RequiredArgsConstructor
public class UserPurchaseAggregationRepository {
    ReactiveMongoTemplate mongoTemplate;

    public Flux<UserPurchaseDto> findAllUserPurchaseByUserKey(String groupKey, String userKey, PageRequest page) {
        var criteria = Criteria.where(UserPurchase.Fields.userKey.name()).is(userKey)
                .and(UserPurchase.Fields.status.name()).is(UserSubscriptionStatus.FINISHED);
        if (Objects.nonNull(groupKey) && !groupKey.isEmpty()) {
            criteria.and(UserPurchase.Fields.groupKey.name()).is(groupKey);
        }
        return findAllUserPurchase(criteria, page);
    }

    public Flux<UserPurchaseDto> getAllGroupPurchase(String groupKey, String purchaseKey, PageRequest page) {
        var criteria = Criteria.where(UserPurchase.Fields.groupKey.name()).is(groupKey)
                .and(UserPurchase.Fields.status.name()).is(UserSubscriptionStatus.FINISHED);

        if (Objects.nonNull(purchaseKey)) {
            criteria.and(UserPurchase.Fields.purchaseKey.name()).is(purchaseKey);
        }
        return findAllUserPurchase(criteria, page);
    }

    private Flux<UserPurchaseDto> findAllUserPurchase(Criteria matchCriteria, PageRequest page) {
        var lookupGroup = "group";
        var lookupSubscription = "sub";
        var lookupUser = "user";
        var aggregation = newAggregation(
                lookup(AggregationUtils.getCollectionName(Group.class, true), UserPurchase.Fields.groupKey.name(), Group.Fields.groupKey.name(), lookupGroup),
                unwind(lookupGroup),
                lookup(AggregationUtils.getCollectionName(Subscription.class, false), UserPurchase.Fields.purchaseKey.name(), Subscription.Fields.subscriptionKey.name(), lookupSubscription),
                unwind(lookupSubscription),
                lookup(AggregationUtils.getCollectionName(User.class, true), UserPurchase.Fields.userKey.name(), User.Fields.userKey.name(), lookupUser),
                unwind(lookupUser),
                addFields()
                        .addField(UserPurchaseDto.Fields.name.name())
                        .withValue(AggregationUtils.makeValueName(lookupSubscription, Subscription.Fields.name.name()))
                        .addField(UserPurchaseDto.Fields.groupName.name())
                        .withValue(AggregationUtils.makeValueName(lookupGroup, Group.Fields.name.name()))
                        .addField(UserPurchaseDto.Fields.groupKey.name())
                        .withValue(AggregationUtils.makeValueName(lookupGroup, Group.Fields.groupKey.name()))
                        .addField(UserPurchaseDto.Fields.userKey.name())
                        .withValue(AggregationUtils.makeValueName(lookupUser, User.Fields.userKey.name()))
                        .addField(UserPurchaseDto.Fields.displayName.name())
                        .withValue(AggregationUtils.makeValueName(lookupUser, User.Fields.displayName.name()))
                        .addField(UserPurchaseDto.Fields.price.name())
                        .withValue(AggregationUtils.makeValueName(lookupSubscription, Subscription.Fields.price.name()))
                        .addField(UserPurchaseDto.Fields.currency.name())
                        .withValue(AggregationUtils.makeValueName(lookupSubscription, Subscription.Fields.currency.name()))
                        .build(),
                match(matchCriteria),
                Aggregation.skip(page.getOffset()),
                Aggregation.limit(page.getPageSize())
        );
        return mongoTemplate.aggregate(
                aggregation,
                AggregationUtils.getCollectionName(UserPurchase.class, false),
                UserPurchaseDto.class
        );
    }

    private Criteria getCriteria(String userKey, String groupKey) {
        var criteria = Criteria.where(UserPurchase.Fields.userKey.name()).is(userKey)
                .and(UserPurchase.Fields.status.name()).is(UserSubscriptionStatus.FINISHED);
        if (Objects.nonNull(groupKey) && !groupKey.isEmpty()) {
            criteria.and(UserPurchase.Fields.groupKey.name()).is(groupKey);
        }
        return criteria;
    }

    public Mono<List<StatisticDto>> getCountForStatistic(StatisticRequestDto requestDto) {
        return mongoTemplate.aggregate(
                        newAggregation(
                                match(
                                        Criteria.where(UserPurchase.Fields.createTimestamp.name()).gte(requestDto.getStartDate()).lte(requestDto.getEndDate())
                                                .and(UserPurchase.Fields.status.name()).is(UserSubscriptionStatus.FINISHED)
                                                .and(UserPurchase.Fields.purchaseKey.name()).is(requestDto.getKey())
                                ),
                                new FreeFormOperation("$group", String.format("{_id: {groupColumnName: {$dateToString:{format: \"%s\", date: \"$%s\"}}}, count: {$sum: 1}}", requestDto.getDataPattern(), UserPurchase.Fields.createTimestamp.name())),
                                new FreeFormOperation("$replaceRoot", String.format("{newRoot: { $mergeObjects: [{%s: \"$_id.groupColumnName\"}, {%s: \"$count\" }]}}", StatisticDto.Fields.columnName.name(), StatisticDto.Fields.number.name())),
                                new FreeFormOperation("$sort", String.format("{ \"%s\": 1}", StatisticDto.Fields.columnName.name()))
                        ),
                        AggregationUtils.getCollectionName(UserPurchase.class, false),
                        StatisticDto.class
                ).buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    public Mono<List<StatisticDto>> getAmountForStatistic(StatisticRequestDto requestDto) {
        var lookupSubscription = "sub";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(UserPurchase.class, false), Payment.Fields.userSubscriptionKey.name(), UserPurchase.Fields.userPurchaseKey.name(), lookupSubscription),
                                unwind(lookupSubscription),
                                match(
                                        Criteria.where(Payment.Fields.createTimestamp.name()).gte(requestDto.getStartDate()).lte(requestDto.getEndDate())
                                                .and(Payment.Fields.status.name()).is(PaymentStatus.SUCCEEDED)
                                                .and(Payment.Fields.paymentType.name()).is(PaymentType.ONE_TIME_PAYMENT)
                                                .and(AggregationUtils.makeMatchName(lookupSubscription, UserPurchase.Fields.purchaseKey.name())).is(requestDto.getKey())
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
