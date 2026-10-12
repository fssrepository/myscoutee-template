package com.raxim.myscoutee.domain.showcase.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** A small project-owned example, independent of MyScoutee product models. */
@Document(collection = "sampleCards")
public record SampleCard(@Id String id, String title, String description, String imageUrl, String palette, String dateIso) {}
