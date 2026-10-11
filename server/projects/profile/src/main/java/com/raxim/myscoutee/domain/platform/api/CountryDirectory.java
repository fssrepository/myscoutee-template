package com.raxim.myscoutee.domain.platform.api;
public interface CountryDirectory {
    java.util.List<? extends Country> supportedCountries();
    interface Country { String getCountryCode(); String getCountryName(); }
    String resolvePartitionKey(com.raxim.myscoutee.common.dto.SharedDtos.LocationCoordinatesDto coordinates);
    String resolvePartitionKey(com.raxim.myscoutee.common.dto.SharedDtos.LocationCoordinatesDto coordinates, String preferred);
}
