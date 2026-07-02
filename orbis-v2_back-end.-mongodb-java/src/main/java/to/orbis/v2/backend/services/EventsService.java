package to.orbis.v2.backend.services;

import com.google.appengine.repackaged.com.google.common.io.Files;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.entity.EventAttendee;
import to.orbis.v2.backend.models.entity.ExtendedPost;
import to.orbis.v2.backend.models.entity.SimplifiedUser;
import to.orbis.v2.backend.repositories.EventAttendeeRepository;
import to.orbis.v2.backend.repositories.EventsAggregationRepository;
import to.orbis.v2.backend.repositories.PostsAggregationsRepository;
import to.orbis.v2.backend.repositories.PostsRepository;

@Service
@RequiredArgsConstructor
public class EventsService {

    PostsRepository postsRepository;
    EventAttendeeRepository eventAttendeeRepository;
    EventsAggregationRepository eventsAggregationRepository;

    PostsAggregationsRepository postsAggregationsRepository;

    public Flux<SimplifiedUser> getAttendees(String postKey, Pageable pageable) {
        return eventsAggregationRepository.getAttendees(postKey, pageable);
    }

    public Mono<Void> notAttend(String postKey, String userKey) {
        return eventAttendeeRepository.deleteAllByPostKeyAndUserKey(postKey, userKey);
    }

    public Mono<Void> attend(String postKey, String userKey) {
        return postsRepository.findOneByPostKey(postKey)
                .filter(p -> p.getType() == PostType.EVENT && !p.isDeleted())
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Event not found")))
                .flatMap(p -> eventAttendeeRepository.findOneByPostKeyAndUserKey(postKey, userKey)
                        .switchIfEmpty(Mono.just(new EventAttendee()
                                        .setPostKey(postKey)
                                        .setUserKey(userKey))
                                .flatMap(eventAttendeeRepository::save)))
                .then();
    }

    public Flux<ExtendedPost> getAttending(boolean pastEvents, String userKey, Pageable pageable) {
        return postsAggregationsRepository.findAttending(pastEvents, userKey, pageable);
    }
}
