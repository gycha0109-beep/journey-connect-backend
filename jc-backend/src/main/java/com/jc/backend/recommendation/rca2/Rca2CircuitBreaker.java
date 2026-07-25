package com.jc.backend.recommendation.rca2;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

public final class Rca2CircuitBreaker {
    public static final int MINIMUM_SAMPLE_COUNT = 20;
    public static final int FAILURE_RATE_PERCENT = 25;
    public static final int TIMEOUT_RATE_PERCENT = 20;
    public static final Duration OPEN_DURATION = Duration.ofSeconds(60);
    public static final int HALF_OPEN_PROBES = 2;

    private enum Outcome { SUCCESS, FAILURE, TIMEOUT }
    private final LongSupplier nanoTime;
    private final Deque<Outcome> samples = new ArrayDeque<>();
    private Rca2RuntimeContracts.BreakerState state = Rca2RuntimeContracts.BreakerState.CLOSED;
    private long openedAt;
    private int halfOpenSuccesses;
    private int halfOpenPermits;

    public Rca2CircuitBreaker(LongSupplier nanoTime) { this.nanoTime = nanoTime; }
    public static Rca2CircuitBreaker system() { return new Rca2CircuitBreaker(System::nanoTime); }

    public synchronized boolean permit() {
        if (state == Rca2RuntimeContracts.BreakerState.OPEN) {
            if (nanoTime.getAsLong() - openedAt < OPEN_DURATION.toNanos()) return false;
            state = Rca2RuntimeContracts.BreakerState.HALF_OPEN;
            halfOpenSuccesses = 0;
            halfOpenPermits = HALF_OPEN_PROBES;
        }
        if (state == Rca2RuntimeContracts.BreakerState.HALF_OPEN) {
            if (halfOpenPermits <= 0) return false;
            halfOpenPermits--;
        }
        return true;
    }

    public synchronized void success() {
        if (state == Rca2RuntimeContracts.BreakerState.HALF_OPEN) {
            halfOpenSuccesses++;
            if (halfOpenSuccesses >= HALF_OPEN_PROBES) close();
            return;
        }
        add(Outcome.SUCCESS);
    }

    public synchronized void failure(boolean timeout) {
        if (state == Rca2RuntimeContracts.BreakerState.HALF_OPEN) {
            open();
            return;
        }
        add(timeout ? Outcome.TIMEOUT : Outcome.FAILURE);
        evaluate();
    }

    public synchronized Rca2RuntimeContracts.BreakerState state() {
        if (state == Rca2RuntimeContracts.BreakerState.OPEN
                && nanoTime.getAsLong() - openedAt >= OPEN_DURATION.toNanos()) {
            state = Rca2RuntimeContracts.BreakerState.HALF_OPEN;
            halfOpenSuccesses = 0;
            halfOpenPermits = HALF_OPEN_PROBES;
        }
        return state;
    }

    private void add(Outcome outcome) {
        samples.addLast(outcome);
        while (samples.size() > 100) samples.removeFirst();
    }

    private void evaluate() {
        if (samples.size() < MINIMUM_SAMPLE_COUNT) return;
        long failures = samples.stream().filter(value -> value == Outcome.FAILURE || value == Outcome.TIMEOUT).count();
        long timeouts = samples.stream().filter(value -> value == Outcome.TIMEOUT).count();
        if (failures * 100 >= (long) FAILURE_RATE_PERCENT * samples.size()
                || timeouts * 100 >= (long) TIMEOUT_RATE_PERCENT * samples.size()) open();
    }

    private void open() {
        state = Rca2RuntimeContracts.BreakerState.OPEN;
        openedAt = nanoTime.getAsLong();
        halfOpenPermits = 0;
        halfOpenSuccesses = 0;
    }

    private void close() {
        state = Rca2RuntimeContracts.BreakerState.CLOSED;
        samples.clear();
        halfOpenPermits = 0;
        halfOpenSuccesses = 0;
    }
}
