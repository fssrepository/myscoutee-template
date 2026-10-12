package com.raxim.myscoutee.common.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.convert.MappingContextTypeInformationMapper;
import org.springframework.data.convert.SimpleTypeInformationMapper;
import org.springframework.data.convert.TypeInformationMapper;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.mapping.Alias;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.util.ClassUtils;

@Configuration
public class MongoConverterConfig {
    @Bean
    static DottedMapKeyPostProcessor preserveDottedMongoMapKeys() {
        return new DottedMapKeyPostProcessor();
    }

    // Register before ordinary post-processors can eagerly create Mongo repositories.
    static final class DottedMapKeyPostProcessor implements BeanPostProcessor, PriorityOrdered {
        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            if (bean instanceof MappingMongoConverter converter) {
                converter.preserveMapKeys(true);
                converter.setTypeMapper(new DefaultMongoTypeMapper(DefaultMongoTypeMapper.DEFAULT_TYPE_KEY,
                        List.of(new DomainTypeInformationMapper(),
                                new MappingContextTypeInformationMapper(converter.getMappingContext()),
                                new SimpleTypeInformationMapper())));
            }
            return bean;
        }
    }

    // Keep persisted type identity stable in both directions, including nested records.
    // DefaultMongoTypeMapper caches resolved aliases; this mapper performs no database I/O.
    static final class DomainTypeInformationMapper implements TypeInformationMapper {
        private static final Map<String, String> CURRENT_NAMES = loadCurrentNames();
        private static final Map<String, String> STORED_NAMES = reverse(CURRENT_NAMES);

        @Override
        public TypeInformation<?> resolveTypeFrom(Alias alias) {
            if (!(alias.getValue() instanceof String stored)) {
                return null;
            }
            String current = translate(stored, CURRENT_NAMES);
            return current == null ? null
                    : TypeInformation.of(ClassUtils.resolveClassName(current, MongoConverterConfig.class.getClassLoader()));
        }

        @Override
        public Alias createAliasFor(TypeInformation<?> type) {
            String stored = translate(type.getType().getName(), STORED_NAMES);
            return stored == null ? Alias.NONE : Alias.of(stored);
        }

        private static String translate(String name, Map<String, String> names) {
            String exact = names.get(name);
            if (exact != null) return exact;
            int nested = name.indexOf('$');
            String root = nested < 0 ? name : name.substring(0, nested);
            String replacement = names.get(root);
            return replacement == null ? null : replacement + (nested < 0 ? "" : name.substring(nested));
        }

        private static Map<String, String> loadCurrentNames() {
            Properties names = new Properties();
            try (var input = new ClassPathResource("mongo-domain-types.properties").getInputStream()) {
                names.load(input);
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot load persisted MongoDB domain type identities", failure);
            }
            Map<String, String> result = new HashMap<>();
            names.forEach((stored, current) -> result.put((String) stored, (String) current));
            return Map.copyOf(result);
        }

        private static Map<String, String> reverse(Map<String, String> names) {
            Map<String, String> result = new HashMap<>();
            names.forEach((stored, current) -> {
                if (result.put(current, stored) != null) {
                    throw new IllegalStateException("Duplicate persisted MongoDB domain type identity: " + current);
                }
            });
            return Map.copyOf(result);
        }
    }
}
