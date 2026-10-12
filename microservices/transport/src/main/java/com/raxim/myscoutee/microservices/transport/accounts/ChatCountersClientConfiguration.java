package com.raxim.myscoutee.microservices.transport.accounts;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Imported only where the manifest assigns accounts to a different owner. */
@Configuration(proxyBeanMethods = false)
public class ChatCountersClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteChatCounters remoteChatCounters(
            @Value("${myscoutee.remote.chat-counters.url}") URI origin,
            @Value("${myscoutee.remote.chat-counters.command-token}") String token,
            @Value("${myscoutee.remote.chat-counters.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.chat-counters.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.chat-counters.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteChatCounters(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
