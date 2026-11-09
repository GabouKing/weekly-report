package com.example.lambda.application;

import com.example.lambda.domain.Evaluation;
import com.example.lambda.domain.TotalEvaluationInDay;
import com.example.lambda.domain.TotalEvaluationUrgency;
import com.example.lambda.domain.Urgency;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReportConsolidatorTest {
    private static final Instant START = Instant.parse("2026-01-01T03:00:00Z");
    private static final Instant END = Instant.parse("2026-01-08T03:00:00Z");
    private final ReportConsolidator consolidator = new ReportConsolidator();

    @Test
    void calculatesTotalAndFractionalAverage() {
        var evaluations = List.of(
                evaluation(4, "2026-01-01T12:00:00Z"),
                evaluation(7, "2026-01-01T13:00:00Z")
        );

        var report = consolidator.execute(evaluations, START, END);

        assertAll(
                () -> assertEquals(START, report.getPeriodStart()),
                () -> assertEquals(END, report.getPeriodEnd()),
                () -> assertEquals(2, report.getTotalEvaluations()),
                () -> assertEquals(5.5, report.getAverageEvaluations(), 0.000001)
        );
    }

    @Test
    void groupsBySaoPauloDayInChronologicalOrder() {
        // UTC midnight is still the previous day in Sao Paulo.
        var evaluations = List.of(
                evaluation(8, "2026-01-02T03:00:00Z"),
                evaluation(5, "2026-01-02T00:00:00.00000Z"),
                evaluation(9, "2026-01-01T15:00:00Z"),
                evaluation(6, "2026-01-02T02:59:59Z")
        );

        var report = consolidator.execute(evaluations, START, END);

        assertEquals(List.of(
                new TotalEvaluationInDay(LocalDate.of(2026, 1, 1), 3),
                new TotalEvaluationInDay(LocalDate.of(2026, 1, 2), 1)
        ), report.getEvaluationsPerDay());
    }

    @Test
    void groupsByUrgencyIncludingClassificationBoundaries() {
        var evaluations = List.of(
                evaluation(0, "2026-01-01T12:00:00Z"),
                evaluation(4, "2026-01-01T12:00:00Z"),
                evaluation(5, "2026-01-01T12:00:00Z"),
                evaluation(6, "2026-01-01T12:00:00Z"),
                evaluation(7, "2026-01-01T12:00:00Z"),
                evaluation(10, "2026-01-01T12:00:00Z")
        );

        var report = consolidator.execute(evaluations, START, END);

        assertEquals(List.of(
                new TotalEvaluationUrgency(Urgency.LOW, 2),
                new TotalEvaluationUrgency(Urgency.MEDIUM, 2),
                new TotalEvaluationUrgency(Urgency.HIGH, 2)
        ), report.getEvaluationsPerUrgency());
    }

    @Test
    void returnsEmptyReportWhenThereAreNoEvaluations() {
        var report = consolidator.execute(List.of(), START, END);

        assertAll(
                () -> assertEquals(0, report.getTotalEvaluations()),
                () -> assertNull(report.getAverageEvaluations()),
                () -> assertTrue(report.getEvaluationsPerDay().isEmpty()),
                () -> assertTrue(report.getEvaluationsPerUrgency().isEmpty())
        );
    }

    private Evaluation evaluation(int score, String date) {
        return new Evaluation(UUID.randomUUID(), "Feedback", score, Instant.parse(date));
    }
}
