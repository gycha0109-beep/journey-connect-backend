package com.jc.data.contract.v1.fingerprint;

import com.jc.data.contract.v1.version.Versions;

public record FingerprintRequestV1(
        Versions.CanonicalizationVersion canonicalizationVersion,
        byte[] canonicalBytes) {
    public FingerprintRequestV1 {
        canonicalBytes = canonicalBytes == null ? null : canonicalBytes.clone();
    }

    @Override
    public byte[] canonicalBytes() {
        return canonicalBytes == null ? null : canonicalBytes.clone();
    }
}
