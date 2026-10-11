package com.raxim.myscoutee.domain.access.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AccessPolicyConditionRegistry {
    private final Map<String, AccessPolicyCondition> conditionsByName;

    public AccessPolicyConditionRegistry(List<AccessPolicyCondition> conditions) {
        LinkedHashMap<String, AccessPolicyCondition> nextConditionsByName = new LinkedHashMap<>();
        for (AccessPolicyCondition condition : conditions == null ? List.<AccessPolicyCondition>of() : conditions) {
            String normalizedName = normalizeName(condition == null ? null : condition.name());
            if (!StringUtils.hasText(normalizedName)) {
                throw new IllegalArgumentException("Access policy condition name must not be blank.");
            }
            if (nextConditionsByName.putIfAbsent(normalizedName, condition) != null) {
                throw new IllegalArgumentException("Duplicate access policy condition: " + normalizedName);
            }
        }
        this.conditionsByName = Map.copyOf(nextConditionsByName);
    }

    public AccessPolicyCondition named(String name) {
        return conditionsByName.get(normalizeName(name));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
