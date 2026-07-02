package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.backend.models.dto.PostComponentDto;
import to.orbis.v2.backend.models.dto.PostDto;
import to.orbis.v2.backend.models.dto.PrimitivePostDto;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.models.requests.posts.CreatePostRequest;
import to.orbis.v2.backend.models.requests.posts.UpdatePostRequest;
import to.orbis.v2.backend.services.ShortLinksService;

@Mapper(componentModel = "spring", uses = {PointMapper.class, UserMapper.class, GroupMapper.class, PlaceMapper.class, NextPageMapper.class, ShortLinksService.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PostMapper {

    @Mapping(target = "signedUrls", source = ".")
    PostDto extendedPostToPostDto(ExtendedPost post);

    PostComponentDto postComponentToPostComponentDto(PostComponent postComponent);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "postKey", ignore = true)
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "createTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "userKey", ignore = true)
    @Mapping(target = "liked", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "shareLink", ignore = true)
    @Mapping(target = "fullShareLink", ignore = true)
    @Mapping(target = "reported", constant = "false")
    @Mapping(target = "reportedMessage", ignore = true)
    @Mapping(target = "reportedSolved", ignore = true)
    @Mapping(target = "reportedTime", ignore = true)
    @Mapping(target = "city", ignore = true)
    Post createRequestToPost(CreatePostRequest createPostRequest);

    PrimitivePostDto primitivePostToPrimitivePostDto(PrimitivePost primitivePost);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "postKey", ignore = true)
    @Mapping(target = "createTimestamp", ignore = true)
    @Mapping(target = "groupKey", ignore = true)
    @Mapping(target = "placeKey", ignore = true)
    @Mapping(target = "userKey", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "liked", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "shareLink", ignore = true)
    @Mapping(target = "fullShareLink", ignore = true)
    @Mapping(target = "reported", constant = "false")
    @Mapping(target = "reportedMessage", ignore = true)
    @Mapping(target = "reportedSolved", ignore = true)
    @Mapping(target = "reportedTime", ignore = true)
    Post updateRequestToPost(UpdatePostRequest updatePost);
}
