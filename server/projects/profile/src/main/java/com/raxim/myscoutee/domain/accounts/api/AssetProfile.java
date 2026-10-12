package com.raxim.myscoutee.domain.accounts.api;

/** Asset participant/ticket presentation and owner-scoring facts. */
public interface AssetProfile extends ProfileSummary, AssetAffinityProfile {
    Integer getAge();
    String getCity();
}
