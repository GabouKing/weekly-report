package com.example.lambda.adapters;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.lambda.application.GenerateWeeklyReportUseCase;
import jakarta.inject.Named;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.concurrent.TimeUnit;

@Named("weekly-report")
public class WeeklyReportLambdaHandler implements RequestHandler<Object, Void> {
    private static final Logger LOG = Logger.getLogger(WeeklyReportLambdaHandler.class);

    private final GenerateWeeklyReportUseCase generateWeeklyReportUseCase;

    public WeeklyReportLambdaHandler(GenerateWeeklyReportUseCase generateWeeklyReportUseCase) {
        this.generateWeeklyReportUseCase = generateWeeklyReportUseCase;
    }

    @Override
    public Void handleRequest(Object input, Context context) {
        String requestId = context.getAwsRequestId();
        Object previousRequestId = MDC.get("requestId");
        long startedAt = System.nanoTime();
        MDC.put("requestId", requestId);
        try {
            LOG.infof("Iniciando geracao do relatorio semanal. requestId=%s", requestId);
            generateWeeklyReportUseCase.execute();
            LOG.infof("Relatorio semanal gerado e publicado. requestId=%s duracaoMs=%d",
                    requestId, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            return null;
        } catch (RuntimeException exception) {
            LOG.errorf(exception, "Falha na geracao do relatorio semanal. requestId=%s duracaoMs=%d",
                    requestId, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            throw exception;
        } finally {
            // A Lambda pode reutilizar a thread em outra invocacao.
            if (previousRequestId == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previousRequestId);
            }
        }
    }
}
