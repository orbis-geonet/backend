package to.orbis.v2.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.mappers.CommentMapper;
import to.orbis.v2.backend.models.Language;
import to.orbis.v2.backend.models.dto.CommentDto;
import to.orbis.v2.backend.models.requests.comments.CreateCommentRequest;
import to.orbis.v2.backend.models.requests.comments.UpdateCommentRequest;
import to.orbis.v2.backend.services.CommentsService;
import to.orbis.v2.backend.utils.ControllerUtils;

import javax.validation.Valid;
import java.util.Collections;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/posts/{postKey}/comments")
@Slf4j
public class CommentsController {

    CommentsService service;
    CommentMapper commentMapper;

    @GetMapping
    @PreAuthorize("permitAll")
    public Mono<List<CommentDto>> getComments(@PathVariable String postKey,
                                              @RequestParam(required = false, defaultValue = "0") int page,
                                              @RequestParam(required = false, defaultValue = "20") int size,
                                              Authentication auth) {

        return service.findComments(postKey, ControllerUtils.maybeAuthorized(auth), PageRequest.of(page, size))
                .map(commentMapper::extendedCommentToCommentDto)
                .buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @SuppressWarnings("MVCPathVariableInspection")
    @GetMapping("/{commentKey}")
    @PreAuthorize("permitAll")
    public Mono<CommentDto> getComment(@PathVariable String commentKey, Authentication auth) {

        return service.getComment(commentKey, ControllerUtils.maybeAuthorized(auth))
                .map(commentMapper::extendedCommentToCommentDto)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Comment not found")));
    }

    @SuppressWarnings("MVCPathVariableInspection")
    @DeleteMapping("/{commentKey}")
    @PreAuthorize("isAuthenticated")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteComment", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> deleteComment(@PathVariable String commentKey, Authentication authentication) {

        return service.deleteComment(commentKey, authentication.getName());
    }

    @PostMapping()
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "postComment", security = @SecurityRequirement(name = "firebase"))
    public Mono<CommentDto> postComment(
            @PathVariable String postKey,
            @RequestBody @Valid CreateCommentRequest commentRequest,
            Authentication authentication
    ) {
        return service.postComment(
                commentMapper.createCommentRequestToComment(commentRequest)
                        .setPostKey(postKey)
                        .setUserKey(authentication.getName())
                ).map(commentMapper::extendedCommentToCommentDto);
    }

    @SuppressWarnings("MVCPathVariableInspection")
    @PutMapping("/{commentKey}")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "editComment", security = @SecurityRequirement(name = "firebase"))
    public Mono<CommentDto> editComment(
            @PathVariable String commentKey,
            @RequestBody UpdateCommentRequest commentRequest,
            Authentication authentication) {
        return service.updateComment(commentKey, commentRequest.getText(), authentication.getName())
                .map(commentMapper::extendedCommentToCommentDto)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Comment not found")));
    }

    @SuppressWarnings("MVCPathVariableInspection")
    @PutMapping("/{commentKey}/like")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "likeComment", security = @SecurityRequirement(name = "firebase"))
    public Mono<CommentDto> likeComment(@PathVariable String commentKey, Authentication auth) {
        return service.likeComment(commentKey, auth.getName())
                .map(commentMapper::extendedCommentToCommentDto)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Comment not found")));
    }

    @SuppressWarnings("MVCPathVariableInspection")
    @DeleteMapping("/{commentKey}/like")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "unlikeComment", security = @SecurityRequirement(name = "firebase"))
    public Mono<CommentDto> unlikeComment(@PathVariable String commentKey, Authentication auth) {
        return service.unlikeComment(commentKey, auth.getName())
                .map(commentMapper::extendedCommentToCommentDto)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Comment not found")));
    }
}
