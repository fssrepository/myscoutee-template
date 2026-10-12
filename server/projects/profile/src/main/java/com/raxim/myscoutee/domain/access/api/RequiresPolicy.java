package com.raxim.myscoutee.domain.access.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPolicy {
    String key();

    String subject();

    String resourceId();

    String actor() default "";

    boolean skipWhenAuthenticationOptional() default false;
}
