package to.orbis.v2.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@DependsOn("firebaseConfiguration")
@RequiredArgsConstructor
@Profile({"prod", "staging"})
public class FirestoreService {


    public static final String NOTIFICATION_SERVER = "notificationServer";
    public static final String CHAT_MESSAGES = "chatMessages";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String DONE = "done";
    @NonFinal
    private Firestore db;
    UUID serverId = UUID.randomUUID();

    ObjectMapper objectMapper;
    NotificationsService notificationsService;

    @PostConstruct
    @SneakyThrows
    public void initListenForChatMessages() {
        db = FirestoreClient.getFirestore();
        db.collection("chatMessages")
                .whereEqualTo("isRead", false).orderBy("timestamp", Query.Direction.DESCENDING).addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null) {
//                         showDebugInfo(snapshot);

                        val documents = snapshot.getDocuments().stream().collect(Collectors.toMap(DocumentSnapshot::getId, Function.identity()));
                        for (DocumentChange dc : snapshot.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                claimNotification(dc.getDocument().getId());
                            }

                            if (dc.getType() == DocumentChange.Type.MODIFIED) {
                                checkAndNotify(dc.getDocument().getId(), documents.get(dc.getDocument().getId()));
                            }
                        }
                    }

                    if (error != null) {
                        log.error("Error while listening for chat", error);
                    }
                });
        log.info("Listening for chat");
    }

    private void checkAndNotify(String id, QueryDocumentSnapshot doc) {
        try {
            if (!Objects.equals(doc.getString(NOTIFICATION_SERVER), serverId.toString())) {
                log.debug("{} claimed by other server. skipping", id);
                return;
            }

            if (!doc.contains("conversationId")) {
                log.error("Corrupted chat message: {}", id);
                finishProcessing(id);
                return;
            }

            val convoId = doc.getString(CONVERSATION_ID);

            if (convoId == null) {
                log.error("Corrupted chat message: {}", id);
                finishProcessing(id);
                return;
            }

            val convo = db.collection("conversation").document(convoId).get().get();

            if (!convo.exists()) {
                log.error("Conversation for {} message with id {} does not exist", id, convoId);
                // not attempting anymore
                finishProcessing(id);

                return;
            }

            val fromUser = doc.getString("senderId");

            if (!(convo.get("participants") instanceof List)) {
                log.error("Conversation {} for chat message {} does not contain list of participants", convoId, id);
                finishProcessing(id);
                return;
            }

            @SuppressWarnings("unchecked")
            val participants = (List<String>) convo.get("participants");

            if (participants == null || participants.size() != 2 || !participants.contains(fromUser)) {
                log.error("Chat conversation {} for message {} does not contain valid list of participants", convoId, id);
                finishProcessing(id);
                return;
            }

            int idx = 1 - participants.indexOf(fromUser);
            val toUser = participants.get(idx);

            notificationsService.sendChatNotification(fromUser, toUser,
                    doc.getString("message"), doc.getString("type"), doc.getString("mediaUrl"), convoId)
                    .subscribeOn(Schedulers.boundedElastic())
                    .publishOn(Schedulers.boundedElastic())
                    .doOnTerminate(() -> finishProcessing(id))
                    .subscribe(_ignored -> {},
                            error -> log.error("Error while sending notification about chat message {}", id, error));
        } catch (Exception ex) {
            log.error("Error while trying to notify chat: {}", id);
        }
    }

    private void finishProcessing(String id) {
        db.collection(CHAT_MESSAGES).document(id).update(NOTIFICATION_SERVER, DONE);
    }

    private void claimNotification(String id) {
        val docRef = db.collection(CHAT_MESSAGES).document(id);

        db.runTransaction(transaction -> {
            val chatMessage = transaction.get(docRef).get();
            if (chatMessage.contains(NOTIFICATION_SERVER) && chatMessage.getString(NOTIFICATION_SERVER) != null
                    && (chatMessage.getUpdateTime() == null
                    || chatMessage.getUpdateTime().getSeconds() > Instant.now().minus(5, ChronoUnit.MINUTES).toEpochMilli() / 1000)
            ) {
                log.debug("server {} is already notifying about {}", chatMessage.get(NOTIFICATION_SERVER), id);
                return "other server already notifying";
            }

            if (Objects.equals(chatMessage.getString(NOTIFICATION_SERVER), "done")) {
                log.debug("Notification is already sent");
                return "Notification is already sent";
            }

            log.debug("Trying to claim {} for {}", id, serverId);
            transaction.update(docRef, NOTIFICATION_SERVER, serverId.toString());

            return "claimed";
        });
    }

    private void showDebugInfo(QuerySnapshot value) {
        log.info("Received: {}", value.getDocuments().stream().map(queryDocumentSnapshot -> {
            val data = new HashMap<>(queryDocumentSnapshot.getData());
            data.put("docId", queryDocumentSnapshot.getId());
            return data;
        }).map(m -> {
            try {
                return objectMapper.writeValueAsString(m);
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }).collect(Collectors.joining("\n")));

        log.info("Received changes: {}", value.getDocumentChanges().stream().map(documentChange -> {
            val data = new HashMap<String, Object>();
            data.put("docId", documentChange.getDocument().getId());
            data.put("type", documentChange.getType());
            data.put("newIndex", documentChange.getNewIndex());
            data.put("oldIndex", documentChange.getOldIndex());
            return data;
        }).map(m -> {
            try {
                return objectMapper.writeValueAsString(m);
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }).collect(Collectors.joining("\n")));
    }

    public void updateLastActive(String name) {
        val map = new HashMap<String, Object>();
        map.put("lastActive", Instant.now().toEpochMilli());
        db.collection("userActivity").document(name).set(map);
    }
}
