package com.improvedigital.prebid.server.settings.model;

import lombok.Value;

@Value
public class SettingsFile {

    CustomTrackerSetting customTrackers = new CustomTrackerSetting();
}
