/*
 * Copyright (c) 2012, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ext.mysql.model;

import org.jkiss.dbeaver.model.impl.struct.AbstractTableConstraint;
import org.jkiss.dbeaver.model.impl.struct.AbstractTableConstraintColumn;
import org.jkiss.dbeaver.model.meta.Property;

/**
 * GenericConstraintColumn
 */
public class MySQLTableConstraintColumn extends AbstractTableConstraintColumn
{
    private AbstractTableConstraint<MySQLTable> constraint;
    private MySQLTableColumn tableColumn;
    private int ordinalPosition;

    public MySQLTableConstraintColumn(AbstractTableConstraint<MySQLTable> constraint, MySQLTableColumn tableColumn, int ordinalPosition)
    {
        this.constraint = constraint;
        this.tableColumn = tableColumn;
        this.ordinalPosition = ordinalPosition;
    }

    //@Property(name = "Name", viewable = true, order = 1)
    @Override
    public String getName()
    {
        return tableColumn.getName();
    }

    @Override
    @Property(id = "name", name = "Column", viewable = true, order = 1)
    public MySQLTableColumn getAttribute()
    {
        return tableColumn;
    }

    @Override
    @Property(name = "Position", viewable = false, order = 2)
    public int getOrdinalPosition()
    {
        return ordinalPosition;
    }

    @Override
    public String getDescription()
    {
        return tableColumn.getDescription();
    }

    @Override
    public AbstractTableConstraint<MySQLTable> getParentObject()
    {
        return constraint;
    }

    @Override
    public MySQLDataSource getDataSource()
    {
        return constraint.getTable().getDataSource();
    }

}
