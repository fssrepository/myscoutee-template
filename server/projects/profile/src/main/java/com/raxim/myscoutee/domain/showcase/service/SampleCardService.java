package com.raxim.myscoutee.domain.showcase.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.showcase.dto.SampleCardDtos;
import com.raxim.myscoutee.domain.showcase.mapper.SampleCardMapper;
import com.raxim.myscoutee.domain.showcase.repository.SampleCardRepository;

@Service
public class SampleCardService {
    private final SampleCardRepository repository;
    public SampleCardService(SampleCardRepository repository) { this.repository = repository; }
    public SampleCardDtos.Page query(int page, int pageSize) {
        if (page < 0 || page > 1000 || pageSize < 1 || pageSize > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sample-card page.");
        }
        // Stable ordering uses MongoDB's existing _id index.
        var result = repository.findAll(PageRequest.of(page, pageSize, Sort.by("id")));
        return new SampleCardDtos.Page(result.getContent().stream().map(SampleCardMapper::toDto).toList(), result.getTotalElements());
    }
}
