package com.networth.networthbackend.gmail;

import com.fasterxml.jackson.databind.JsonNode;
import com.networth.networthbackend.common.Jsons;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies the OIDC token Pub/Sub attaches to each push request, so the public webhook only acts
 * on calls Google actually made for our subscription. Checks the RS256 signature against Google's
 * published keys, then issuer, audience, service-account email and expiry.
 */
@Slf4j
public class PubSubTokenVerifier {

    static final String ENV_AUDIENCE = "GMAIL_PUSH_AUDIENCE";
    static final String ENV_SA_EMAIL = "GMAIL_PUSH_SA_EMAIL";

    private static final String CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** Google's signing keys by key id. Keys rotate, so an unknown kid triggers one re-fetch. */
    private static final Map<String, PublicKey> KEYS = new ConcurrentHashMap<>();

    /** True only for a valid, unexpired token issued to our push subscription's service account. */
    public boolean verify(String authorizationHeader) {
        String audience = System.getenv(ENV_AUDIENCE);
        String saEmail = System.getenv(ENV_SA_EMAIL);
        if (audience == null || audience.isBlank() || saEmail == null || saEmail.isBlank()) {
            log.error("{} / {} not set; rejecting all push requests", ENV_AUDIENCE, ENV_SA_EMAIL);
            return false;
        }
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) return false;
        try {
            String[] parts = authorizationHeader.substring("Bearer ".length()).trim().split("\\.");
            if (parts.length != 3) return false;
            Base64.Decoder b64 = Base64.getUrlDecoder();
            JsonNode header = Jsons.MAPPER.readTree(b64.decode(parts[0]));
            JsonNode claims = Jsons.MAPPER.readTree(b64.decode(parts[1]));

            if (!"RS256".equals(header.path("alg").asText())) return false;
            PublicKey key = key(header.path("kid").asText());
            if (key == null) return false;
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!sig.verify(b64.decode(parts[2]))) return false;

            return ISSUERS.contains(claims.path("iss").asText())
                    && audience.equals(claims.path("aud").asText())
                    && saEmail.equalsIgnoreCase(claims.path("email").asText())
                    && claims.path("email_verified").asBoolean(false)
                    && Instant.now().getEpochSecond() < claims.path("exp").asLong(0);
        } catch (Exception e) {
            log.warn("Push token verification failed: {}", e.getMessage());
            return false;
        }
    }

    private static PublicKey key(String kid) throws Exception {
        PublicKey k = KEYS.get(kid);
        if (k != null) return k;
        synchronized (KEYS) {
            k = KEYS.get(kid);
            if (k != null) return k;
            HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(CERTS_URL))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new IllegalStateException("certs HTTP " + res.statusCode());
            KeyFactory kf = KeyFactory.getInstance("RSA");
            Base64.Decoder b64 = Base64.getUrlDecoder();
            for (JsonNode jwk : Jsons.MAPPER.readTree(res.body()).path("keys")) {
                BigInteger n = new BigInteger(1, b64.decode(jwk.path("n").asText()));
                BigInteger e = new BigInteger(1, b64.decode(jwk.path("e").asText()));
                KEYS.put(jwk.path("kid").asText(), kf.generatePublic(new RSAPublicKeySpec(n, e)));
            }
            return KEYS.get(kid);
        }
    }
}
