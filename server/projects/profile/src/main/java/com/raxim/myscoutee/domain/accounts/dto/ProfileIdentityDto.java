package com.raxim.myscoutee.domain.accounts.dto;

/** Profile-to-account/workspace identity, without presentation, financial or device data. */
public record ProfileIdentityDto(String userId, String accountUserId, String workspaceGroupId) {}
