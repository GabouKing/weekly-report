package com.example.lambda.application;

import com.example.lambda.adapters.XlsxReportGenerator;
import com.example.lambda.domain.Evaluation;
import com.example.lambda.ports.EvaluationReader;
import com.example.lambda.ports.ReportPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class GenerateWeeklyReportUseCase {
    private static final Logger LOG = Logger.getLogger(GenerateWeeklyReportUseCase.class);

    private final ReportConsolidator reportConsolidator;
    private final EvaluationReader evaluationReader;
    private final XlsxReportGenerator xlsxReportGenerator;
    private final ReportPublisher reportPublisher;

    public GenerateWeeklyReportUseCase(ReportConsolidator reportConsolidator,
                                      EvaluationReader evaluationReader,
                                      XlsxReportGenerator xlsxReportGenerator,
                                      ReportPublisher reportPublisher)
    {
        this.reportConsolidator = reportConsolidator;
        this.evaluationReader = evaluationReader;
        this.xlsxReportGenerator = xlsxReportGenerator;
        this.reportPublisher = reportPublisher;
    }

    public void execute() {
        Instant periodEnd = Instant.now();

        Instant periodStart = periodEnd
                .atZone(ReportTime.ZONE)
                .toLocalDate()
                .minusWeeks(1)
                .atStartOfDay(ReportTime.ZONE)
                .toInstant();
        LOG.infof("Iniciando leitura das avaliacoes. inicioInclusivo=%s fimExclusivo=%s fuso=%s",
                periodStart, periodEnd, ReportTime.ZONE);
        List<Evaluation> evaluations = evaluationReader.getEvaluationsToReport(periodStart, periodEnd);

        LOG.infof("Iniciando consolidacao. totalAvaliacoes=%d", evaluations.size());
        long consolidationStartedAt = System.nanoTime();
        var weeklyReport = reportConsolidator.execute(evaluations, periodStart, periodEnd);
        LOG.infof("Consolidacao concluida. totalAvaliacoes=%d media=%s duracaoMs=%d",
                weeklyReport.getTotalEvaluations(), weeklyReport.getAverageEvaluations(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - consolidationStartedAt));

        LOG.info("Iniciando geracao do XLSX.");
        long generationStartedAt = System.nanoTime();
        var spreadsheet = xlsxReportGenerator.generate(weeklyReport);
        LOG.infof("XLSX gerado. tamanhoBytes=%d duracaoMs=%d", spreadsheet.length,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - generationStartedAt));
        String fileName = xlsxReportGenerator.fileName(weeklyReport);
        reportPublisher.publish(spreadsheet, fileName);
    }
}
