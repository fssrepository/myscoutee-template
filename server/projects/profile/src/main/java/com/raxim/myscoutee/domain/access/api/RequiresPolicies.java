package com.raxim.myscoutee.domain.access.api;

import com.raxim.myscoutee.domain.access.api.RequiresPolicy;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPolicies {
    Mode mode() default Mode.ALL;

    RequiresPolicy[] value();

    boolean skipWhenAuthenticationOptional() default false;

    enum Mode {
        ALL,
        ANY
    }
}
