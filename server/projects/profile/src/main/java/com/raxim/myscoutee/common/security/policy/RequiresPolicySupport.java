package com.raxim.myscoutee.common.security.policy;

import com.raxim.myscoutee.domain.access.api.RequiresPolicies;
import com.raxim.myscoutee.domain.access.api.RequiresPolicy;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

final class RequiresPolicySupport {
    private RequiresPolicySupport() {
    }

    static PolicyRequirement findRequirement(Method method, Object target) {
        Class<?> targetClass = target == null ? null : target.getClass();
        return findRequirement(method, targetClass);
    }

    static PolicyRequirement findRequirement(Method method, Class<?> targetClass) {
        Method specificMethod = targetClass == null
                ? method
                : AopUtils.getMostSpecificMethod(method, targetClass);
        PolicyRequirement requirement = findRequirement(specificMethod);
        if (requirement != null) {
            return requirement;
        }
        return specificMethod.equals(method) ? null : findRequirement(method);
    }

    private static PolicyRequirement findRequirement(Method method) {
        RequiresPolicies policies = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPolicies.class);
        if (policies != null) {
            return new PolicyRequirement(
                    policies.mode(),
                    policies.skipWhenAuthenticationOptional(),
                    Arrays.asList(policies.value()));
        }
        RequiresPolicy annotation = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPolicy.class);
        if (annotation != null) {
            return new PolicyRequirement(
                    RequiresPolicies.Mode.ALL,
                    annotation.skipWhenAuthenticationOptional(),
                    List.of(annotation));
        }
        return null;
    }

    record PolicyRequirement(
            RequiresPolicies.Mode mode,
            boolean skipWhenAuthenticationOptional,
            List<RequiresPolicy> policies) {
    }
}
