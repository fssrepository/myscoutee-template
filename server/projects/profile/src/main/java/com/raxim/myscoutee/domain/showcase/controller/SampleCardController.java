package com.raxim.myscoutee.domain.showcase.controller;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.accounts.api.SelectedProfile;
import com.raxim.myscoutee.domain.showcase.dto.SampleCardDtos;
import com.raxim.myscoutee.domain.showcase.service.SampleCardService;

@RestController
@RequestMapping("/showcase/cards")
public class SampleCardController {
    private final SelectedProfile profiles;
    private final SampleCardService cards;
    public SampleCardController(SelectedProfile profiles, SampleCardService cards) {
        this.profiles = profiles; this.cards = cards;
    }
    @GetMapping
    public SampleCardDtos.Page query(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int pageSize) {
        if (!StringUtils.hasText(profiles.resolveSelectedUserId(null))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is required.");
        }
        return cards.query(page, pageSize);
    }
}
