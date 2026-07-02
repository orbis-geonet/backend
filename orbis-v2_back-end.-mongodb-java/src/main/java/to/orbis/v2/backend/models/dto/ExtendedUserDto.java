package to.orbis.v2.backend.models.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExtendedUserDto extends UserDto {

    Boolean deleted;
    boolean following;
    boolean pending;
    boolean blocked;

    Integer totalFollowing;
    Integer followedGroups;
    Integer followedPlaces;
    Integer totalFollowers;

    Integer groupAdminCount;
    Integer groupMemberCount;

}

