package com.raxim.myscoutee.microservices.bootstrap;

import com.raxim.myscoutee.common.security.policy.PolicyExpressionEvaluator;
import com.raxim.myscoutee.common.security.policy.RequiresPolicyAdvisor;
import com.raxim.myscoutee.common.security.policy.RequiresPolicyMethodInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** The same canonical policy annotation advice, with the assembly's native or remote decision owner. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@Import({RequiresPolicyAdvisor.class, RequiresPolicyMethodInterceptor.class, PolicyExpressionEvaluator.class})
public class PolicyAuthorizationConfiguration {}
