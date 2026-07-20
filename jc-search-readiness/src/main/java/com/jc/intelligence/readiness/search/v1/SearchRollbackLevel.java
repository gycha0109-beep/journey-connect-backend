package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchRollbackLevel implements WireValue {
    LEVEL_0_SAMPLE_ZERO("level_0_sample_zero"), LEVEL_1_MODE_DISABLED("level_1_mode_disabled"),
    LEVEL_2_NO_OP_BEAN("level_2_no_op_bean"), LEVEL_3_REMOVE_HOOK_CALL("level_3_remove_hook_call"),
    LEVEL_4_REMOVE_MODULE_DEPENDENCY("level_4_remove_module_dependency");
    private final String wireValue;
    SearchRollbackLevel(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
