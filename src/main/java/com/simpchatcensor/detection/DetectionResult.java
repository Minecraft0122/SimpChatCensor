package com.simpchatcensor.detection;

import com.simpchatcensor.config.DetectionAction;

public record DetectionResult(
        DetectionType type,
        String detectedText,
        String rule,
        DetectionAction action,
        String detail
) {
    public static DetectionResult of(
            DetectionType type,
            String detectedText,
            String rule,
            DetectionAction action,
            String detail
    ) {
        return new DetectionResult(
                type,
                detectedText == null ? "" : detectedText,
                rule == null ? "" : rule,
                action,
                detail == null ? "" : detail
        );
    }
}
