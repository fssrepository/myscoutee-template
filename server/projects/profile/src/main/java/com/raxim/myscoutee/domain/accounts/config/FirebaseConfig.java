package com.raxim.myscoutee.domain.accounts.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration;

import jakarta.annotation.PostConstruct;

@Configuration
public class FirebaseConfig {

    private final RuntimeConfiguration configProperties;

    public FirebaseConfig(RuntimeConfiguration configProperties) {
        this.configProperties = configProperties;
    }

    @PostConstruct
    public void init() throws IOException {
        if (!configProperties.isStartupFirebaseEnabled()) {
            return;
        }

        String serviceAccountPath = this.configProperties.getFirebaseServiceAccountPath();

        if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
            throw new IOException("Firebase service account path is not configured. "
                    + "Set 'config.firebase-service-account-path' or FIREBASE_SERVICE_ACCOUNT_PATH.");
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        InputStream serviceAccount = new FileInputStream(serviceAccountPath);

        try (serviceAccount) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
        }
    }
}
