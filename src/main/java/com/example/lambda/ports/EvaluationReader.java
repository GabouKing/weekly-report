package com.example.lambda.ports;

import com.example.lambda.domain.Evaluation;

import java.time.Instant;
import java.util.List;

public interface EvaluationReader {
    List<Evaluation> getEvaluationsToReport(Instant startPeriod, Instant endPeriod);
}
