package com.raxim.myscoutee.microservices.transport.chat;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Imported only where the manifest assigns chat to a different owner. */
@Configuration(proxyBeanMethods = false)
public class ChatDirectoryClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteChatDirectory remoteChatDirectory(
            @Value("${myscoutee.remote.chat-directory.url}") URI origin,
            @Value("${myscoutee.remote.chat-directory.read-token}") String token,
            @Value("${myscoutee.remote.chat-directory.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.chat-directory.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.chat-directory.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteChatDirectory(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
