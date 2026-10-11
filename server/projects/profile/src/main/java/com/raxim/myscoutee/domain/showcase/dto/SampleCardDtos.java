package com.raxim.myscoutee.domain.showcase.dto;

import java.util.List;

public final class SampleCardDtos {
    private SampleCardDtos() {}
    public record Card(String id, String title, String description, String imageUrl, String palette) {}
    public record Page(List<Card> items, long total) {}
}
