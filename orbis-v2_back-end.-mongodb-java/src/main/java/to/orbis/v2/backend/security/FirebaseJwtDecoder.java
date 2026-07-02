package to.orbis.v2.backend.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class FirebaseJwtDecoder implements ReactiveJwtDecoder {

    RSASSAVerifier verifier;

    @Override
    public Mono<Jwt> decode(String token) throws JwtException {
        try {
            final JWT parsed = JWTParser.parse(token);

            if (!parsed.getJWTClaimsSet().getClaim("iss").equals("admin-panel")) {
                FirebaseAuth.getInstance().verifyIdToken(token, true);
            } else {
                verifyAdminPanelToken(parsed);
            }

            return Mono.just(new Jwt(
                    parsed.getParsedString(),
                    ((Date) parsed.getJWTClaimsSet().getClaim("iat")).toInstant(),
                    ((Date) parsed.getJWTClaimsSet().getClaim("exp")).toInstant(),
                    new LinkedHashMap<>(parsed.getHeader().toJSONObject()),
                    parsed.getJWTClaimsSet().getClaims()
            ));
        } catch (FirebaseAuthException | ParseException | JOSEException e) {
            log.warn("Auth failed", e);
            if (e.getMessage().startsWith("No user record found")) {
                throw new JwtException("No user record found", e);
            }
            throw new JwtException(e.getMessage(), e);
        }
    }

    private void verifyAdminPanelToken(JWT parsed) throws JOSEException {
        if (!(parsed instanceof JWSObject)) {
            throw new JwtException("Unsigned token");
        }

        val signed = (JWSObject) parsed;
        if (!signed.verify(verifier)) {
            throw new JwtException("Bad signature");
        }
    }
}
