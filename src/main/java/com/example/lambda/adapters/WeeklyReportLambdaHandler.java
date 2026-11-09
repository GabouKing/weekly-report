package com.example.lambda.adapters;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.lambda.application.GenerateWeeklyReportUseCase;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

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
        LOG.infof("Iniciando geracao do relatorio semanal. requestId=%s", requestId);
        generateWeeklyReportUseCase.execute();
        LOG.infof("Relatorio semanal gerado e publicado. requestId=%s", requestId);
        return null;
    }
}
