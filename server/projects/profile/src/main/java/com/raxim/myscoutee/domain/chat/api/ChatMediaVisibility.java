package com.raxim.myscoutee.domain.chat.api;

import java.util.List;
import com.raxim.myscoutee.domain.chat.dto.ChatMessageMediaVisibilityDto;

public interface ChatMediaVisibility {
    List<ChatMessageMediaVisibilityDto> messages(List<String> urls);
}
