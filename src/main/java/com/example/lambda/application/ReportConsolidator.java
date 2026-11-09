package com.example.lambda.application;

import com.example.lambda.domain.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
public class ReportConsolidator {

    public WeeklyReport execute(List<Evaluation> evaluations, Instant periodStart, Instant periodEnd) {
        return new WeeklyReport(
                periodStart,
                periodEnd,
                "Relatório de avaliação Semanal",
                evaluations.size(),
                calculateAverage(evaluations),
                groupByDay(evaluations),
                groupByUrgency(evaluations)
        );
    }

    private Double calculateAverage(List<Evaluation> evaluations) {
        var average = evaluations.stream()
                .mapToInt(Evaluation::getEvaluation)
                .average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    private List<TotalEvaluationInDay> groupByDay(List<Evaluation> evaluations) {
        Map<LocalDate, Integer> totalsPerDay = new TreeMap<>();
        for (Evaluation evaluation : evaluations) {
            LocalDate day = evaluation.getCreatedDate()
                    .atZone(ReportTime.ZONE)
                    .toLocalDate();
            totalsPerDay.merge(day, 1, Integer::sum);
        }

        return totalsPerDay.entrySet().stream()
                        .map(entry -> new TotalEvaluationInDay(
                                entry.getKey(),
                                entry.getValue()
                        ))
                        .toList();
    }

    private List<TotalEvaluationUrgency> groupByUrgency(List<Evaluation> evaluations) {
        Map<Urgency, Integer> totalsPerUrgency = new TreeMap<>();
        for (Evaluation evaluation : evaluations) {
            Urgency urgency = Urgency.classify(evaluation.getEvaluation());
            totalsPerUrgency.merge(urgency, 1, Integer::sum);
        }

        return totalsPerUrgency.entrySet().stream()
                        .map(entry -> new TotalEvaluationUrgency(
                                entry.getKey(),
                                entry.getValue()
                        ))
                        .toList();

    }
}
