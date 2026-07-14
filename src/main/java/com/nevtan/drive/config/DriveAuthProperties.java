package com.nevtan.drive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "drive.auth")
public class DriveAuthProperties {

    private boolean devTokenEnabled = true;

    public boolean isDevTokenEnabled() {
        return devTokenEnabled;
    }

    public void setDevTokenEnabled(boolean devTokenEnabled) {
        this.devTokenEnabled = devTokenEnabled;
    }
}
