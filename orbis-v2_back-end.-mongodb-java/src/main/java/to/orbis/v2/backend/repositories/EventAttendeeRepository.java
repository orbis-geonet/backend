package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.EventAttendee;

@Repository
public interface EventAttendeeRepository extends ReactiveMongoRepository<EventAttendee, ObjectId> {
    Mono<Void> deleteAllByPostKeyAndUserKey(String postKey, String userKey);

    Mono<EventAttendee> findOneByPostKeyAndUserKey(String postKey, String userKey);
}
