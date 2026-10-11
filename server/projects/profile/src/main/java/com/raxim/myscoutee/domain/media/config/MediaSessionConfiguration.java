package com.raxim.myscoutee.domain.media.config;

/** The same HTTP cookie adapter is used by monolith and media owner. */
@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Import(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class)
public class MediaSessionConfiguration {}
