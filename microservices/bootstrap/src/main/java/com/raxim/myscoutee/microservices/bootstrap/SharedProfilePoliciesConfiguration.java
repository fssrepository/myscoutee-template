package com.raxim.myscoutee.microservices.bootstrap;

import com.raxim.myscoutee.domain.administration.api.AdministratorClassification;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Complete canonical stateless policy, imported only outside its owner's assembly. */
@Configuration(proxyBeanMethods = false)
public class SharedProfilePoliciesConfiguration {
    @Bean
    AdministratorClassification administratorClassification() {
        return new AdministratorClassification() {};
    }
}
