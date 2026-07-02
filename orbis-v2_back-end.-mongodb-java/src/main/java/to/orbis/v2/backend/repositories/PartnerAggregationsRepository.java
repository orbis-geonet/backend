package to.orbis.v2.backend.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.FreeFormOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.PartnerStatisticType;
import to.orbis.v2.backend.models.dto.partner.*;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.utils.AggregationUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
@RequiredArgsConstructor
public class PartnerAggregationsRepository {

    ReactiveMongoTemplate mongoTemplate;


    public Mono<PartnerStripeAccountInfo> findPartnerStripeInfo(String partnerKey) {
        var lookupStripeAccount = "partnerStripeAccount";
        return mongoTemplate.aggregate(
                        newAggregation(
                                match(Criteria.where(Partner.Fields.partnerKey.name()).is(partnerKey)),
                                lookup(AggregationUtils.getCollectionName(StripeAccount.class, false), Partner.Fields.userKey.name(), StripeAccount.Fields.userKey.name(), lookupStripeAccount),
                                unwind(lookupStripeAccount),
                                new FreeFormOperation("$project", String.format("{_id: 0, \"%s\": 1, \"%s\": 1, \"%s\": \"$%s.%s\"}", Partner.Fields.partnerKey.name(), Partner.Fields.userKey.name(), GroupStripeAccountInfo.Fields.stripeId.name(), lookupStripeAccount, StripeAccount.Fields.stripeId.name()))
                        ),
                        AggregationUtils.getCollectionName(Partner.class, false),
                        PartnerStripeAccountInfo.class
                )
                .singleOrEmpty();
    }

    public Mono<CountResult> countNewUsers(String partnerKey) {
        return mongoTemplate.aggregate(
                        newAggregation(
                                match(Criteria.where(User.Fields.partnerKey.name()).is(partnerKey)),
                                count().as("result")
                        ),
                        AggregationUtils.getCollectionName(User.class, true),
                        CountResult.class
                )
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(new CountResult(0)));
    }

    public Mono<List<PartnerStatisticDto>> getUsersStatistic(PartnerStatisticType type, LocalDate from, LocalDate till, String partnerKey) {
        return mongoTemplate.aggregate(
                        newAggregation(
                                match(
                                        Criteria.where(User.Fields.createTimestamp.name()).gte(from).lte(till)
                                                .and(User.Fields.partnerKey.name()).is(partnerKey)
                                ),
                                new FreeFormOperation("$group", String.format("{ _id: {groupColumnName: {$dateToString:{format: \"%s\", date: \"$%s\"}}}, count: {$sum: 1} }", type.getFormat(), User.Fields.createTimestamp.name())),
                                new FreeFormOperation("$replaceRoot", String.format("{ newRoot: { $mergeObjects: [{%s: \"$_id.groupColumnName\"}, {%s: \"$count\" }]}}", PartnerStatisticDto.Fields.columnName.name(), PartnerStatisticDto.Fields.number.name())),
                                new FreeFormOperation("$sort", String.format("{ \"%s\": 1}", PartnerStatisticDto.Fields.columnName.name()))
                        ),
                        AggregationUtils.getCollectionName(User.class, true),
                        PartnerStatisticDto.class
                ).buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    public Mono<CountResult> countGroups(String partnerKey) {
        var lookupGroup = "lookupGroup";
        return mongoTemplate.aggregate(
                        newAggregation(
                                match(Criteria.where(User.Fields.partnerKey.name()).is(partnerKey)),
                                lookup(AggregationUtils.getCollectionName(Group.class, true), User.Fields.userKey.name(), Group.Fields.mainAdmin.name(), lookupGroup),
                                unwind(lookupGroup),
                                count().as("result")
                        ),
                        AggregationUtils.getCollectionName(User.class, true),
                        CountResult.class
                )
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(new CountResult(0)));
    }

    public Mono<List<PartnerStatisticDto>> getGroupsStatistic(PartnerStatisticType type, LocalDate from, LocalDate till, String partnerKey) {
        var lookupGroup = "lookupGroup";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(Group.class, true), User.Fields.userKey.name(), Group.Fields.mainAdmin.name(), lookupGroup),
                                unwind(lookupGroup),
                                match(
                                        Criteria.where(AggregationUtils.makeMatchName(lookupGroup, Group.Fields.createTimestamp.name())).gte(from).lte(till)
                                                .and(User.Fields.partnerKey.name()).is(partnerKey)
                                ),
                                new FreeFormOperation("$group", String.format("{ _id: {groupColumnName: {$dateToString:{format: \"%s\", date: \"$%s.%s\"}}}, count: {$sum: 1} }", type.getFormat(), lookupGroup, User.Fields.createTimestamp.name())),
                                new FreeFormOperation("$replaceRoot", String.format("{ newRoot: { $mergeObjects: [{%s: \"$_id.groupColumnName\"}, {%s: \"$count\" }]}}", PartnerStatisticDto.Fields.columnName.name(), PartnerStatisticDto.Fields.number.name())),
                                new FreeFormOperation("$sort", String.format("{ \"%s\": 1}", PartnerStatisticDto.Fields.columnName.name()))
                        ),
                        AggregationUtils.getCollectionName(User.class, true),
                        PartnerStatisticDto.class
                ).buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    public Mono<List<PartnerStatisticGroupInfoDto>> getGroupsStatisticInfo(LocalDate from, LocalDate till, String partnerKey) {
        var lookupGroup = "lookupGroup";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(Group.class, true), User.Fields.userKey.name(), Group.Fields.mainAdmin.name(), lookupGroup),
                                unwind(lookupGroup),
                                match(
                                        Criteria.where(AggregationUtils.makeMatchName(lookupGroup, Group.Fields.createTimestamp.name())).gte(from).lte(till)
                                                .and(User.Fields.partnerKey.name()).is(partnerKey)
                                ),
                                new FreeFormOperation("$project", String.format("{%s: \"$%s.%s\", %s: {$dateToString:{format: \"%s\", date: \"$%s.%s\"}}, %s: {$size: \"$%s.%s\"}, %s: \"$%s.%s\"}",
                                        PartnerStatisticGroupInfoDto.Fields.groupName.name(), lookupGroup, Group.Fields.name.name(),
                                        PartnerStatisticGroupInfoDto.Fields.createDate.name(), PartnerStatisticType.DAY.getFormat(), lookupGroup, Group.Fields.createTimestamp.name(),
                                        PartnerStatisticGroupInfoDto.Fields.members.name(), lookupGroup, Group.Fields.members.name(),
                                        PartnerStatisticGroupInfoDto.Fields.subscriptionActivated.name(), lookupGroup, Group.Fields.isSubscriptionActivate.name()
                                )
                                )
                        ),
                        AggregationUtils.getCollectionName(User.class, true),
                        PartnerStatisticGroupInfoDto.class
                ).buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    public Mono<CountResult> countSubscription(String partnerKey) {
        var lookupGroup = "lookupGroup";
        var lookupSubscription = "lookupSubscription";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(Group.class, true), User.Fields.userKey.name(), Group.Fields.mainAdmin.name(), lookupGroup),
                                unwind(lookupGroup),
                                lookup(AggregationUtils.getCollectionName(Subscription.class, false), String.format("%s.%s", lookupGroup, Group.Fields.groupKey.name()), Subscription.Fields.groupKey.name(), lookupSubscription),
                                unwind(lookupSubscription),
                                match(
                                        Criteria.where(User.Fields.partnerKey.name()).is(partnerKey)
                                ),
                                count().as("result")
                        ),
                        AggregationUtils.getCollectionName(User.class, true),
                        CountResult.class
                )
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(new CountResult(0)));
    }

    public Mono<List<PartnerStatisticDto>> getSubscriptionStatistic(PartnerStatisticType type, LocalDate from, LocalDate till, String partnerKey) {
        var lookupGroup = "lookupGroup";
        var lookupSubscription = "lookupSubscription";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(Group.class, true), User.Fields.userKey.name(), Group.Fields.mainAdmin.name(), lookupGroup),
                                unwind(lookupGroup),
                                lookup(AggregationUtils.getCollectionName(Subscription.class, false), String.format("%s.%s", lookupGroup, Group.Fields.groupKey.name()), Subscription.Fields.groupKey.name(), lookupSubscription),
                                unwind(lookupSubscription),
                                match(
                                        Criteria.where(AggregationUtils.makeMatchName(lookupSubscription, Subscription.Fields.createTimestamp.name())).gte(from).lte(till)
                                                .and(User.Fields.partnerKey.name()).is(partnerKey)
                                ),
                                new FreeFormOperation("$group", String.format("{ _id: {groupColumnName: {$dateToString:{format: \"%s\", date: \"$%s.%s\"}}}, count: {$sum: 1} }", type.getFormat(), lookupSubscription, User.Fields.createTimestamp.name())),
                                new FreeFormOperation("$replaceRoot", String.format("{ newRoot: { $mergeObjects: [{%s: \"$_id.groupColumnName\"}, {%s: \"$count\" }]}}", PartnerStatisticDto.Fields.columnName.name(), PartnerStatisticDto.Fields.number.name())),
                                new FreeFormOperation("$sort", String.format("{ \"%s\": 1}", PartnerStatisticDto.Fields.columnName.name()))
                        ),
                        AggregationUtils.getCollectionName(User.class, true),
                        PartnerStatisticDto.class
                ).buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    public Mono<List<PartnerStatisticSubscriptionInfoDto>> getSubscriptionStatisticInfo(LocalDate from, LocalDate till, String partnerKey) {
        var lookupGroup = "lookupGroup";
        var lookupSubscription = "lookupSubscription";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(Group.class, true), User.Fields.userKey.name(), Group.Fields.mainAdmin.name(), lookupGroup),
                                unwind(lookupGroup),
                                lookup(AggregationUtils.getCollectionName(Subscription.class, false), String.format("%s.%s", lookupGroup, Group.Fields.groupKey.name()), Subscription.Fields.groupKey.name(), lookupSubscription),
                                unwind(lookupSubscription),
                                match(
                                        Criteria.where(AggregationUtils.makeMatchName(lookupSubscription, Subscription.Fields.createTimestamp.name())).gte(from).lte(till)
                                                .and(User.Fields.partnerKey.name()).is(partnerKey)
                                ),
                                new FreeFormOperation("$project", String.format("{%s: \"$%s.%s\", %s: \"$%s.%s\", %s: {$dateToString:{format: \"%s\", date: \"$%s.%s\"}}, %s: \"$%s.%s\", %s: \"$%s.%s\"}",
                                        PartnerStatisticSubscriptionInfoDto.Fields.groupName.name(), lookupGroup, Group.Fields.name.name(),
                                        PartnerStatisticSubscriptionInfoDto.Fields.subscriptionName.name(), lookupSubscription, Subscription.Fields.name.name(),
                                        PartnerStatisticSubscriptionInfoDto.Fields.createDate.name(), PartnerStatisticType.DAY.getFormat(), lookupSubscription, Subscription.Fields.createTimestamp.name(),
                                        PartnerStatisticSubscriptionInfoDto.Fields.price.name(), lookupSubscription, Subscription.Fields.price.name(),
                                        PartnerStatisticSubscriptionInfoDto.Fields.currency.name(), lookupSubscription, Subscription.Fields.currency.name()
                                        )
                                )
                        ),
                        AggregationUtils.getCollectionName(User.class, true),
                        PartnerStatisticSubscriptionInfoDto.class
                ).buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    public Mono<AmountSumResult> countGroupPerformance(String partnerKey) {
        var lookupGroup = "lookupGroup";
        var lookupSubscription = "lookupSubscription";
        var lookupUserSubscription = "lookupUserSubscription";
        var lookupPayment = "lookupPayment";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(Group.class, true), User.Fields.userKey.name(), Group.Fields.mainAdmin.name(), lookupGroup),
                                unwind(lookupGroup),
                                lookup(AggregationUtils.getCollectionName(Subscription.class, false), String.format("%s.%s", lookupGroup, Group.Fields.groupKey.name()), Subscription.Fields.groupKey.name(), lookupSubscription),
                                unwind(lookupSubscription),
                                lookup(AggregationUtils.getCollectionName(UserSubscription.class, false), String.format("%s.%s", lookupGroup, Group.Fields.groupKey.name()), UserSubscription.Fields.groupKey.name(), lookupUserSubscription),
                                unwind(lookupUserSubscription),
                                lookup(AggregationUtils.getCollectionName(Payment.class, false), String.format("%s.%s", lookupUserSubscription, UserSubscription.Fields.userSubscriptionKey.name()), Payment.Fields.userSubscriptionKey.name(), lookupPayment),
                                unwind(lookupPayment),
                                match(
                                        Criteria.where(AggregationUtils.makeMatchName(lookupPayment, Payment.Fields.amount.name())).ne(null)
                                                .and(User.Fields.partnerKey.name()).is(partnerKey)
                                ),
                                new FreeFormOperation("$group", String.format(
                                        "{ _id: null, result: {$sum: {$toDouble: \"$%s.%s\"}} }",
                                        lookupPayment, Payment.Fields.amount.name())
                                )
                        ),
                        AggregationUtils.getCollectionName(User.class, true),
                        AmountSumResult.class
                )
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(new AmountSumResult(0D)));
    }

    public Mono<List<PartnerAmountStatisticDto>> getGroupPerformanceStatistic(PartnerStatisticType type, LocalDate from, LocalDate till, String partnerKey) {
        var lookupGroup = "lookupGroup";
        var lookupSubscription = "lookupSubscription";
        var lookupUserSubscription = "lookupUserSubscription";
        var lookupPayment = "lookupPayment";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(Group.class, true), User.Fields.userKey.name(), Group.Fields.mainAdmin.name(), lookupGroup),
                                unwind(lookupGroup),
                                lookup(AggregationUtils.getCollectionName(Subscription.class, false), String.format("%s.%s", lookupGroup, Group.Fields.groupKey.name()), Subscription.Fields.groupKey.name(), lookupSubscription),
                                unwind(lookupSubscription),
                                lookup(AggregationUtils.getCollectionName(UserSubscription.class, false), String.format("%s.%s", lookupGroup, Group.Fields.groupKey.name()), UserSubscription.Fields.groupKey.name(), lookupUserSubscription),
                                unwind(lookupUserSubscription),
                                lookup(AggregationUtils.getCollectionName(Payment.class, false), String.format("%s.%s", lookupUserSubscription, UserSubscription.Fields.userSubscriptionKey.name()), Payment.Fields.userSubscriptionKey.name(), lookupPayment),
                                unwind(lookupPayment),
                                match(
                                        Criteria.where(AggregationUtils.makeMatchName(lookupPayment, Payment.Fields.createTimestamp.name())).gte(from).lte(till)
                                                .and(AggregationUtils.makeMatchName(lookupPayment, Payment.Fields.amount.name())).ne(null)
                                                .and(User.Fields.partnerKey.name()).is(partnerKey)
                                ),
                                new FreeFormOperation("$group", String.format(
                                        "{ _id: {groupColumnName: {$dateToString:{format: \"%s\", date: \"$%s.%s\"}}}, amount: {$sum: {$toDouble: \"$%s.%s\"}} }",
                                        type.getFormat(), lookupPayment, Payment.Fields.createTimestamp.name(), lookupPayment, Payment.Fields.amount.name())
                                ),
                                new FreeFormOperation("$replaceRoot", String.format("{ newRoot: { $mergeObjects: [{%s: \"$_id.groupColumnName\"}, {%s: \"$amount\" }]}}", PartnerAmountStatisticDto.Fields.columnName.name(), PartnerAmountStatisticDto.Fields.amount.name())),
                                new FreeFormOperation("$sort", String.format("{ \"%s\": 1}", PartnerStatisticDto.Fields.columnName.name()))
                        ),
                        AggregationUtils.getCollectionName(User.class, true),
                        PartnerAmountStatisticDto.class
                ).buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    public Mono<List<PartnerStatisticGroupPerformanceInfoDto>> getGroupPerformanceStatisticInfo(LocalDate from, LocalDate till, String partnerKey) {
        var lookupGroup = "lookupGroup";
        var lookupSubscription = "lookupSubscription";
        var lookupUserSubscription = "lookupUserSubscription";
        var lookupPayment = "lookupPayment";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(Group.class, true), User.Fields.userKey.name(), Group.Fields.mainAdmin.name(), lookupGroup),
                                unwind(lookupGroup),
                                lookup(AggregationUtils.getCollectionName(Subscription.class, false), String.format("%s.%s", lookupGroup, Group.Fields.groupKey.name()), Subscription.Fields.groupKey.name(), lookupSubscription),
                                unwind(lookupSubscription),
                                lookup(AggregationUtils.getCollectionName(UserSubscription.class, false), String.format("%s.%s", lookupGroup, Group.Fields.groupKey.name()), UserSubscription.Fields.groupKey.name(), lookupUserSubscription),
                                unwind(lookupUserSubscription),
                                lookup(AggregationUtils.getCollectionName(Payment.class, false), String.format("%s.%s", lookupUserSubscription, UserSubscription.Fields.userSubscriptionKey.name()), Payment.Fields.userSubscriptionKey.name(), lookupPayment),
                                unwind(lookupPayment),
                                match(
                                        Criteria.where(AggregationUtils.makeMatchName(lookupPayment, Payment.Fields.createTimestamp.name())).gte(from).lte(till)
                                                .and(AggregationUtils.makeMatchName(lookupPayment, Payment.Fields.amount.name())).ne(null)
                                                .and(User.Fields.partnerKey.name()).is(partnerKey)
                                ),
                                new FreeFormOperation("$project", String.format("{%s: \"$%s.%s\", %s: \"$%s.%s\", %s: {$dateToString:{format: \"%s\", date: \"$%s.%s\"}}, %s: \"$%s.%s\", %s: \"$%s.%s\"}",
                                        PartnerStatisticGroupPerformanceInfoDto.Fields.groupName.name(), lookupGroup, Group.Fields.name.name(),
                                        PartnerStatisticGroupPerformanceInfoDto.Fields.subscriptionName.name(), lookupSubscription, Subscription.Fields.name.name(),
                                        PartnerStatisticGroupPerformanceInfoDto.Fields.createDate.name(), PartnerStatisticType.DAY.getFormat(), lookupPayment, Payment.Fields.createTimestamp.name(),
                                        PartnerStatisticGroupPerformanceInfoDto.Fields.amount.name(), lookupPayment, Payment.Fields.amount.name(),
                                        PartnerStatisticGroupPerformanceInfoDto.Fields.currency.name(), lookupPayment, Payment.Fields.currency.name()
                                )
                                )
                        ),
                        AggregationUtils.getCollectionName(User.class, true),
                        PartnerStatisticGroupPerformanceInfoDto.class
                ).buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    public Mono<AmountSumResult> countPartnerEarning(String userKey) {
        var lookupGroup = "lookupGroup";
        var lookupSubscription = "lookupSubscription";
        var lookupUserSubscription = "lookupUserSubscription";
        var lookupPayment = "lookupPayment";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(Payment.class, false), StripeTransfer.Fields.paymentId.name(), Payment.Fields.paymentId.name(), lookupPayment),
                                unwind(lookupPayment),
                                lookup(AggregationUtils.getCollectionName(UserSubscription.class, false), String.format("%s.%s", lookupPayment, Payment.Fields.userSubscriptionKey.name()), UserSubscription.Fields.userSubscriptionKey.name(), lookupUserSubscription),
                                unwind(lookupUserSubscription),
                                lookup(AggregationUtils.getCollectionName(Group.class, true), String.format("%s.%s", lookupUserSubscription, UserSubscription.Fields.groupKey.name()), Group.Fields.groupKey.name(), lookupGroup),
                                unwind(lookupGroup),
                                lookup(AggregationUtils.getCollectionName(Subscription.class, false), String.format("%s.%s", lookupUserSubscription, UserSubscription.Fields.subscriptionKey.name()), Subscription.Fields.subscriptionKey.name(), lookupSubscription),
                                unwind(lookupSubscription),
                                match(
                                        Criteria.where(StripeTransfer.Fields.amount.name()).ne(null)
                                                .and(StripeTransfer.Fields.userKey.name()).is(userKey)
                                ),
                                new FreeFormOperation("$group", String.format(
                                        "{ _id: null, result: {$sum: {$toDouble: \"$%s\"}} }",
                                        StripeTransfer.Fields.amount.name())
                                )
                        ),
                        AggregationUtils.getCollectionName(StripeTransfer.class, false),
                        AmountSumResult.class
                )
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(new AmountSumResult(0D)));
    }

    public Mono<List<PartnerAmountStatisticDto>> getPartnerEarningStatistic(PartnerStatisticType type, LocalDate from, LocalDate till, String userKey) {
        var lookupGroup = "lookupGroup";
        var lookupSubscription = "lookupSubscription";
        var lookupUserSubscription = "lookupUserSubscription";
        var lookupPayment = "lookupPayment";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(Payment.class, false), StripeTransfer.Fields.paymentId.name(), Payment.Fields.paymentId.name(), lookupPayment),
                                unwind(lookupPayment),
                                lookup(AggregationUtils.getCollectionName(UserSubscription.class, false), String.format("%s.%s", lookupPayment, Payment.Fields.userSubscriptionKey.name()), UserSubscription.Fields.userSubscriptionKey.name(), lookupUserSubscription),
                                unwind(lookupUserSubscription),
                                lookup(AggregationUtils.getCollectionName(Group.class, true), String.format("%s.%s", lookupUserSubscription, UserSubscription.Fields.groupKey.name()), Group.Fields.groupKey.name(), lookupGroup),
                                unwind(lookupGroup),
                                lookup(AggregationUtils.getCollectionName(Subscription.class, false), String.format("%s.%s", lookupUserSubscription, UserSubscription.Fields.subscriptionKey.name()), Subscription.Fields.subscriptionKey.name(), lookupSubscription),
                                unwind(lookupSubscription),
                                match(
                                        Criteria.where(StripeTransfer.Fields.createTimestamp.name()).gte(from).lte(till)
                                                .and(StripeTransfer.Fields.amount.name()).ne(null)
                                                .and(StripeTransfer.Fields.userKey.name()).is(userKey)
                                ),
                                new FreeFormOperation("$group", String.format(
                                        "{ _id: {groupColumnName: {$dateToString:{format: \"%s\", date: \"$%s\"}}}, amount: {$sum: {$toDouble: \"$%s\"}} }",
                                        type.getFormat(), StripeTransfer.Fields.createTimestamp.name(), StripeTransfer.Fields.amount.name())
                                ),
                                new FreeFormOperation("$replaceRoot", String.format("{ newRoot: { $mergeObjects: [{%s: \"$_id.groupColumnName\"}, {%s: \"$amount\" }]}}", PartnerAmountStatisticDto.Fields.columnName.name(), PartnerAmountStatisticDto.Fields.amount.name())),
                                new FreeFormOperation("$sort", String.format("{ \"%s\": 1}", PartnerStatisticDto.Fields.columnName.name()))
                        ),
                        AggregationUtils.getCollectionName(StripeTransfer.class, false),
                        PartnerAmountStatisticDto.class
                ).buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    public Mono<List<PartnerStatisticEarningInfoDto>> getPartnerEarningStatisticInfo(LocalDate from, LocalDate till, String userKey) {
        var lookupGroup = "lookupGroup";
        var lookupSubscription = "lookupSubscription";
        var lookupUserSubscription = "lookupUserSubscription";
        var lookupPayment = "lookupPayment";
        return mongoTemplate.aggregate(
                        newAggregation(
                                lookup(AggregationUtils.getCollectionName(Payment.class, false), StripeTransfer.Fields.paymentId.name(), Payment.Fields.paymentId.name(), lookupPayment),
                                unwind(lookupPayment),
                                lookup(AggregationUtils.getCollectionName(UserSubscription.class, false), String.format("%s.%s", lookupPayment, Payment.Fields.userSubscriptionKey.name()), UserSubscription.Fields.userSubscriptionKey.name(), lookupUserSubscription),
                                unwind(lookupUserSubscription),
                                lookup(AggregationUtils.getCollectionName(Group.class, true), String.format("%s.%s", lookupUserSubscription, UserSubscription.Fields.groupKey.name()), Group.Fields.groupKey.name(), lookupGroup),
                                unwind(lookupGroup),
                                lookup(AggregationUtils.getCollectionName(Subscription.class, false), String.format("%s.%s", lookupUserSubscription, UserSubscription.Fields.subscriptionKey.name()), Subscription.Fields.subscriptionKey.name(), lookupSubscription),
                                unwind(lookupSubscription),
                                match(
                                        Criteria.where(StripeTransfer.Fields.createTimestamp.name()).gte(from).lte(till)
                                                .and(StripeTransfer.Fields.amount.name()).ne(null)
                                                .and(StripeTransfer.Fields.userKey.name()).is(userKey)
                                ),
                                new FreeFormOperation("$project", String.format("{ %s: \"$%s.%s\", %s: \"$%s.%s\", %s: {$dateToString:{format: \"%s\", date: \"$%s\"}}, %s: \"$%s\", %s: \"$%s\" }",
                                        PartnerStatisticEarningInfoDto.Fields.groupName.name(), lookupGroup, Group.Fields.name.name(),
                                        PartnerStatisticEarningInfoDto.Fields.subscriptionName.name(), lookupSubscription, Subscription.Fields.name.name(),
                                        PartnerStatisticEarningInfoDto.Fields.createDate.name(), PartnerStatisticType.DAY.getFormat(), StripeTransfer.Fields.createTimestamp.name(),
                                        PartnerStatisticEarningInfoDto.Fields.amount.name(), StripeTransfer.Fields.amount.name(),
                                        PartnerStatisticEarningInfoDto.Fields.currency.name(), StripeTransfer.Fields.currency.name()
                                )
                                )
                        ),
                        AggregationUtils.getCollectionName(StripeTransfer.class, false),
                        PartnerStatisticEarningInfoDto.class
                ).buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }
}
