package to.orbis.v2.backend.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoHashUtilsTest {

    @Test
    void someTest() {
        double centerLat = 27.6267745;
        double centerLon = 85.5246335;

        assertThat(GeoHashUtils.nearbyHashes(centerLat, centerLon, 4)).containsExactlyInAnyOrder(
                "tuuz",
                "tuvj",
                "tuuy",
                "tuuv",
                "tuuw",
                "tuvh",
                "tuug",
                "tuut",
                "tuuu",
                "tuue",
                "tuus",
                "tuvn",
                "tuuk", "tuuf", "tuud", "tuuq", "tuvp", "tuum", "tuux", "tuv5", "tuv4", "tuu7", "tvhb"
        );
    }
}
