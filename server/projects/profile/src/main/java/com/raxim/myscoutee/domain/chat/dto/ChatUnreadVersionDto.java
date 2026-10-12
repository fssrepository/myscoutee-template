package com.raxim.myscoutee.domain.chat.dto;

/** Keeps a nullable legacy version distinct from an absent HTTP response. */
public record ChatUnreadVersionDto(String version) {}
