package to.orbis.v2.backend.models.entity;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class ChatUser {
    public ChatUser(String userKey, String displayName, String imageName, String providerImageUrl, Set<String> blockedBy) {
        this.userKey = userKey;
        this.displayName = displayName;
        this.imageName = imageName;
        this.providerImageUrl = providerImageUrl;
        this.blockedBy = blockedBy == null ? new HashSet<>() : blockedBy;
    }

    String userKey;
    String displayName;
    String imageName;
    String providerImageUrl;
    Set<String> blockedBy;
    boolean blocked;
}
