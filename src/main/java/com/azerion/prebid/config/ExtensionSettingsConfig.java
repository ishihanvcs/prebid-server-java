package com.azerion.prebid.config;

import com.azerion.prebid.settings.CustomSettings;
import com.azerion.prebid.settings.FileCustomSettings;
import io.vertx.core.file.FileSystem;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

public class ExtensionSettingsConfig {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionSettingsConfig.class);
    private static final long DEFAULT_SETTINGS_LOADING_TIMEOUT = 2000L;

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
                @Value("${settings.filesystem.custom-settings-filename}") String settingsFileName,
                FileSystem fileSystem) {
            return new FileCustomSettings(fileSystem, settingsFileName);
        }
    }

    @Configuration
    static class CustomSettingsConfiguration {

        @Bean
        CustomSettings customSettings(
                FileCustomSettings customFileSettings) {
            return customFileSettings;
        }
    }
}
