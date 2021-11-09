package com.azerion.prebid.settings.model;

import lombok.Value;

import java.util.Collections;
import java.util.List;

@Value
public class SettingsFile {

    List<Placement> placements = Collections.emptyList();
    CustomTrackerSetting customTrackers = new CustomTrackerSetting();
}
