package to.orbis.v2.backend.models.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class NextPage {

    Instant timestamp;
    Double dist;

    Instant chekinsTimestamp;
    Double chekinsDistance;

    Integer sliderShift;

    Integer pageNumber;
}
