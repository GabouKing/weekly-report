package com.example.lambda.domain;

public enum Urgency {
    LOW,
    MEDIUM,
    HIGH;

    public static Urgency classify(Integer evaluation) {
        return switch (evaluation) {
            case null -> throw new IllegalArgumentException("Evaluation must not be null");
            case Integer ev when ev < 5 -> HIGH;
            case Integer ev when ev < 7 -> MEDIUM;
            default -> LOW;
        };
    }
}
