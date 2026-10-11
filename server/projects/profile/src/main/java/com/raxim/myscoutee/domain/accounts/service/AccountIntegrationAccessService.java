package com.raxim.myscoutee.domain.accounts.service;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.domain.accounts.api.AccountIntegrationAccess;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;
@Service
public class AccountIntegrationAccessService implements AccountIntegrationAccess {
 private final UsersRepository users;
 public AccountIntegrationAccessService(UsersRepository users) { this.users=users; }
 public long updateIntegrationAccess(String owner, long revision, IntegrationAccess value) { return users.updateIntegrationAccess(owner,revision,value); }
}
