package com.azerion.prebid.config;

import com.azerion.prebid.settings.CachingCustomSettings;
import com.azerion.prebid.settings.CustomSettings;
import com.azerion.prebid.settings.FileCustomSettings;
import com.azerion.prebid.utils.SettingsLoader;
import io.vertx.core.file.FileSystem;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.settings.ApplicationSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.Clock;

public class ExtensionSettingsConfig {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionSettingsConfig.class);
    private static final long DEFAULT_SETTINGS_LOADING_TIMEOUT = 500L;

    @Configuration
    static class CustomFileSettingsConfiguration {

        /**
         * Create {{@link Timeout}} object based on configuration or default, to be used
         * during placement or account loading
         * @return Timeout
         */
        @Bean
        Timeout settingsLoadingTimeout(ApplicationContext applicationContext, Clock clock) {
            final long lngTimeoutMs = applicationContext
                        .getEnvironment()
                        .getProperty("settings.default-loading-timeout", Long.class, DEFAULT_SETTINGS_LOADING_TIMEOUT);
            final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
            return timeoutFactory.create(lngTimeoutMs);
        }

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
                Timeout settingsLoaderTimeout
        ) {
            return new SettingsLoader(applicationSettings, customSettings, settingsLoaderTimeout);
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
    static class CustomSettingsConfiguration {

        @Bean
        CustomSettings customSettings(
                @Autowired(required = false) CachingCustomSettings customCachingSettings,
                FileCustomSettings customFileSettings) {
            return ObjectUtils.defaultIfNull(customCachingSettings, customFileSettings);
        }
    }
}
