package com.raxim.myscoutee.domain.accounts.service;
import java.util.*;
import org.bson.Document;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import com.raxim.myscoutee.domain.accounts.api.AccountJobScopes;
import com.raxim.myscoutee.domain.platform.api.JobAudience.WorkspaceScope;
@Service
public class AccountJobScopeService implements AccountJobScopes {
    private final MongoTemplate mongo;
    public AccountJobScopeService(MongoTemplate mongo) { this.mongo = mongo; }
    @Override public Set<String> matchingOwners(Collection<String> ids, WorkspaceScope scope) {
        if (ids.isEmpty()) return Set.of();
        var query = Query.query(Criteria.where("userId").in(ids));
        query.fields().include("userId", "workspaceGroupId");
        // Mongo $lookup followed by $in/$nin treats duplicate owner rows as one array:
        // included scopes match any row; the default scope requires no matching row.
        Map<String, Boolean> hasIncludedWorkspace = new LinkedHashMap<>();
        for (Document owner : mongo.find(query, Document.class, "users")) {
            String id = owner.getString("userId");
            String workspace = owner.getString("workspaceGroupId");
            boolean included = workspace != null && scope.ids().contains(workspace);
            hasIncludedWorkspace.merge(id, included, (left, right) -> left || right);
        }
        Set<String> selected = new LinkedHashSet<>();
        hasIncludedWorkspace.forEach((id, included) -> { if (included == scope.included()) selected.add(id); });
        return selected;
    }
}
