package com.raxim.myscoutee.microservices.bootstrap;

import com.raxim.myscoutee.common.config.PlatformParametersService;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Reuse the canonical parameter reader with this assembly's native or remote catalog. */
@Configuration(proxyBeanMethods = false)
@Import(PlatformParametersService.class)
public class SharedSchedulingConfiguration {}
