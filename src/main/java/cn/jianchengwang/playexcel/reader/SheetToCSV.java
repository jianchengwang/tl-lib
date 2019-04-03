package cn.jianchengwang.playexcel.reader;

import cn.jianchengwang.playexcel.converter.Converter;
import cn.jianchengwang.playexcel.kit.ExcelKit;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;

import java.lang.reflect.Field;
import java.util.stream.Stream;

/**
 * Uses the XSSF Event SAX helpers to do most of the work
 * of parsing the Sheet XML, and outputs the contents
 * as a (basic) CSV.
 */
@Slf4j
public class SheetToCSV<T> extends ReaderConverter implements XSSFSheetXMLHandler.SheetContentsHandler {

    private boolean firstCellOfRow;
    private int     currentRow = -1;
    private int     currentCol = -1;

    private final OPCPackage opcPackage;
    private final Stream.Builder<T> rowsStream;
    private final Class<T>          type;
    private final int               startRow;

    private T row;

    public SheetToCSV(OPCPackage opcPackage, int startRow, Class<T> type) {
        this.opcPackage = opcPackage;
        this.rowsStream = Stream.builder();
        this.startRow = startRow;

        this.type = type;

        try {
            this.initFieldConverter(type.getDeclaredFields());
        } catch (Exception e) {
            log.error("init field converter fail", e);
        }
    }

    @Override
    public void startRow(int rowNum) {
        // Prepare for this row
        firstCellOfRow = true;
        currentRow = rowNum;
        currentCol = -1;
        if (currentRow < startRow) {
            return;
        }
        row = ExcelKit.newInstance(type);
    }

    @Override
    public void endRow(int rowNum) {
        if (currentRow < startRow) {
            return;
        }
        rowsStream.add(row);
    }

    @Override
    public void cell(String cellReference, String formattedValue,
                     XSSFComment comment) {

        if (currentRow < startRow) {
            return;
        }

        if (firstCellOfRow) {
            firstCellOfRow = false;
        }

        // gracefully handle missing CellRef here in a similar way as XSSFCell does
        if (cellReference == null) {
            cellReference = new CellAddress(currentRow, currentCol).formatAsString();
        }

        currentCol = (int) (new CellReference(cellReference)).getCol();

        Field field = fieldIndexes.get(currentCol);
        if (null != field) {
            try {
                Object    cellValue = formattedValue;
                Converter converter = fieldConverters.get(field);
                if (null != converter) {
                    cellValue = converter.stringToR(formattedValue);
                }
                field.set(row, cellValue);
            } catch (Exception e) {
                log.error("write field {} value fail", field.getName(), e);
            }
        }
    }

    public OPCPackage getOpcPackage() {
        return opcPackage;
    }

    public Stream.Builder<T> getRowsStream() {
        return rowsStream;
    }

}