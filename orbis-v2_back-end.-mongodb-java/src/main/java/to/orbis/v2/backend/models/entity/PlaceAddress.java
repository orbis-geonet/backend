package to.orbis.v2.backend.models.entity;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class PlaceAddress {
    private String fullAddress;
    private String country;
    private String city;
    private String street;
    private String number;
    private String neighberhood;
    private String postalCode;
}
