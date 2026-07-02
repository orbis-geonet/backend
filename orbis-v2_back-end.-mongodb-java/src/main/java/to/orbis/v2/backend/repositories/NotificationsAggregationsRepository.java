package to.orbis.v2.backend.repositories;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.ExtendedNotification;
import to.orbis.v2.backend.models.entity.Notification;
import to.orbis.v2.backend.models.entity.User;

import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
@RequiredArgsConstructor
public class NotificationsAggregationsRepository {
    ReactiveMongoTemplate mongoTemplate;

    public Flux<ExtendedNotification> findNotificationsForUser(String userKey, Pageable pageable) {
        return mongoTemplate.aggregate(newAggregation(
                match(Criteria.where(Notification.Fields.forUserKey.name()).is(userKey)),
                sort(Sort.Direction.DESC, Notification.Fields.timestamp.name()),
                skip(pageable.getOffset()),
                limit(pageable.getPageSize()),
                lookup("users", Notification.Fields.forUserKey.name(), "userKey", "forUser"),
                unwind("forUser", true),
                lookup("users", Notification.Fields.fromUserKey.name(), "userKey", "fromUser"),
                unwind("fromUser", true),

                match(Criteria.where("fromUser."+User.Fields.blockedBy.name()).ne(userKey)),

                lookup("comments", Notification.Fields.commentKey.name(), "commentKey", "comment"),
                unwind("comment", true),

                lookup("groups", Notification.Fields.groupKey.name(), "groupKey", "group"),
                unwind("group", true),

                lookup("places", Notification.Fields.placeKey.name(), "placeKey", "place"),
                unwind("place", true),

                lookup("posts", Notification.Fields.postKey.name(), "postKey", "post"),
                unwind("post", true)
        ), "notifications", ExtendedNotification.class);
    }

    public Mono<Void> setSeenStatus(List<String> notificationKeys, String userKey) {
        Query q = new Query(Criteria.where(Notification.Fields.forUserKey.name()).is(userKey)
                .and(Notification.Fields.notificationKey.name()).in(notificationKeys));

        val update = new Update();
        update.set(Notification.Fields.seen.name(), true);

        return mongoTemplate.updateMulti(q, update, Notification.class).then();
    }
}
