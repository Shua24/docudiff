package dev.docudiff.extract;

import dev.docudiff.document.SheetCell;
import dev.docudiff.document.SheetModel;
import dev.docudiff.document.SheetRow;
import dev.docudiff.document.WorkbookModel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an XLSX byte array into a {@link WorkbookModel}.
 *
 * <p>A formula cell contributes its formula to the row signature; the cached value is kept as
 * display context only. That is what makes "SUM(A1:A5)" to "A1+A2+A3+A4+A5" register as a change
 * even when both produce the same number.
 */
public final class XlsxExtractor {

    public WorkbookModel extract(byte[] xlsxBytes) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<SheetModel> sheets = new ArrayList<>();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                sheets.add(new SheetModel(sheet.getSheetName(), rowsOf(sheet, evaluator)));
            }
            WorkbookModel model = new WorkbookModel(sheets);
            return model;
        }
    }

    private List<SheetRow> rowsOf(Sheet sheet, FormulaEvaluator evaluator) {
        List<SheetRow> rows = new ArrayList<>();
        for (Row row : sheet) {
            List<SheetCell> cells = new ArrayList<>();
            for (Cell cell : row) {
                cells.add(cellOf(cell, evaluator));
            }
            if (cells.isEmpty()) {
                continue;
            }
            String signature = signatureOf(cells);
            rows.add(new SheetRow(row.getRowNum() + 1, signature, cells));
        }
        return rows;
    }

    private SheetCell cellOf(Cell cell, FormulaEvaluator evaluator) {
        String formula = formulaOf(cell);
        String displayedValue = displayedValueOf(cell, evaluator);
        Boolean bold = null;
        String fillColor = null;
        if (cell.getCellStyle() instanceof XSSFCellStyle xssfStyle) {
            fillColor = xssfStyle.getFillForegroundColorColor() == null
                    ? null
                    : xssfStyle.getFillForegroundColorColor().getARGBHex();
            if (cell.getSheet().getWorkbook() instanceof XSSFWorkbook xssfWorkbook) {
                bold = xssfWorkbook.getFontAt(xssfStyle.getFontIndex()).getBold();
            }
        }
        return new SheetCell(cell.getColumnIndex() + 1, formula, displayedValue, bold, fillColor);
    }

    private String formulaOf(Cell cell) {
        if (cell instanceof XSSFCell xssfCell && xssfCell.getCellType() == CellType.FORMULA) {
            return xssfCell.getCellFormula();
        }
        if (cell.getCellType() == CellType.FORMULA) {
            return cell.getCellFormula();
        }
        return null;
    }

    private String displayedValueOf(Cell cell, FormulaEvaluator evaluator) {
        try {
            return new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(cell, evaluator);
        } catch (RuntimeException exception) {
            return cell.toString();
        }
    }

    private String signatureOf(List<SheetCell> cells) {
        StringBuilder builder = new StringBuilder();
        for (SheetCell cell : cells) {
            builder.append(cell.column()).append(':').append(cell.contentKey()).append('\u001f');
        }
        String signature = builder.toString();
        return signature;
    }
}
