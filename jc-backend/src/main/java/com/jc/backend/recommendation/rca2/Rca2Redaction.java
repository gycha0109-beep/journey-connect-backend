package com.jc.backend.recommendation.rca2;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Rca2Redaction {
    private static final List<String> FORBIDDEN = List.of(
            "user:", "subject:", "session:", "run:", "exposure:", "jdbc:", "password", "token", "endpoint");

    public Rca2RuntimeContracts.Evidence verify(Rca2RuntimeContracts.Evidence evidence) {
        for (Object value : List.of(evidence.hashedRequestRef(), evidence.primaryDigest(), evidence.candidateDigest(),
                evidence.classification(), evidence.sourceCheckpoint(), evidence.candidateCheckpoint(),
                evidence.lineageFingerprint(), evidence.timeoutClass(), evidence.errorClass())) {
            String text = String.valueOf(value).toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN) {
                if (text.contains(forbidden)) throw new IllegalArgumentException("redaction contract failed");
            }
        }
        return evidence;
    }

    public static final class StructuredLogSink implements Consumer<Rca2RuntimeContracts.Evidence> {
        private static final Logger log = LoggerFactory.getLogger(StructuredLogSink.class);
        @Override public void accept(Rca2RuntimeContracts.Evidence evidence) {
            log.info("rca2_shadow lane={} contractVersion={} queryRegistryVersion={} primaryDigest={} candidateDigest={} classification={} sourceCheckpoint={} candidateCheckpoint={} lineageFingerprint={} shadowLatencyBucket={} primaryLatencyBucket={} timeoutClass={} errorClass={} flagVersion={} deploymentVersion={} testedSha={}",
                    evidence.lane(), evidence.contractVersion(), evidence.queryRegistryVersion(), evidence.primaryDigest(),
                    evidence.candidateDigest(), evidence.classification(), evidence.sourceCheckpoint(),
                    evidence.candidateCheckpoint(), evidence.lineageFingerprint(), evidence.shadowLatencyBucket(),
                    evidence.primaryLatencyBucket(), evidence.timeoutClass(), evidence.errorClass(), evidence.flagVersion(),
                    evidence.deploymentVersion(), evidence.testedSha());
        }
    }
}
