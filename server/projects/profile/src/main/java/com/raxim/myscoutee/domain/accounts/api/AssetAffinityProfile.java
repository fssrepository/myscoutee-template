package com.raxim.myscoutee.domain.accounts.api;

import com.raxim.myscoutee.domain.accounts.dto.HostConfidenceFactsDto;

/** Account-owned inputs to asset affinity and host-confidence scoring. */
public interface AssetAffinityProfile {
    String getWorkspaceGroupId();
    Double getAffinity();
    HostConfidenceFactsDto getHostConfidenceFacts();
}
