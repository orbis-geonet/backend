package to.orbis.v2.backend.services;

import lombok.SneakyThrows;
import lombok.val;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckinServiceTest {

    @Test
    @SneakyThrows
    void WGS84Available() {
        val calc = new GeodeticCalculator();
        calc.setStartingGeographicPoint(50, 50);
        calc.setDestinationGeographicPoint(-50, -50);
        val res = calc.getOrthodromicDistance();
        assertTrue(res > 100);
    }

}
