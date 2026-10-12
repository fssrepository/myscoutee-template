package com.raxim.myscoutee.common.security.policy;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PolicyExpressionEvaluator {
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public String evaluate(String expressionText, Method method, Object[] arguments, Object target) {
        return evaluate(expressionText, method, arguments, target, Map.of());
    }

    public String evaluate(
            String expressionText,
            Method method,
            Object[] arguments,
            Object target,
            Map<String, Object> variables) {
        String normalizedExpression = safeText(expressionText);
        if (!StringUtils.hasText(normalizedExpression)) {
            return null;
        }

        StandardEvaluationContext context = new StandardEvaluationContext(target);
        context.setVariable("target", target);
        Object[] safeArguments = arguments == null ? new Object[0] : arguments;
        for (int index = 0; index < safeArguments.length; index += 1) {
            context.setVariable("p" + index, safeArguments[index]);
            context.setVariable("a" + index, safeArguments[index]);
        }

        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            for (int index = 0; index < Math.min(parameterNames.length, safeArguments.length); index += 1) {
                context.setVariable(parameterNames[index], safeArguments[index]);
            }
        }
        if (variables != null) {
            variables.forEach(context::setVariable);
        }

        Object value = expressionCache
                .computeIfAbsent(normalizedExpression, expressionParser::parseExpression)
                .getValue(context);
        return value == null ? null : safeText(String.valueOf(value));
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
