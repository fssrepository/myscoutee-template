package com.raxim.myscoutee.microservices.transport.common;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

/** Command outcome handling over the existing bounded HTTP implementation. No retries. */
public class RemoteCommandTransport extends RemoteReadTransport {
    public RemoteCommandTransport(URI origin,String token,boolean allowHttp,Duration connectTimeout,
            Duration requestTimeout,ObjectMapper mapper,MeterRegistry metrics) {
        super(origin,token,allowHttp,connectTimeout,requestTimeout,mapper,metrics);
    }
    protected <T> T command(String path,byte[] body,TypeReference<T> type,String operation,int maxBytes,
            java.util.function.Predicate<T> valid,String delegation,String requestId) {
        return command(path,body,type,operation,maxBytes,valid,Map.of(ActorDelegation.HEADER,delegation),requestId);
    }
    /** Producer-owned signals use their dedicated capability, not an end-user identity. */
    protected <T> T producerCommand(String path,byte[] body,TypeReference<T> type,String operation,int maxBytes,
            java.util.function.Predicate<T> valid,String requestId) {
        return command(path,body,type,operation,maxBytes,valid,Map.of(),requestId);
    }
    private <T> T command(String path,byte[] body,TypeReference<T> type,String operation,int maxBytes,
            java.util.function.Predicate<T> valid,Map<String,String> headers,String requestId) {
        try {return exchangeAuthorized(path,body,type,operation,maxBytes,valid,headers);}
        catch(RemoteReadException failure) {
            if((failure.status()==0 && !"BUSY".equals(failure.code())) || failure.status()>=500 || failure.status()==200)
                throw new OutcomeUnknownException(requestId,failure);
            throw failure;
        }
    }
    public static final class OutcomeUnknownException extends RuntimeException {
        private final String requestId;
        public OutcomeUnknownException(String requestId,RuntimeException cause) {
            super("Command outcome unknown; retain the same request ID for reconciliation",cause);this.requestId=requestId;
        }
        public String requestId(){return requestId;}
    }
}
