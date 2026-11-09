package com.example.lambda.adapters;

import com.example.lambda.domain.Evaluation;
import com.example.lambda.ports.EvaluationReader;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DynamoDbEvaluationReader implements EvaluationReader {
    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoDbEvaluationReader(
            DynamoDbClient dynamoDb,
            @ConfigProperty(name = "reports.evaluations-table") String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    @Override
    public List<Evaluation> getEvaluationsToReport(Instant startPeriod, Instant endPeriod) {
        var request = ScanRequest.builder()
                .tableName(tableName).build();

        List<Evaluation> evaluations = new ArrayList<>();
        for (var page : dynamoDb.scanPaginator(request)){
            for(var item : page.items()){
                Instant date = Instant.parse(item.get("data").s());

                if(!date.isBefore(startPeriod) && date.isBefore(endPeriod)){
                    evaluations.add(new Evaluation(
                            UUID.fromString(item.get("id").s()),
                            item.get("descricao").s(),
                            Integer.valueOf(item.get("nota").n()),
                            date
                            ));
                }
            }
        }
        return evaluations;
    }
}
