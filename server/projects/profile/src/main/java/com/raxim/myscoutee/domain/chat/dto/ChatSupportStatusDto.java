package com.raxim.myscoutee.domain.chat.dto;

import com.raxim.myscoutee.domain.chat.api.ChatDirectory;

public record ChatSupportStatusDto(String status) implements ChatDirectory.SupportCase {
    @Override public String getStatus() { return status; }
}
