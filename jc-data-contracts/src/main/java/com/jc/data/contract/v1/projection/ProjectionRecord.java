package com.jc.data.contract.v1.projection;

import java.util.Map;

public interface ProjectionRecord {
    String recordRef();

    String projectionName();

    String subjectRef();

    long sourceEventCount();

    String projectionRecordFingerprint();

    Map<String, Object> canonicalFields();
}
