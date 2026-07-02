package to.orbis.v2.backend.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import to.orbis.v2.backend.models.entity.EventAttendee;
import to.orbis.v2.backend.models.entity.SimplifiedUser;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
@RequiredArgsConstructor
public class EventsAggregationRepository {

    ReactiveMongoTemplate mongoTemplate;

    public Flux<SimplifiedUser> getAttendees(String postKey, Pageable pageable) {
        return mongoTemplate.aggregate(newAggregation(
                match(Criteria.where(EventAttendee.Fields.postKey.name()).is(postKey)),
                sort(Sort.Direction.ASC, "id"),
                skip(pageable.getOffset()),
                limit(pageable.getPageSize()),
                lookup("users", "userKey", "userKey", "user"),
                unwind("user"),
                replaceRoot("user")
        ), EventAttendee.class, SimplifiedUser.class);
    }
}
