package com.azerion.prebid.settings.model;

import lombok.Value;

import java.util.List;

@Value
public class SettingsFile {
    List<Placement> placements;
}