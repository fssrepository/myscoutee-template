package com.raxim.myscoutee.microservices.transport.notifications;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import com.raxim.myscoutee.common.config.JsonConfig;
import com.raxim.myscoutee.common.config.mongo.*;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery.NotificationIntent;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.microservices.transport.platform.JobAudienceProtocol;
import com.raxim.myscoutee.microservices.bootstrap.ServiceSecurityConfiguration;
import com.raxim.myscoutee.microservices.transport.common.RemoteCommandTransport.OutcomeUnknownException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class NotificationDeliveryHttpTest {
    static final String TOKEN="test-only-notification-delivery-token-000000000000000000000";
    static final ObjectMapper mapper=new JsonConfig().objectMapper();
    static final AtomicInteger sequence=new AtomicInteger();
    record Call(MongoSessionMode mode, boolean active, String group, boolean once, String operationId, NotificationIntent intent) {}
    static final List<Call> calls=new CopyOnWriteArrayList<>();
    static ConfigurableApplicationContext server;
    static RemoteNotificationDelivery remote;
    static SimpleMeterRegistry meters;
    static HttpClient http;
    static URI origin;
    static Path evidence;
    @BeforeAll static void start() throws Exception {
        evidence=Path.of(System.getProperty("wireEvidenceDirectory"),"notification-delivery");Files.createDirectories(evidence);
        try(var files=Files.list(evidence)){for(var file:files.filter(p->p.getFileName().toString().startsWith("response-")).toList())Files.delete(file);}
        server=new SpringApplicationBuilder(Fixture.class).properties("server.address=127.0.0.1","server.port=0","management.server.port=0",
                "myscoutee.remote.notification-delivery.command-token="+TOKEN,"spring.main.banner-mode=off","logging.level.root=WARN").run();
        origin=URI.create("http://127.0.0.1:"+server.getEnvironment().getProperty("local.server.port"));
        http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();meters=new SimpleMeterRegistry();
        remote=new RemoteNotificationDelivery(origin,TOKEN,true,Duration.ofSeconds(1),Duration.ofSeconds(3),mapper,meters);
    }
    @BeforeEach void reset(){calls.clear();}
    @AfterEach void clear(){MongoSessionModeContext.clear();}
    @AfterAll static void stop(){if(remote!=null)remote.close();if(http!=null)http.shutdownNow();if(server!=null)server.close();if(meters!=null)meters.close();}
    static NotificationIntent intent(String type) {
        return new NotificationIntent(type,"chat","chat-id","users",List.of("profile-1","profile-2"),"Title","Body","/chat?id=chat-id",
                "/icon","/badge","chat-tag",Map.of("scope","chat"),"author-id");
    }
    static byte[] body(boolean active,String group,String operation,NotificationIntent intent) {
        return mapper.writeValueAsBytes(new NotificationDeliveryProtocol.Command(new JobAudienceProtocol.Context(active,group),operation,intent));
    }
    @Test void preservesCompleteIntentBatchModeJobScopeAndReceipt() throws Exception {
        for(var mode:List.of(MongoSessionMode.REAL,MongoSessionMode.DEMO)) {
            MongoSessionModeContext.set(mode);
            for(var context:List.of(new JobAudienceProtocol.Context(false,null),new JobAudienceProtocol.Context(true,null),new JobAudienceProtocol.Context(true,"work"))) {
                var item=intent("chat.message");
                var result=JobGroupContext.call(context.active(),context.groupId(),()->remote.enqueueOnce("event-1",item));
                assertEquals("outbox-id",result.orElseThrow().getId());assertEquals("sync-id",result.orElseThrow().getSyncKey());
                assertEquals(new Call(mode,context.active(),context.groupId(),true,"event-1",item),calls.getLast());
                assertEquals(200,request(NotificationDeliveryProtocol.ONCE,body(context.active(),context.groupId(),"event-1",item),TOKEN,mode.name(),"POST","application/json").statusCode());
            }
            remote.enqueue(intent("chat.message"));
            assertFalse(calls.getLast().once());assertNull(calls.getLast().operationId());
            assertEquals(200,request(NotificationDeliveryProtocol.ENQUEUE,body(false,null,null,intent("chat.message")),TOKEN,mode.name(),"POST","application/json").statusCode());
        }
        // Recipient lists cross the boundary once, never one call per recipient.
        assertEquals(16,calls.size());
    }
    @Test void canonicalEmptyResultIsDifferentFromFailureAndNullIntentIsNoOp() throws Exception {
        assertTrue(remote.enqueue(null).isEmpty());assertTrue(remote.enqueueOnce("event",null).isEmpty());assertTrue(calls.isEmpty());
        assertTrue(remote.enqueue(intent("empty")).isEmpty());
        assertEquals(200,request(NotificationDeliveryProtocol.ENQUEUE,body(false,null,null,intent("empty")),TOKEN,"REAL","POST","application/json").statusCode());
        assertThrows(OutcomeUnknownException.class,()->remote.enqueue(intent("fail")));
        var failed=request(NotificationDeliveryProtocol.ONCE,body(true,"work","event",intent("fail")),TOKEN,"REAL","POST","application/json");
        assertEquals(503,failed.statusCode());assertFalse(failed.body().contains("private"));
        // The next unscoped request is not contaminated by the failed job scope.
        remote.enqueue(intent("chat.message"));assertFalse(calls.getLast().active());assertNull(calls.getLast().group());
    }
    @Test void exactCommandsRequireCapabilityAndRejectOversizeOrAmbiguousContextBeforeOwner() throws Exception {
        for(var path:NotificationDeliveryProtocol.paths()) {
            byte[] command=body(false,null,null,intent("chat.message"));
            assertEquals(401,request(path,command,null,"REAL","POST","application/json").statusCode());
            assertEquals(400,request(path,command,TOKEN,"invalid","POST","application/json").statusCode());
            assertEquals(403,request(path,command,TOKEN,"REAL","GET","application/json").statusCode());
            assertEquals(415,request(path,command,TOKEN,"REAL","POST","text/plain").statusCode());
            assertEquals(413,request(path,new byte[NotificationDeliveryProtocol.MAX_REQUEST_BYTES+1],TOKEN,"REAL","POST","application/json").statusCode());
            assertEquals(400,request(path,"{}".getBytes(),TOKEN,"REAL","POST","application/json").statusCode());
            assertEquals(400,request(path,body(false,"work",null,intent("chat.message")),TOKEN,"REAL","POST","application/json").statusCode());
            assertEquals(400,request(path,"{\"context\":{\"active\":false},\"unexpected\":true}".getBytes(),TOKEN,"REAL","POST","application/json").statusCode());
        }
        assertEquals(400,request(NotificationDeliveryProtocol.ENQUEUE,body(false,null,"not-enqueue-once",intent("chat.message")),TOKEN,"REAL","POST","application/json").statusCode());
        assertThrows(IllegalArgumentException.class,()->remote.enqueueOnce("x".repeat(4097),intent("chat.message")));
        assertTrue(calls.isEmpty());
    }
    @Test void lostReplyDoesNotRetryAndKeepsCallerOwnedOperationForExplicitReconciliation() throws Exception {
        var proxy=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var forwarded=new AtomicInteger();
        proxy.createContext(NotificationDeliveryProtocol.ONCE,exchange->{
            try {
                assertEquals(200,request(NotificationDeliveryProtocol.ONCE,exchange.getRequestBody().readAllBytes(),TOKEN,"REAL","POST","application/json").statusCode());
                forwarded.incrementAndGet();exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,100);
            } catch(Exception failure){throw new RuntimeException(failure);} finally {exchange.close();}
        });proxy.start();
        try(var broken=new RemoteNotificationDelivery(URI.create("http://127.0.0.1:"+proxy.getAddress().getPort()),TOKEN,true,
                Duration.ofSeconds(1),Duration.ofSeconds(1),mapper,meters)) {
            var failure=assertThrows(OutcomeUnknownException.class,()->broken.enqueueOnce("lost-event",intent("chat.message")));
            assertEquals("lost-event",failure.requestId());assertEquals(1,forwarded.get());assertEquals(1,calls.size());
            remote.enqueueOnce("lost-event",intent("chat.message"));assertEquals(2,calls.size());assertEquals(calls.getFirst(),calls.getLast());
        } finally {proxy.stop(0);}
    }
    static HttpResponse<String> request(String path,byte[] body,String credential,String mode,String method,String contentType) throws Exception {
        var builder=HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(5)).header("Content-Type",contentType);
        if(credential!=null)builder.header("Authorization","Bearer "+credential);
        if(mode!=null)builder.header("X-MyScoutee-Session-Mode",mode);
        var result=http.send(builder.method(method,HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals("no-store",result.headers().firstValue("Cache-Control").orElseThrow());
        Files.writeString(evidence.resolve("response-"+sequence.incrementAndGet()+".json"),mapper.writeValueAsString(Map.of("path",path,"method","post","status",result.statusCode(),"body",mapper.readTree(result.body()))));return result;
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName={"org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration","org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration","org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration","org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"})
    @Import({JsonConfig.class,NotificationDeliveryServerConfiguration.class,ServiceSecurityConfiguration.class})
    static class Fixture {
        @Bean NotificationDelivery owner(){return new NotificationDelivery(){
            public Optional<? extends Receipt> enqueue(NotificationIntent intent){return record(false,null,intent);}
            public Optional<? extends Receipt> enqueueOnce(String id,NotificationIntent intent){return record(true,id,intent);}
            private Optional<? extends Receipt> record(boolean once,String id,NotificationIntent intent){
                calls.add(new Call(MongoSessionModeContext.currentMode(),JobGroupContext.isActive(),JobGroupContext.groupId(),once,id,intent));
                if(intent!=null && "fail".equals(intent.type()))throw new IllegalStateException("private outbox detail");
                if(intent==null || "empty".equals(intent.type()))return Optional.empty();
                return Optional.of(new NotificationDeliveryProtocol.ReceiptDto("outbox-id","sync-id"));
            }
        };}
    }
}
