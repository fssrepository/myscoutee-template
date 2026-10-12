package com.raxim.myscoutee.domain.platform.service;

import com.raxim.myscoutee.domain.platform.mapper.OperatorTlsMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.raxim.myscoutee.domain.platform.config.OperatorTlsProperties;
import com.raxim.myscoutee.domain.platform.repository.OperatorTlsRepository;

@org.junit.jupiter.api.Tag("standard")
class OperatorTlsServiceTest {

    @Test
    void springUsesTheRuntimeConstructorWhenTheClockConstructorAlsoExists() {
        new ApplicationContextRunner()
                .withBean(OperatorTlsProperties.class, () -> mock(OperatorTlsProperties.class))
                .withBean(OperatorTlsRepository.class, () -> mock(OperatorTlsRepository.class))
                .withBean(OperatorTlsMapper.class, () -> mock(OperatorTlsMapper.class))
                .withBean(OperatorTlsService.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(OperatorTlsService.class));
    }
}
