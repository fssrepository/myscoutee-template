package com.raxim.myscoutee.microservices.transport.security;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.raxim.myscoutee.common.config.mongo.*;
import com.raxim.myscoutee.common.security.http.HttpSecurityContribution;
import com.raxim.myscoutee.domain.accounts.api.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicAuthorizationHttpTest {
    static final String TOKEN = "test-only-public-authority-00000000000000000000000";
    static final ObjectMapper MAPPER = new com.raxim.myscoutee.common.config.JsonConfig().objectMapper();
    static final AtomicInteger controllerCalls = new AtomicInteger();
    static ActorDelegation.Issuer issuer;
    static ActorDelegation.Verifier verifier;
    static ConfigurableApplicationContext server;
    static RemotePublicAuthorization remote;
    static SimpleMeterRegistry metrics;
    static URI origin;
    @BeforeAll static void start() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        issuer = new ActorDelegation.Issuer(Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()), publicKey, Clock.systemUTC());
        verifier = new ActorDelegation.Verifier(publicKey, Clock.systemUTC());
        server = new SpringApplicationBuilder(Fixture.class).properties("server.address=127.0.0.1", "server.port=0",
                "management.server.port=0", "spring.main.banner-mode=off", "logging.level.root=WARN").run();
        origin = URI.create("http://127.0.0.1:" + server.getEnvironment().getProperty("local.server.port"));
        metrics = new SimpleMeterRegistry();
        remote = new RemotePublicAuthorization(origin, TOKEN, true, "community", Duration.ofSeconds(1), Duration.ofSeconds(3), MAPPER, metrics);
    }
    @AfterAll static void stop() { if (remote != null) remote.close(); if (server != null) server.close(); if (metrics != null) metrics.close(); }
    @BeforeEach void prepare() { controllerCalls.set(0); SecurityContextHolder.clearContext(); MongoSessionModeContext.clear(); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); MongoSessionModeContext.clear(); }
    static MockHttpServletRequest request(String method, String path, String actor, String mode) {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1"); request.addHeader("X-App-Session-Kind", mode);
        if (actor != null) request.addHeader("X-Demo-User-Id", actor);
        return request;
    }
    @Test void appliesOwnerRulesWithoutCallingBusinessControllersAndBindsTheNonce() {
        var request = request("POST", "/protected", "member", "demo");
        request.setContent("business body must stay unread".getBytes());
        String nonce = "x".repeat(43);
        var response = remote.authorize(request, nonce);
        assertEquals(200, response.statusCode()); assertEquals(0, controllerCalls.get());
        var proof = MAPPER.readValue(response.body(), PublicAuthorizationProtocol.Result.class);
        assertEquals("DEMO", proof.mode());
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        var actor = verifier.verifyPrincipal(proof.actor(), "assembly:public:community", "POST", "/protected", nonce.getBytes());
        assertEquals("member", actor.getUserId());
        assertEquals("workspace", actor.getProfileIdentity().workspaceGroupId());
        assertThrows(RuntimeException.class, () -> verifier.verifyPrincipal(proof.actor(), "assembly:public:assets", "POST", "/protected", nonce.getBytes()));
        assertThrows(RuntimeException.class, () -> verifier.verifyPrincipal(proof.actor(), "assembly:public:community", "POST", "/protected", "y".repeat(43).getBytes()));
        assertEquals("business body must stay unread", new String(request.getContentAsByteArray()));
        assertEquals(403, remote.authorize(request("GET", "/admin", "member", "demo"), nonce).statusCode());
        assertEquals(0, controllerCalls.get());
    }
    @Test void supportsAnonymousHeadAndOriginalHttpMethodRules() {
        var reply = remote.authorize(request("HEAD", "/public", null, "real"), "x".repeat(43));
        assertEquals(200, reply.statusCode());
        var result = MAPPER.readValue(reply.body(), PublicAuthorizationProtocol.Result.class);
        assertNull(result.actor()); assertNull(result.mediaUserId()); assertEquals("REAL", result.mode());
        assertEquals(200, remote.authorize(request("GET", "/read-only", null, "real"), "x".repeat(43)).statusCode());
        assertEquals(403, remote.authorize(request("POST", "/read-only", null, "real"), "x".repeat(43)).statusCode());
        assertEquals(0, controllerCalls.get());
    }
    @Test void cookiesWithoutOwnerVerifiedHeaderCredentialsCannotReachAReceiver() throws Exception {
        var filter = new PublicAuthorizationClientConfiguration.Authorize(remote, verifier, MAPPER, "community");
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            var request = request(method, "/protected", null, "real");
            request.addHeader("Cookie", "JSESSIONID=untrusted; myscoutee_media_session=untrusted");
            request.addHeader("Origin", "https://foreign.example.invalid");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (in, out) -> fail("Cookie-only mutation reached the receiver"));
            assertEquals(403, response.getStatus());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
        assertEquals(0, controllerCalls.get());
    }
    @Test void receiverRetainsMultipartBodyAndRestoresAuthorityAndMode() throws Exception {
        var request = request("POST", "/protected", "member", "demo");
        byte[] body = "--boundary\r\nContent-Disposition: form-data; name=\"file\"\r\n\r\nimage bytes\r\n--boundary--".getBytes();
        request.setContentType("multipart/form-data; boundary=boundary"); request.setContent(body);
        var response = new MockHttpServletResponse();
        var filter = new PublicAuthorizationClientConfiguration.Authorize(remote, verifier, MAPPER, "community");
        AtomicInteger handled = new AtomicInteger();
        filter.doFilter(request, response, (in, out) -> {
            handled.incrementAndGet(); assertArrayEquals(body, in.getInputStream().readAllBytes());
            assertEquals(MongoSessionMode.DEMO, MongoSessionModeContext.currentMode());
            var principal = assertInstanceOf(AuthenticatedActor.class, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            assertEquals("member", principal.getUserId());
        });
        assertEquals(1, handled.get()); assertEquals(0, controllerCalls.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication()); assertEquals(MongoSessionMode.REAL, MongoSessionModeContext.currentMode());
    }
    @Test void incorrectServiceCredentialCannotObtainAuthorityOrExecuteAController() {
        try (var bad = new RemotePublicAuthorization(origin, "bad-token-0000000000000000000000000000000000000", true,
                "community", Duration.ofSeconds(1), Duration.ofSeconds(3), MAPPER, metrics)) {
            assertEquals(401, bad.authorize(request("POST", "/protected", "member", "demo"), "x".repeat(43)).statusCode());
            assertEquals(0, controllerCalls.get());
        }
    }
    @Test void onlyAnExplicitMediaAuthorityCanProduceAMediaPrincipal() throws Exception {
        for (String authority : Arrays.asList(null, "OTHER", "MEDIA")) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "viewer", null, List.of((org.springframework.security.core.GrantedAuthority) () -> authority)));
            var request = new MockHttpServletRequest("GET", "/protected");
            request.setAttribute(PublicAuthorizationProtocol.ATTRIBUTE,
                    new PublicAuthorizationProtocol.Exchange("community", "x".repeat(43)));
            var response = new MockHttpServletResponse();

            new PublicAuthorizationOwnerConfiguration.CompleteExchange(issuer, MAPPER)
                    .doFilter(request, response, (in, out) -> fail("Exchange must stop before a business controller"));

            assertEquals("MEDIA".equals(authority) ? 200 : 403, response.getStatus());
            if ("MEDIA".equals(authority)) {
                var result = MAPPER.readValue(response.getContentAsByteArray(), PublicAuthorizationProtocol.Result.class);
                assertEquals("viewer", result.mediaUserId());
                assertNull(result.actor());
            } else {
                assertTrue(response.getContentAsString().contains("UNSUPPORTED_PRINCIPAL"));
            }
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration", "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
            "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration", "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"})
    @Import({com.raxim.myscoutee.common.config.JsonConfig.class, PublicAuthorizationOwnerConfiguration.class, Routes.class})
    static class Fixture {
        @Bean ActorDelegation.Issuer issuer() { return issuer; }
        @Bean ActorDelegationBrokerProtocol.Policies policies() {
            return new ActorDelegationBrokerProtocol.Policies(Map.of("community", new ActorDelegationBrokerProtocol.ClientPolicy(
                    TOKEN, Set.of("assembly:public:community"), List.of())));
        }
        @Bean SecurityFilterChain nativeRules(HttpSecurity http, HttpSecurityContribution contribution) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(r -> r.requestMatchers("/public").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/read-only").permitAll()
                    .requestMatchers("/admin").hasAuthority("A").anyRequest().authenticated());
            http.addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain next)
                        throws java.io.IOException, ServletException {
                    MongoSessionModeContext.set("demo".equals(req.getHeader("X-App-Session-Kind")) ? MongoSessionMode.DEMO : MongoSessionMode.REAL);
                    try {
                        String user = req.getHeader("X-Demo-User-Id");
                        if (user != null) {
                            var profile = mock(WorkspaceProfile.class); when(profile.getUserId()).thenReturn(user);
                            when(profile.getAccountUserId()).thenReturn("account"); when(profile.getWorkspaceGroupId()).thenReturn("workspace");
                            var account = mock(AccountIdentity.class); when(account.getUserId()).thenReturn(user); when(account.getUser()).thenReturn(profile);
                            when(account.getEmail()).thenReturn("user@example.invalid");
                            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(account, null,
                                    List.of(new SimpleGrantedAuthority("U"))));
                        }
                        next.doFilter(req, res);
                    } finally { MongoSessionModeContext.clear(); }
                }
            }, UsernamePasswordAuthenticationFilter.class);
            contribution.configure(http); return http.build();
        }
    }
    @RestController static class Routes {
        @RequestMapping({"/protected", "/public", "/read-only", "/admin"}) String action() { controllerCalls.incrementAndGet(); return "business"; }
    }
}
