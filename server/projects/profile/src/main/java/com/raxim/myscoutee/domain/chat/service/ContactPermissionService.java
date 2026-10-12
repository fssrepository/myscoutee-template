package com.raxim.myscoutee.domain.chat.service;
import java.util.*;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.domain.chat.api.ContactPermissions;
import com.raxim.myscoutee.domain.chat.document.ContactChatAccess;
import com.raxim.myscoutee.domain.chat.repository.ContactChatAccessRepository;
@Service
public class ContactPermissionService implements ContactPermissions {
    private final ContactChatAccessRepository permissions;
    public ContactPermissionService(ContactChatAccessRepository permissions) { this.permissions = permissions; }
    public String pairId(String first, String second) { return ContactChatAccess.pairId(first, second); }
    public List<ContactChatAccess> findForUser(String user) { return permissions.findForUser(user); }
    public Optional<ContactChatAccess> findById(String id) { return permissions.findById(id); }
    public ContactChatAccess save(Change value) {
        var draft = new ContactChatAccess(value.id(), value.leftUserId(), value.rightUserId(), value.requestedBy(),
                value.status(), value.requestedAtIso(), value.decidedAtIso(), value.version());
        try { return permissions.save(draft); }
        catch (org.springframework.dao.OptimisticLockingFailureException | org.springframework.dao.DuplicateKeyException conflict) {
            var saved = permissions.findById(value.id()).orElse(null);
            long nextVersion = value.version() == null ? 0L : Math.addExact(value.version(), 1L);
            if (saved != null && Objects.equals(saved.version(), nextVersion)
                    && saved.equals(new ContactChatAccess(value.id(), value.leftUserId(), value.rightUserId(), value.requestedBy(),
                        value.status(), value.requestedAtIso(), value.decidedAtIso(), nextVersion))) return saved;
            throw conflict;
        }
    }
}
