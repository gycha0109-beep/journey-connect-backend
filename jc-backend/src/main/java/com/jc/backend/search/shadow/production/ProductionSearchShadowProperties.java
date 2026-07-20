package com.jc.backend.search.shadow.production;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.intelligence.search-shadow.production")
public final class ProductionSearchShadowProperties {
    public static final int APPROVED_MAXIMUM_SAMPLING_BPS = 10;

    private boolean enabled;
    private boolean killSwitch = true;
    private int samplingBps;
    private int maxApprovedSamplingBps = APPROVED_MAXIMUM_SAMPLING_BPS;
    private List<String> allowlistHashes = new ArrayList<>();
    private int maxCandidates = 100;
    private int timeoutMs = 200;
    private int hardTimeoutMs = 300;
    private int coreConcurrency = 1;
    private int maxConcurrency = 2;
    private int queueCapacity = 8;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isKillSwitch() { return killSwitch; }
    public void setKillSwitch(boolean killSwitch) { this.killSwitch = killSwitch; }
    public int getSamplingBps() { return samplingBps; }
    public void setSamplingBps(int samplingBps) { this.samplingBps = samplingBps; }
    public int getMaxApprovedSamplingBps() { return maxApprovedSamplingBps; }
    public void setMaxApprovedSamplingBps(int maxApprovedSamplingBps) {
        this.maxApprovedSamplingBps = maxApprovedSamplingBps;
    }
    public List<String> getAllowlistHashes() { return List.copyOf(allowlistHashes); }
    public void setAllowlistHashes(List<String> allowlistHashes) {
        this.allowlistHashes = allowlistHashes == null ? new ArrayList<>() : new ArrayList<>(allowlistHashes);
    }
    public int getMaxCandidates() { return maxCandidates; }
    public void setMaxCandidates(int maxCandidates) { this.maxCandidates = maxCandidates; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public int getHardTimeoutMs() { return hardTimeoutMs; }
    public void setHardTimeoutMs(int hardTimeoutMs) { this.hardTimeoutMs = hardTimeoutMs; }
    public int getCoreConcurrency() { return coreConcurrency; }
    public void setCoreConcurrency(int coreConcurrency) { this.coreConcurrency = coreConcurrency; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
}
