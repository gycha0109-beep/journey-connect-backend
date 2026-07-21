package com.jc.data.contract.v1.compatibility;

public enum CompatibilityClassification {
    COMPATIBLE,
    COMPATIBLE_WITH_IGNORED_OPTIONAL_FIELDS,
    INCOMPATIBLE_SCHEMA,
    INCOMPATIBLE_REQUIRED_ENUM,
    UNSUPPORTED_CONSUMER_VERSION,
    MIGRATION_REQUIRED
}
