package com.jc.backend.recommendation.rca2;

import java.util.EnumMap;
import java.util.Map;

public final class Rca2Op2RollbackMatrix {
    public enum Level { LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4, LEVEL_5, LEVEL_6, LEVEL_7 }
    public enum Status { PASS, FAIL, NOT_EXECUTED, BLOCKED_EXTERNAL_DEPENDENCY, NOT_APPLICABLE }
    public record Entry(String control, String trigger, String owner, String procedure, String maximumExecutionTime,
            String expectedState, String verificationQuery, String recoveryCriteria, String escalation,
            String evidence, Status drillStatus) {}

    public Map<Level, Entry> matrix() {
        EnumMap<Level, Entry> result = new EnumMap<>(Level.class);
        result.put(Level.LEVEL_1, entry("FLAG_OFF", "warning ceiling or operator hold", "OPERATIONS",
                "replace signed snapshot with default OFF and traffic 0", "60s", "FLAG_OFF_TRAFFIC_ZERO",
                "flag decision is disabled for P1 and P2", "cause removed and six-role reapproval", "RELIABILITY",
                "unit drill and flag snapshot", Status.PASS));
        result.put(Level.LEVEL_2, entry("LANE_KILL_SWITCH", "single-lane critical condition", "OPERATIONS",
                "kill affected lane and cancel queued lane work", "30s", "AFFECTED_LANE_BLOCKED",
                "submission returns LANE_KILLED", "lane incident closed", "RELIABILITY",
                "kill-switch unit drill", Status.PASS));
        result.put(Level.LEVEL_3, entry("GLOBAL_SHADOW_DISABLE", "cross-lane or privacy critical condition", "OPERATIONS",
                "activate global kill and cancel queued work", "30s", "ALL_SHADOW_BLOCKED_PRIMARY_UNCHANGED",
                "submission returns GLOBAL_KILLED", "incident closed and approvals renewed", "SYSTEM_COORDINATION",
                "global kill and queue-cancel test", Status.PASS));
        result.put(Level.LEVEL_4, entry("CONFIG_ROLLBACK", "invalid or stale configuration", "OPERATIONS",
                "restore last approved OFF/0 snapshot", "5m", "SAFE_DEFAULT_RESTORED",
                "effective traffic and configured traffic equal 0", "configuration validated", "SYSTEM_COORDINATION",
                "configuration contract test", Status.PASS));
        result.put(Level.LEVEL_5, entry("DEPLOYMENT_ROLLBACK", "isolated deployment regression", "OPERATIONS",
                "rollback deployment to prior exact version", "15m", "PRIOR_VERSION_RUNNING",
                "deployment digest and RCA-2 beans verified", "new build approved", "RELIABILITY",
                "external deployment path unresolved", Status.NOT_EXECUTED));
        result.put(Level.LEVEL_6, entry("CREDENTIAL_REVOKE", "credential concern or scope violation", "OPERATIONS",
                "revoke workload lease in approved secret manager", "5m", "CREDENTIAL_UNAVAILABLE",
                "credential provider returns missing/revoked", "new lease approved", "PRIVACY_SECURITY",
                "secret manager unresolved; contract only", Status.BLOCKED_EXTERNAL_DEPENDENCY));
        result.put(Level.LEVEL_7, entry("NETWORK_ROUTE_REVOKE", "production route or route concern", "OPERATIONS",
                "remove nonproduction route/allowlist in infrastructure control plane", "5m", "DENY_BY_DEFAULT",
                "connection is denied and production route remains absent", "route reapproved", "PRIVACY_SECURITY",
                "infrastructure repository/path unresolved; contract only", Status.BLOCKED_EXTERNAL_DEPENDENCY));
        return Map.copyOf(result);
    }

    private static Entry entry(String control, String trigger, String owner, String procedure, String maximum,
            String expected, String verification, String recovery, String escalation, String evidence, Status status) {
        return new Entry(control, trigger, owner, procedure, maximum, expected, verification, recovery, escalation,
                evidence, status);
    }
}
