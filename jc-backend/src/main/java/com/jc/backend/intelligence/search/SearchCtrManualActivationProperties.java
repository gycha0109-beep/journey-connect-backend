package com.jc.backend.intelligence.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.intelligence.search-ctr.manual")
public final class SearchCtrManualActivationProperties {

    private boolean enabled;
    private boolean killSwitch = true;
    private String environment = "";
    private String windowStart = "";
    private String producerBuildId = "";
    private String approvalRef = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isKillSwitch() {
        return killSwitch;
    }

    public void setKillSwitch(boolean killSwitch) {
        this.killSwitch = killSwitch;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(String windowStart) {
        this.windowStart = windowStart;
    }

    public String getProducerBuildId() {
        return producerBuildId;
    }

    public void setProducerBuildId(String producerBuildId) {
        this.producerBuildId = producerBuildId;
    }

    public String getApprovalRef() {
        return approvalRef;
    }

    public void setApprovalRef(String approvalRef) {
        this.approvalRef = approvalRef;
    }
}
