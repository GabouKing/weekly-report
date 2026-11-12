package com.example.lambda.adapters;

import com.example.lambda.domain.Evaluation;
import com.example.lambda.ports.EvaluationReader;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DynamoDbEvaluationReader implements EvaluationReader {
    private static final Logger LOG = Logger.getLogger(DynamoDbEvaluationReader.class);
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
        long startedAt = System.nanoTime();
        int pages = 0;
        long scannedItems = 0;
        LOG.infof("Consultando DynamoDB. tabela=%s", tableName);
        var request = ScanRequest.builder()
                .tableName(tableName).build();

        List<Evaluation> evaluations = new ArrayList<>();
        for (var page : dynamoDb.scanPaginator(request)){
            pages++;
            scannedItems += page.items().size();
            int selectedBeforePage = evaluations.size();
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
            LOG.debugf("Pagina DynamoDB processada. tabela=%s pagina=%d itensLidos=%d selecionados=%d",
                    tableName, pages, page.items().size(), evaluations.size() - selectedBeforePage);
        }
        LOG.infof("Leitura DynamoDB concluida. tabela=%s paginas=%d itensLidos=%d selecionados=%d duracaoMs=%d",
                tableName, pages, scannedItems, evaluations.size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        return evaluations;
    }
}
