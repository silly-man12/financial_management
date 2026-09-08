package com.example.financial_management.services;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.example.financial_management.model.report.response.DailyReportResponse;
import com.example.financial_management.model.report.response.DailyReportResponseItem;
import com.example.financial_management.model.report.response.MonthlyReportResponse;
import com.example.financial_management.model.report.response.MonthlyReportResponseItem;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PdfReportService {

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0");

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0";
        return MONEY_FORMAT.format(amount);
    }

    private BaseFont getUnicodeBaseFont() {
        try {
            return BaseFont.createFont("C:/Windows/Fonts/arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (Exception e) {
            try {
                return BaseFont.createFont("C:/Windows/Fonts/times.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ex) {
                try {
                    return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                } catch (Exception ex2) {
                    throw new RuntimeException("Error initializing PDF font", ex2);
                }
            }
        }
    }

    public ResponseEntity<byte[]> exportMonthlyReportByMonthPDF(String monthLabel, DailyReportResponse data) {
        BaseFont baseFont = getUnicodeBaseFont();
        Font titleFont = new Font(baseFont, 16, Font.BOLD);
        Font contentFont = new Font(baseFont, 10, Font.NORMAL);
        Font summaryFont = new Font(baseFont, 12, Font.BOLD);
        Font headerFont = new Font(baseFont, 11, Font.BOLD);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, out);
            doc.open();

            // --- Tiêu đề ---
            Paragraph title = new Paragraph("Báo cáo tháng - " + monthLabel, titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            doc.add(Chunk.NEWLINE);

            // --- Bảng dữ liệu ---
            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            addTableHeader(table, new String[] { "Ngày", "Thu nhập", "Chi tiêu" }, headerFont);

            for (DailyReportResponseItem item : data.getItems()) {
                table.addCell(new Phrase(item.getDate().toString(), contentFont));
                table.addCell(new Phrase(formatMoney(item.getIncome()), contentFont));
                table.addCell(new Phrase(formatMoney(item.getExpense()), contentFont));
            }

            doc.add(table);
            doc.add(Chunk.NEWLINE);

            // --- Tổng kết ---
            Paragraph summaryTitle = new Paragraph("Tổng kết", summaryFont);
            summaryTitle.setAlignment(Element.ALIGN_LEFT);
            doc.add(summaryTitle);
            doc.add(Chunk.NEWLINE);

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(60);
            summaryTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            summaryTable.setWidths(new float[] { 3, 2 });

            addSummaryRow(summaryTable, "Tổng thu nhập:", formatMoney(data.getTotalIncome()), contentFont);
            addSummaryRow(summaryTable, "Tổng chi tiêu:", formatMoney(data.getTotalExpense()), contentFont);
            addSummaryRow(summaryTable, "Số dư ròng (Net):", formatMoney(data.getNet()), summaryFont);

            doc.add(summaryTable);

            doc.close();
            String safeMonth = monthLabel != null ? monthLabel.trim().replace("/", "-").replace("\\", "-") : "month";
            return buildPdfResponse(out.toByteArray(), "report-" + safeMonth + ".pdf");
        } catch (Exception e) {
            log.error("Lỗi khi tạo PDF báo cáo tháng: {}", e.getMessage(), e);
            throw new RuntimeException("Error generating monthly PDF", e);
        }
    }

    public ResponseEntity<byte[]> exportMonthlyReportByYearPDF(int year, MonthlyReportResponse data) {
        BaseFont baseFont = getUnicodeBaseFont();
        Font titleFont = new Font(baseFont, 16, Font.BOLD);
        Font contentFont = new Font(baseFont, 10, Font.NORMAL);
        Font summaryFont = new Font(baseFont, 12, Font.BOLD);
        Font headerFont = new Font(baseFont, 11, Font.BOLD);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph("Báo cáo năm - " + year, titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            doc.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            addTableHeader(table, new String[] { "Tháng", "Thu nhập", "Chi tiêu", "Net" }, headerFont);

            // Biến cộng dồn
            BigDecimal totalIncome = BigDecimal.ZERO;
            BigDecimal totalExpense = BigDecimal.ZERO;
            BigDecimal totalNet = BigDecimal.ZERO;

            for (MonthlyReportResponseItem item : data.getItems()) {
                table.addCell(new Phrase(String.valueOf(item.getMonth()), contentFont));
                table.addCell(new Phrase(formatMoney(item.getIncome()), contentFont));
                table.addCell(new Phrase(formatMoney(item.getExpense()), contentFont));
                table.addCell(new Phrase(formatMoney(item.getNet()), contentFont));

                totalIncome = totalIncome.add(item.getIncome());
                totalExpense = totalExpense.add(item.getExpense());
                totalNet = totalNet.add(item.getNet());
            }

            // Thêm dòng tổng cuối bảng
            PdfPCell totalCell = new PdfPCell(new Phrase("TỔNG CỘNG", summaryFont));
            totalCell.setColspan(1);
            totalCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(totalCell);
            table.addCell(new Phrase(formatMoney(totalIncome), summaryFont));
            table.addCell(new Phrase(formatMoney(totalExpense), summaryFont));
            table.addCell(new Phrase(formatMoney(totalNet), summaryFont));

            doc.add(table);
            doc.close();
            return buildPdfResponse(out.toByteArray(), "report-" + year + ".pdf");
        } catch (Exception e) {
            log.error("Lỗi khi tạo PDF báo cáo năm: {}", e.getMessage(), e);
            throw new RuntimeException("Error generating yearly PDF", e);
        }
    }

    private void addSummaryRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addTableHeader(PdfPTable table, String[] headers, Font font) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private ResponseEntity<byte[]> buildPdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
