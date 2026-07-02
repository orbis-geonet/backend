package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.val;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "stories")
@FieldNameConstants(asEnum = true)
public class Story extends Entity {
    String groupKey;
    Instant timestamp;
    LinkedList<Post> posts;
    MyGeoJsonMultiPoint coordinates;
    String city;
    LinkedList<String> cities;

    public Story addPost(Post post) {
        if (posts == null) posts = new LinkedList<>();
        posts.addFirst(post);

        val coords = coordinates == null ? new ArrayList<Point>() : new ArrayList<Point>(coordinates.getCoordinates());
        LinkedList<String> citiesList = cities == null ? new LinkedList<>() : new LinkedList<>(cities);

        coords.add(post.getCoordinates());
        citiesList.add(post.getCity());

        while (posts.size() > 10) {
            val removed = posts.removeLast();
            coords.remove(removed.getCoordinates());
            citiesList.removeLast();
        }

        coordinates = new MyGeoJsonMultiPoint(coords);
        city = post.getCity();
        cities = citiesList;
        timestamp = post.getTimestamp();

        return this;
    }

    public Story removePost(String postKey) {
        val pi = posts.iterator();

        List<Point> coords = coordinates == null ? new ArrayList<>() : new ArrayList<>(coordinates.getCoordinates());
        LinkedList<String> citiesList = cities == null ? new LinkedList<>() : new LinkedList<>(cities);
        val ci = citiesList.iterator();

        while(pi.hasNext()) {
            val p = pi.next();
            val c = ci.next();

            if (p.getPostKey().equals(postKey)) {
                pi.remove();
                citiesList.remove(c);
                coords.remove(p.getCoordinates());
            }
        }

        coordinates = new MyGeoJsonMultiPoint(coords);
        cities = citiesList;

        return this;
    }
}
