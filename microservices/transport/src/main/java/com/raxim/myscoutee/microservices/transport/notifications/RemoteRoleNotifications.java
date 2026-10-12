package com.raxim.myscoutee.microservices.transport.notifications;

import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.domain.notifications.api.RoleNotifications;
import com.raxim.myscoutee.microservices.transport.common.RemoteCommandTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class RemoteRoleNotifications extends RemoteCommandTransport implements RoleNotifications {
    private final ObjectMapper mapper;
    public RemoteRoleNotifications(URI origin,String token,boolean allowHttp,Duration connectTimeout,
            Duration requestTimeout,ObjectMapper mapper,MeterRegistry metrics) {
        super(origin,token,allowHttp,connectTimeout,requestTimeout,mapper,metrics);this.mapper=mapper;
    }
    @Override public void releaseAvailable(String version) {
        if(version==null || !RoleNotificationsProtocol.bounded(version))throw new IllegalArgumentException("Invalid release version");
        publish(RoleNotificationsProtocol.RELEASE,new RoleNotificationsProtocol.Release(version),"notifications.role-release","release-available:"+version);
    }


    private void publish(String path,Object body,String operation,String eventId) {
        producerCommand(path,mapper.writeValueAsBytes(body),new TypeReference<RoleNotificationsProtocol.Receipt>() {},operation,
                4096,value->Boolean.TRUE.equals(value.completed()),eventId);
    }
}
