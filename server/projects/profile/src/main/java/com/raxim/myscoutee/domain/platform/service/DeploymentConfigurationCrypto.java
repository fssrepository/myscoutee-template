package com.raxim.myscoutee.domain.platform.service;

import com.raxim.myscoutee.domain.platform.service.OperatorConfigurationService;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.platform.config.OperatorConfigurationProperties;
import com.raxim.myscoutee.domain.platform.document.DeploymentConfiguration;
import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;

@Component
public class DeploymentConfigurationCrypto implements com.raxim.myscoutee.domain.platform.api.SecretEncryption,
        com.raxim.myscoutee.domain.platform.api.EmailSecrets {
    static final String ALGORITHM = "AES-256-GCM";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_KEY_FILE_BYTES = 4096;
    private static final String AAD_PREFIX = "myscoutee/deployment-configuration/v1/";

    private final OperatorConfigurationProperties properties;
    private final SecureRandom secureRandom;

    @Autowired
    public DeploymentConfigurationCrypto(OperatorConfigurationProperties properties) {
        this(properties, new SecureRandom());
    }

    DeploymentConfigurationCrypto(
            OperatorConfigurationProperties properties,
            SecureRandom secureRandom) {
        this.properties = Objects.requireNonNull(properties);
        this.secureRandom = Objects.requireNonNull(secureRandom);
    }

    public String unavailableReason() {
        try {
            loadKey();
            return null;
        } catch (IllegalStateException exception) {
            return exception.getMessage();
        }
    }

    public String unavailableReason(DeploymentConfiguration configuration) {
        try {
            loadKey();
            if (configuration == null) {
                return null;
            }
            DeploymentConfiguration.Firebase firebase = configuration.getFirebase();
            if (firebase != null) {
                verify(
                        OperatorConfigurationService.FIREBASE_AUTHENTICATION_PURPOSE,
                        firebase.getAuthenticationCredentialEnvelope());
                verify(
                        OperatorConfigurationService.FIREBASE_MESSAGING_PURPOSE,
                        firebase.getMessagingCredentialEnvelope());
            }
            return null;
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return exception.getMessage();
        }
    }

    @Override public EncryptedSecret encryptEmailSecret(com.raxim.myscoutee.domain.platform.api.EmailSecrets.Purpose purpose, String plaintext) {
        return encrypt(Objects.requireNonNull(purpose).aadPurpose(), plaintext);
    }
    @Override public String decryptEmailSecret(com.raxim.myscoutee.domain.platform.api.EmailSecrets.Purpose purpose, EncryptedSecret envelope) {
        return decrypt(Objects.requireNonNull(purpose).aadPurpose(), envelope);
    }
    public EncryptedSecret encrypt(String purpose, String plaintext) {
        if (!StringUtils.hasText(plaintext)) {
            throw new IllegalArgumentException("Secret plaintext must not be blank.");
        }
        KeyMaterial key = loadKey();
        byte[] nonce = new byte[NONCE_BYTES];
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = null;
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key.key(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(purpose));
            ciphertext = cipher.doFinal(plaintextBytes);

            EncryptedSecret envelope = new EncryptedSecret();
            envelope.setAlgorithm(ALGORITHM);
            envelope.setKeyId(key.keyId());
            envelope.setNonce(Base64.getEncoder().encodeToString(nonce));
            envelope.setCiphertext(Base64.getEncoder().encodeToString(ciphertext));
            return envelope;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Deployment configuration secret encryption is unavailable.",
                    exception);
        } finally {
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(plaintextBytes, (byte) 0);
            if (ciphertext != null) {
                Arrays.fill(ciphertext, (byte) 0);
            }
        }
    }

    public String decrypt(String purpose, EncryptedSecret envelope) {
        byte[] plaintext = decryptBytes(purpose, envelope);
        if (plaintext == null) {
            return null;
        }
        try {
            return new String(plaintext, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private void verify(String purpose, EncryptedSecret envelope) {
        if (envelope == null) {
            return;
        }
        byte[] plaintext = decryptBytes(purpose, envelope);
        if (plaintext != null) {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private byte[] decryptBytes(String purpose, EncryptedSecret envelope) {
        if (envelope == null) {
            return null;
        }
        KeyMaterial key = loadKey();
        if (!ALGORITHM.equals(envelope.getAlgorithm())
                || !constantTimeEquals(key.keyId(), envelope.getKeyId())) {
            throw new IllegalStateException(
                    "Deployment configuration secret uses a different or unsupported master key.");
        }
        byte[] nonce = null;
        byte[] ciphertext = null;
        try {
            if (!StringUtils.hasText(envelope.getNonce())
                    || !StringUtils.hasText(envelope.getCiphertext())) {
                throw new IllegalArgumentException(
                        "Invalid encrypted secret envelope.");
            }
            nonce = Base64.getDecoder().decode(envelope.getNonce());
            ciphertext = Base64.getDecoder().decode(envelope.getCiphertext());
            if (nonce.length != NONCE_BYTES
                    || ciphertext.length <= GCM_TAG_BITS / Byte.SIZE) {
                throw new IllegalArgumentException(
                        "Invalid encrypted secret envelope.");
            }

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key.key(),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(purpose));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Deployment configuration secret could not be decrypted.",
                    exception);
        } finally {
            if (nonce != null) {
                Arrays.fill(nonce, (byte) 0);
            }
            if (ciphertext != null) {
                Arrays.fill(ciphertext, (byte) 0);
            }
        }
    }

    private KeyMaterial loadKey() {
        Path configuredPath = properties.getMasterKeyPath();
        if (configuredPath == null) {
            throw new IllegalStateException(
                    "Operator configuration master-key path is not configured.");
        }
        Path path = configuredPath.toAbsolutePath().normalize();
        byte[] fileBytes = null;
        byte[] keyBytes = null;
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || Files.isSymbolicLink(path)) {
                throw new IllegalStateException(
                        "Operator configuration master key must be a regular non-symlink file.");
            }
            if (attributes.size() <= 0 || attributes.size() > MAX_KEY_FILE_BYTES) {
                throw new IllegalStateException(
                        "Operator configuration master key file has an invalid size.");
            }
            fileBytes = readBounded(path);
            keyBytes = decodeKey(fileBytes);
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
            return new KeyMaterial(key, keyId(keyBytes));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Operator configuration master key is not readable at " + path + ".",
                    exception);
        } finally {
            if (fileBytes != null) {
                Arrays.fill(fileBytes, (byte) 0);
            }
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    private byte[] readBounded(Path path) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_KEY_FILE_BYTES + 1);
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = Files.newByteChannel(path, options)) {
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                // Continue until EOF or the hard bound.
            }
        }
        if (!buffer.hasRemaining()) {
            throw new IllegalStateException(
                    "Operator configuration master key file is too large.");
        }
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private byte[] decodeKey(byte[] fileBytes) {
        String encoded = new String(fileBytes, StandardCharsets.US_ASCII).trim();
        byte[] decoded;
        try {
            if (encoded.matches("[0-9a-fA-F]{64}")) {
                decoded = HexFormat.of().parseHex(encoded);
            } else {
                decoded = Base64.getDecoder().decode(encoded);
            }
        } catch (IllegalArgumentException exception) {
            if (fileBytes.length == KEY_BYTES) {
                decoded = fileBytes.clone();
            } else {
                throw new IllegalStateException(
                        "Operator configuration master key must contain 32 raw bytes, "
                                + "64 hexadecimal characters, or base64 for 32 bytes.",
                        exception);
            }
        }
        if (decoded.length != KEY_BYTES) {
            Arrays.fill(decoded, (byte) 0);
            throw new IllegalStateException(
                    "Operator configuration master key must decode to exactly 32 bytes.");
        }
        return decoded;
    }

    private byte[] aad(String purpose) {
        if (!StringUtils.hasText(purpose)) {
            throw new IllegalArgumentException("Encrypted secret purpose must not be blank.");
        }
        return (AAD_PREFIX + purpose.trim()).getBytes(StandardCharsets.UTF_8);
    }

    private String keyId(byte[] keyBytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(keyBytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private record KeyMaterial(
            SecretKeySpec key,
            String keyId) {
    }
}
