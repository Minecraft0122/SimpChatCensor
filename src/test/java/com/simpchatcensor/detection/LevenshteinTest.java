package com.simpchatcensor.detection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevenshteinTest {
    @Test
    void detectsNearMissesWithinThreshold() {
        assertTrue(Levenshtein.withinDistance("fack", "fuck", 1));
        assertFalse(Levenshtein.withinDistance("friendly", "fuck", 1));
    }
}
