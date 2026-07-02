package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.UserPictureType;
import to.orbis.v2.backend.models.entity.UserPicture;

@Repository
public interface UserPictureRepository extends ReactiveMongoRepository<UserPicture, ObjectId> {
    Flux<UserPicture> findByUserKeyAndTypeOrderByTimestampDesc(String userKey, UserPictureType type, Pageable pageable);

    Mono<Void> deleteByPictureKey(String pictureKey);
    Mono<Void> deleteAllByUserKeyAndType(String userKey, UserPictureType type);
    Mono<UserPicture> findFirstByUserKeyAndTypeOrderByTimestampDesc(String userKey, UserPictureType type);
}
