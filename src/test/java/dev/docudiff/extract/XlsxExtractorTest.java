package dev.docudiff.extract;

import dev.docudiff.document.SheetCell;
import dev.docudiff.document.SheetModel;
import dev.docudiff.document.SheetRow;
import dev.docudiff.document.WorkbookModel;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XlsxExtractorTest {

    @Test
    @DisplayName("a one-cell sheet extracts one sheet with one row")
    void oneCellSheetExtractsOneRow() throws Exception {
        byte[] xlsxBytes = workbookWithLiteralCell("hello");

        WorkbookModel workbook = new XlsxExtractor().extract(xlsxBytes);

        assertEquals(1, workbook.sheets().size());
        SheetModel sheet = workbook.sheets().get(0);
        assertEquals("Sheet1", sheet.name());
        assertEquals(1, sheet.rows().size());
        SheetRow row = sheet.rows().get(0);
        assertEquals(1, row.rowNumber());
        assertEquals("hello", row.cells().get(0).displayedValue());
        assertNull(row.cells().get(0).formula());
    }

    @Test
    @DisplayName("a formula cell records the formula text as its content")
    void formulaCellRecordsFormulaText() throws Exception {
        byte[] xlsxBytes = workbookWithFormulaCell("SUM(1,2)");

        WorkbookModel workbook = new XlsxExtractor().extract(xlsxBytes);

        SheetCell cell = workbook.sheets().get(0).rows().get(0).cells().get(0);
        assertEquals("SUM(1,2)", cell.formula());
        assertEquals("=SUM(1,2)", cell.contentKey(),
                "a formula cell is identified by its formula, not by the number it produced");
    }

    @Test
    @DisplayName("a bold, filled cell records its formatting")
    void boldFilledCellRecordsFormatting() throws Exception {
        byte[] xlsxBytes = workbookWithStyledCell();

        WorkbookModel workbook = new XlsxExtractor().extract(xlsxBytes);

        SheetCell cell = workbook.sheets().get(0).rows().get(0).cells().get(0);
        assertEquals(Boolean.TRUE, cell.bold());
        assertTrue(cell.fillColor() != null && !cell.fillColor().isBlank(),
                "a solid fill must record a colour, got: " + cell.fillColor());
    }

    @Test
    @DisplayName("two sheets keep their names and order")
    void twoSheetsKeepNamesAndOrder() throws Exception {
        byte[] xlsxBytes = workbookWithTwoSheets();

        WorkbookModel workbook = new XlsxExtractor().extract(xlsxBytes);

        assertEquals(2, workbook.sheets().size());
        assertEquals("First", workbook.sheets().get(0).name());
        assertEquals("Second", workbook.sheets().get(1).name());
    }

    @Test
    @DisplayName("the row signature changes when a formula changes and the value does not")
    void signatureChangesOnFormulaChangeOnly() throws Exception {
        WorkbookModel first = new XlsxExtractor().extract(workbookWithFormulaCell("SUM(1,2)"));
        WorkbookModel second = new XlsxExtractor().extract(workbookWithFormulaCell("1+2"));

        String firstSignature = first.sheets().get(0).rows().get(0).signature();
        String secondSignature = second.sheets().get(0).rows().get(0).signature();

        assertTrue(!firstSignature.equals(secondSignature),
                "a formula rewrite must change the row signature even when the result is the same");
    }

    private byte[] workbookWithLiteralCell(String text) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue(text);
            workbook.write(buffer);
            return buffer.toByteArray();
        }
    }

    private byte[] workbookWithFormulaCell(String formula) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");
            XSSFRow row = sheet.createRow(0);
            XSSFCell cell = row.createCell(0);
            cell.setCellFormula(formula);
            workbook.write(buffer);
            return buffer.toByteArray();
        }
    }

    private byte[] workbookWithStyledCell() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");
            XSSFCell cell = sheet.createRow(0).createCell(0);
            cell.setCellValue("styled");
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            cell.setCellStyle(style);
            workbook.write(buffer);
            return buffer.toByteArray();
        }
    }

    private byte[] workbookWithTwoSheets() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            workbook.createSheet("First").createRow(0).createCell(0).setCellValue("a");
            workbook.createSheet("Second").createRow(0).createCell(0).setCellValue("b");
            workbook.write(buffer);
            return buffer.toByteArray();
        }
    }

    @Test
    @DisplayName("a cell formatted to two decimals renders as 2.50, not 2.5")
    void numberFormatIsPreserved() throws Exception {
        byte[] xlsxBytes = workbookWithFormattedNumber();

        SheetModel sheet = new XlsxExtractor().extract(xlsxBytes).sheets().get(0);

        assertEquals("2.50", sheet.rows().get(0).cells().get(0).displayedValue(),
                "DataFormatter must honour the cell's number format, or money columns read as "
                        + "spurious changes");
    }

    private byte[] workbookWithFormattedNumber() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");
            XSSFCell cell = sheet.createRow(0).createCell(0);
            cell.setCellValue(2.5);
            XSSFCellStyle style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
            cell.setCellStyle(style);
            workbook.write(buffer);
            return buffer.toByteArray();
        }
    }
}
