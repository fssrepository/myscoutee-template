package com.raxim.myscoutee.common.dto;

public final class SharedDtos {
    private SharedDtos() {}

    public record LocationCoordinatesDto(
            Double latitude,
            Double longitude) {
    }
}
