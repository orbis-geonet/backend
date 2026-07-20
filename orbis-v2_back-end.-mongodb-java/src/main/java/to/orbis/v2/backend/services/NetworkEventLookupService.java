package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.utils.GeoHashUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@Slf4j
@RequiredArgsConstructor
public class NetworkEventLookupService {

    private static final String COLLECTION = "network_events";

    private final ReactiveMongoTemplate mongoTemplate;

    public Mono<String> byKey(String collectionName, String key, boolean javaProxied) {
        if (javaProxied || key == null) return Mono.empty();
        return find(collectionName, "keyHash", hashKey(key));
    }

    public Mono<String> bySecondary(String collectionName, String value, boolean javaProxied) {
        if (javaProxied || value == null) return Mono.empty();
        return find(collectionName, "secondaryHash", hashShort(value));
    }

    public Mono<String> byName(String collectionName, String value, boolean javaProxied) {
        if (javaProxied || value == null) return Mono.empty();
        return find(collectionName, "nameHash", hashShort(value));
    }

    public Mono<String> byParent(String collectionName, String value, boolean javaProxied) {
        if (javaProxied || value == null) return Mono.empty();
        return find(collectionName, "parentHash", hashShort(value));
    }

    public Mono<String> byParent2(String collectionName, String value, boolean javaProxied) {
        if (javaProxied || value == null) return Mono.empty();
        return find(collectionName, "parentHash2", hashShort(value));
    }

    public Mono<String> byAuthor(String collectionName, String value, boolean javaProxied) {
        if (javaProxied || value == null) return Mono.empty();
        return find(collectionName, "authorHash", hashShort(value));
    }

    public Mono<String> byGeo(String collectionName, Double lat, Double lon, boolean javaProxied) {
        if (javaProxied || lat == null || lon == null) return Mono.empty();
        return find(collectionName, "geoHash", GeoHashUtils.geoHashEncode3Bytes(lat, lon));
    }

    public Mono<String> byCollection(String collectionName, boolean javaProxied) {
        if (javaProxied) return Mono.empty();
        return find(collectionName, null, null);
    }

    private Mono<String> find(String collectionName, String hashField, String hashValue) {
        Criteria criteria = Criteria.where("collectionName").is(collectionName).and("status").is("pending");
        if (hashField != null) {
            criteria = criteria.and(hashField).is(hashValue);
        }
        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "timestamp"));

        return mongoTemplate.find(query, Document.class, COLLECTION)
                .next()
                .map(doc -> doc.getObjectId("_id").toHexString());
    }

    public static String hashKey(String value) {
        return hash(value, 8, "0000000000000000");
    }

    public static String hashShort(String value) {
        return hash(value, 4, "00000000");
    }

    private static String hash(String value, int bytes, String zero) {
        if (value == null || value.isEmpty()) return zero;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] h = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes; i++) {
                String hex = Integer.toHexString(0xff & h[i]);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            return zero;
        }
    }
}
