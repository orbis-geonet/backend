package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.repositories.FollowsRepository;
import to.orbis.v2.backend.repositories.StoriesAggregationRepository;
import to.orbis.v2.backend.repositories.StoriesRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoriesService {

    StoriesRepository storiesRepository;
    StoriesAggregationRepository storiesAggregationRepository;
    FollowsRepository followsRepository;
    PlacesInfoService placesInfoService;

    public Mono<Story> saveToStory(Post post) {
        return storiesRepository.findOneByGroupKey(post.getGroupKey())
                .switchIfEmpty(Mono.just(new Story().setGroupKey(post.getGroupKey())))
                .map(s -> s.addPost(post))
                .flatMap(storiesRepository::save);
    }

    public Flux<ExtendedStory> getNearStories(GeoJsonPoint geoJsonPoint, double distance, boolean seen, Optional<String> userKey, Pageable pageable) {
        return storiesAggregationRepository.findNearStories(geoJsonPoint, distance, seen, userKey, pageable);
    }

    public Flux<ExtendedStory> getCityStories(GeoJsonPoint geoJsonPoint, boolean seen, Optional<String> userKey, Pageable pageable) {
        return placesInfoService.findCityByCoordinates(geoJsonPoint)
                .flatMapMany(city -> storiesAggregationRepository.findCityStories(city, seen, userKey, pageable));
    }

    public Flux<ExtendedStory> getNewsStories(boolean seen, String userKey, Pageable pageable) {
        return followsRepository.findAllByFollowerKeyAndAcceptedTrue(userKey)
                .buffer()
                .flatMap(follows -> storiesAggregationRepository.findUserStories(follows, seen, userKey, pageable));
    }

    public Mono<Void> deletePostFromStory(String postKey) {
        return storiesAggregationRepository.findByPostsContains(postKey)
                .map(s -> s.removePost(postKey))
                .buffer(20)
                .flatMap(storiesRepository::saveAll)
                .then();
    }
}
