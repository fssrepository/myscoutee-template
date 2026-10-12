package com.raxim.myscoutee.microservices.transport.accounts;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummaries;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummary;
import com.raxim.myscoutee.domain.accounts.dto.ProfileSummaryDto;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class ProfileSummariesEndpoint {
    private final ProfileSummaries owner;
    private final ObjectMapper mapper;
    public ProfileSummariesEndpoint(ProfileSummaries owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = ProfileSummariesProtocol.STATUS, produces = "application/json")
    public ResponseEntity<?> status(HttpServletRequest request) throws java.io.IOException {
        return read(request, ProfileSummariesProtocol.StatusQuery.class, ProfileSummariesProtocol::valid,
                query -> summaries(owner.findByProfileStatus(query.status())));
    }
    @PostMapping(value = ProfileSummariesProtocol.SINGLE, produces = "application/json")
    public ResponseEntity<?> single(HttpServletRequest request) throws java.io.IOException {
        return read(request, ProfileSummariesProtocol.SingleQuery.class, ProfileSummariesProtocol::valid,
                query -> new ProfileSummariesProtocol.SingleResult(true,
                        owner.findByUserId(query.userId()).map(ProfileSummaryDto::from).orElse(null)));
    }
    @PostMapping(value = ProfileSummariesProtocol.BATCH, produces = "application/json")
    public ResponseEntity<?> batch(HttpServletRequest request) throws java.io.IOException {
        return read(request, ProfileSummariesProtocol.BatchQuery.class, ProfileSummariesProtocol::valid,
                query -> summaries(owner.findByUserIdIn(query.ids())));
    }


    @PostMapping(value = ProfileSummariesProtocol.COUNT, produces = "application/json")
    public ResponseEntity<?> count(HttpServletRequest request) throws java.io.IOException {
        return read(request, ProfileSummariesProtocol.BatchQuery.class, ProfileSummariesProtocol::valid,
                query -> owner.countByUserIdIn(query.ids()));
    }
    @PostMapping(value = ProfileSummariesProtocol.PAGE, produces = "application/json")
    public ResponseEntity<?> page(HttpServletRequest request) throws java.io.IOException {
        return read(request, ProfileSummariesProtocol.PageQuery.class, ProfileSummariesProtocol::valid,
                query -> summaries(owner.pageByUserIdIn(query.ids(), query.offset(), query.limit())));
    }


    private List<ProfileSummaryDto> summaries(List<? extends ProfileSummary> values) {
        return values.stream().map(value -> value == null ? null : ProfileSummaryDto.from(value)).toList();
    }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> queryOwner)
            throws java.io.IOException {
        try {
            byte[] body = ReadQueryBodies.readBytes(request, ProfileSummariesProtocol.MAX_REQUEST_BYTES);
            T query;
            try {
                query = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(body);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!valid.test(query)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            return response(200, queryOwner.apply(query));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new ProfileSummariesProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new ProfileSummariesProtocol.Error("PROFILE_SUMMARIES_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
