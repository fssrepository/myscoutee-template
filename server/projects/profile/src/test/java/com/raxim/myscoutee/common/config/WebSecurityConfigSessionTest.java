package com.raxim.myscoutee.common.config;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseService;
import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseTokenVerifier;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.common.config.mongo.DemoSessionRequestSupport;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;

@org.junit.jupiter.api.Tag("standard")
class WebSecurityConfigSessionTest {
    @Test void loginIsPublicButOrdinaryRequestsRequireAnActiveSession() throws Exception {
        try (TestContext context = context(false, "")) {
            context.mockMvc().perform(post("/auth/session/login").header("Authorization", "Bearer stale"))
                    .andExpect(status().isOk());
            for (String path : List.of("/auth/me", "/admin/probe", "/operator/probe")) {
                context.mockMvc().perform(get(path).param("userId", "victim"))
                        .andExpect(status().isUnauthorized());
                context.mockMvc().perform(post(path).cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "untrusted")))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    @Test void oneCredentialFormatServesEveryRoleAndServerAuthoritiesControlAccess() throws Exception {
        for (String role : List.of("U", "A", "O")) {
            try (TestContext context = context(false, "")) {
                var app = context.applicationContext();
                var accounts = app.getBean(FirebaseService.class);
                var sessions = app.getBean(com.raxim.myscoutee.domain.accounts.service.UserLoginSessionService.class);
                var user = new com.raxim.myscoutee.domain.accounts.document.Users(); user.setUserId("account");
                when(accounts.loadUserById("account")).thenReturn(new FirebasePrincipal("account", "account@example.invalid", List.of("U", role), user));
                when(sessions.authenticateAccessToken("app-token")).thenReturn(new com.raxim.myscoutee.domain.accounts.security.session.AccountSessionCredential("account", "session", true));
                context.mockMvc().perform(get("/auth/me").header("Authorization", "Bearer app-token")).andExpect(status().isOk());
                context.mockMvc().perform(get("/admin/probe").header("Authorization", "Bearer app-token"))
                        .andExpect(role.equals("A") ? status().isOk() : status().isForbidden());
                context.mockMvc().perform(get("/operator/probe").header("Authorization", "Bearer app-token"))
                        .andExpect(role.equals("O") ? status().isOk() : status().isForbidden());
                context.mockMvc().perform(get("/auth/me").header("Authorization", "Bearer revoked"))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    @Test void explicitDevelopmentMemberFallbackStillWorks() throws Exception {
        try (TestContext context = context(false, "member@example.invalid")) {
            var account = new FirebasePrincipal("member", "member@example.invalid", "U");
            when(context.applicationContext().getBean(FirebaseService.class).loadUserByUsername(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenReturn(account);
            context.mockMvc().perform(get("/auth/me")).andExpect(status().isOk());
        }
    }

    private TestContext context(boolean bootstrapConfigured, String memberUser) {
        GenericWebApplicationContext applicationContext =
                new GenericWebApplicationContext();
        applicationContext.setServletContext(new MockServletContext());

        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(false);
        properties.setMemberUser(memberUser);
        applicationContext.registerBean(
                ConfigProperties.class,
                () -> properties);
        applicationContext.registerBean(
                FirebaseService.class,
                () -> mock(FirebaseService.class));
        applicationContext.registerBean(
                com.raxim.myscoutee.domain.accounts.service.UserLoginSessionService.class,
                () -> mock(com.raxim.myscoutee.domain.accounts.service.UserLoginSessionService.class));
        applicationContext.registerBean(
                com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class,
                () -> mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
        applicationContext.registerBean(com.raxim.myscoutee.domain.accounts.service.ProfileAuthenticationService.class,
                com.raxim.myscoutee.domain.accounts.service.ProfileAuthenticationService::new);
        applicationContext.registerBean(com.raxim.myscoutee.domain.integrations.config.IntegrationTokenAuthenticationFilter.class,
                () -> new com.raxim.myscoutee.domain.integrations.config.IntegrationTokenAuthenticationFilter(
                        mock(com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService.class),
                        mock(com.raxim.myscoutee.domain.accounts.api.ProfileDirectory.class), java.util.Optional.empty(),
                        mock(com.raxim.myscoutee.domain.integrations.service.IntegrationRoleAccessService.class), null, null));
        applicationContext.registerBean(com.raxim.myscoutee.domain.integrations.config.AdminClientAuthenticationFilter.class,
                () -> new com.raxim.myscoutee.domain.integrations.config.AdminClientAuthenticationFilter(
                        mock(com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService.class),
                        mock(com.raxim.myscoutee.domain.integrations.service.AdminClientAccessService.class)));
        new AnnotatedBeanDefinitionReader(applicationContext).register(
                TestMvcConfiguration.class,
                WebSecurityConfig.class);
        applicationContext.refresh();

        MockMvc mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        return new TestContext(applicationContext, mockMvc);
    }

    private record TestContext(
            GenericWebApplicationContext applicationContext,
            MockMvc mockMvc) implements AutoCloseable {
        @Override
        public void close() {
            applicationContext.close();
        }
    }

    @Configuration
    @EnableWebMvc
    static class TestMvcConfiguration {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @PostMapping("/auth/session/login")
        String bootstrap() {
            return "bootstrap";
        }

        @GetMapping("/auth/me")
        String realOrDemo() {
            return "real-or-demo";
        }

        @GetMapping("/admin/probe")
        String admin() {
            return "admin";
        }

        @GetMapping("/operator/probe")
        String operator() {
            return "operator";
        }
    }
}
