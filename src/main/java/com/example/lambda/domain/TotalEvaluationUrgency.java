package com.example.lambda.domain;

import java.util.Objects;

public record TotalEvaluationUrgency(Urgency urgency, Integer totalEvaluations) {
    public TotalEvaluationUrgency(Urgency urgency, Integer totalEvaluations) {
        this.urgency = Objects.requireNonNull(urgency, "Urgency must not be null");
        if (totalEvaluations == null || totalEvaluations < 0) {
            throw new IllegalArgumentException("Total evaluations must be non-negative");
        }
        this.totalEvaluations = totalEvaluations;
    }
}
