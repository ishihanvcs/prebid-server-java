package com.improvedigital.prebid.server.settings;

import com.improvedigital.prebid.server.settings.model.CustomTracker;
import com.improvedigital.prebid.server.settings.model.ParsedCustomSettings;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of {@link CustomSettings}.
 * <p>
 * Reads an application settings from YAML file on file system, stores and serves them in and from the memory.
 * <p>
 * Immediately loads stored request data from local files. These are stored in memory for low-latency reads.
 * This expects each file in the directory to be named "{config_id}.json".
 */
public class FileCustomSettings implements CustomSettings {

    private static final Logger logger = LoggerFactory.getLogger(FileCustomSettings.class);

    private final Map<String, CustomTracker> trackersMap;

    public FileCustomSettings(FileSystem fileSystem, String settingsFileName) {
        ParsedCustomSettings parsedCustomSettings = readSettingsFile(Objects.requireNonNull(fileSystem),
                settingsFileName);

        this.trackersMap = CustomTracker.filterDisabledTrackers(parsedCustomSettings.getTrackers());
    }

    @Override
    public Future<Collection<CustomTracker>> getCustomTrackers(Timeout timeout) {
        return Future.succeededFuture(trackersMap.values());
    }

    @Override
    public Future<Map<String, CustomTracker>> getCustomTrackersMap(Timeout timeout) {
        return Future.succeededFuture(trackersMap);
    }

    @Override
    public Future<CustomTracker> getCustomTrackerById(String trackerId, Timeout timeout) {
        return mapValueToFuture(trackersMap, "CustomTracker", trackerId);
    }

    /**
     * Reading YAML settings file.
     */
    private static ParsedCustomSettings readSettingsFile(FileSystem fileSystem, String fileName) {
        logger.debug("Reading custom settings from: " + fileName);
        if (!StringUtils.isBlank(fileName)) {
            try {
                final Buffer buf = fileSystem.readFileBlocking(fileName);
                return new YAMLMapper().readValue(buf.getBytes(), ParsedCustomSettings.class);
            } catch (IOException e) {
                logger.warn("Couldn't read file settings", e);
            }
        }
        return new ParsedCustomSettings();
    }

    private static <T> Future<T> mapValueToFuture(Map<String, T> map, String modelType, String id) {
        final T value = map.get(id);
        return value != null
                ? Future.succeededFuture(value)
                : Future.failedFuture(new PreBidException(String.format("%s not found: %s", modelType, id)));
    }
}

