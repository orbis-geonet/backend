package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import to.orbis.v2.backend.models.dto.NotificationDto;
import to.orbis.v2.backend.models.entity.ExtendedNotification;

@Mapper(componentModel = "spring", uses = {PlaceMapper.class, UserMapper.class, GroupMapper.class, PostMapper.class, CommentMapper.class, PointMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface NotificationMapper {

    NotificationDto extendedNotificationToNotificationDto(ExtendedNotification notification);
}
