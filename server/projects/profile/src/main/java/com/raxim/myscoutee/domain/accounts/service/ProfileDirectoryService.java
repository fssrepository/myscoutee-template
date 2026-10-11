package com.raxim.myscoutee.domain.accounts.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.domain.accounts.api.ProfileDirectory;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummaries;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;

@Service
public class ProfileDirectoryService implements ProfileDirectory, ProfileSummaries {
    private final UsersRepository users;
    public ProfileDirectoryService(UsersRepository users) { this.users = users; }
    @Override public Optional<Users> findByUserId(String userId) { return users.findByUserId(userId); }
    @Override public List<Users> findByUserIdIn(Collection<String> userIds) { return users.findByUserIdIn(userIds); }
}
