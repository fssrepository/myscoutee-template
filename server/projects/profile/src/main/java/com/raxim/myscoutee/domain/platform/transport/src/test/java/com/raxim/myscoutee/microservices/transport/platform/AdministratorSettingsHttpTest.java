package com.raxim.myscoutee.microservices.transport.platform;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.raxim.myscoutee.common.config.JsonConfig;
import com.raxim.myscoutee.common.config.mongo.*;
import com.raxim.myscoutee.domain.platform.document.AppSettings;
import com.raxim.myscoutee.domain.platform.repository.AppSettingsRepository;
import com.raxim.myscoutee.domain.platform.service.AppSettingsService;
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
import static org.mockito.ArgumentMatchers.*;

class AdministratorSettingsHttpTest {
    static final String TOKEN="test-only-administrator-settings-token-0000000000000000000";
    static final ObjectMapper mapper=new JsonConfig().objectMapper();
    static final AtomicInteger sequence=new AtomicInteger();
    static final Map<MongoSessionMode,AppSettings> documents=new EnumMap<>(MongoSessionMode.class);
    static ConfigurableApplicationContext server;
    static RemoteAdministratorSettings remote;
    static AppSettingsService owner;
    static SimpleMeterRegistry meters;
    static HttpClient http;
    static URI origin;
    static Path evidence;
    static void seed(){
        for(var mode:MongoSessionMode.values()) {
            var row=new AppSettings();row.setId("private-settings-id");row.setSettingsKey("global");row.setAdminAccessEnabled(mode==MongoSessionMode.REAL);
            row.setAdminEmails(List.of(" "+mode.name()+"@TEST ","second@test"));documents.put(mode,row);
        }
    }
    @BeforeAll static void start() throws Exception {
        seed();evidence=Path.of(System.getProperty("wireEvidenceDirectory"),"administrator-settings");Files.createDirectories(evidence);
        try(var files=Files.list(evidence)){for(var file:files.filter(p->p.getFileName().toString().startsWith("response-")).toList())Files.delete(file);}
        server=new SpringApplicationBuilder(Fixture.class).properties("server.address=127.0.0.1","server.port=0","management.server.port=0",
                "myscoutee.remote.administrator-settings.read-token="+TOKEN,"spring.main.banner-mode=off","logging.level.root=WARN").run();
        origin=URI.create("http://127.0.0.1:"+server.getEnvironment().getProperty("local.server.port"));
        http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();meters=new SimpleMeterRegistry();owner=server.getBean(AppSettingsService.class);
        remote=new RemoteAdministratorSettings(origin,TOKEN,true,Duration.ofSeconds(1),Duration.ofSeconds(3),mapper,meters);
    }
    @BeforeEach void reset(){org.mockito.Mockito.reset(owner);seed();owner.refreshCache();clearInvocations(owner,server.getBean(AppSettingsRepository.class));}
    @AfterEach void clear(){MongoSessionModeContext.clear();}
    @AfterAll static void stop(){if(remote!=null)remote.close();if(http!=null)http.shutdownNow();if(server!=null)server.close();if(meters!=null)meters.close();}
    static byte[] body(Collection<String> emails){return mapper.writeValueAsBytes(new AdministratorSettingsProtocol.Emails(emails));}
    @Test void nativeNormalizationEnabledGateAndTenantCacheSurviveHttp() throws Exception {
        for(var mode:List.of(MongoSessionMode.REAL,MongoSessionMode.DEMO)) {
            MongoSessionModeContext.set(mode);
            assertEquals(owner.adminEmails(),remote.adminEmails());
            String email=" "+mode.name()+"@TEST ";
            assertEquals(owner.isAdminEmail(email),remote.isAdminEmail(email));
            assertFalse(remote.isAdminEmail("other@test"));assertFalse(remote.isAdminEmail(null));
            var batch=Arrays.asList(email,"other@test","second@test",null,email);
            assertEquals(owner.adminEmailsAmong(batch),remote.adminEmailsAmong(batch));
            assertEquals(200,request(AdministratorSettingsProtocol.EMAILS,null,TOKEN,mode.name(),"GET","application/json").statusCode());
            assertEquals(200,request(AdministratorSettingsProtocol.CLASSIFY,body(batch),TOKEN,mode.name(),"POST","application/json").statusCode());
        }
        verifyNoInteractions(server.getBean(AppSettingsRepository.class));
        documents.get(MongoSessionMode.DEMO).setAdminAccessEnabled(true);owner.refreshCache();MongoSessionModeContext.set(MongoSessionMode.DEMO);
        assertTrue(remote.isAdminEmail(" DEMO@TEST "));
        documents.get(MongoSessionMode.DEMO).setAdminAccessEnabled(false);owner.refreshCache();assertFalse(remote.isAdminEmail("demo@test"));
    }
    @Test void largeAudienceUsesOneBatchAndDoesNotNormalizeAwayOriginalInputValues() {
        var emails=new ArrayList<String>();for(int i=0;i<200;i++)emails.add("other-"+i+"@test");emails.add(" REAL@TEST ");emails.add("real@test");
        var timer=meters.find("myscoutee.remote.requests").tag("operation","platform.admin-classification").timers();
        long before=timer.stream().mapToLong(io.micrometer.core.instrument.Timer::count).sum();
        assertEquals(Set.of(" REAL@TEST ","real@test"),remote.adminEmailsAmong(emails));
        long after=meters.find("myscoutee.remote.requests").tag("operation","platform.admin-classification").timers().stream().mapToLong(io.micrometer.core.instrument.Timer::count).sum();
        assertEquals(1,after-before);verify(owner).adminEmailsAmong(emails);
    }
    @Test void protectedBoundedQueriesDoNotTurnFailuresIntoAuthorization() throws Exception {
        var command=body(List.of("real@test"));
        assertEquals(401,request(AdministratorSettingsProtocol.EMAILS,null,null,"REAL","GET","application/json").statusCode());
        assertEquals(401,request(AdministratorSettingsProtocol.CLASSIFY,command,"wrong","REAL","POST","application/json").statusCode());
        assertEquals(400,request(AdministratorSettingsProtocol.CLASSIFY,command,TOKEN,"invalid","POST","application/json").statusCode());
        assertEquals(403,request(AdministratorSettingsProtocol.CLASSIFY,null,TOKEN,"REAL","GET","application/json").statusCode());
        assertEquals(403,request(AdministratorSettingsProtocol.EMAILS,command,TOKEN,"REAL","POST","application/json").statusCode());
        assertEquals(415,request(AdministratorSettingsProtocol.CLASSIFY,command,TOKEN,"REAL","POST","text/plain").statusCode());
        assertEquals(413,request(AdministratorSettingsProtocol.CLASSIFY,new byte[AdministratorSettingsProtocol.MAX_REQUEST_BYTES+1],TOKEN,"REAL","POST","application/json").statusCode());
        for(var invalid:List.of("{}".getBytes(),body(Collections.nCopies(4097,"x")),body(List.of("x".repeat(321)))))
            assertEquals(400,request(AdministratorSettingsProtocol.CLASSIFY,invalid,TOKEN,"REAL","POST","application/json").statusCode());
        verify(owner,never()).isAdminEmail(any());
        doThrow(new IllegalStateException("private settings failure")).when(owner).adminEmails();
        doThrow(new IllegalStateException("private settings failure")).when(owner).isAdminEmail(any());
        assertThrows(RemoteReadException.class,remote::adminEmails);assertThrows(RemoteReadException.class,()->remote.isAdminEmail("real@test"));
        for(var path:AdministratorSettingsProtocol.paths()){
            var response=request(path,path.equals(AdministratorSettingsProtocol.EMAILS)?null:command,TOKEN,"REAL",path.equals(AdministratorSettingsProtocol.EMAILS)?"GET":"POST","application/json");
            assertEquals(503,response.statusCode());assertFalse(response.body().contains("private"));
        }
    }
    static HttpResponse<String> request(String path,byte[] body,String token,String mode,String method,String contentType) throws Exception {
        var builder=HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(5)).header("Content-Type",contentType);
        if(token!=null)builder.header("Authorization","Bearer "+token);
        if(mode!=null)builder.header("X-MyScoutee-Session-Mode",mode);
        var response=http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals("no-store",response.headers().firstValue("Cache-Control").orElseThrow());
        Files.writeString(evidence.resolve("response-"+sequence.incrementAndGet()+".json"),mapper.writeValueAsString(Map.of("path",path,"method",path.equals(AdministratorSettingsProtocol.EMAILS)?"get":"post","status",response.statusCode(),"body",mapper.readTree(response.body()))));return response;
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName={"org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration","org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration","org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration","org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"})
    @Import({JsonConfig.class,AdministratorSettingsServerConfiguration.class,ServiceSecurityConfiguration.class})
    static class Fixture {
        @Bean com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService changes() {
            return mock(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService.class);
        }
        @Bean com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner databases() {
            return new com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner("real", "demo");
        }

        @Bean AppSettingsService owner(AppSettingsRepository repository){return spy(new AppSettingsService(repository));}
        @Bean AppSettingsRepository repository(){var repository=mock(AppSettingsRepository.class);when(repository.findBySettingsKey("global")).thenAnswer(call->Optional.of(documents.get(MongoSessionModeContext.currentMode())));return repository;}
    }
}
