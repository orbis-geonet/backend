package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import to.orbis.v2.backend.models.dto.ExtendedFollowDto;
import to.orbis.v2.backend.models.entity.ExtendedFollow;

@Mapper(componentModel = "spring", uses = {PlaceMapper.class, UserMapper.class, GroupMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface FollowsMapper {
    ExtendedFollowDto extendedFollowToExtendedFollowDto(ExtendedFollow extendedFollow);
}

