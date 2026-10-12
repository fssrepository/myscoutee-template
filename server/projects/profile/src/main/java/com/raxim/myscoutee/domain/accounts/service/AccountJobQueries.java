package com.raxim.myscoutee.domain.accounts.service;
import org.springframework.stereotype.Component;
import org.springframework.data.mongodb.core.query.Criteria;
import com.raxim.myscoutee.domain.platform.api.JobAudience;
@Component
public class AccountJobQueries {
    private final JobAudience audience;
    public AccountJobQueries(JobAudience audience) { this.audience = audience; }
    public Criteria workspaceCriteria(String field) {
        var scope = audience.workspaceScope();
        return scope.included() ? Criteria.where(field).in(scope.ids()) : Criteria.where(field).nin(scope.ids());
    }
}
