package de.ralfrosenkranz.codevibrator.ui.model;

import javax.swing.table.AbstractTableModel;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FileTableModel extends AbstractTableModel {
    private final List<FileRow> rows = new ArrayList<>();
    private final String[] cols = {"Zip", "Name", "Size", "Modified"};

    private Comparator<FileRow> sorter = Comparator.comparing(r -> r.name.toLowerCase());

    public void setRows(List<FileRow> newRows) {
        rows.clear();
        rows.addAll(newRows);
        rows.sort(sorter);
        fireTableDataChanged();
    }

    public FileRow getRow(int modelRow) {
        return rows.get(modelRow);
    }

    public void sortByName(boolean asc) {
        sorter = Comparator.comparing((FileRow r) -> r.name.toLowerCase());
        if (!asc) sorter = sorter.reversed();
        rows.sort(sorter);
        fireTableDataChanged();
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int column) { return cols[column]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        FileRow r = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> r.includeInZip;
            case 1 -> r.name;
            case 2 -> r.size;
            case 3 -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(r.modified);
            default -> null;
        };
    }


    
@Override
public boolean isCellEditable(int rowIndex, int columnIndex) {
    return columnIndex == 0;
}

@Override
public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (columnIndex == 0) {
        rows.get(rowIndex).includeInZip = (Boolean) aValue;
        fireTableCellUpdated(rowIndex, columnIndex);
    }
}

@Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 0 -> Boolean.class;
            case 2 -> Long.class;
            default -> String.class;
        };
    }
}
