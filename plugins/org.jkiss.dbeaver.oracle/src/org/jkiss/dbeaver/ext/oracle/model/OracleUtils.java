/*
 * Copyright (c) 2011, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ext.oracle.model;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.IDatabasePersistAction;
import org.jkiss.dbeaver.ext.oracle.model.source.OracleSourceObject;
import org.jkiss.dbeaver.ext.oracle.model.source.OracleSourceObjectEx;
import org.jkiss.dbeaver.model.DBConstants;
import org.jkiss.dbeaver.model.DBPEvent;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCExecutionContext;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.impl.edit.AbstractDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCAbstractCache;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.*;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Oracle utils
 */
public class OracleUtils {

    static final Log log = LogFactory.getLog(OracleUtils.class);

    public static String getDDL(
        DBRProgressMonitor monitor,
        String objectType,
        DBSEntity object) throws DBCException
    {
        String objectName = object.getName();
        String objectFullName = object instanceof DBSEntityQualified ?
            ((DBSEntityQualified)object).getFullQualifiedName() : objectName;
        OracleSchema schema = null;
        if (object instanceof OracleSchemaObject) {
            schema = ((OracleSchemaObject)object).getSchema();
        } else if (object instanceof OracleTableBase) {
            schema = ((OracleTableBase)object).getContainer();
        }
        final OracleDataSource dataSource = (OracleDataSource) object.getDataSource();
        monitor.beginTask("Load sources for " + objectType + " '" + objectFullName + "'...", 1);
        final JDBCExecutionContext context = dataSource.openContext(
            monitor,
            DBCExecutionPurpose.META,
            "Load source code for " + objectType + " '" + objectFullName + "'");
        try {
            final JDBCPreparedStatement dbStat = context.prepareStatement(
                "SELECT DBMS_METADATA.GET_DDL(?,?" +
                (schema == null ? "": ",?") +
                ") TXT " +
                "FROM DUAL");
            try {
                dbStat.setString(1, objectType);
                dbStat.setString(2, object.getName());
                if (schema != null) {
                    dbStat.setString(3, schema.getName());
                }
                final JDBCResultSet dbResult = dbStat.executeQuery();
                try {
                    if (dbResult.next()) {
                        return dbResult.getString(1);
                    } else {
                        log.warn("No DDL for " + objectType + " '" + objectFullName + "'");
                        return "EMPTY DDL";
                    }
                }
                finally {
                    dbResult.close();
                }
            }
            finally {
                dbStat.close();
            }
        } catch (SQLException e) {
            throw new DBCException(e);
        } finally {
            context.close();
            monitor.done();
        }
    }

    public static String normalizeSourceName(OracleSourceObject object, boolean body)
    {
        try {
            String source = body ?
                ((OracleSourceObjectEx)object).getSourceDefinition(null) :
                object.getSourceDeclaration(null);
            if (source == null) {
                return null;
            }
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                object.getSourceType() + (body ? "\\s+BODY" : "") +
                "\\s(\\s*)([\\w$\\.]+)[\\s\\(]+", java.util.regex.Pattern.CASE_INSENSITIVE);
            final Matcher matcher = pattern.matcher(source);
            if (matcher.find()) {
                String objectName = matcher.group(2);
                if (objectName.indexOf('.') == -1) {
                    if (!objectName.equalsIgnoreCase(object.getName())) {
                        object.setName(DBObjectNameCaseTransformer.transformName(object, objectName));
                        object.getDataSource().getContainer().fireEvent(new DBPEvent(DBPEvent.Action.OBJECT_UPDATE, object));
                    }
                    return source;//.substring(0, matcher.start(1)) + object.getSchema().getName() + "." + objectName + source.substring(matcher.end(2));
                }
            }
            return source;
        } catch (DBException e) {
            log.error(e);
            return null;
        }
    }

    public static void addSchemaChangeActions(List<IDatabasePersistAction> actions, OracleSourceObject object)
    {
        actions.add(0, new AbstractDatabasePersistAction(
            "Set target schema",
            "ALTER SESSION SET CURRENT_SCHEMA=" + object.getSchema().getName(),
            IDatabasePersistAction.ActionType.INITIALIZER));
        if (object.getSchema() != object.getDataSource().getSelectedEntity()) {
            actions.add(new AbstractDatabasePersistAction(
                "Set current schema",
                "ALTER SESSION SET CURRENT_SCHEMA=" + object.getDataSource().getSelectedEntity().getName(),
                IDatabasePersistAction.ActionType.FINALIZER));
        }
    }

    public static String getSource(DBRProgressMonitor monitor, OracleSourceObject sourceObject, boolean body) throws DBCException
    {
        if (sourceObject.getSourceType().isCustom()) {
            return "???? CUSTOM";
        }
        final String sourceType = sourceObject.getSourceType().name();
        final OracleSchema sourceOwner = sourceObject.getSchema();
        if (sourceOwner == null) {
            log.warn("No source owner for object '" + sourceObject.getName() + "'");
            return null;
        }
        monitor.beginTask("Load sources for '" + sourceObject.getName() + "'...", 1);
        final JDBCExecutionContext context = sourceOwner.getDataSource().openContext(
            monitor,
            DBCExecutionPurpose.META,
            "Load source code for " + sourceType + " '" + sourceObject.getName() + "'");
        try {
            final JDBCPreparedStatement dbStat = context.prepareStatement(
                "SELECT TEXT FROM SYS.ALL_SOURCE " +
                    "WHERE TYPE=? AND OWNER=? AND NAME=? " +
                    "ORDER BY LINE");
            try {
                dbStat.setString(1, body ? sourceType + " BODY" : sourceType);
                dbStat.setString(2, sourceOwner.getName());
                dbStat.setString(3, sourceObject.getName());
                dbStat.setFetchSize(DBConstants.METADATA_FETCH_SIZE);
                final JDBCResultSet dbResult = dbStat.executeQuery();
                try {
                    StringBuilder source = null;
                    int lineCount = 0;
                    while (dbResult.next()) {
                        if (monitor.isCanceled()) {
                            break;
                        }
                        final String line = dbResult.getString(1);
                        if (source == null) {
                            source = new StringBuilder(200);
                        }
                        source.append(line);
                        lineCount++;
                        monitor.subTask("Line " + lineCount);
                    }
                    return source == null ? null : source.toString();
                }
                finally {
                    dbResult.close();
                }
            }
            finally {
                dbStat.close();
            }
        } catch (SQLException e) {
            throw new DBCException(e);
        } finally {
            context.close();
            monitor.done();
        }
    }

    public static String getAdminViewPrefix(OracleDataSource dataSource)
    {
        return dataSource.isAdmin() ? "SYS.DBA_" : "SYS.USER_";
    }

    public static String getAdminAllViewPrefix(OracleDataSource dataSource)
    {
        return dataSource.isAdmin() ? "SYS.DBA_" : "SYS.ALL_";
    }

    static <PARENT extends DBSObject> Object resolveLazyReference(
        DBRProgressMonitor monitor,
        PARENT parent,
        JDBCAbstractCache<PARENT,?> cache,
        DBSObjectLazy<?> referrer,
        Object propertyId)
        throws DBException
    {
        final Object reference = referrer.getLazyReference(propertyId);
        if (reference instanceof String) {
            Object object = cache.getObject(
                monitor,
                parent,
                (String) reference);
            if (object != null) {
                return object;
            } else {
                log.warn("Object '" + reference + "' not found");
                return reference;
            }
        } else {
            return reference;
        }
    }

    public static boolean getObjectStatus(
        DBRProgressMonitor monitor,
        DBSObjectStateful object,
        OracleObjectType objectType)
        throws DBCException
    {
        final JDBCExecutionContext context = (JDBCExecutionContext)object.getDataSource().openContext(
            monitor,
            DBCExecutionPurpose.META,
            "Refresh state of " + objectType.getTypeName() + " '" + object.getName() + "'");
        try {
            final JDBCPreparedStatement dbStat = context.prepareStatement(
                "SELECT STATUS FROM SYS.ALL_OBJECTS WHERE OBJECT_TYPE=? AND OBJECT_NAME=?");
            try {
                dbStat.setString(1, objectType.getTypeName());
                dbStat.setString(2, DBObjectNameCaseTransformer.transformName(object, object.getName()));
                final JDBCResultSet dbResult = dbStat.executeQuery();
                try {
                    if (dbResult.next()) {
                        return "VALID".equals(dbResult.getString("STATUS"));
                    } else {
                        log.warn(objectType.getTypeName() + " '" + object.getName() + "' not found in system dictionary");
                        return false;
                    }
                }
                finally {
                    dbResult.close();
                }
            }
            finally {
                dbStat.close();
            }
        } catch (SQLException e) {
            throw new DBCException(e);
        } finally {
            context.close();
        }
    }

}
