package com.raxim.myscoutee.domain.platform.api;

import com.raxim.myscoutee.domain.platform.dto.OperatorNotificationRecipientsDto;

/** Configured operator notification addresses; contains no bootstrap password or credential. */
public interface OperatorNotificationSettings {
    OperatorNotificationRecipientsDto operatorNotificationRecipients();
}
