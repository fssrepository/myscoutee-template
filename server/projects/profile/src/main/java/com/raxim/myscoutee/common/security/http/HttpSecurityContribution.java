package com.raxim.myscoutee.common.security.http;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/** Optional deployment filter contribution; canonical authentication and route rules stay unchanged. */
@FunctionalInterface
public interface HttpSecurityContribution {
    void configure(HttpSecurity http) throws Exception;
}
