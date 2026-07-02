package to.orbis.v2.backend.runners;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.FreeFormOperation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.entity.ExtendedPost;
import to.orbis.v2.backend.services.PostService;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Component
@Slf4j
@RequiredArgsConstructor
@Profile("groupingRunner")
public class TestGroupingRunner implements CommandLineRunner {

    ReactiveMongoTemplate mongoTemplate;
    PostService postService;

    @Override
    public void run(String... args) {

        val start = Instant.now();

        val f = mongoTemplate.aggregate(Aggregation.newAggregation(
                sort(Sort.by("timestamp").descending()),
                skip(0L),
                lookup("places", "placeKey", "placeKey", "place"),
                unwind("place", true),
                lookup("groups", "groupKey", "groupKey", "group"),
                unwind("group", true),
                lookup("users", "userKey", "userKey", "user"),
                unwind("user", true),
                new FreeFormOperation("$unset", "group.admins", "group.members", "group.followers")
        ), "posts", ExtendedPost.class);

        final Flux<ExtendedPost> posts = postService.getPosts(Optional.empty(), Optional.empty(), EnumSet.allOf(PostType.class), false);
        postService.groupSliders(posts, true, 50)
                .map(Objects::toString)
                .doOnComplete(() -> log.info("Took: {}", Duration.between(start, Instant.now()).toMillis()))
                .subscribe(z -> log.info("Generated: {}", z));
    }

}
