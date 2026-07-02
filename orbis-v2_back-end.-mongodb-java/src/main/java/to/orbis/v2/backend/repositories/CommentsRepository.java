package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Comment;

public interface CommentsRepository extends ReactiveMongoRepository<Comment, ObjectId> {
    Mono<Comment> findOneByCommentKey(String commentKey);
}
