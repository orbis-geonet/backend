package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.backend.models.dto.CommentDto;
import to.orbis.v2.backend.models.dto.PrimitiveCommentDto;
import to.orbis.v2.backend.models.entity.Comment;
import to.orbis.v2.backend.models.entity.ExtendedComment;
import to.orbis.v2.backend.models.entity.PrimitiveComment;
import to.orbis.v2.backend.models.requests.comments.CreateCommentRequest;

@Mapper(componentModel = "spring", uses = {PlaceMapper.class, UserMapper.class, GroupMapper.class, PostMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CommentMapper {

    CommentDto extendedCommentToCommentDto(ExtendedComment comment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "commentKey", ignore = true)
    @Mapping(target = "postKey", ignore = true)
    @Mapping(target = "userKey", ignore = true)
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "createTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "liked", ignore = true)
    Comment createCommentRequestToComment(CreateCommentRequest commentRequest);

    PrimitiveCommentDto primitiveCommentToPrimitiveCommentDto(PrimitiveComment primitiveComment);
}
