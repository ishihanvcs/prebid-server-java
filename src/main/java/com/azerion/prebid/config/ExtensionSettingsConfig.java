package com.azerion.prebid.config;

import com.azerion.prebid.services.AccountHttpPeriodicRefreshService;
import com.azerion.prebid.settings.CachingCustomSettings;
import com.azerion.prebid.settings.CustomSettings;
import com.azerion.prebid.settings.FileCustomSettings;
import com.azerion.prebid.settings.SettingsLoader;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class ExtensionSettingsConfig {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionSettingsConfig.class);

    @Configuration
    static class CustomFileSettingsConfiguration {

        @Bean
        FileCustomSettings customFileSettings(
                @Value("${settings.filesystem.custom-settings-filename:}")
                        String settingsFileName,
                FileSystem fileSystem) {
            return new FileCustomSettings(fileSystem, settingsFileName);
        }

        @Bean
        SettingsLoader customSettingsLoader(
                ApplicationSettings applicationSettings,
                CustomSettings customSettings,
                Metrics metrics,
                JacksonMapper mapper,
                @Value("${settings.default-loading-timeout:#{500}}") long defaultTimeoutMs,
                TimeoutFactory timeoutFactory
        ) {
            return new SettingsLoader(
                    applicationSettings, customSettings,
                    metrics, mapper, timeoutFactory, defaultTimeoutMs
            );
        }
    }

    @Configuration
    static class CustomCachingSettingsConfiguration {

        @Component
        @ConfigurationProperties(prefix = "settings.in-memory-cache")
        @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = {"ttl-seconds", "cache-size"})
        @Validated
        @Data
        @NoArgsConstructor
        private static class CustomSettingsCacheProperties {
            @NotNull
            @Min(1)
            private Integer ttlSeconds;
            @NotNull
            @Min(1)
            private Integer cacheSize;
        }

        @Bean
        @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = {"ttl-seconds", "cache-size"})
        CachingCustomSettings customCachingSettings(
                CustomSettingsCacheProperties cacheProperties,
                FileCustomSettings customFileSettings
        ) {

            return new CachingCustomSettings(
                    customFileSettings,
                    cacheProperties.getTtlSeconds(),
                    cacheProperties.getCacheSize());
        }
    }

    @Configuration
    @ConditionalOnBean(name = "cachingApplicationSettings")
    @ConditionalOnProperty(prefix = "settings.in-memory-cache.http-update",
            name = {"endpoint", "refresh-rate", "timeout"})
    static class CustomHttpPeriodicRefreshServiceConfiguration {

        @Value("${settings.in-memory-cache.http-update.endpoint}")
        String endPoint;

        @Value("${settings.in-memory-cache.http-update.refresh-rate}")
        long refreshPeriod;

        @Value("${settings.in-memory-cache.http-update.timeout}")
        long timeout;

        @Autowired
        Vertx vertx;

        @Autowired
        HttpClient httpClient;

        @Autowired
        JacksonMapper mapper;

        @Value("${settings.in-memory-cache.ttl-seconds:#{0}}")
        int cacheTtlSeconds;

        @Bean
        AccountHttpPeriodicRefreshService accountHttpPeriodicRefreshService(
                CachingApplicationSettings cachingApplicationSettings
        ) {
            return new AccountHttpPeriodicRefreshService(
                    cachingApplicationSettings,
                    endPoint,
                    refreshPeriod,
                    timeout,
                    1000L * cacheTtlSeconds, // converted to milliseconds
                    vertx,
                    httpClient,
                    mapper
            );
        }
    }

    @Configuration
    static class CustomSettingsConfiguration {

        @Bean
        CustomSettings customSettings(
                @Autowired(required = false) CachingCustomSettings customCachingSettings,
                FileCustomSettings customFileSettings) {
            return ObjectUtils.defaultIfNull(customCachingSettings, customFileSettings);
        }
    }
}
