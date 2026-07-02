package to.orbis.v2.backend.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PartnerStatisticType {
    MONTH("%Y-%m"),
    DAY("%Y-%m-%d");

    private final String format;
}
