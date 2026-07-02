package to.orbis.v2.backend.repositories;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import to.orbis.v2.backend.models.entity.Polygon;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.geoNear;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.skip;

@Repository
@RequiredArgsConstructor
public class PolygonsAggregationRepository {

    ReactiveMongoTemplate reactiveMongoTemplate;

    public Flux<Polygon> findPolygonsByLocation(GeoJsonPoint point, Double distance, Pageable pageable) {
        AggregationOperation[] ops = prepareAggregationOps(point, distance, pageable);
        Aggregation aggregation = Aggregation.newAggregation(ops);
        return reactiveMongoTemplate.aggregate(aggregation, "polygons", Polygon.class);
    }

    private AggregationOperation[] prepareAggregationOps(GeoJsonPoint point, Double distance, Pageable pageable) {
        val query = NearQuery.near(point).inKilometers();

        val withLimit = distance == null ? query
                : query.maxDistance(new Distance(distance, Metrics.KILOMETERS));

        val mayBePageable = Optional.ofNullable(pageable).filter(Pageable::isPaged);

        return Stream.of(
                        Stream.of(geoNear(withLimit, "dist").useIndex("polygonCenter")),
                        mayBePageable.stream().flatMap(p -> Stream.of(skip(p.getOffset()),
                                limit(p.getPageSize())))
                ).flatMap(Function.identity())
                .toArray(AggregationOperation[]::new);
    }
}