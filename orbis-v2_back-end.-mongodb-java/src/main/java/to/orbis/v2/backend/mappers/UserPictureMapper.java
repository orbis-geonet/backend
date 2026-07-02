package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.dto.UserPictureDto;
import to.orbis.v2.backend.models.entity.UserPicture;
import to.orbis.v2.backend.models.ig.IgMedia;
import to.orbis.v2.backend.models.ig.IgMediaType;
import to.orbis.v2.backend.models.requests.CreateUserPicture;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring",
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface UserPictureMapper {

    UserPictureDto userPictureToUserPictureDto(UserPicture userPicture);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pictureKey", ignore = true)
    @Mapping(target = "userKey", ignore = true)
    @Mapping(target = "type", expression = "java(to.orbis.v2.backend.models.UserPictureType.ORBIS)")
    @Mapping(target = "imageType", expression = "java(to.orbis.v2.backend.models.PostType.IMAGE)")
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "igMediaId", ignore = true)
    @Mapping(target = "loadTimestamp", ignore = true)
    UserPicture createUserPictureToUserPicture(CreateUserPicture userPicture);

    @Mapping(target = "id", expression = "java(new org.bson.types.ObjectId())")
    @Mapping(target = "pictureKey", ignore = true)
    @Mapping(target = "userKey", ignore = true)
    @Mapping(target = "pictureUrl", source = "mediaUrl")
    @Mapping(target = "type", expression = "java(to.orbis.v2.backend.models.UserPictureType.INSTAGRAM)")
    @Mapping(target = "imageType", source = "mediaType")
    @Mapping(target = "igMediaId", source = "id")
    @Mapping(target = "loadTimestamp", expression = "java(java.time.Instant.now())")
    UserPicture igMediaToUserPicture(IgMedia igMedia);

    default List<String> stringToList(String url) {
        return Collections.singletonList(url);
    }

    default Instant zonedTimeToInstance(ZonedDateTime zonedDateTime) {
        return zonedDateTime.toInstant();
    }

    default PostType mediaTypeToPostType(IgMediaType mediaType) {
        switch (mediaType) {
            case IMAGE:
            case CAROUSEL_ALBUM:
                return PostType.IMAGE;
            case VIDEO:
                return PostType.VIDEO;
        }
        return PostType.IMAGE;
    }
}
