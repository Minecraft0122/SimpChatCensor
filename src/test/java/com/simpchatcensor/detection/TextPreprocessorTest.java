package com.simpchatcensor.detection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextPreprocessorTest {
    @Test
    void advancedPreprocessNormalizesLeetAccentsAndSpaces() {
        assertEquals("fuck", TextPreprocessor.advanced("f u c k"));
        assertEquals("ass", TextPreprocessor.advanced("@ $ $"));
        assertEquals("angel", TextPreprocessor.advanced("Áng3l"));
    }

    @Test
    void tokenizesNormalizedText() {
        var tokens = TextPreprocessor.tokens("hello, 混蛋!", true);
        assertTrue(tokens.contains("混蛋"), tokens.toString());
    }
}
