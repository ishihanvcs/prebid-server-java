package com.azerion.prebid.config;

import com.azerion.prebid.settings.CustomSettings;
import com.azerion.prebid.settings.FileCustomSettings;
import io.vertx.core.file.FileSystem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class ExtensionSettingsConfig {

    @Configuration
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
        CustomSettings customSettings(
                FileCustomSettings customFileSettings) {
            return customFileSettings;
        }
    }
}
