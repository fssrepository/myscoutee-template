package com.raxim.myscoutee.domain.platform.dto;

import java.util.List;

public record OperatorNotificationRecipientsDto(List<String> users, String bootstrapEmail) {
    public OperatorNotificationRecipientsDto { users = List.copyOf(users); }
}
