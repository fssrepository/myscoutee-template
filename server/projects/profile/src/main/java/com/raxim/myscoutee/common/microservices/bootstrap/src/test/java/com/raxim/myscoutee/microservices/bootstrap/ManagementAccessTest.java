package com.raxim.myscoutee.microservices.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {ManagementAccessTest.Endpoints.class, ServiceSecurityConfiguration.class})
class ManagementAccessTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach void setup() { mvc = webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void metricsAndHealthAreReadableOnlyOnManagementPort() throws Exception {
        for (String route : new String[]{"/health", "/metrics"}) {
            mvc.perform(get(route).with(request -> { request.setLocalPort(8081); return request; }))
                    .andExpect(status().isOk());
            mvc.perform(get(route).with(request -> { request.setLocalPort(8080); return request; }))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void NeitherPortExposesUnboundBusinessRoutes() throws Exception {
        for (int port : new int[]{8080, 8081}) {
            mvc.perform(get("/business").with(request -> { request.setLocalPort(port); return request; }))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void managementAndFallbackNeverAcceptUnprotectedMutations() throws Exception {
        for (int port : new int[]{8080, 8081}) {
            for (String route : new String[]{"/health", "/metrics", "/business"}) {
                mvc.perform(post(route).with(request -> { request.setLocalPort(port); return request; }))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Configuration @EnableWebMvc @EnableWebSecurity
    static class Endpoints {
        @Bean Probe probe() { return new Probe(); }
    }
    @RestController static class Probe {
        @GetMapping({"/health", "/metrics", "/business"}) String read() { return "ok"; }
    }
}
