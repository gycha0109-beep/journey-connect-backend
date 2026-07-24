package com.jc.backend.recommendation.dataadoption;

import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.RequirementClassification.AUTHORITY_PROTECTED;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.RequirementClassification.DERIVABLE;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.RequirementClassification.EXACT;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.RequirementClassification.INCOMPATIBLE;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.RequirementClassification.MISSING;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.finding;

import java.util.List;

public final class CompatibilityMatricesV1 {
    private CompatibilityMatricesV1() {
    }

    public static List<RecommendationDataConsumerContracts.CompatibilityFinding> p1() {
        return List.of(
                finding("subject reference", EXACT, "Data subject reference is explicit but requires restricted identity binding to current user identity."),
                finding("projection as-of time", EXACT, "Projection as-of maps to deterministic P1 reference time."),
                finding("source checkpoint", DERIVABLE, "Checkpoint can prove source coverage but is not the current event query itself."),
                finding("profile schema version", EXACT, "recommendation-profile-input-v1 is explicit."),
                finding("projection policy version", EXACT, "recommendation-profile-projection-policy-v1 is explicit."),
                finding("7/30/90-day window", EXACT, "The Data projection admits only the three approved windows."),
                finding("interaction counts", DERIVABLE, "Counts are available at aggregate grain."),
                finding("recent region references", DERIVABLE, "Ranked recent region references are available without current feature transforms."),
                finding("recent content references", DERIVABLE, "Ranked recent content references are available."),
                finding("recent tag references", DERIVABLE, "Ranked recent tag references are available without vocabulary conversion."),
                finding("engagement signals", DERIVABLE, "Positive aggregates are available."),
                finding("negative signals", DERIVABLE, "Negative aggregates are available."),
                finding("source event count", DERIVABLE, "Count is available but not event partition evidence."),
                finding("source lineage", EXACT, "Source lineage fingerprint is explicit."),
                finding("record fingerprints", EXACT, "Data record fingerprint is explicit for the Data schema."),
                finding("event-grain ordering", MISSING, "Aggregate projection does not contain ordered BehaviorProfileEvent rows."),
                finding("event timestamps", MISSING, "Aggregate projection does not preserve every event timestamp."),
                finding("explicit preferences", MISSING, "recommendation_user_preference semantics are absent."),
                finding("BehaviorProfileEvent partition behavior", MISSING, "Input/accepted/ignored/duplicate partitions are not reproduced."),
                finding("feature-vocabulary transform", MISSING, "Current P1 region/tag vocabulary transformation is Intelligence-owned."),
                finding("decay inputs", MISSING, "Per-event age inputs required for decay are unavailable."),
                finding("saturation inputs", MISSING, "Current per-feature saturation inputs are not provably equivalent."),
                finding("profile snapshot fingerprint semantics", AUTHORITY_PROTECTED, "Data record fingerprints cannot replace current profile snapshot fingerprint semantics."),
                finding("aggregate-to-event conversion", INCOMPATIBLE, "Synthetic BehaviorProfileEvent reconstruction from counts is forbidden."));
    }

    public static List<RecommendationDataConsumerContracts.CompatibilityFinding> p2() {
        return List.of(
                finding("experiment", EXACT, "Experiment reference is explicit."),
                finding("experiment version", EXACT, "Experiment version is explicit."),
                finding("variant", EXACT, "Consumer restricts variants to baseline or treatment."),
                finding("P2 exposure reference", EXACT, "Exposure reference is explicit and authority-gated."),
                finding("bound recommendation run", EXACT, "Run reference is explicit and compared to the exposure binding."),
                finding("subject", EXACT, "Subject is explicit but requires restricted identity binding."),
                finding("session", EXACT, "Session reference is explicit and binding-checked."),
                finding("exposure timestamp", EXACT, "Exposure timestamp is explicit."),
                finding("604800-second outcome window", EXACT, "Only the protected seven-day window is admitted."),
                finding("click/like/save/share", EXACT, "Only protected engagement event families are admitted."),
                finding("fallback observed", EXACT, "Fallback is accepted only from the bound exposed run."),
                finding("outcome event references", EXACT, "Outcome evidence references are explicit."),
                finding("checkpoint/source count/lineage", EXACT, "Projection evidence fields are explicit."),
                finding("stale unexposed assignment", AUTHORITY_PROTECTED, "Current stale-assignment filtering requires migration equivalence."),
                finding("one-observation dedupe", AUTHORITY_PROTECTED, "Experiment/version/subject dedupe remains a protected migration dimension."),
                finding("canonical dataset bytes/hash", AUTHORITY_PROTECTED, "recommendation-evaluation-dataset-v1 bytes and hash remain authoritative."),
                finding("evaluation/release evidence", AUTHORITY_PROTECTED, "Existing evaluation and release evidence is immutable."));
    }
}
