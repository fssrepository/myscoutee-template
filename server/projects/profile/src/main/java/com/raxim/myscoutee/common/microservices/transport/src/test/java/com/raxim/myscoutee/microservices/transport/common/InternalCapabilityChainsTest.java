package com.raxim.myscoutee.microservices.transport.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the security boundary for every transport in the versioned binding inventory. */
class InternalCapabilityChainsTest {
    private static final String TOKEN = "test-only-capability-token-0000000000000000000000000";
    private static AnnotationConfigWebApplicationContext context;

    @BeforeAll static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Security.class);
        context.refresh();
    }
    @AfterAll static void stop() { if (context != null) context.close(); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    static Stream<String> bindings() throws Exception {
        var data = new JsonMapper().readTree(Files.readString(Path.of(System.getProperty("transportBindingsPath"))));
        return data.get("bindings").valueStream().map(binding ->
                "com.raxim.myscoutee.microservices.transport." + binding.get("package").asText()
                        + "." + binding.get("configuration").asText());
    }

    @ParameterizedTest(name = "{0}") @MethodSource("bindings")
    void csrfIsEnabledAndOnlyAVerifiedCapabilityCanBypassIt(String binding) throws Exception {
        var configuration = Class.forName(binding + "ServerConfiguration");
        var factory = Arrays.stream(configuration.getDeclaredMethods())
                .filter(method -> method.getReturnType() == SecurityFilterChain.class).findFirst().orElseThrow();
        factory.setAccessible(true);
        var http = context.getBean(HttpSecurity.class);
        var chain = (SecurityFilterChain) factory.invoke(configuration.getConstructor().newInstance(), http, TOKEN);
        var filters = chain.getFilters();
        var credential = filters.stream().filter(InternalReadCredentialFilter.class::isInstance)
                .map(InternalReadCredentialFilter.class::cast).findFirst().orElseThrow();
        var csrf = filters.stream().filter(CsrfFilter.class::isInstance).map(CsrfFilter.class::cast).findFirst().orElseThrow();
        assertTrue(filters.indexOf(credential) < filters.indexOf(csrf));

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("cookie-user", null, List.of()));
        var rejected = new MockHttpServletResponse();
        csrf.doFilter(new MockHttpServletRequest("POST", "/_internal/test"), rejected,
                (request, response) -> fail("An ordinary user acquired the service CSRF exemption"));
        assertEquals(403, rejected.getStatus());

        var request = new MockHttpServletRequest("POST", "/_internal/test");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        request.addHeader(InternalReadCredentialFilter.MODE_HEADER, "DEMO");
        var response = new MockHttpServletResponse();
        var reached = new AtomicBoolean();
        credential.doFilter(request, response, (incoming, outgoing) ->
                csrf.doFilter(incoming, outgoing, (verified, result) -> reached.set(true)));
        assertEquals(200, response.getStatus());
        assertTrue(reached.get());
    }

    @Configuration(proxyBeanMethods = false) @EnableWebSecurity @EnableWebMvc
    static class Security {}
}
