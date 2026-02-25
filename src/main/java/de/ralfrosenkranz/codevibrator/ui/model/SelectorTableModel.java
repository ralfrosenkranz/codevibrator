package de.ralfrosenkranz.codevibrator.ui.model;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class SelectorTableModel extends AbstractTableModel {
    private final List<SelectorRow> rows = new ArrayList<>();
    private final String[] cols = {"Pattern", "FORCE", "AKTIV", "Inherited"};

    public void setRows(List<SelectorRow> newRows) {
        rows.clear();
        rows.addAll(newRows);
        fireTableDataChanged();
    }

    public List<SelectorRow> rows() { return rows; }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int column) { return cols[column]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        SelectorRow r = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> r.pattern;
            case 1 -> r.force;
            case 2 -> r.active;
            case 3 -> r.inherited ? "yes" : "no";
            default -> "";
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 1 || columnIndex == 2;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        SelectorRow r = rows.get(rowIndex);
        if (columnIndex == 1) r.force = (Boolean) aValue;
        if (columnIndex == 2) r.active = (Boolean) aValue;
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 1,2 -> Boolean.class;
            default -> String.class;
        };
    }
}
