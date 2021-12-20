package com.azerion.prebid.settings.model;

import lombok.Value;

@Value
public class SettingsFile {

    CustomTrackerSetting customTrackers = new CustomTrackerSetting();
}
