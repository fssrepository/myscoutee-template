package com.raxim.myscoutee.domain.accounts.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.accounts.api.AccountNotifications;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;

@Service
public class AccountNotificationService implements AccountNotifications {
    private final UsersRepository users;
    private final MongoTemplate mongo;

    public AccountNotificationService(UsersRepository users, MongoTemplate mongo) {
        this.users = users;
        this.mongo = mongo;
    }

    @Override
    public List<Recipient> recipients(Collection<String> profileIds) {
        if (profileIds.isEmpty()) return List.of();
        return users.findByUserIdIn(profileIds).stream().map(user -> {
            var location = user.getLocationCoordinates();
            boolean located = location != null && location.latitude() != null && location.longitude() != null
                    && Double.isFinite(location.latitude()) && Double.isFinite(location.longitude());
            var devices = user.getDevices() == null ? List.<Device>of() : user.getDevices().stream()
                    .filter(Objects::nonNull)
                    .map(device -> new Device(device.getDeviceId(), device.getFirebaseToken(),
                            Boolean.TRUE.equals(device.getNotificationsEnabled())))
                    .toList();
            return new Recipient(user.getUserId(), user.getAccountUserId(), user.getWorkspaceGroupId(), located, devices);
        }).toList();
    }

    @Override
    public List<Sender> senders(Collection<String> profileIds) {
        if (profileIds.isEmpty()) return List.of();
        return users.findByUserIdIn(profileIds).stream().filter(Objects::nonNull)
                .map(user -> new Sender(user.getUserId(), user.getName(), user.getImages() == null ? null
                        : user.getImages().stream().filter(StringUtils::hasText).map(String::trim).findFirst().orElse(null)))
                .toList();
    }

    @Override
    public List<RoleRecipient> roleRecipients(Collection<String> emails) {
        if (emails.isEmpty()) return List.of();
        return users.findByEmailIn(emails).stream()
                .filter(user -> !StringUtils.hasText(user.getWorkspaceGroupId()) && !"D".equals(user.getStatus()))
                .map(user -> new RoleRecipient(user.getUserId(), user.getEmail(), user.isOperator())).toList();
    }

    @Override
    public String notificationAccountId(String profileId) {
        return users.findByUserId(profileId).map(user -> StringUtils.hasText(user.getAccountUserId())
                ? user.getAccountUserId() : profileId).orElse(profileId);
    }

    @Override
    public boolean muted(String accountId) {
        return users.findByUserId(accountId).map(Users::getNotificationPreferences)
                .map(Users.UserNotificationPreferencesRecord::muted).map(Boolean.TRUE::equals).orElse(false);
    }

    @Override
    public void setMuted(String accountId, boolean muted) {
        mongo.updateFirst(Query.query(Criteria.where("userId").is(accountId)),
                new Update().set("notificationPreferences.muted", muted), Users.class);
    }

    @Override
    public void incrementUnreadCounter(String accountId, int delta) {
        if (delta == 0) return;
        mongo.updateFirst(Query.query(Criteria.where("userId").is(accountId)),
                new Update().inc("activities.notifications", delta), Users.class);
    }

    @Override
    public TokenCleanup removeInvalidPushTokens(Collection<String> accountIds, Set<String> invalidTokens) {
        if (accountIds.isEmpty() || invalidTokens.isEmpty()) return new TokenCleanup(0, 0);
        List<Users> changed = new ArrayList<>();
        int removed = 0;
        // Re-read once after provider delivery; the command never accepts or saves a foreign mutable user document.
        for (Users user : users.findByUserIdIn(accountIds)) {
            if (user.getDevices() == null || user.getDevices().isEmpty()) continue;
            var retained = user.getDevices().stream().filter(device -> device != null
                    && !invalidTokens.contains(device.getFirebaseToken() == null ? "" : device.getFirebaseToken().trim())).toList();
            if (retained.size() == user.getDevices().size()) continue;
            removed += user.getDevices().size() - retained.size();
            user.setDevices(retained);
            changed.add(user);
        }
        if (!changed.isEmpty()) users.saveAll(changed);
        return new TokenCleanup(removed, changed.size());
    }
}
