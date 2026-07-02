package to.orbis.v2.backend.models;

import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.util.Assert;
import org.springframework.util.NumberUtils;
import org.springframework.util.ObjectUtils;
import to.orbis.v2.backend.models.entity.MyGeoJsonMultiPoint;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"rawtypes", "unchecked", "UnnecessaryUnboxing"})
public class MyGeoConverters {
    public enum DocumentToGeoJsonMultiPointConverter implements Converter<Document, MyGeoJsonMultiPoint> {

        INSTANCE;

        /*
         * (non-Javadoc)
         * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
         */
        @Override
        public MyGeoJsonMultiPoint convert(Document source) {

            if (source == null) {
                return null;
            }

            Assert.isTrue(ObjectUtils.nullSafeEquals(source.get("type"), "MultiPoint"),
                    String.format("Cannot convert type '%s' to MultiPoint.", source.get("type")));

            List cords = (List) source.get("coordinates");

            return new MyGeoJsonMultiPoint(toListOfPoint(cords));
        }
    }


    static List<Point> toListOfPoint(List listOfCoordinatePairs) {

        List<Point> points = new ArrayList<>();

        for (Object point : listOfCoordinatePairs) {

            Assert.isInstanceOf(List.class, point);

            List<Number> coordinatesList = (List<Number>) point;

            points.add(new GeoJsonPoint(toPrimitiveDoubleValue(coordinatesList.get(0)),
                    toPrimitiveDoubleValue(coordinatesList.get(1))));
        }
        return points;
    }

    private static double toPrimitiveDoubleValue(Object value) {

        Assert.isInstanceOf(Number.class, value, "Argument must be a Number.");
        return NumberUtils.convertNumberToTargetClass((Number) value, Double.class).doubleValue();
    }
}
