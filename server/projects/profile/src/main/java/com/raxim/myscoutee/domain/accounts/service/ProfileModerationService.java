package com.raxim.myscoutee.domain.accounts.service;
import com.raxim.myscoutee.domain.accounts.api.*;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import org.springframework.util.StringUtils;
@org.springframework.stereotype.Service
public class ProfileModerationService implements ProfileModeration {
    private final UsersRepository users;
    private final ProfileStatusWork statusWork;
    public ProfileModerationService(UsersRepository users, ProfileStatusWork statusWork) {
        this.users = users; this.statusWork = statusWork;
    }
    public boolean block(String userId) { return block(userId, null); }
    public boolean block(String userId, SupportNotice notice) {
        Users user = users.findByUserId(userId).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Profile not found."));
        if ("blocked".equals(normalize(user.getProfileStatus()))) return false;
        user.setPreviousProfileStatus(StringUtils.hasText(user.getProfileStatus()) ? user.getProfileStatus() : "public");
        user.setProfileStatus("blocked");
        if (notice != null) {
            var pending = new java.util.ArrayList<>(user.getProfileSupportWork());
            if (pending.size() >= 100) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Previous moderation messages are still pending.");
            pending.add(new Users.ProfileSupportWork("profile-block:" + java.util.UUID.randomUUID(),
                    notice.adminUserId(), notice.adminName(), notice.adminInitials(), notice.text()));
            user.setProfileSupportWork(pending);
        }
        statusWork.save(user); return true;
    }
    public void unblock(String userId) {
        Users user = users.findByUserId(userId).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Profile not found."));
        if (!"blocked".equals(normalize(user.getProfileStatus()))) return;
        String previous = normalize(user.getPreviousProfileStatus());
        user.setProfileStatus(StringUtils.hasText(previous) && !"blocked".equals(previous) ? previous : "public");
        user.setPreviousProfileStatus(null); statusWork.save(user);
    }
    private String normalize(String value) { return value == null ? "" : value.trim(); }
}
