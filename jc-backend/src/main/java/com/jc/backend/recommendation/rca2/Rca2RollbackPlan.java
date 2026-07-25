package com.jc.backend.recommendation.rca2;

import java.util.EnumMap;
import java.util.Map;

public final class Rca2RollbackPlan {
    public enum Level {
        LEVEL_1_FLAG_OFF,
        LEVEL_2_LANE_KILL_SWITCH,
        LEVEL_3_GLOBAL_SHADOW_DISABLE,
        LEVEL_4_CONFIG_ROLLBACK,
        LEVEL_5_DEPLOYMENT_ROLLBACK,
        LEVEL_6_CREDENTIAL_REVOKE,
        LEVEL_7_NETWORK_ROUTE_REVOKE
    }
    public enum Status { PASS, NOT_EXECUTED }
    public record Step(String trigger, String owner, String procedure, String verification,
            String recoveryCriteria, String evidence, String expectedUserImpact, Status status) {}

    public Map<Level, Step> hierarchy() {
        EnumMap<Level, Step> steps = new EnumMap<>(Level.class);
        steps.put(Level.LEVEL_1_FLAG_OFF, step("lane risk", "OPERATIONS", "set flag OFF", "execution count stops", "cause removed", "flag snapshot", Status.PASS));
        steps.put(Level.LEVEL_2_LANE_KILL_SWITCH, step("single lane failure", "OPERATIONS", "kill affected lane", "lane blocked; other lane isolated", "lane review", "kill audit", Status.PASS));
        steps.put(Level.LEVEL_3_GLOBAL_SHADOW_DISABLE, step("cross-lane or privacy failure", "OPERATIONS", "activate global kill", "all lanes blocked", "incident closed", "global kill audit", Status.PASS));
        steps.put(Level.LEVEL_4_CONFIG_ROLLBACK, step("invalid signed config", "OPERATIONS", "restore prior OFF/0 config", "fail-closed snapshot", "config validated", "config digest", Status.PASS));
        steps.put(Level.LEVEL_5_DEPLOYMENT_ROLLBACK, step("runtime regression", "OPERATIONS", "rollback isolated deployment", "RCA-2 beans absent", "build approved", "deployment ref", Status.PASS));
        steps.put(Level.LEVEL_6_CREDENTIAL_REVOKE, step("credential concern", "OPERATIONS", "revoke secret-manager lease", "provider returns missing", "new lease approved", "contract simulation", Status.NOT_EXECUTED));
        steps.put(Level.LEVEL_7_NETWORK_ROUTE_REVOKE, step("route concern", "OPERATIONS", "remove nonproduction allowlist", "deny-by-default", "route reapproved", "contract simulation", Status.NOT_EXECUTED));
        return Map.copyOf(steps);
    }

    private static Step step(String trigger, String owner, String procedure, String verification,
            String recovery, String evidence, Status status) {
        return new Step(trigger, owner, procedure, verification, recovery, evidence, "NONE", status);
    }
}
