package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.User;

import java.util.Locale;
import java.util.concurrent.Flow;

@Repository
public interface UsersRepository extends ReactiveMongoRepository<User, ObjectId> {
    Mono<User> findOneByUserKey(String userKey);

    Mono<User> findOneByEmailAndDeletedFalse(String email);

    Flux<User> findAllBySuperAdminTrue();

    Mono<Long> countByDisplayName(String displayName);

    Mono<User> findBySlug(String slug);

    Flux<User> findAllBySlug(String slug);

    Mono<Long> countByEmptySlug(String emptySlug);
}
