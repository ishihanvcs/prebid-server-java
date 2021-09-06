package com.azerion.prebid.settings;

import com.azerion.prebid.settings.model.Placement;
import com.azerion.prebid.settings.model.SettingsFile;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link CustomSettings}.
 * <p>
 * Reads an application settings from YAML file on file system, stores and serves them in and from the memory.
 * <p>
 * Immediately loads stored request data from local files. These are stored in memory for low-latency reads.
 * This expects each file in the directory to be named "{config_id}.json".
 */
public class FileCustomSettings implements CustomSettings {

    private static final String JSON_SUFFIX = ".json";
    private static final Logger logger = LoggerFactory.getLogger(FileCustomSettings.class);

    private final Map<String, Placement> placements;

    public FileCustomSettings(FileSystem fileSystem, String settingsFileName) {

        final SettingsFile settingsFile = readSettingsFile(Objects.requireNonNull(fileSystem),
                Objects.requireNonNull(settingsFileName));

        logger.debug("Custom settings are read successfully.");
        placements = toMap(settingsFile.getPlacements(),
                Placement::getId,
                Function.identity());
        logger.debug("Placements loaded:", placements);
    }

    @Override
    public Future<Placement> getPlacementById(String placementId, Timeout timeout) {
        return mapValueToFuture(placements, placementId);
    }

    private static <T, K, U> Map<K, U> toMap(List<T> list, Function<T, K> keyMapper, Function<T, U> valueMapper) {
        return list != null ? list.stream().collect(Collectors.toMap(keyMapper, valueMapper)) : Collections.emptyMap();
    }

    /**
     * Reading YAML settings file.
     */
    private static SettingsFile readSettingsFile(FileSystem fileSystem, String fileName) {
        logger.debug("Reading custom settings from: " + fileName);
        final Buffer buf = fileSystem.readFileBlocking(fileName);
        try {
            return new YAMLMapper().readValue(buf.getBytes(), SettingsFile.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Couldn't read file settings", e);
        }
    }

    private static <T> Future<T> mapValueToFuture(Map<String, T> map, String id) {
        final T value = map.get(id);
        return value != null
                ? Future.succeededFuture(value)
                : Future.failedFuture(new PreBidException(String.format("Placement not found: %s", id)));
    }
}
