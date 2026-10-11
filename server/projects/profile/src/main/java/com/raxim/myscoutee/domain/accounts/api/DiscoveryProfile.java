package com.raxim.myscoutee.domain.accounts.api;

/** Display and distance/language matching facts for group, campaign and service discovery. */
public interface DiscoveryProfile extends ProfileSummary {
    Double getAffinity();
    ScoringProfile.Coordinates getLocationCoordinates();
    java.util.List<String> getLanguages();
}
