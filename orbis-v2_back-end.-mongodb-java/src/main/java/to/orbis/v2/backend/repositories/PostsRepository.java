package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Post;

@Repository
public interface PostsRepository extends ReactiveMongoRepository<Post, ObjectId> {
    Mono<Post> findOneByPostKey(String postKey);

    Mono<Long> countByTitle(String title);
}
