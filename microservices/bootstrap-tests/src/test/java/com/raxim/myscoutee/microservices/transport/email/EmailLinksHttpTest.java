package com.raxim.myscoutee.microservices.transport.email;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.raxim.myscoutee.common.config.JsonConfig;
import com.raxim.myscoutee.common.config.mongo.*;
import com.raxim.myscoutee.domain.email.config.EmailProperties;
import com.raxim.myscoutee.microservices.bootstrap.ServiceSecurityConfiguration;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport.RemoteReadException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailLinksHttpTest {
    static final String TOKEN="test-only-email-links-token-000000000000000000000000000";
    static final ObjectMapper mapper=new JsonConfig().objectMapper();
    static final AtomicInteger sequence=new AtomicInteger();
    static ConfigurableApplicationContext server;
    static EmailProperties owner;
    static RemoteEmailLinks remote;
    static SimpleMeterRegistry meters;
    static HttpClient http;
    static URI origin;
    static Path evidence;
    @BeforeAll static void start() throws Exception {
        evidence=Path.of(System.getProperty("wireEvidenceDirectory"),"email-links");Files.createDirectories(evidence);
        try(var files=Files.list(evidence)){for(var file:files.filter(p->p.getFileName().toString().startsWith("response-")).toList())Files.delete(file);}
        server=new SpringApplicationBuilder(Fixture.class).properties("server.address=127.0.0.1","server.port=0","management.server.port=0",
                "myscoutee.remote.email-links.read-token="+TOKEN,"spring.main.banner-mode=off","logging.level.root=WARN").run();
        origin=URI.create("http://127.0.0.1:"+server.getEnvironment().getProperty("local.server.port"));
        http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();meters=new SimpleMeterRegistry();
        owner=server.getBean(EmailProperties.class);
        remote=new RemoteEmailLinks(origin,TOKEN,true,Duration.ofSeconds(1),Duration.ofSeconds(3),mapper,meters);
    }
    @BeforeEach void reset(){org.mockito.Mockito.reset(owner);owner.setBaseUrl("https://configured.example/app");owner.getSendgrid().setApiKey("private-provider-key");clearInvocations(owner);}
    @AfterEach void clear(){MongoSessionModeContext.clear();}
    @AfterAll static void stop(){if(remote!=null)remote.close();if(http!=null)http.shutdownNow();if(server!=null)server.close();if(meters!=null)meters.close();}
    @Test void returnsOnlyTheCanonicalPublicLinkAndPreservesNativeNullOrBlank() throws Exception {
        for(var mode:List.of(MongoSessionMode.REAL,MongoSessionMode.DEMO)) {
            MongoSessionModeContext.set(mode);
            assertEquals(owner.getBaseUrl(),remote.getBaseUrl());
            var result=request(TOKEN,mode.name(),"GET");assertEquals(200,result.statusCode());
            assertEquals(Set.of("resolved","baseUrl"),mapper.readValue(result.body(),Map.class).keySet());
            assertFalse(result.body().contains("private-provider-key"));assertFalse(result.body().contains("sendgrid"));
        }
        owner.setBaseUrl(null);assertNull(remote.getBaseUrl());assertEquals(200,request(TOKEN,"REAL","GET").statusCode());
        owner.setBaseUrl("");assertEquals("",remote.getBaseUrl());assertEquals(200,request(TOKEN,"REAL","GET").statusCode());
    }
    @Test void absentCapabilityInvalidModeAndFailureNeverReturnAFallbackOrigin() throws Exception {
        assertEquals(401,request(null,"REAL","GET").statusCode());
        assertEquals(401,request("incorrect","REAL","GET").statusCode());
        assertEquals(400,request(TOKEN,"other","GET").statusCode());
        assertEquals(403,request(TOKEN,"REAL","POST").statusCode());
        verify(owner,never()).getBaseUrl();
        doThrow(new IllegalStateException("private config failure")).when(owner).getBaseUrl();
        assertEquals(503,assertThrows(RemoteReadException.class,remote::getBaseUrl).status());
        var failed=request(TOKEN,"REAL","GET");assertEquals(503,failed.statusCode());assertFalse(failed.body().contains("private"));
    }
    @Test void malformedSuccessfulResponseDoesNotBecomeNativeNull() throws Exception {
        var proxy=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        proxy.createContext(EmailLinksProtocol.PATH,exchange->{byte[] body="{}".getBytes();exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});proxy.start();
        try(var invalid=new RemoteEmailLinks(URI.create("http://127.0.0.1:"+proxy.getAddress().getPort()),TOKEN,true,Duration.ofSeconds(1),Duration.ofSeconds(1),mapper,meters)) {
            assertThrows(RemoteReadException.class,invalid::getBaseUrl);
        } finally {proxy.stop(0);}
    }
    static HttpResponse<String> request(String token,String mode,String method) throws Exception {
        var builder=HttpRequest.newBuilder(origin.resolve(EmailLinksProtocol.PATH)).timeout(Duration.ofSeconds(5));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        if(mode!=null)builder.header("X-MyScoutee-Session-Mode",mode);
        var response=http.send(builder.method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals("no-store",response.headers().firstValue("Cache-Control").orElseThrow());
        Files.writeString(evidence.resolve("response-"+sequence.incrementAndGet()+".json"),mapper.writeValueAsString(Map.of("path",EmailLinksProtocol.PATH,"method","get","status",response.statusCode(),"body",mapper.readTree(response.body()))));return response;
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName={"org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration","org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration","org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration","org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"})
    @Import({JsonConfig.class,EmailLinksServerConfiguration.class,ServiceSecurityConfiguration.class})
    static class Fixture {
        @Bean EmailProperties owner(){return spy(new EmailProperties());}
    }
}
