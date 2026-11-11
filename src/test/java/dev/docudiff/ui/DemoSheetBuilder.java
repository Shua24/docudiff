package dev.docudiff.ui;

import dev.docudiff.library.ImportService;
import dev.docudiff.library.LibraryRoot;
import dev.docudiff.versioning.VersionRepository;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Not part of the app. Builds a two-version spreadsheet in the demo library so the grid diff can
 * be seen with real cell changes: an edited number, an edited price, and a new row.
 */
public final class DemoSheetBuilder {

    public static void main(String[] args) throws Exception {
        LibraryRoot libraryRoot = LibraryRoot.defaultRoot();
        Path source = Files.createTempDirectory("inv-").resolve("inventory.xlsx");
        Files.write(source, workbook(
                new String[]{"Product", "Quantity", "Unit price"},
                new String[]{"Widget", "10", "2.50"},
                new String[]{"Gadget", "4", "9.00"},
                new String[]{"Sprocket", "18", "0.75"}));

        Path folder = new ImportService(libraryRoot).importFile(source);
        System.out.println("imported into " + folder);

        VersionRepository repository = new VersionRepository(folder);
        Path workingFile = folder.resolve("inventory.xlsx");

        Files.write(workingFile, workbook(
                new String[]{"Product", "Quantity", "Unit price"},
                new String[]{"Widget", "12", "2.50"},
                new String[]{"Gadget", "4", "9.75"},
                new String[]{"Sprocket", "18", "0.75"},
                new String[]{"Doohickey", "7", "1.20"}));
        repository.snapshot("inventory", "inventory-snapshot-2026-11-10");

        System.out.println("versions: " + repository.listVersions().size());
        repository.listVersions().forEach(version -> System.out.println("  " + version.name()));
    }

    private static byte[] workbook(String[]... rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Inventory");
            org.apache.poi.xssf.usermodel.XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(
                    org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(
                    org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                XSSFRow row = sheet.createRow(rowIndex);
                for (int columnIndex = 0; columnIndex < rows[rowIndex].length; columnIndex++) {
                    XSSFCell cell = row.createCell(columnIndex);
                    String text = rows[rowIndex][columnIndex];
                    if (text.matches("-?\\d+(\\.\\d+)?")) {
                        cell.setCellValue(Double.parseDouble(text));
                    } else {
                        cell.setCellValue(text);
                    }
                    if (rowIndex == 0) {
                        cell.setCellStyle(headerStyle);
                    }
                }
            }
            workbook.write(buffer);
            return buffer.toByteArray();
        }
    }
}
