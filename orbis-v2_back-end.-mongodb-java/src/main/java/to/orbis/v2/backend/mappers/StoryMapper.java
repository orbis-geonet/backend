package to.orbis.v2.backend.mappers;

import org.bson.types.ObjectId;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.backend.models.dto.StoryDto;
import to.orbis.v2.backend.models.entity.ExtendedStory;

@Mapper(componentModel = "spring", uses = {PointMapper.class, UserMapper.class, PostMapper.class, NextPageMapper.class},
        disableSubMappingMethodsGeneration = true,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface StoryMapper {

    @Mapping(target = "storyKey", source = "_id")
    StoryDto storyToStoryDto(ExtendedStory extendedStory);

    default String objectIdToString(ObjectId objectId) {
        return objectId.toHexString();
    }
}
