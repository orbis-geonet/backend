package to.orbis.v2.backend.repositories;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.FreeFormOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Comment;
import to.orbis.v2.backend.models.entity.ExtendedComment;
import to.orbis.v2.backend.models.entity.User;
import to.orbis.v2.backend.repositories.queries.CommentQuery;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
@RequiredArgsConstructor
public class CommentsAggregationRepository {

    ReactiveMongoTemplate mongoTemplate;

    private Aggregation newAggregation(Stream<AggregationOperation> operations) {
        return Aggregation.newAggregation(operations.toArray(AggregationOperation[]::new));
    }

    public Flux<ExtendedComment> findComments(CommentQuery commentQuery, Optional<String> userKey) {
        return mongoTemplate.aggregate(newAggregation(
                        Stream.of(
                                commentQuery.getPostKey().map(pk -> Stream.of(
                                        match(Criteria.where(Comment.Fields.postKey.name()).is(pk)),
                                        match(Criteria.where(Comment.Fields.replyToKey.name()).exists(false))
                                )).stream().flatMap(Function.identity()),
                                commentQuery.getCommentKey().map(ck -> match(Criteria.where(Comment.Fields.commentKey.name()).is(ck))).stream(),
                                Stream.of(
                                        match(Criteria.where(Comment.Fields.deleted.name()).is(false)),
                                        sort(Sort.Direction.ASC, Comment.Fields.timestamp.name())),
                                lookupUnwind("user"),
                                userKey.stream().map(vk -> match(Criteria.where("user." + User.Fields.blockedBy.name()).ne(vk))),
                                commentQuery.getSkip().map(Aggregation::skip).stream(),
                                commentQuery.getLimit().map(Aggregation::limit).stream(),
                                lookupUnwind("post"),
                                Stream.of(new FreeFormOperation("$lookup", "{ \"from\" : \"comments\", \"let\": {\"ck\": \"$commentKey\"}, \"as\":\"replies\", \"pipeline\": [\n" +
                                                "  {\"$match\": {\"$expr\": {\"$and\": [\n" +
                                                "      {\"$eq\": [\"$$ck\", \"$replyToKey\"]},\n" +
                                                "      {\"$eq\": [\"$deleted\", false]}\n" +
                                                "      ]}}},\n" +
                                                "  { \"$lookup\" : { \"from\" : \"users\", \"localField\" : \"userKey\", \"foreignField\" : \"userKey\", \"as\" : \"user\"}}, \n" +
                                                "  { \"$unwind\" : { \"path\" : \"$user\", \"preserveNullAndEmptyArrays\" : true}}, \n" +
                                                userKey.map(vk -> String.format("{ \"$match\" : { \"user.blockedBy\" : { \"$ne\" : \"%s\"}}}," +
                                                                "{ \"$set\" : { \"userLiked\" : { \"$in\" : [\"%s\", { \"$ifNull\" : [\"$liked\", []]}]}}},", vk, vk))
                                                        .orElse("") +
                                                "  { \"$sort\" : { \"timestamp\" : 1}}, \n" +
                                                "]}"),
                                        project(ExtendedComment.class))).flatMap(Function.identity())),
                Comment.class, ExtendedComment.class);
    }

    private Stream<AggregationOperation> lookupUnwind(String fieldName) {
        return Stream.of(
                lookup(fieldName + "s", fieldName + "Key", fieldName + "Key", fieldName),
                unwind(fieldName));
    }

    public Mono<UpdateResult> updateComment(String userKey, String commentKey, String text) {
        Query query = new Query(Criteria.where(Comment.Fields.commentKey.name()).is(commentKey).and(Comment.Fields.userKey.name()).is(userKey));
        Update update = new Update().set(Comment.Fields.text.name(), text).set(Comment.Fields.timestamp.name(), Instant.now());
        return mongoTemplate.updateFirst(query, update, Comment.class);
    }

    public Mono<UpdateResult> deleteComment(String commentKey, String userKey) {
        Query query = new Query(Criteria.where(Comment.Fields.commentKey.name()).is(commentKey).and(Comment.Fields.userKey.name()).is(userKey));
        Update update = new Update().set(Comment.Fields.deleted.name(), true);
        Query repliesQuery = new Query(Criteria.where(Comment.Fields.replyToKey.name()).is(commentKey));
        return mongoTemplate.updateFirst(query, update, Comment.class)
                .flatMap(ur -> ur.getMatchedCount() > 0 ? mongoTemplate.updateMulti(repliesQuery, update, Comment.class)
                        : Mono.just(ur));
    }

    public Mono<User> findCommentAuthor(String commentKey) {
        return mongoTemplate.aggregate(
                        Aggregation.newAggregation(
                                match(Criteria.where(Comment.Fields.commentKey.name()).is(commentKey)),
                                lookup("users", Comment.Fields.userKey.name(), User.Fields.userKey.name(), "user"),
                                unwind("user"),
                                replaceRoot("user")),
                        Comment.class, User.class)
                .singleOrEmpty();
    }

    public Mono<UpdateResult> likeComment(String commentKey, String userKey) {
        val q = new Query(Criteria.where(Comment.Fields.commentKey.name()).is(commentKey));
        val u = new Update().addToSet(Comment.Fields.liked.name()).value(userKey);
        return mongoTemplate.updateFirst(q, u, Comment.class);
    }

    public Mono<UpdateResult> unlikeComment(String commentKey, String userKey) {
        val q = new Query(Criteria.where(Comment.Fields.commentKey.name()).is(commentKey));
        val u = new Update().pull(Comment.Fields.liked.name(), userKey);
        return mongoTemplate.updateFirst(q, u, Comment.class);
    }
}
