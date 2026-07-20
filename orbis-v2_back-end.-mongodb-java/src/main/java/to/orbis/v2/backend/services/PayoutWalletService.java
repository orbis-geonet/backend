package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.dto.crypto.PayoutWalletDto;
import to.orbis.v2.backend.models.entity.PayoutWallet;
import to.orbis.v2.backend.repositories.PayoutWalletRepository;

import java.math.BigInteger;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutWalletService {
    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private final PayoutWalletRepository payoutWalletRepository;

    public Mono<PayoutWalletDto> setWallet(String userKey, String solanaPubkey) {
        String pubkey = solanaPubkey == null ? "" : solanaPubkey.trim();
        if (!isValidSolanaPubkey(pubkey)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Solana address."));
        }
        return payoutWalletRepository.findByUserKey(userKey)
                .switchIfEmpty(Mono.fromSupplier(() -> newWallet(userKey)))
                .flatMap(wallet -> {
                    wallet.setSolanaPubkey(pubkey);
                    wallet.setTimestamp(Instant.now());
                    return payoutWalletRepository.save(wallet);
                })
                .map(wallet -> new PayoutWalletDto().setSolanaPubkey(wallet.getSolanaPubkey()).setReady(true));
    }

    public Mono<PayoutWalletDto> getWallet(String userKey) {
        return payoutWalletRepository.findByUserKey(userKey)
                .map(wallet -> new PayoutWalletDto().setSolanaPubkey(wallet.getSolanaPubkey()).setReady(true))
                .switchIfEmpty(Mono.fromSupplier(() -> new PayoutWalletDto().setReady(false)));
    }

    public Mono<Void> deleteWallet(String userKey) {
        return payoutWalletRepository.findByUserKey(userKey)
                .flatMap(payoutWalletRepository::delete)
                .then();
    }

    public Mono<String> requirePayoutPubkey(String userKey) {
        return payoutWalletRepository.findByUserKey(userKey)
                .map(PayoutWallet::getSolanaPubkey)
                .filter(pubkey -> pubkey != null && !pubkey.isEmpty())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tribe owner has not set an ORBIS payout wallet.")));
    }

    private PayoutWallet newWallet(String userKey) {
        var id = new ObjectId();
        var wallet = PayoutWallet.builder()
                .userKey(userKey)
                .createTimestamp(Instant.now())
                .build();
        wallet.setId(id);
        return wallet;
    }

    public static boolean isValidSolanaPubkey(String value) {
        if (value == null || value.length() < 32 || value.length() > 44) {
            return false;
        }
        byte[] decoded = base58Decode(value);
        return decoded != null && decoded.length == 32;
    }

    private static byte[] base58Decode(String input) {
        if (input.isEmpty()) {
            return new byte[0];
        }
        BigInteger value = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(58);
        for (int i = 0; i < input.length(); i++) {
            int digit = BASE58_ALPHABET.indexOf(input.charAt(i));
            if (digit < 0) {
                return null;
            }
            value = value.multiply(base).add(BigInteger.valueOf(digit));
        }
        byte[] full = value.toByteArray();
        int offset = (full.length > 1 && full[0] == 0) ? 1 : 0;
        int leadingZeros = 0;
        for (int i = 0; i < input.length() && input.charAt(i) == '1'; i++) {
            leadingZeros++;
        }
        byte[] decoded = new byte[leadingZeros + (full.length - offset)];
        System.arraycopy(full, offset, decoded, leadingZeros, full.length - offset);
        return decoded;
    }
}
