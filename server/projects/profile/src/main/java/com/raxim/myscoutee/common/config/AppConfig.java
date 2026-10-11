package com.raxim.myscoutee.common.config;

import java.util.Locale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import com.raxim.myscoutee.domain.administration.config.AdminStatsProperties;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.domain.media.config.MediaStorageProperties;
import com.raxim.myscoutee.domain.platform.config.OperatorConfigurationProperties;
import com.raxim.myscoutee.domain.access.config.RegionSecurityProperties;

@Configuration
@EnableConfigurationProperties({
        ConfigProperties.class,
        MediaStorageProperties.class,
        AdminStatsProperties.class,
        RegionSecurityProperties.class,
        OperatorConfigurationProperties.class,
})
@EnableSpringDataWebSupport
@EnableScheduling
public class AppConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver slr = new AcceptHeaderLocaleResolver();
        slr.setDefaultLocale(Locale.ENGLISH);
        return slr;
    }

    @Bean
    @Primary
    public LocalValidatorFactoryBean getValidator(MessageSource messageSource) {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource);
        bean.afterPropertiesSet();
        return bean;
    }
}
