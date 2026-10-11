package com.raxim.myscoutee.common.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import com.raxim.myscoutee.common.util.JsonUtil;

class LocalDateTimeModule extends SimpleModule {
    public LocalDateTimeModule() {
        addSerializer(LocalDateTime.class, new LocalDateTimeSerializer());
    }
}

class LocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ofPattern(JsonUtil.ISO_DATE_STRING);

    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext serializers) throws JacksonException {
        gen.writeString(value.format(ISO_DATE_FORMATTER));
    }
}

@Configuration
public class JsonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new LocalDateTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.INDENT_OUTPUT, false)
                .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
    }
}
