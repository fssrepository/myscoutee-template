package com.raxim.myscoutee.domain.platform.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("operator.update")
public class OperatorUpdateProperties {
    private String softwareVersion = "unknown";
    public String getSoftwareVersion() { return softwareVersion; }
    public void setSoftwareVersion(String value) { softwareVersion = value == null ? "unknown" : value.trim(); }

    private boolean enabled;
    private boolean monitorEnabled;
    public boolean isMonitorEnabled() { return monitorEnabled; }
    public void setMonitorEnabled(boolean value) { monitorEnabled = value; }
    private String githubRepository = "fssrepository/myscoutee";
    private boolean includePrereleases;
    private Path tlsCaCertificate;
    public Path getTlsCaCertificate() { return tlsCaCertificate; }
    public void setTlsCaCertificate(Path value) { tlsCaCertificate = value; }
    private String releaseApiOrigin = "https://api.github.com";
    private String releaseDownloadOrigin = "https://github.com";

    public String getReleaseApiOrigin() { return releaseApiOrigin; }
    public void setReleaseApiOrigin(String value) { releaseApiOrigin = value; }
    public String getReleaseDownloadOrigin() { return releaseDownloadOrigin; }
    public void setReleaseDownloadOrigin(String value) { releaseDownloadOrigin = value; }


    public String getGithubRepository() { return githubRepository; }
    public void setGithubRepository(String value) { githubRepository = value; }
    public boolean isIncludePrereleases() { return includePrereleases; }
    public void setIncludePrereleases(boolean value) { includePrereleases = value; }
    private Path stateDir = Path.of("/var/lib/myscoutee/update");
    private Path releasePublicKeyPath =
            Path.of("/etc/myscoutee/update/release-public-key.pem");
    private long maxArtifactBytes = 4_294_967_296L;
    private long stagingSafetyReserveBytes = 536_870_912L;
    private Duration downloadConnectTimeout = Duration.ofSeconds(10);
    private Duration downloadTimeout = Duration.ofMinutes(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getStateDir() {
        return stateDir;
    }

    public void setStateDir(Path stateDir) {
        if (stateDir != null) {
            this.stateDir = stateDir;
        }
    }

    public Path getReleasePublicKeyPath() {
        return releasePublicKeyPath;
    }

    public void setReleasePublicKeyPath(Path releasePublicKeyPath) {
        if (releasePublicKeyPath != null) {
            this.releasePublicKeyPath = releasePublicKeyPath;
        }
    }

    public long getMaxArtifactBytes() {
        return maxArtifactBytes;
    }

    public void setMaxArtifactBytes(long maxArtifactBytes) {
        if (maxArtifactBytes > 0L) {
            this.maxArtifactBytes = maxArtifactBytes;
        }
    }

    public long getStagingSafetyReserveBytes() {
        return stagingSafetyReserveBytes;
    }

    public void setStagingSafetyReserveBytes(long stagingSafetyReserveBytes) {
        if (stagingSafetyReserveBytes >= 0L) {
            this.stagingSafetyReserveBytes = stagingSafetyReserveBytes;
        }
    }

    public Duration getDownloadConnectTimeout() {
        return downloadConnectTimeout;
    }

    public void setDownloadConnectTimeout(Duration downloadConnectTimeout) {
        if (positive(downloadConnectTimeout)) {
            this.downloadConnectTimeout = downloadConnectTimeout;
        }
    }

    public Duration getDownloadTimeout() {
        return downloadTimeout;
    }

    public void setDownloadTimeout(Duration downloadTimeout) {
        if (positive(downloadTimeout)) {
            this.downloadTimeout = downloadTimeout;
        }
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }
}
