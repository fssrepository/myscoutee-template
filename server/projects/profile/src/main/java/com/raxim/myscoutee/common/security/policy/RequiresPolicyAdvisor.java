package com.raxim.myscoutee.common.security.policy;

import java.lang.reflect.Method;

import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class RequiresPolicyAdvisor extends StaticMethodMatcherPointcutAdvisor {
    public RequiresPolicyAdvisor(RequiresPolicyMethodInterceptor interceptor) {
        setAdvice(interceptor);
        setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return RequiresPolicySupport.findRequirement(method, targetClass) != null;
    }
}
