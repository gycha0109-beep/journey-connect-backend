package com.jc.backend.recommendation.rca2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Pure alert evaluation contract. External delivery is explicit and unavailable by default. */
public final class Rca2Op2AlertPolicy {
    public enum Severity { CRITICAL, WARNING }
    public enum Action { FLAG_OFF, LANE_KILL_SWITCH, GLOBAL_SHADOW_DISABLE, CREDENTIAL_REVOKE,
        NETWORK_ROUTE_REVOKE, HOLD_ENABLEMENT }
    public enum DeliveryStatus { DELIVERED, ROUTE_UNAVAILABLE }

    public record Rule(String id, Severity severity, String metric, double threshold, Action action) {}
    public record Snapshot(long submissions, Map<String, Long> counts, Set<String> degradedWarnings) {
        public Snapshot {
            if (submissions < 0) throw new IllegalArgumentException("submissions must be nonnegative");
            counts = Map.copyOf(counts == null ? Map.of() : counts);
            degradedWarnings = Set.copyOf(degradedWarnings == null ? Set.of() : degradedWarnings);
        }
        long count(String metric) { return Math.max(0L, counts.getOrDefault(metric, 0L)); }
        double rate(String metric) { return submissions == 0 ? 0.0 : count(metric) * 100.0 / submissions; }
    }
    public record Alert(String id, Severity severity, Rca2RuntimeContracts.Lane lane, Action action,
            DeliveryStatus deliveryStatus) {}
    public interface Route { DeliveryStatus deliver(Alert alert); }

    private final Set<String> dedup = ConcurrentHashMap.newKeySet();
    private final List<Rule> rules = rules();

    public List<Alert> evaluate(Snapshot snapshot, Rca2RuntimeContracts.Lane lane, String windowKey,
            Route route, Rca2KillSwitch killSwitch) {
        Objects.requireNonNull(snapshot); Objects.requireNonNull(lane); Objects.requireNonNull(route); Objects.requireNonNull(killSwitch);
        if (windowKey == null || !windowKey.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("bounded windowKey required");
        List<Alert> result = new ArrayList<>();
        for (Rule rule : rules) {
            if (!triggered(rule, snapshot)) continue;
            String key = rule.id() + "|" + lane.name() + "|" + windowKey;
            if (!dedup.add(key)) continue;
            Alert pending = new Alert(rule.id(), rule.severity(), lane, rule.action(), DeliveryStatus.ROUTE_UNAVAILABLE);
            DeliveryStatus delivery = route.deliver(pending);
            Alert alert = new Alert(rule.id(), rule.severity(), lane, rule.action(), delivery);
            if (rule.severity() == Severity.CRITICAL) applySafety(rule.action(), lane, killSwitch);
            result.add(alert);
        }
        return List.copyOf(result);
    }

    public List<Rule> inventory() { return rules; }
    public static Route unavailableRoute() { return ignored -> DeliveryStatus.ROUTE_UNAVAILABLE; }

    private static boolean triggered(Rule rule, Snapshot snapshot) {
        if (rule.severity() == Severity.CRITICAL) return snapshot.count(rule.metric()) > 0;
        return switch (rule.id()) {
            case "timeout_rate" -> snapshot.rate("shadow_timeout_total") >= 20.0;
            case "exception_rate" -> snapshot.rate("shadow_exception_total") >= 25.0;
            case "queue_rejection_rate" -> snapshot.rate("shadow_queue_rejection_total") >= 5.0;
            case "late_discard_rate" -> snapshot.rate("shadow_late_discard_total") >= 5.0;
            default -> snapshot.count(rule.metric()) > 0 || snapshot.degradedWarnings().contains(rule.id());
        };
    }

    private static void applySafety(Action action, Rca2RuntimeContracts.Lane lane, Rca2KillSwitch killSwitch) {
        switch (action) {
            case FLAG_OFF, LANE_KILL_SWITCH -> killSwitch.killLane(lane);
            case GLOBAL_SHADOW_DISABLE, CREDENTIAL_REVOKE, NETWORK_ROUTE_REVOKE -> killSwitch.killGlobal();
            case HOLD_ENABLEMENT -> { }
        }
    }

    private static List<Rule> rules() {
        Map<String, Rule> result = new LinkedHashMap<>();
        critical(result, "response_mutation_detected", "shadow_response_mutation_total", Action.GLOBAL_SHADOW_DISABLE);
        critical(result, "database_write_detected", "shadow_database_write_total", Action.GLOBAL_SHADOW_DISABLE);
        critical(result, "cache_write_detected", "shadow_cache_write_total", Action.GLOBAL_SHADOW_DISABLE);
        critical(result, "event_emission_detected", "shadow_event_emission_total", Action.GLOBAL_SHADOW_DISABLE);
        critical(result, "notification_emission_detected", "shadow_notification_emission_total", Action.GLOBAL_SHADOW_DISABLE);
        critical(result, "redaction_failure_detected", "shadow_redaction_failure_total", Action.GLOBAL_SHADOW_DISABLE);
        critical(result, "production_route_detected", "shadow_production_route_detection_total", Action.NETWORK_ROUTE_REVOKE);
        critical(result, "production_identity_detected", "shadow_production_identity_detection_total", Action.GLOBAL_SHADOW_DISABLE);
        critical(result, "authority_mismatch_detected", "shadow_authority_mismatch_total", Action.GLOBAL_SHADOW_DISABLE);
        critical(result, "traffic_ceiling_violation", "traffic_ceiling_violation_total", Action.FLAG_OFF);
        critical(result, "credential_scope_violation", "credential_scope_violation_total", Action.CREDENTIAL_REVOKE);
        warning(result, "timeout_rate", "shadow_timeout_total", 20.0);
        warning(result, "exception_rate", "shadow_exception_total", 25.0);
        warning(result, "queue_rejection_rate", "shadow_queue_rejection_total", 5.0);
        warning(result, "late_discard_rate", "shadow_late_discard_total", 5.0);
        warning(result, "task_age", "shadow_task_age_warning_total", 0.0);
        warning(result, "checkpoint_lag", "shadow_checkpoint_lag_warning_total", 0.0);
        warning(result, "executor_saturation", "shadow_executor_saturation_warning_total", 0.0);
        warning(result, "credential_refresh_failure", "shadow_credential_refresh_failure_total", 0.0);
        warning(result, "allowlist_lookup_failure", "shadow_allowlist_lookup_failure_total", 0.0);
        warning(result, "unexpected_p1_mismatch", "shadow_p1_unexpected_mismatch_total", 0.0);
        warning(result, "unexpected_p2_mismatch", "shadow_p2_unexpected_mismatch_total", 0.0);
        return List.copyOf(result.values());
    }

    private static void critical(Map<String, Rule> target, String id, String metric, Action action) {
        target.put(id, new Rule(id, Severity.CRITICAL, metric, 0.0, action));
    }
    private static void warning(Map<String, Rule> target, String id, String metric, double threshold) {
        target.put(id, new Rule(id, Severity.WARNING, metric, threshold, Action.HOLD_ENABLEMENT));
    }
}
