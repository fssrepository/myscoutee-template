package com.raxim.myscoutee.microservices.bootstrap;

/** Reuse the canonical HTTP adapter where the account filter issues media cookies. */
@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Import(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class)
public class SharedMediaSessionConfiguration {}
