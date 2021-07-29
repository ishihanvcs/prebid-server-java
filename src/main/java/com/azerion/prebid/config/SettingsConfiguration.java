package com.azerion.prebid.config;

import com.azerion.prebid.settings.FileCustomSettings;
import com.azerion.prebid.settings.CustomSettings;
import io.vertx.core.file.FileSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class SettingsConfiguration {
    @Configuration
    @ConditionalOnProperty(prefix = "settings.filesystem",
            name = {"custom-settings-filename"})
    static class CustomFileSettingsConfiguration {
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
        CustomSettings customSettings (
                @Autowired(required = false) FileCustomSettings customFileSettings) {
            return customFileSettings;
        }
    }
}
