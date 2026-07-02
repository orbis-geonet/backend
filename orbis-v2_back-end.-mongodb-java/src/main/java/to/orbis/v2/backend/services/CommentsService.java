package to.orbis.v2.backend.services;

import com.google.appengine.repackaged.com.google.common.io.Files;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.exceptions.UnknownUserException;
import to.orbis.v2.backend.models.Language;
import to.orbis.v2.backend.models.entity.Comment;
import to.orbis.v2.backend.models.entity.ExtendedComment;
import to.orbis.v2.backend.repositories.CommentsAggregationRepository;
import to.orbis.v2.backend.repositories.CommentsRepository;
import to.orbis.v2.backend.repositories.PostsRepository;
import to.orbis.v2.backend.repositories.UsersRepository;
import to.orbis.v2.backend.repositories.queries.CommentQuery;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentsService {

    NotificationsService notificationsService;

    CommentsRepository commentsRepository;
    CommentsAggregationRepository commentsAggregationRepository;

    UsersRepository usersRepository;
    PostsRepository postsRepository;

    public Flux<ExtendedComment> findComments(String postKey, Optional<String> userKey, Pageable pageable) {
        return commentsAggregationRepository.findComments(CommentQuery.builder().withPostKey(postKey).withPageable(pageable).build(), userKey)
                .map(this::filterSortReplies);
    }

    public Mono<ExtendedComment> getComment(String commentKey, Optional<String> userKey) {
        return commentsAggregationRepository.findComments(CommentQuery.builder().withCommentKey(commentKey).build(), userKey)
                .singleOrEmpty()
                .map(this::filterSortReplies);
    }

    private ExtendedComment filterSortReplies(ExtendedComment comment) {
        return comment.setReplies(
                comment.getReplies().stream()
                        .filter(c -> c.getCommentKey() != null)
                        .sorted(Comparator.comparing(Comment::getTimestamp))
                        .collect(Collectors.toList()));

    }

    public Mono<ExtendedComment> postComment(Comment comment) {

        comment.setId(new ObjectId());
        comment.setCommentKey(comment.getId().toHexString());

        return checkDetails(comment)
                .flatMap(commentsRepository::save)
                .map(savedComment -> {
                    notificationsService.notifyNewComment(savedComment)
                            .subscribeOn(Schedulers.boundedElastic())
                            .publishOn(Schedulers.boundedElastic())
                            .subscribe(_ignored -> {
                            }, error -> log.error("Failed to notify regarding new comment {}", savedComment.getCommentKey(), error));
                    return savedComment;
                })
                .map(Comment::getCommentKey)
                // user key is to check if current user liked comment. freshly made comment is not liked so can pass empty here
                .flatMap(commentKey -> getComment(commentKey, Optional.empty()));
    }

    private Mono<Comment> checkDetails(Comment comment) {
        return usersRepository.findOneByUserKey(comment.getUserKey())
                .switchIfEmpty(Mono.error(UnknownUserException::new))
                .flatMap(_user -> postsRepository.findOneByPostKey(comment.getPostKey()))
                .filter(post -> !post.isDeleted())
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Post you are commenting on is not found")))
                .flatMap(post -> (!comment.isReply())
                        ? Mono.just(comment)
                        : checkReplyToExists(comment));
    }

    private Mono<Comment> checkReplyToExists(Comment comment) {
        return commentsRepository.findOneByCommentKey(comment.getReplyToKey())
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Comment you are replying to is not found")))
                .flatMap(parentComment -> parentComment.isDeleted()
                        ? Mono.error(() -> new NoDataFoundException("Comment you are replying to is deleted"))
                        : Mono.just(parentComment))
                .flatMap(parentComment -> (parentComment.isReply())
                        ? Mono.error(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only one level of replies is allowed"))
                        : Mono.just(parentComment))
                .flatMap(
                        parentComment -> !parentComment.getPostKey().equals(comment.getPostKey())
                                ? Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "You can not reply to comment from one post in the comment from another post"))

                                : Mono.just(comment));
    }

    public Mono<ExtendedComment> updateComment(String commentKey, String text, String userKey) {
        return commentsAggregationRepository.updateComment(userKey, commentKey, text)
                .flatMap(_ignored -> getComment(commentKey, Optional.of(userKey)));
    }

    public Mono<Void> deleteComment(String commentKey, String userKey) {
        return commentsAggregationRepository.deleteComment(commentKey, userKey).then();
    }

    public Mono<ExtendedComment> unlikeComment(String commentKey, String userKey) {
        return commentsAggregationRepository.unlikeComment(commentKey, userKey)
                .flatMap(_ignored -> getComment(commentKey, Optional.of(userKey)));
    }

    public Mono<ExtendedComment> likeComment(String commentKey, String userKey) {
        return commentsAggregationRepository.likeComment(commentKey, userKey)
                .flatMap(_ignored -> getComment(commentKey, Optional.of(userKey)));
    }
}
