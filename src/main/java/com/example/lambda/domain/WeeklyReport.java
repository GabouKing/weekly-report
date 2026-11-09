package com.example.lambda.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class WeeklyReport {
    private final Instant periodStart;
    private final Instant periodEnd;
    private final String description;
    private final Integer totalEvaluations;
    private final Double averageEvaluations;
    private final List<TotalEvaluationInDay> evaluationsPerDay;
    private final List<TotalEvaluationUrgency> evaluationsPerUrgency;

    public WeeklyReport(Instant periodStart, Instant periodEnd, String description,
                        Integer totalEvaluations, Double averageEvaluations,
                        List<TotalEvaluationInDay> evaluationsPerDay,
                        List<TotalEvaluationUrgency> evaluationsPerUrgency) {
        this.periodStart = Objects.requireNonNull(periodStart, "Period start must not be null");
        this.periodEnd = Objects.requireNonNull(periodEnd, "Period end must not be null");
        if (!periodStart.isBefore(periodEnd)) {
            throw new IllegalArgumentException("Period start must be before period end");
        }
        if (totalEvaluations == null || totalEvaluations < 0) {
            throw new IllegalArgumentException("Total evaluations must be non-negative");
        }
        if (totalEvaluations > 0 && (averageEvaluations == null || !Double.isFinite(averageEvaluations))) {
            throw new IllegalArgumentException("Average must be finite for a non-empty report");
        }
        if (totalEvaluations == 0 && averageEvaluations != null) {
            throw new IllegalArgumentException("Average must be null for an empty report");
        }
        this.description = description;
        this.totalEvaluations = totalEvaluations;
        this.averageEvaluations = averageEvaluations;
        this.evaluationsPerDay = List.copyOf(evaluationsPerDay);
        this.evaluationsPerUrgency = List.copyOf(evaluationsPerUrgency);
    }

    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public String getDescription() { return description; }
    public Integer getTotalEvaluations() { return totalEvaluations; }
    public Double getAverageEvaluations() { return averageEvaluations; }
    public List<TotalEvaluationInDay> getEvaluationsPerDay() { return evaluationsPerDay; }
    public List<TotalEvaluationUrgency> getEvaluationsPerUrgency() { return evaluationsPerUrgency; }
}

