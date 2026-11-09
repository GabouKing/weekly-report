package com.example.lambda.adapters;

import com.example.lambda.domain.TotalEvaluationInDay;
import com.example.lambda.domain.TotalEvaluationUrgency;
import com.example.lambda.domain.Urgency;
import com.example.lambda.domain.WeeklyReport;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XlsxReportGeneratorTest {
    private final XlsxReportGenerator generator = new XlsxReportGenerator();

    @Test
    void preservesTemplateAndUpdatesValuesAndBothChartCaches() throws Exception {
        var report = report(21, 19.0 / 3,
                List.of(new TotalEvaluationInDay(LocalDate.of(2026, 11, 1), 21)),
                List.of(new TotalEvaluationUrgency(Urgency.LOW, 4),
                        new TotalEvaluationUrgency(Urgency.MEDIUM, 7),
                        new TotalEvaluationUrgency(Urgency.HIGH, 10)));

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(generator.generate(report)))) {
            var summary = workbook.getSheet("weekly-report");
            assertEquals(3, workbook.getNumberOfSheets());
            assertEquals("A1:B2", summary.getMergedRegion(0).formatAsString());
            assertTrue(DateUtil.isCellDateFormatted(summary.getRow(3).getCell(0)));
            assertEquals(LocalDate.of(2026, 11, 1),
                    summary.getRow(3).getCell(0).getLocalDateTimeCellValue().toLocalDate());
            assertEquals(21, summary.getRow(6).getCell(0).getNumericCellValue());
            assertEquals(19.0 / 3, summary.getRow(6).getCell(1).getNumericCellValue(), 0.000001);
            assertEquals("0.00", summary.getRow(6).getCell(1).getCellStyle().getDataFormatString());

            var days = workbook.getSheet("Avaliacoes Por Dia");
            assertEquals(7, days.getPhysicalNumberOfRows());
            assertEquals(0, days.getRow(6).getCell(1).getNumericCellValue());
            var urgencies = workbook.getSheet("Avaliacoes Por Urgencia");
            assertEquals("Baixa", urgencies.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Média", urgencies.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Alta", urgencies.getRow(2).getCell(0).getStringCellValue());

            var charts = summary.getDrawingPatriarch().getCharts();
            var daySeries = charts.get(0).getCTChart().getPlotArea().getBarChartArray(0).getSerArray(0);
            assertEquals(7, daySeries.getVal().getNumRef().getNumCache().sizeOfPtArray());
            var urgencySeries = charts.get(1).getCTChart().getPlotArea().getDoughnutChartArray(0).getSerArray(0);
            assertEquals("Média", urgencySeries.getCat().getStrRef().getStrCache().getPtArray(1).getV());
            assertEquals(10, Double.parseDouble(urgencySeries.getVal().getNumRef().getNumCache().getPtArray(2).getV()));
        }
    }

    @Test
    void emptyReportDoesNotRetainSampleValues() throws Exception {
        byte[] bytes = generator.generate(report(0, null, List.of(), List.of()));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(
                bytes))) {
            assertEquals(CellType.BLANK, workbook.getSheet("weekly-report").getRow(6).getCell(1).getCellType());
            var sheet = workbook.getSheet("Avaliacoes Por Urgencia");
            for (int row = 0; row < 3; row++) {
                assertEquals(0, sheet.getRow(row).getCell(1).getNumericCellValue());
            }
        }
    }

    @Test
    void namesFileUsingDescriptionAndSaoPauloDatesWithoutTime() {
        assertEquals("Relatório de avaliação Semanal - 2026-11-01-2026-11-08.xlsx",
                generator.fileName(report(0, null, List.of(), List.of())));
        var utcBoundary = new WeeklyReport(Instant.parse("2026-11-02T01:00:00Z"),
                Instant.parse("2026-11-09T01:00:00Z"), "Relatório", 0, null, List.of(), List.of());
        assertEquals("Relatório - 2026-11-01-2026-11-08.xlsx", generator.fileName(utcBoundary));
    }

    private WeeklyReport report(int total, Double average, List<TotalEvaluationInDay> days,
                                List<TotalEvaluationUrgency> urgencies) {
        return new WeeklyReport(Instant.parse("2026-11-01T03:00:00Z"),
                Instant.parse("2026-11-08T03:00:00Z"), "Relatório de avaliação Semanal",
                total, average, days, urgencies);
    }
}
