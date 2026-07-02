package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import to.orbis.v2.backend.models.dto.IgLinkDto;
import to.orbis.v2.backend.models.entity.IgLink;

@Mapper(componentModel = "spring",
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface IgMapper {
    IgLinkDto igLinkToIgLinkDto(IgLink igLink);
}

