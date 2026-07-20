package com.jc.recommendation.support;

import java.util.Comparator;

public final class Utf16CodeUnitComparator {
    public static final Comparator<String> ASCENDING = String::compareTo;

    private Utf16CodeUnitComparator() {
    }
}
