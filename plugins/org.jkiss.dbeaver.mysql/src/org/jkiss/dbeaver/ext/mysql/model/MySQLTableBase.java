/*
 * Copyright (c) 2012, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ext.mysql.model;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBPNamedObject2;
import org.jkiss.dbeaver.model.DBPRefreshableObject;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCExecutionContext;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.struct.JDBCTable;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSTableColumn;

import java.io.UnsupportedEncodingException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

/**
 * MySQLTable base
 */
public abstract class MySQLTableBase extends JDBCTable<MySQLDataSource, MySQLCatalog>
    implements DBPNamedObject2,DBPRefreshableObject
{
    static final Log log = LogFactory.getLog(MySQLTableBase.class);

    private List<MySQLTableColumn> columns;

    protected MySQLTableBase(MySQLCatalog catalog)
    {
        super(catalog, false);
    }

    protected MySQLTableBase(
        MySQLCatalog catalog,
        ResultSet dbResult)
    {
        super(catalog, JDBCUtils.safeGetString(dbResult, 1), true);

        this.columns = null;
    }

    @Override
    public String getFullQualifiedName()
    {
        return DBUtils.getFullQualifiedName(getDataSource(),
            getContainer(),
            this);
    }

    @Override
    public Collection<MySQLTableColumn> getColumns(DBRProgressMonitor monitor)
        throws DBException
    {
        if (columns == null) {
            getContainer().tableCache.loadChildren(monitor, getContainer(), this);
        }
        return columns;
    }

    @Override
    public MySQLTableColumn getColumn(DBRProgressMonitor monitor, String columnName)
        throws DBException
    {
        return DBUtils.findObject(getColumns(monitor), columnName);
    }

    @Override
    public boolean refreshObject(DBRProgressMonitor monitor) throws DBException
    {
        columns = null;
        return true;
    }

    public boolean isColumnsCached()
    {
        return columns != null;
    }

    public void setColumns(List<MySQLTableColumn> columns)
    {
        this.columns = columns;
    }


    public String getDDL(DBRProgressMonitor monitor)
        throws DBException
    {
        if (!isPersisted()) {
            return "";
        }
        JDBCExecutionContext context = getDataSource().openContext(monitor, DBCExecutionPurpose.META, "Retrieve table DDL");
        try {
            PreparedStatement dbStat = context.prepareStatement(
                "SHOW CREATE " + (isView() ? "VIEW" : "TABLE") + " " + getFullQualifiedName());
            try {
                ResultSet dbResult = dbStat.executeQuery();
                try {
                    if (dbResult.next()) {
                        byte[] ddl;
                        if (isView()) {
                            ddl = dbResult.getBytes("Create View");
                        } else {
                            ddl = dbResult.getBytes("Create Table");
                        }
                        if (ddl == null) {
                            return null;
                        } else {
                            try {
                                return new String(ddl, getContainer().getDefaultCharset().getName());
                            } catch (UnsupportedEncodingException e) {
                                log.debug(e);
                                return new String(ddl);
                            }
                        }
                    } else {
                        return "DDL is not available";
                    }
                }
                finally {
                    dbResult.close();
                }
            }
            finally {
                dbStat.close();
            }
        }
        catch (SQLException ex) {
            throw new DBException(ex);
        }
        finally {
            context.close();
        }
    }

}
