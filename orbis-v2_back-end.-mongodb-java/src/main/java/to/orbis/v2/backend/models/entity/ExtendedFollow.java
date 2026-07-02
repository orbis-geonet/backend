package to.orbis.v2.backend.models.entity;

import lombok.Data;

@Data
public class ExtendedFollow {
    SimplifiedGroup group;
    SimplifiedUser user;
    Place place;
}
