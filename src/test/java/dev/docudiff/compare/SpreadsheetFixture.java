package dev.docudiff.compare;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Builds real xlsx bytes for tests. Real files, not mocks: the extractor is POI, so a fake would
 * test the fake.
 */
final class SpreadsheetFixture {

    private SpreadsheetFixture() {
    }

    static byte[] workbook(List<String> columnAValues) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");
            for (int index = 0; index < columnAValues.size(); index++) {
                XSSFRow row = sheet.createRow(index);
                XSSFCell cell = row.createCell(0);
                cell.setCellValue(columnAValues.get(index));
            }
            workbook.write(buffer);
            return buffer.toByteArray();
        }
    }
}
