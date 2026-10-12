package com.raxim.myscoutee.domain.showcase.mapper;

import com.raxim.myscoutee.domain.showcase.document.SampleCard;
import com.raxim.myscoutee.domain.showcase.dto.SampleCardDtos.Card;

public final class SampleCardMapper {
    private SampleCardMapper() {}
    public static Card toDto(SampleCard card) {
        return new Card(card.id(), card.title(), card.description(), card.imageUrl(), card.palette(), card.dateIso());
    }
}
