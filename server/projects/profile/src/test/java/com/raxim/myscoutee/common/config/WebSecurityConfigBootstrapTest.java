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

import com.raxim.myscoutee.domain.platform.security.bootstrap.OperatorBootstrapAuthenticationService;
import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseAuthenticationProvider;
import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseService;
import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseTokenVerifier;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.common.config.mongo.DemoSessionRequestSupport;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;

@org.junit.jupiter.api.Tag("standard")
class WebSecurityConfigBootstrapTest {
    @Test
    void bootstrapSecuresRealRequestsWhenFirebaseIsDisabled()
            throws Exception {
        try (TestContext context = context(true)) {
            MockMvc mockMvc = context.mockMvc();

            mockMvc.perform(post("/auth/operator-bootstrap"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("bootstrap"));
            mockMvc.perform(post("/auth/operator-bootstrap")
                            .header(
                                    "Authorization",
                                    "OperatorBootstrap expired-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("bootstrap"));
            mockMvc.perform(get("/auth/me"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/probe"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/auth/me")
                            .header(
                                    DemoSessionRequestSupport
                                            .SESSION_KIND_HEADER,
                                    "demo"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("real-or-demo"));
            mockMvc.perform(get("/operator/probe")
                            .header(
                                    "Authorization",
                                    "OperatorBootstrap signed-token")
                            .header(
                                    "X-Forwarded-Proto",
                                    "https"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("operator"));
        }
    }

    @Test
    void localFirebaseDisabledModeRemainsOpenWithoutBootstrap()
            throws Exception {
        try (TestContext context = context(false)) {
            context.mockMvc().perform(get("/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("real-or-demo"));
        }
    }

    private TestContext context(boolean bootstrapConfigured) {
        return context(bootstrapConfigured, "member1@myscoutee.local");
    }

    @Test
    void productionWithoutFirebaseRejectsCallerSelectedIdentity() throws Exception {
        for (boolean bootstrapConfigured : List.of(false, true)) {
            try (TestContext context = context(bootstrapConfigured, "")) {
                FirebaseService firebase = context.applicationContext().getBean(FirebaseService.class);
                when(firebase.loadUserById("victim")).thenReturn(new FirebasePrincipal(
                        "victim", "victim@example.invalid", List.of("U", "A"), null));
                context.mockMvc().perform(get("/auth/me").param("userId", "victim"))
                        .andExpect(status().isForbidden());
                context.mockMvc().perform(get("/admin/probe").param("userId", "victim"))
                        .andExpect(status().isForbidden());
                org.mockito.Mockito.verify(firebase, org.mockito.Mockito.never()).loadUserById("victim");
            }
        }
    }

    private TestContext context(boolean bootstrapConfigured, String memberUser) {
        GenericWebApplicationContext applicationContext =
                new GenericWebApplicationContext();
        applicationContext.setServletContext(new MockServletContext());

        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(false);
        properties.setMemberUser(memberUser);
        OperatorBootstrapAuthenticationService bootstrapService =
                mock(OperatorBootstrapAuthenticationService.class);
        when(bootstrapService.isConfigured())
                .thenReturn(bootstrapConfigured);
        UserDetails principal = new FirebasePrincipal(
                "operator-id",
                "operator@deployment.invalid",
                List.of("U", "O"),
                null);
        when(bootstrapService.authenticateToken("signed-token"))
                .thenReturn(principal);

        applicationContext.registerBean(
                ConfigProperties.class,
                () -> properties);
        applicationContext.registerBean(
                FirebaseService.class,
                () -> mock(FirebaseService.class));
        applicationContext.registerBean(
                FirebaseAuthenticationProvider.class,
                FirebaseAuthenticationProvider::new);
        applicationContext.registerBean(
                FirebaseTokenVerifier.class,
                () -> mock(FirebaseTokenVerifier.class));
        applicationContext.registerBean(
                com.raxim.myscoutee.domain.accounts.service.UserLoginSessionService.class,
                () -> mock(com.raxim.myscoutee.domain.accounts.service.UserLoginSessionService.class));
        applicationContext.registerBean(
                com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class,
                () -> mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
        applicationContext.registerBean(
                OperatorBootstrapAuthenticationService.class,
                () -> bootstrapService);
        applicationContext.registerBean(com.raxim.myscoutee.domain.accounts.service.ProfileAuthenticationService.class,
                com.raxim.myscoutee.domain.accounts.service.ProfileAuthenticationService::new);
        applicationContext.registerBean(com.raxim.myscoutee.domain.integrations.config.IntegrationTokenAuthenticationFilter.class,
                () -> new com.raxim.myscoutee.domain.integrations.config.IntegrationTokenAuthenticationFilter(
                        mock(com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService.class),
                        mock(com.raxim.myscoutee.domain.accounts.api.ProfileDirectory.class), java.util.Optional.empty(),
                        mock(com.raxim.myscoutee.domain.integrations.service.IntegrationRoleAccessService.class)));
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
        @PostMapping("/auth/operator-bootstrap")
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
