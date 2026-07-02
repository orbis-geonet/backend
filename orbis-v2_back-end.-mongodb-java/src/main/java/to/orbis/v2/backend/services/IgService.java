package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.mappers.UserPictureMapper;
import to.orbis.v2.backend.models.IgStatus;
import to.orbis.v2.backend.models.UserPictureType;
import to.orbis.v2.backend.models.entity.IgLink;
import to.orbis.v2.backend.models.entity.UserPicture;
import to.orbis.v2.backend.models.ig.IgMedia;
import to.orbis.v2.backend.models.ig.IgMediaType;
import to.orbis.v2.backend.models.ig.IgResponse;
import to.orbis.v2.backend.repositories.IgRepository;
import to.orbis.v2.backend.repositories.UserPictureRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IgService {

    IgRepository repository;
    IgWebClient webClient;
    UserPictureRepository userPictureRepository;
    UserPictureMapper userPictureMapper;

    public Mono<IgLink> connect(String userKey) {
        return repository.findByUserKeyEqualsAndExpirationTimeAfter(userKey, Instant.now())
                .map(igLink -> igLink.setStatus(IgStatus.CONNECTED))
                .switchIfEmpty(startConnecting(userKey));
    }

    private Mono<IgLink> startConnecting(String userKey) {
        val state = UUID.randomUUID().toString();
        val link = IgLink
                .builder()
                .state(state)
                .status(IgStatus.NOT_CONNECTED)
                .userKey(userKey)
                .build()
                .setAuthLink(webClient.generateAuthUrl(state));
        return repository.save(link);
    }

    public Mono<String> finishConnect(String code, String state) {
        return repository.findByStateEquals(state)
                .flatMap(igLink -> webClient.exchangeCodeForToken(igLink, code))
                .flatMap(webClient::exchangeForLongTermToken)
                .map(igLink -> igLink.setStatus(IgStatus.CONNECTED).setState(null))
                .flatMap(repository::save)
                .flatMap(igLink -> repository.deleteAllByUserKeyEqualsAndIdNot(igLink.getUserKey(), igLink.getId())
                        .then(Mono.just(igLink)))
                .map(_link -> "connected");
    }

    public Mono<IgResponse<IgMedia>> listMedia(String userKey) {
        return repository.findByUserKeyEqualsAndExpirationTimeAfter(userKey, Instant.now())
                .switchIfEmpty(Mono.error(() -> new RuntimeException("Not connected. Use /ig/connect first")))
                .flatMap(webClient::listMedia);
    }

    public Flux<UserPicture> fillMedia(IgLink igLink) {
        return userPictureRepository.findFirstByUserKeyAndTypeOrderByTimestampDesc(igLink.getUserKey(), UserPictureType.INSTAGRAM)
                .map(Optional::of)
                .switchIfEmpty(Mono.just(Optional.empty()))
                .flatMapMany(freshPicture -> {

                    val media = webClient.listMedia(igLink);

                    return media.flux().expand(resp -> {
                        log.info("Expanding from {}, got {} media", resp.getData().get(0).getId(), resp.getData().size());
                        val maybeFoundFresh = freshPicture.filter(fp -> resp
                                .getData().stream().map(IgMedia::getId)
                                .anyMatch(id -> fp.getIgMediaId().equals(id)))
                                .map(_found -> Flux.<IgResponse<IgMedia>>empty());

                        return maybeFoundFresh
                                .orElseGet(() -> resp.getPaging().getNext() == null
                                        ? Flux.<IgResponse<IgMedia>>empty()
                                        : webClient.listMedia(resp.getPaging().getNext()).flux());
                    })
                            .flatMap(m -> Flux.fromIterable(m.getData()))
                            .sort(Comparator.comparing(IgMedia::getTimestamp).reversed())
                            .takeWhile(m -> freshPicture.map(fp -> !fp.getIgMediaId().equals(m.getId())).orElse(true))
                            .flatMap(igMedia -> {
                                if (igMedia.getMediaType() == IgMediaType.CAROUSEL_ALBUM) {
                                    return fetchAlbumImages(igLink, igMedia);
                                }
                                return Mono.just(userPictureMapper.igMediaToUserPicture(igMedia));
                            });
                });
    }

    private Mono<UserPicture> fetchAlbumImages(IgLink igLink, IgMedia igMedia) {
        return webClient.getChildrenMedia(igLink, igMedia)
                .map(urls -> userPictureMapper.igMediaToUserPicture(igMedia).setPictureUrl(urls));
    }

    public Mono<Void> removeIgLinkForIgUserId(Long igUserId) {
        return repository.findFirstByIgUserId(igUserId)
                .flatMap(igLink -> userPictureRepository.deleteAllByUserKeyAndType(igLink.getUserKey(), UserPictureType.INSTAGRAM))
                .then(repository.deleteAllByIgUserId(igUserId));
    }

    public Mono<UserPicture> refreshLinks(String userKey, UserPicture userPicture) {
        return repository.findByUserKeyEqualsAndExpirationTimeAfter(userKey, Instant.now())
                .flatMap(igLink -> {
                    val mediaUrl = userPicture.getPictureUrl().get(0);
                    return userPicture.getPictureUrl().size() > 1
                            // refresh carousel
                            ? webClient.getChildrenMedia(igLink, new IgMedia().setMediaUrl(mediaUrl).setId(userPicture.getIgMediaId()))
                            : webClient.getMediaLink(igLink, userPicture.getIgMediaId(), mediaUrl);
                })
                .map(pictureUrl -> userPicture
                        .setPictureUrl(pictureUrl)
                        .setLoadTimestamp(Instant.now()))
                .flatMap(userPictureRepository::save);
    }
}
