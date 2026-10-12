package com.raxim.myscoutee.common.security.policy;

import com.raxim.myscoutee.domain.access.api.AccessPermissions;
import com.raxim.myscoutee.domain.access.api.RequiresPolicies;
import com.raxim.myscoutee.domain.access.api.RequiresPolicy;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RequiresPolicyMethodInterceptor implements MethodInterceptor {
    private final AccessPermissions accessControlService;
    private final PolicyExpressionEvaluator expressionEvaluator;

    public RequiresPolicyMethodInterceptor(
            @Lazy AccessPermissions accessControlService,
            PolicyExpressionEvaluator expressionEvaluator) {
        this.accessControlService = accessControlService;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        RequiresPolicySupport.PolicyRequirement requirement = RequiresPolicySupport.findRequirement(
                invocation.getMethod(),
                invocation.getThis());
        if (requirement == null) {
            return invocation.proceed();
        }
        if (requirement.skipWhenAuthenticationOptional() && !accessControlService.requiresAuthenticatedActor()) {
            return invocation.proceed();
        }
        if (requirement.policies().isEmpty()) {
            throw denied();
        }

        Method method = mostSpecificMethod(invocation);
        Object[] arguments = invocation.getArguments();
        Object target = invocation.getThis();
        if (allows(requirement, method, arguments, target)) {
            return invocation.proceed();
        }
        throw denied();
    }

    private boolean allows(
            RequiresPolicySupport.PolicyRequirement requirement,
            Method method,
            Object[] arguments,
            Object target) {
        if (requirement.mode() == RequiresPolicies.Mode.ANY) {
            ResponseStatusException expressionFailure = null;
            for (RequiresPolicy requiresPolicy : requirement.policies()) {
                try {
                    if (allows(requiresPolicy, method, arguments, target)) {
                        return true;
                    }
                } catch (ResponseStatusException exception) {
                    expressionFailure = exception;
                }
            }
            if (expressionFailure != null) {
                throw expressionFailure;
            }
            return false;
        }

        for (RequiresPolicy requiresPolicy : requirement.policies()) {
            if (!allows(requiresPolicy, method, arguments, target)) {
                return false;
            }
        }
        return true;
    }

    private boolean allows(RequiresPolicy requiresPolicy, Method method, Object[] arguments, Object target) {
        String actorUserId = resolveActorUserId(requiresPolicy, method, arguments, target);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("actorUserId", actorUserId);
        String subjectUserId = evaluate(requiresPolicy.subject(), method, arguments, target, variables);
        String resourceId = evaluate(requiresPolicy.resourceId(), method, arguments, target, variables);
        return accessControlService.can(requiresPolicy.key(), actorUserId, subjectUserId, resourceId);
    }

    private String resolveActorUserId(RequiresPolicy requiresPolicy, Method method, Object[] arguments, Object target) {
        String actorExpression = requiresPolicy.actor();
        if (StringUtils.hasText(actorExpression)) {
            return evaluate(actorExpression, method, arguments, target, Map.of());
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return accessControlService.resolveActorUserId(authentication);
    }

    private String evaluate(
            String expression,
            Method method,
            Object[] arguments,
            Object target,
            Map<String, Object> variables) {
        try {
            return expressionEvaluator.evaluate(expression, method, arguments, target, variables);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Policy expression could not be evaluated.", exception);
        }
    }

    private ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Policy authorization denied.");
    }

    private Method mostSpecificMethod(MethodInvocation invocation) {
        Object target = invocation.getThis();
        if (target == null) {
            return invocation.getMethod();
        }
        return AopUtils.getMostSpecificMethod(invocation.getMethod(), target.getClass());
    }
}
