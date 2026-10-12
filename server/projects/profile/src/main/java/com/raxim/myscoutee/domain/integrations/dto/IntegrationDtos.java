package com.raxim.myscoutee.domain.integrations.dto;

import java.util.List;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;

public final class IntegrationDtos {
    private IntegrationDtos() {
    }

    public record IntegrationTokenDto(
            String id,
            String name,
            String prefix,
            String createdAt,
            String expiresAt,
            String claimedAt,
            String claimedAddress,
            String lastUsedAt,
            IntegrationAccess.Mode accessMode) {
        public IntegrationTokenDto(String id, String name, String prefix, String createdAt, String expiresAt,
                String claimedAt, String claimedAddress, String lastUsedAt) {
            this(id, name, prefix, createdAt, expiresAt, claimedAt, claimedAddress, lastUsedAt, IntegrationAccess.Mode.WRITE);
        }
        public IntegrationTokenDto withAccess(IntegrationAccess.Mode mode) {
            return new IntegrationTokenDto(id, name, prefix, createdAt, expiresAt, claimedAt, claimedAddress, lastUsedAt, mode);
        }
    }

    public record IntegrationSettingsDto(
            String baseUrl, Integer maxActiveTokens, Integer maxBatchSize,
            List<IntegrationTokenDto> tokens, McpDtos.Settings mcp, long accessRevision) {
        public IntegrationSettingsDto(String baseUrl, Integer maxActiveTokens, Integer maxBatchSize,
                List<IntegrationTokenDto> tokens) {
            this(baseUrl, maxActiveTokens, maxBatchSize, tokens, null, 0);
        }
    }

    public record IntegrationClientAccessDto(String id, IntegrationAccess.Mode accessMode) { }
    public record IntegrationSettingsUpdateDto(long accessRevision, IntegrationAccess.Mode mcpAccess,
            List<IntegrationClientAccessDto> clients) { }

    public record IntegrationTokenCreateRequestDto(
            String name,
            Integer expiresInDays) {
    }

    public record IntegrationTokenCreatedDto(
            IntegrationTokenDto token,
            String value) {
    }

    public record IntegrationConnectDto(
            Boolean connected,
            Integer maxBatchSize,
            String profileId,
            String profileName,
            String groupId,
            String groupName,
            List<String> operations,
            List<IntegrationGroupScopeDto> inviteGroups,
            IntegrationAccess.Mode accessMode) {
        public IntegrationConnectDto(Boolean connected, Integer maxBatchSize, String profileId, String profileName,
                String groupId, String groupName, List<String> operations, List<IntegrationGroupScopeDto> inviteGroups) {
            this(connected, maxBatchSize, profileId, profileName, groupId, groupName, operations, inviteGroups, IntegrationAccess.Mode.WRITE);
        }
    }

    public record IntegrationGroupScopeDto(String id, String name) { }

}
