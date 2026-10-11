package com.raxim.myscoutee.common.security.crypto;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
/** Pure public-key protocol verification; no key loading or durable state access. */
public final class Ed25519Signatures {
    private Ed25519Signatures() {}
    private static final String PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_KEY_END = "-----END PUBLIC KEY-----";

    public static PublicKey parsePublicKeyDer(byte[] publicKeyDer, String fieldName) {
        try {
            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(
                            Objects.requireNonNull(publicKeyDer, fieldName + " must not be null.")));
            if (publicKey.getEncoded() == null
                    || !java.util.Arrays.equals(publicKeyDer, publicKey.getEncoded())) {
                throw new IllegalArgumentException(fieldName + " is not canonical Ed25519 SPKI DER.");
            }
            return publicKey;
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException(fieldName + " is not an Ed25519 SPKI public key.", exception);
        }
    }

    public static PublicKey parsePublicKeyPemOrDer(
            byte[] publicKeyFile,
            String fieldName) {
        byte[] supplied = Objects.requireNonNull(
                publicKeyFile,
                fieldName + " must not be null.");
        String text = new String(supplied, StandardCharsets.US_ASCII).trim();
        if (!text.startsWith(PUBLIC_KEY_BEGIN)
                || !text.endsWith(PUBLIC_KEY_END)) {
            return parsePublicKeyDer(supplied, fieldName);
        }
        String encoded = text.substring(
                        PUBLIC_KEY_BEGIN.length(),
                        text.length() - PUBLIC_KEY_END.length())
                .replaceAll("\\s", "");
        try {
            byte[] publicDer = Base64.getDecoder().decode(encoded);
            return parsePublicKeyDer(publicDer, fieldName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    fieldName + " is not a canonical Ed25519 PUBLIC KEY PEM.",
                    exception);
        }
    }

    public static boolean verifyBase64(PublicKey publicKey, byte[] canonicalMessage, String signatureBase64) {
        byte[] signature = CanonicalValues.decodeStandardBase64(signatureBase64, "signature");
        if (signature.length != 64) {
            throw new IllegalArgumentException("Ed25519 signature must be 64 bytes.");
        }
        return verify(publicKey, canonicalMessage, signature);
    }

    public static boolean verify(PublicKey publicKey, byte[] canonicalMessage, byte[] signatureBytes) {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(Objects.requireNonNull(publicKey, "Public key must not be null."));
            verifier.update(Objects.requireNonNull(canonicalMessage, "Canonical message must not be null."));
            return verifier.verify(Objects.requireNonNull(signatureBytes, "Signature must not be null."));
        } catch (SignatureException exception) {
            // Malformed attacker-controlled Ed25519 signatures are invalid input,
            // not an unavailable cryptographic primitive or server failure.
            return false;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to verify Ed25519 signature.", exception);
        }
    }

}
