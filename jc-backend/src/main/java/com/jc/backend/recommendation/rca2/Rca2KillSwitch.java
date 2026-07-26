package com.jc.backend.recommendation.rca2;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Rca2KillSwitch {
    private final AtomicBoolean global = new AtomicBoolean(false);
    private final Map<Rca2RuntimeContracts.Lane, AtomicBoolean> lanes = new EnumMap<>(Rca2RuntimeContracts.Lane.class);

    public Rca2KillSwitch() {
        for (var lane : Rca2RuntimeContracts.Lane.values()) lanes.put(lane, new AtomicBoolean(false));
    }

    public boolean globalKilled() { return global.get(); }
    public boolean laneKilled(Rca2RuntimeContracts.Lane lane) { return lanes.get(lane).get(); }
    public void killGlobal() { global.set(true); }
    public void restoreGlobal() { global.set(false); }
    public void killLane(Rca2RuntimeContracts.Lane lane) { lanes.get(lane).set(true); }
    public void restoreLane(Rca2RuntimeContracts.Lane lane) { lanes.get(lane).set(false); }
    public void failClosed() { killGlobal(); }
}
