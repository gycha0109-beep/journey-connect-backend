package com.jc.backend.recommendation.rca2;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Test-only provider. Spring production profiles never register this provider. */
public final class Rca2NonProductionTestCredentialProvider implements Rca2WorkloadCredentialProvider {
    private final AtomicReference<Lease> current;
    private final Function<Instant, Lease> refresher;
    private volatile boolean revoked;

    public Rca2NonProductionTestCredentialProvider(Lease initial, Function<Instant, Lease> refresher) {
        current = new AtomicReference<>(initial);
        this.refresher = Objects.requireNonNull(refresher, "refresher");
    }

    @Override public Optional<Lease> current(Instant now) {
        return revoked ? Optional.empty() : Optional.ofNullable(current.get());
    }

    @Override public RefreshResult refresh(Instant now) {
        if (revoked) return new RefreshResult(Status.REVOKED, "missing");
        try {
            Lease next = refresher.apply(now);
            current.set(next);
            return new RefreshResult(Rca2WorkloadCredentialProvider.validate(next, now), next.credentialIdHash());
        } catch (RuntimeException exception) {
            return new RefreshResult(Status.REFRESH_FAILED, "missing");
        }
    }

    @Override public void revoke(String credentialIdHash, Instant now) {
        revoked = true;
        Lease lease = current.getAndSet(null);
        if (lease != null) lease.close();
    }

    @Override public boolean externalReady() { return false; }
}
