package com.example.lambda.domain;

import java.time.Instant;
import java.util.UUID;

public class Evaluation {
    private final UUID id;
    private final String description;
    private final Integer evaluation;
    private final Instant createdDate;

    public Evaluation(UUID id, String description, Integer evaluation, Instant createdDate) {
        this.id = id;
        this.description = description;
        this.evaluation = evaluation;
        this.createdDate = createdDate;
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public Integer getEvaluation() { return evaluation; }
    public Instant getCreatedDate() { return createdDate; }
}
