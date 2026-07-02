package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Story;

@Repository
public interface StoriesRepository extends ReactiveMongoRepository<Story, ObjectId> {
    Mono<Story> findOneByGroupKey(String groupKey);
}
