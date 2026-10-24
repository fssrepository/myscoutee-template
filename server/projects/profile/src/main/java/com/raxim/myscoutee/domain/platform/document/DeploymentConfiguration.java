package com.raxim.myscoutee.domain.platform.document;

import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "deploymentConfigurations")
@lombok.Getter @lombok.Setter
public class DeploymentConfiguration implements com.raxim.myscoutee.domain.platform.api.DeploymentSettings.Configuration {
    public static final String SINGLETON_ID = "deployment-configuration";
    public static final int SCHEMA_VERSION = 5;

    @Id
    private String id;
    @Version
    private Long version;
    private Integer schemaVersion;
    private Branding branding;
    private List<SocialLink> socialLinks;
    private PrivacyContact privacyContact;
    private Integration integration;
    private Firebase firebase;
    private String updatedAt;

    @lombok.Getter @lombok.Setter
    public static class Branding {
        private String productName;
        private Integer logoCharacterIndex;
        private String homeLabel;
        private String logoUrl;
        private Long revision;

    }

    @lombok.Getter @lombok.Setter
    public static class SocialLink {
        private String provider;
        private String label;
        private String url;
        private String icon;
        private String handle;

    }

    @lombok.Getter @lombok.Setter
    public static class PrivacyContact {
        private String dataControllerName;
        private String privacyContactEmail;

    }

    @lombok.Getter @lombok.Setter
    public static class Integration implements com.raxim.myscoutee.domain.platform.api.DeploymentSettings.Integration {
        private String publicBaseUrl;

    }

    @lombok.Getter @lombok.Setter
    public static class Firebase {
        private String projectId;
        private Long revision;
        private boolean active;
        private Long authenticationTestedRevision;
        private String authenticationTestedAt;
        private Long messagingTestedRevision;
        private String messagingTestedAt;
        private String activatedAt;
        private FirebasePublicClient publicClient;
        private EncryptedSecret authenticationCredentialEnvelope;
        private EncryptedSecret messagingCredentialEnvelope;

        public void setAuthenticationTestedRevision(
                Long authenticationTestedRevision) {
            this.authenticationTestedRevision =
                    authenticationTestedRevision;
        }

        public void setAuthenticationTestedAt(
                String authenticationTestedAt) {
            this.authenticationTestedAt = authenticationTestedAt;
        }

        public void setMessagingTestedRevision(
                Long messagingTestedRevision) {
            this.messagingTestedRevision = messagingTestedRevision;
        }

        public void setAuthenticationCredentialEnvelope(
                EncryptedSecret authenticationCredentialEnvelope) {
            this.authenticationCredentialEnvelope = authenticationCredentialEnvelope;
        }

        public void setMessagingCredentialEnvelope(
                EncryptedSecret messagingCredentialEnvelope) {
            this.messagingCredentialEnvelope = messagingCredentialEnvelope;
        }
    }

    @lombok.Getter @lombok.Setter
    public static class FirebasePublicClient {
        private String apiKey;
        private String authDomain;
        private String storageBucket;
        private String messagingSenderId;
        private String appId;
        private String measurementId;
        private String vapidKey;

    }

}
