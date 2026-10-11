package com.raxim.myscoutee.domain.accounts.dto;

import java.util.List;

/** Stored inputs used by the existing host-confidence calculation, without trait labels or private profile details. */
public record HostConfidenceFactsDto(Double averageRating, Integer peopleMet, Integer totalEvents,
        Integer repeatCount, List<Integer> traitEvidenceCounts) {
    public HostConfidenceFactsDto {
        if (traitEvidenceCounts != null) traitEvidenceCounts = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(traitEvidenceCounts));
    }
}
