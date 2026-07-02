package to.orbis.v2.backend.utils;

import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.LatLong;
import lombok.SneakyThrows;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.opengis.referencing.FactoryException;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class GeoHashUtils {
    public static String encodeHash(GeoJsonPoint point) {
        return GeoHash.encodeHash(point.getY(), point.getX(), 4);
    }

    public static Set<String> nearbyHashes(double lat, double lng, int length) {
        Set<String> current = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Set<String> result = new HashSet<>();
        String hash = GeoHash.encodeHash(lat, lng, length);
        current.add(hash);
        visited.add(hash);
        result.add(hash);

        GeodeticCalculator geodeticCalc = new GeodeticCalculator();

        geodeticCalc.setStartingGeographicPoint(lng, lat);

        while (!current.isEmpty()) {

            Set<String> newCurrent = current.stream().flatMap(c -> GeoHash.neighbours(c).stream())
                    .filter(v -> !visited.contains(v))
                    .collect(Collectors.toSet());

            visited.addAll(newCurrent);

            GeodeticCalculator finalGeodeticCalc = geodeticCalc;
            current = newCurrent.stream().filter(v -> {
                LatLong point = GeoHash.decodeHash(v);
                finalGeodeticCalc.setDestinationGeographicPoint(point.getLon(), point.getLat());
                return finalGeodeticCalc.getOrthodromicDistance() < 70000;
            }).collect(Collectors.toSet());

            result.addAll(current);
        }

        return result;
    }

    public static Set<String> alternativeHashes(double lat, double lng, int length) {
        GeodeticCalculator geodeticCalc = new GeodeticCalculator();

        geodeticCalc.setStartingGeographicPoint(lng, lat);
        Set<String> result = new HashSet<>();

        for (int i = 0; i < 3599; i++) {
            for (int j = 0; j <= 50; j++) {
                geodeticCalc.setDirection(i*0.1, j*1000);
                final Point2D point = geodeticCalc.getDestinationGeographicPoint();
                result.add(GeoHash.encodeHash(point.getY(), point.getX(), length));
            }
        }

        return result;
    }

    public static String geoHashEncode3Bytes(double lat, double lon) {
        int latSnapped = Math.min(399, Math.max(0, (int) Math.floor((lat + 90) / 0.45)));
        int lonSnapped = Math.min(799, Math.max(0, (int) Math.floor((lon + 180) / 0.45)));
        int packed = (latSnapped << 12) | lonSnapped;
        byte[] buf = new byte[3];
        buf[0] = (byte) ((packed >> 16) & 0xff);
        buf[1] = (byte) ((packed >> 8) & 0xff);
        buf[2] = (byte) (packed & 0xff);
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
