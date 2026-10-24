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
@org.springframework.context.annotation.Import(com.raxim.myscoutee.microservices.transport.security.ActorDelegationSignerConfiguration.class)
public class ChatQueriesClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteChatQueries remoteChatQueries(
            @Value("${myscoutee.remote.chat-queries.url}") URI origin,
            @Value("${myscoutee.remote.chat-queries.command-token}") String token,
            @Value("${myscoutee.remote.chat-queries.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.chat-queries.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.chat-queries.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics, com.raxim.myscoutee.microservices.transport.security.ActorDelegation.Signer signer) {
        return new RemoteChatQueries(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics, signer);
    }
}
