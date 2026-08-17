package to.orbis.v2.polygons.creator.utils;

import org.bson.types.ObjectId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a deterministic, content-addressed identity for a polygon.
 *
 * <p>A polygon is fully determined by the set of places it is built from. In the federated
 * clone network the very same territory is computed independently on every clone (each clone
 * runs its own polygon-creator over its own — eventually consistent — copy of the places), and
 * cross-clone sync ({@code syncStateToDb}) as well as the on-chain batch push both deduplicate by
 * {@code polygonKey}. If that key were random (a fresh {@link ObjectId} per computation) the same
 * polygon would carry a different key on each clone and could never be reconciled, so the records
 * pile up and overlap instead of merging.
 *
 * <p>Deriving the key from the (order-independent) set of place keys guarantees that the same
 * place-set yields the same key on every clone and on every recomputation, so updates land on the
 * existing document and overlapping clones converge to a single polygon.
 */
public final class PolygonKeyUtils {

    private PolygonKeyUtils() {
    }

    /**
     * Deterministic polygon key: hex SHA-256 of the place keys, sorted so ordering does not matter.
     * Falls back to a random key only when there are no place keys to derive identity from.
     */
    public static String deterministicKey(List<String> placeKeys) {
        if (placeKeys == null || placeKeys.isEmpty()) {
            return new ObjectId().toHexString();
        }

        List<String> normalized = new ArrayList<>(placeKeys);
        normalized.removeIf(key -> key == null || key.isBlank());
        if (normalized.isEmpty()) {
            return new ObjectId().toHexString();
        }
        normalized.sort(String::compareTo);

        String canonical = normalized.stream().distinct().collect(Collectors.joining(","));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available on a standard JVM; fail safe to a stable but non-random key.
            return Integer.toHexString(canonical.hashCode());
        }
    }
}
