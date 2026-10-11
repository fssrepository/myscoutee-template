package com.raxim.myscoutee.domain.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;

import com.raxim.myscoutee.domain.platform.document.CountryPartition;
import com.raxim.myscoutee.common.dto.SharedDtos.LocationCoordinatesDto;
import com.raxim.myscoutee.domain.platform.repository.CountryPartitionsRepository;
import tools.jackson.databind.ObjectMapper;

@org.junit.jupiter.api.Tag("standard")
class CountryPartitionServiceTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private CountryPartitionService service;
    private final LocationCoordinatesDto point = new LocationCoordinatesDto(47.4979, 19.0402);

    @BeforeEach
    void setup() {
        when(mongo.indexOps(CountryPartition.class)).thenReturn(mock(IndexOperations.class));
        service = new CountryPartitionService(mock(CountryPartitionsRepository.class), mongo, mock(ObjectMapper.class));
        clearInvocations(mongo);
    }

    @Test
    void matchingSavedCountryNeedsOnlyOneLookupAndNoGeometryTransfer() {
        CountryPartition match = new CountryPartition();
        match.setPartitionKey("country:hu");
        when(mongo.findOne(any(Query.class), eq(CountryPartition.class))).thenReturn(match);
        assertThat(service.resolvePartitionKey(point, "country:hu")).isEqualTo("country:hu");
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(1)).findOne(query.capture(), eq(CountryPartition.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("partitionKey", "country:hu").containsKey("geometry");
        assertThat(query.getValue().getFieldsObject()).containsEntry("partitionKey", 1).doesNotContainKey("geometry");
        verifyNoMoreInteractions(mongo);
    }

    @Test
    void outsideSavedCountryUsesOneSpatialLookupForAnotherCountry() {
        CountryPartition match = new CountryPartition();
        match.setPartitionKey("country:hu");
        when(mongo.findOne(any(Query.class), eq(CountryPartition.class))).thenReturn(null, match);
        assertThat(service.resolvePartitionKey(point, "country:de")).isEqualTo("country:hu");
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(2)).findOne(query.capture(), eq(CountryPartition.class));
        Query lookup = query.getAllValues().get(1);
        assertThat(lookup.getQueryObject()).containsKey("geometry").doesNotContainKey("partitionKey");
        assertThat(lookup.getLimit()).isEqualTo(1);
        assertThat(lookup.getSortObject()).containsEntry("specificity", -1);
        verifyNoMoreInteractions(mongo);
    }

    @Test
    void unsupportedPointHasNoArtificialCountryFallback() {
        assertThat(service.resolvePartitionKey(point, "country:hu")).isNull();
        verify(mongo, times(2)).findOne(any(Query.class), eq(CountryPartition.class));
        verifyNoMoreInteractions(mongo);
    }

    @Test
    void malformedPointDoesNotQueryCountries() {
        assertThat(service.resolvePartitionKey(new LocationCoordinatesDto(91d, 19d), "country:hu")).isNull();
        verifyNoInteractions(mongo);
    }
}
