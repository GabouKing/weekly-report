package com.example.lambda.domain;

import java.time.LocalDate;
import java.util.Objects;

public record TotalEvaluationInDay(LocalDate day, Integer totalEvaluations) {
    public TotalEvaluationInDay(LocalDate day, Integer totalEvaluations) {
        this.day = Objects.requireNonNull(day, "Day must not be null");
        if (totalEvaluations == null || totalEvaluations < 0) {
            throw new IllegalArgumentException("Total evaluations must be non-negative");
        }
        this.totalEvaluations = totalEvaluations;
    }
}
