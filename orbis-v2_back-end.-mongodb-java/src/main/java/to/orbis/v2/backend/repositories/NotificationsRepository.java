package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Notification;

public interface NotificationsRepository extends ReactiveMongoRepository<Notification, ObjectId> {
    Mono<Long> countByForUserKeyAndSeen(String userKey, boolean seen);
    Mono<Void> deleteAllByNotificationKeyAndForUserKey(String notificationKey, String forUserKey);
}
