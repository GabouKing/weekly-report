package com.example.lambda.adapters;

import com.example.lambda.application.ReportTime;
import com.example.lambda.domain.Urgency;
import com.example.lambda.domain.WeeklyReport;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@ApplicationScoped
public class XlsxReportGenerator {
    private static final String TEMPLATE = "/templates/weekly-report.xlsx";
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");

    public byte[] generate(WeeklyReport report) {
        Objects.requireNonNull(report, "Report must not be null");

        // Cada chamada abre sua propria copia do modelo, preservando estilos,
        // mesclagens, cores, posicao e tipo dos graficos sem compartilhar estado.
        try (InputStream template = Objects.requireNonNull(
                getClass().getResourceAsStream(TEMPLATE), "XLSX template not found");
             XSSFWorkbook workbook = new XSSFWorkbook(template);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            fillSummary(workbook, report);
            fillDays(workbook, report);
            fillUrgencies(workbook, report);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Nao foi possivel gerar o XLSX", exception);
        }
    }

    public String fileName(WeeklyReport report) {
        // As datas do nome usam o fuso de negocio, sem hora ou conversao implicita
        // pelo fuso da maquina. Caracteres invalidos em nomes de arquivo sao removidos.
        String description = Objects.requireNonNull(report.getDescription(), "Description must not be null")
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (description.isBlank()) {
            description = "Relatorio";
        }
        return description + " - " + localDate(report.getPeriodStart())
                + "-" + localDate(report.getPeriodEnd()) + ".xlsx";
    }

    private void fillSummary(XSSFWorkbook workbook, WeeklyReport report) {
        XSSFSheet sheet = workbook.getSheet("weekly-report");
        cell(sheet, 0, 0).setCellValue(report.getDescription());
        cell(sheet, 2, 0).setCellValue("Início");
        cell(sheet, 2, 1).setCellValue("Fim");
        cell(sheet, 3, 0).setCellValue(localDate(report.getPeriodStart()).atStartOfDay());
        cell(sheet, 3, 1).setCellValue(localDate(report.getPeriodEnd()).atStartOfDay());
        cell(sheet, 5, 1).setCellValue("Média das Notas");
        cell(sheet, 6, 0).setCellValue(report.getTotalEvaluations());

        // Numero continua sendo numero no Excel; formatacao nao altera a media real.
        Cell average = cell(sheet, 6, 1);
        CellStyle averageStyle = workbook.createCellStyle();
        averageStyle.cloneStyleFrom(average.getCellStyle());
        averageStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        average.setCellStyle(averageStyle);
        if (report.getAverageEvaluations() == null) {
            average.setBlank();
        } else {
            average.setCellValue(report.getAverageEvaluations());
        }
    }

    private void fillDays(XSSFWorkbook workbook, WeeklyReport report) {
        XSSFSheet sheet = workbook.getSheet("Avaliacoes Por Dia");
        CellStyle dateStyle = sheet.getRow(0).getCell(0).getCellStyle();
        CellStyle countStyle = sheet.getRow(0).getCell(1).getCellStyle();
        clearRows(sheet);

        // Inclui dias sem avaliacoes para o grafico representar todo o intervalo.
        // O fim e exclusivo: meia-noite nao adiciona um dia extra ao grafico.
        Map<LocalDate, Integer> counts = new TreeMap<>();
        LocalDate lastDay = localDate(report.getPeriodEnd().minusNanos(1));
        for (LocalDate day = localDate(report.getPeriodStart()); !day.isAfter(lastDay); day = day.plusDays(1)) {
            counts.put(day, 0);
        }
        report.getEvaluationsPerDay().forEach(item -> counts.put(item.day(), item.totalEvaluations()));
        String[] labels = new String[counts.size()];
        Double[] values = new Double[counts.size()];
        int index = 0;
        for (var entry : counts.entrySet()) {
            Cell date = cell(sheet, index, 0);
            date.setCellValue(entry.getKey().atStartOfDay());
            date.setCellStyle(dateStyle);
            Cell count = cell(sheet, index, 1);
            count.setCellValue(entry.getValue());
            count.setCellStyle(countStyle);
            labels[index] = entry.getKey().format(DAY_LABEL);
            values[index] = entry.getValue().doubleValue();
            index++;
        }
        sheet.setColumnWidth(0, 16 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        updateChart(workbook, 0, sheet, labels, values, "Avaliações por dia");
    }

    private void fillUrgencies(XSSFWorkbook workbook, WeeklyReport report) {
        XSSFSheet sheet = workbook.getSheet("Avaliacoes Por Urgencia");
        // Rotulos sao apenas de apresentacao: enum e regra de classificacao nao mudam.
        Urgency[] order = {Urgency.LOW, Urgency.MEDIUM, Urgency.HIGH};
        String[] labels = {"Baixa", "Média", "Alta"};
        Double[] values = new Double[order.length];
        for (int index = 0; index < order.length; index++) {
            Urgency urgency = order[index];
            int total = report.getEvaluationsPerUrgency().stream()
                    .filter(item -> item.urgency() == urgency)
                    .mapToInt(item -> item.totalEvaluations()).sum();
            cell(sheet, index, 0).setCellValue(labels[index]);
            cell(sheet, index, 1).setCellValue(total);
            values[index] = (double) total;
        }
        sheet.setColumnWidth(0, 16 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        updateChart(workbook, 1, sheet, labels, values, "Avaliações por urgência");
    }

    private void updateChart(XSSFWorkbook workbook, int index, XSSFSheet source,
                             String[] labels, Double[] values, String title) {
        XSSFChart chart = workbook.getSheet("weekly-report").getDrawingPatriarch().getCharts().get(index);
        chart.setTitleText(title);
        XDDFChartData data = chart.getChartSeries().get(0);
        String categoryRange = new CellRangeAddress(0, labels.length - 1, 0, 0)
                .formatAsString(source.getSheetName(), true);
        String valueRange = new CellRangeAddress(0, values.length - 1, 1, 1)
                .formatAsString(source.getSheetName(), true);

        // Atualiza referencias E caches: os graficos nao ficam presos aos numeros
        // da planilha de exemplo e podem ser exibidos sem recalculo manual.
        data.getSeries(0).replaceData(
                XDDFDataSourcesFactory.fromArray(labels, categoryRange),
                XDDFDataSourcesFactory.fromArray(values, valueRange));
        chart.plot(data);
    }

    private Cell cell(XSSFSheet sheet, int rowIndex, int column) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        return row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
    }

    private void clearRows(XSSFSheet sheet) {
        for (int index = sheet.getLastRowNum(); index >= 0; index--) {
            Row row = sheet.getRow(index);
            if (row != null) {
                sheet.removeRow(row);
            }
        }
    }

    private LocalDate localDate(java.time.Instant instant) {
        return instant.atZone(ReportTime.ZONE).toLocalDate();
    }
}
