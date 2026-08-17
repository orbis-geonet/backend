package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.UserSubscriptionStatus;
import to.orbis.v2.backend.models.entity.UserSubscription;
import to.orbis.v2.backend.repositories.GroupsRepository;
import to.orbis.v2.backend.repositories.SubscriptionRepository;
import to.orbis.v2.backend.repositories.UserSubscriptionRepository;
import to.orbis.v2.backend.repositories.UsersRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleService {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final GroupsRepository groupsRepository;
    private final UsersRepository usersRepository;
    private final NotificationsService notificationsService;

    @Scheduled(cron = "0 0 9 * * *")
    public void processSubscriptionLifecycle() {
        expireSubscriptions()
                .then(sendRenewalReminders())
                .subscribe(
                        ignored -> {},
                        error -> log.error("Subscription lifecycle job failed", error),
                        () -> log.info("Subscription lifecycle job completed")
                );
    }

    private Mono<Void> expireSubscriptions() {
        var now = Instant.now();
        return userSubscriptionRepository.findAllByStatusInAndEndDateBefore(
                        List.of(UserSubscriptionStatus.ACTIVATED, UserSubscriptionStatus.DEACTIVATED_REQUEST),
                        now
                )
                .flatMap(userSubscription -> {
                    userSubscription.setStatus(UserSubscriptionStatus.DEACTIVATED);
                    userSubscription.setTimestamp(now);
                    return userSubscriptionRepository.save(userSubscription);
                })
                .then();
    }

    private Mono<Void> sendRenewalReminders() {
        var now = Instant.now();
        var today = LocalDate.now();
        var reminderEnd = now.plus(7, ChronoUnit.DAYS);
        return userSubscriptionRepository.findAllByStatusAndEndDateBetween(UserSubscriptionStatus.ACTIVATED, now, reminderEnd)
                .filter(userSubscription -> userSubscription.getLastReminderDate() == null || !today.equals(userSubscription.getLastReminderDate()))
                .flatMap(userSubscription -> Mono.zip(
                                subscriptionRepository.findOneBySubscriptionKeyAndDeletedFalse(userSubscription.getSubscriptionKey()),
                                groupsRepository.findOneByGroupKeyAndDeletedFalse(userSubscription.getGroupKey()),
                                usersRepository.findOneByUserKey(userSubscription.getUserKey())
                        )
                        .flatMap(tuple -> notificationsService.notifySubscriptionRenewalReminder(tuple.getT1(), tuple.getT2(), userSubscription, tuple.getT3())
                                .then(markReminderSent(userSubscription, today))))
                .then();
    }

    private Mono<UserSubscription> markReminderSent(UserSubscription userSubscription, LocalDate today) {
        userSubscription.setLastReminderDate(today);
        userSubscription.setTimestamp(Instant.now());
        return userSubscriptionRepository.save(userSubscription);
    }
}
