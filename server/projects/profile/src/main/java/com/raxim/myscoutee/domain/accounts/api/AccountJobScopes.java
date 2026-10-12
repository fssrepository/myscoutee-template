package com.raxim.myscoutee.domain.accounts.api;
import java.util.*;
import com.raxim.myscoutee.domain.platform.api.JobAudience.WorkspaceScope;
public interface AccountJobScopes { Set<String> matchingOwners(Collection<String> ids, WorkspaceScope scope); }
