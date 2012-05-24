/*
 * Copyright (c) 2012, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ext.wmi.model;

import org.eclipse.swt.graphics.Image;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.ui.IObjectImageProvider;
import org.jkiss.dbeaver.model.exec.*;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.*;
import org.jkiss.wmi.service.WMIConstants;
import org.jkiss.wmi.service.WMIException;
import org.jkiss.wmi.service.WMIObject;
import org.jkiss.wmi.service.WMIObjectAttribute;

import java.util.*;

/**
 * WMI result set
 */
public class WMIResultSet implements DBCResultSet, DBCResultSetMetaData, DBCEntityMetaData
{
    private DBCExecutionContext context;
    private WMIClass classObject;
    private Collection<WMIObject> rows;
    private Iterator<WMIObject> iterator;
    private WMIObject row;
    private List<DBCColumnMetaData> properties;

    public WMIResultSet(DBCExecutionContext context, WMIClass classObject, Collection<WMIObject> rows) throws WMIException
    {
        this.context = context;
        this.classObject = classObject;
        this.rows = rows;
        this.iterator = rows.iterator();
        this.row = null;
        {
            // Init meta properties
            WMIObject metaObject;
            if (classObject != null) {
                metaObject = classObject.getClassObject();
            } else if (!rows.isEmpty()) {
                metaObject = rows.iterator().next();
            } else {
                metaObject = null;
            }
            if (metaObject == null) {
                properties = Collections.emptyList();
            } else {
                Collection<WMIObjectAttribute> props = metaObject.getAttributes(WMIConstants.WBEM_FLAG_ALWAYS);
                properties = new ArrayList<DBCColumnMetaData>(props.size());
                int index = 0;
                for (WMIObjectAttribute prop : props) {
                    if (!prop.isSystem()) {
                        properties.add(new MetaProperty(prop, index++));
                    }
                }
            }
        }

    }

    @Override
    public DBCExecutionContext getContext()
    {
        return context;
    }

    @Override
    public DBCStatement getSource()
    {
        return null;
    }

    @Override
    public Object getColumnValue(int index) throws DBCException
    {
        try {
            if (index > properties.size()) {
                throw new DBCException("Column index " + index + " out of bounds (" + properties.size() + ")");
            }
            return row.getValue(properties.get(index - 1).getName());
        } catch (WMIException e) {
            throw new DBCException(e);
        }
    }

    @Override
    public Object getColumnValue(String name) throws DBCException
    {
        try {
            return row.getValue(name);
        } catch (WMIException e) {
            throw new DBCException(e);
        }
    }

    @Override
    public boolean nextRow() throws DBCException
    {
        if (!this.iterator.hasNext()) {
            return false;
        }
        row = iterator.next();
        return true;
    }

    @Override
    public boolean moveTo(int position) throws DBCException
    {
        throw new DBCException("Not Implemented");
    }

    @Override
    public DBCResultSetMetaData getResultSetMetaData() throws DBCException
    {
        return this;
    }

    @Override
    public void close()
    {
        for (WMIObject row : rows) {
            row.release();
        }
        rows.clear();
        row = null;
    }

    /////////////////////////////////////////////////////////////
    // DBCResultSetMetaData

    @Override
    public DBCResultSet getResultSet()
    {
        return this;
    }

    @Override
    public List<DBCColumnMetaData> getColumns()
    {
        return properties;
    }

    /////////////////////////////////////////////////////////////
    // DBCTableMetaData

    @Override
    public DBSEntity getEntity(DBRProgressMonitor monitor) throws DBException
    {
        return classObject == null ? null : classObject;
    }

    @Override
    public String getEntityName()
    {
        return classObject == null ? null : classObject.getName();
    }

    @Override
    public String getEntityAlias()
    {
        return null;
    }

    @Override
    public boolean isIdentified(DBRProgressMonitor monitor) throws DBException
    {
        return false;
    }

    @Override
    public DBCEntityIdentifier getBestIdentifier(DBRProgressMonitor monitor) throws DBException
    {
        return null;
    }

    /////////////////////////////////////////////////////////////
    // Meta property

    private class MetaProperty implements DBCColumnMetaData,IObjectImageProvider
    {
        private final WMIObjectAttribute attribute;
        private final int index;

        private MetaProperty(WMIObjectAttribute attribute, int index)
        {
            this.attribute = attribute;
            this.index = index;
        }

        @Override
        public String getName()
        {
            return attribute.getName();
        }

        @Override
        public long getMaxLength()
        {
            return 0;
        }

        @Override
        public String getTypeName()
        {
            return attribute.getTypeName();
        }

        @Override
        public int getTypeID()
        {
            return attribute.getType();
        }

        @Override
        public int getScale()
        {
            return 0;
        }

        @Override
        public int getPrecision()
        {
            return 0;
        }

        @Override
        public int getIndex()
        {
            return index;
        }

        @Override
        public String getLabel()
        {
            return attribute.getName();
        }

        @Override
        public String getTableName()
        {
            return classObject == null ? null : classObject.getName();
        }

        @Override
        public String getCatalogName()
        {
            return null;
        }

        @Override
        public String getSchemaName()
        {
            return classObject == null ? null : classObject.getNamespace().getName();
        }

        @Override
        public boolean isReadOnly()
        {
            return false;
        }

        @Override
        public boolean isWritable()
        {
            return false;
        }

        @Override
        public DBSEntityAttribute getTableColumn(DBRProgressMonitor monitor) throws DBException
        {
            return classObject == null ? null : classObject.getAttribute(monitor, getName());
        }

        @Override
        public DBCEntityMetaData getTable()
        {
            return null;
        }

        @Override
        public boolean isForeignKey(DBRProgressMonitor monitor) throws DBException
        {
            return false;
        }

        @Override
        public List<DBSEntityReferrer> getReferrers(DBRProgressMonitor monitor) throws DBException
        {
            return null;
        }

        @Override
        public Image getObjectImage()
        {
            return WMIClassAttribute.getPropertyImage(attribute.getType());
        }
    }

}
