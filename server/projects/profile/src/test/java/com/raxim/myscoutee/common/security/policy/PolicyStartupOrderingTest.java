package com.raxim.myscoutee.common.security.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.raxim.myscoutee.domain.access.api.AccessPermissions;
import com.raxim.myscoutee.domain.access.api.RequiresPolicy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Tag("standard")
class PolicyStartupOrderingTest {
    @Test void registeringAdvisorsDoesNotInitializePolicyDependenciesBeforeMetricPostProcessors() {
        try (var context = new AnnotationConfigApplicationContext(Fixture.class)) {
            var created = context.getBean(AtomicInteger.class);
            assertThat(created.get()).isZero();
            var protectedBean = context.getBean(ProtectedBean.class);
            assertThat(protectedBean.read()).isEqualTo("allowed");
            assertThat(protectedBean.read()).isEqualTo("allowed");
            assertThat(created.get()).isEqualTo(1);
            verify(context.getBean(AccessPermissions.class), times(2)).can("policy", "actor", "actor", null);
        }
    }
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import({RequiresPolicyAdvisor.class, RequiresPolicyMethodInterceptor.class, PolicyExpressionEvaluator.class})
    static class Fixture {
        @Bean AtomicInteger created() { return new AtomicInteger(); }
        @Bean @Lazy AccessPermissions permissions(AtomicInteger created) {
            created.incrementAndGet();
            var permissions = mock(AccessPermissions.class);
            when(permissions.can("policy", "actor", "actor", null)).thenReturn(true);
            return permissions;
        }
        @Bean ProtectedBean protectedBean() { return new ProtectedBean(); }
    }
    static class ProtectedBean {
        @RequiresPolicy(key = "policy", actor = "'actor'", subject = "'actor'", resourceId = "")
        public String read() { return "allowed"; }
    }
}
