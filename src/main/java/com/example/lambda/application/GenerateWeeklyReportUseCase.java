package com.example.lambda.application;

import com.example.lambda.adapters.XlsxReportGenerator;
import com.example.lambda.domain.Evaluation;
import com.example.lambda.ports.EvaluationReader;
import com.example.lambda.ports.ReportPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class GenerateWeeklyReportUseCase {

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
        List<Evaluation> evaluations = evaluationReader.getEvaluationsToReport(periodStart, periodEnd);

        var weeklyReport = reportConsolidator.execute(evaluations, periodStart, periodEnd);
        var spreadsheet = xlsxReportGenerator.generate(weeklyReport);
        String fileName = xlsxReportGenerator.fileName(weeklyReport);
        reportPublisher.publish(spreadsheet, fileName);
    }
}
