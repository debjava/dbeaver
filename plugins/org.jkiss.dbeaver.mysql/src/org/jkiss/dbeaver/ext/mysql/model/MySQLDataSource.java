/*
 * Copyright (c) 2012, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ext.mysql.model;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProduct;
import org.eclipse.core.runtime.Platform;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.mysql.MySQLConstants;
import org.jkiss.dbeaver.ext.mysql.MySQLDataSourceProvider;
import org.jkiss.dbeaver.ext.mysql.model.plan.MySQLPlanAnalyser;
import org.jkiss.dbeaver.ext.mysql.model.session.MySQLSessionManager;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.admin.sessions.DBAServerSessionManager;
import org.jkiss.dbeaver.model.exec.*;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCExecutionContext;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.exec.plan.DBCPlan;
import org.jkiss.dbeaver.model.exec.plan.DBCQueryPlanner;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSource;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataTypeCache;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.*;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * GenericDataSource
 */
public class MySQLDataSource extends JDBCDataSource implements DBSObjectSelector, DBCQueryPlanner, IAdaptable
{
    static final Log log = LogFactory.getLog(MySQLDataSource.class);

    private final JDBCDataTypeCache dataTypeCache;
    private List<MySQLEngine> engines;
    private final CatalogCache catalogCache = new CatalogCache();
    private List<MySQLPrivilege> privileges;
    private List<MySQLUser> users;
    private List<MySQLCharset> charsets;
    private Map<String, MySQLCollation> collations;
    private String activeCatalogName;
    //private List<MySQLInformationFolder> informationFolders;

    public MySQLDataSource(DBSDataSourceContainer container)
        throws DBException
    {
        super(container);
        dataTypeCache = new JDBCDataTypeCache(container);
    }

    @Override
    protected Map<String, String> getInternalConnectionProperties()
    {
        return MySQLDataSourceProvider.getConnectionsProps();
    }

    public String[] getTableTypes()
    {
        return MySQLConstants.TABLE_TYPES;
    }

    public CatalogCache getCatalogCache()
    {
        return catalogCache;
    }

    public Collection<MySQLCatalog> getCatalogs()
    {
        return catalogCache.getCachedObjects();
    }

    public MySQLCatalog getCatalog(String name)
    {
        return catalogCache.getCachedObject(name);
    }

    @Override
    public void initialize(DBRProgressMonitor monitor)
        throws DBException
    {
        super.initialize(monitor);

        dataTypeCache.getObjects(monitor, this);
        JDBCExecutionContext context = openContext(monitor, DBCExecutionPurpose.META, "Load basic datasource metadata");
        try {
            // Read engines
            {
                engines = new ArrayList<MySQLEngine>();
                JDBCPreparedStatement dbStat = context.prepareStatement("SHOW ENGINES");
                try {
                    JDBCResultSet dbResult = dbStat.executeQuery();
                    try {
                        while (dbResult.next()) {
                            MySQLEngine engine = new MySQLEngine(this, dbResult);
                            engines.add(engine);
                        }
                    } finally {
                        dbResult.close();
                    }
                } catch (SQLException ex ) {
                    // Engines are not supported. Shame on it. Leave this list empty
                } finally {
                    dbStat.close();
                }
            }

            // Read charsets and collations
            {
                charsets = new ArrayList<MySQLCharset>();
                JDBCPreparedStatement dbStat = context.prepareStatement("SHOW CHARSET");
                try {
                    JDBCResultSet dbResult = dbStat.executeQuery();
                    try {
                        while (dbResult.next()) {
                            MySQLCharset charset = new MySQLCharset(this, dbResult);
                            charsets.add(charset);
                        }
                    } finally {
                        dbResult.close();
                    }
                } catch (SQLException ex ) {
                    // Engines are not supported. Shame on it. Leave this list empty
                } finally {
                    dbStat.close();
                }
                Collections.sort(charsets, DBUtils.<MySQLCharset>nameComparator());


                collations = new LinkedHashMap<String, MySQLCollation>();
                dbStat = context.prepareStatement("SHOW COLLATION");
                try {
                    JDBCResultSet dbResult = dbStat.executeQuery();
                    try {
                        while (dbResult.next()) {
                            String charsetName = JDBCUtils.safeGetString(dbResult, MySQLConstants.COL_CHARSET);
                            MySQLCharset charset = getCharset(charsetName);
                            if (charset == null) {
                                log.warn("Charset '" + charsetName + "' not found.");
                                continue;
                            }
                            MySQLCollation collation = new MySQLCollation(charset, dbResult);
                            collations.put(collation.getName(), collation);
                            charset.addCollation(collation);
                        }
                    } finally {
                        dbResult.close();
                    }
                } catch (SQLException ex ) {
                    // Engines are not supported. Shame on it. Leave this list empty
                } finally {
                    dbStat.close();
                }
            }

            // Read catalogs
            catalogCache.getObjects(monitor, this);

            {
                // Get active schema
                try {
                    JDBCPreparedStatement dbStat = context.prepareStatement("SELECT DATABASE()");
                    try {
                        JDBCResultSet resultSet = dbStat.executeQuery();
                        try {
                            resultSet.next();
                            activeCatalogName = resultSet.getString(1);
                        } finally {
                            resultSet.close();
                        }
                    } finally {
                        dbStat.close();
                    }
                } catch (SQLException e) {
                    log.error(e);
                }
            }

        } catch (SQLException ex) {
            throw new DBException("Error reading metadata", ex);
        }
        finally {
            context.close();
        }

/*
        // Construct information folders
        informationFolders = new ArrayList<MySQLInformationFolder>();
        informationFolders.add(new MySQLInformationFolder(this, "Session Status") {
            public Collection getObjects(DBRProgressMonitor monitor) throws DBException
            {
                return getSessionStatus(monitor);
            }
        });
        informationFolders.add(new MySQLInformationFolder(this, "Global Status") {
            public Collection getObjects(DBRProgressMonitor monitor) throws DBException
            {
                return getGlobalStatus(monitor);
            }
        });
        informationFolders.add(new MySQLInformationFolder(this, "Session Variables") {
            public Collection getObjects(DBRProgressMonitor monitor) throws DBException
            {
                return getSessionVariables(monitor);
            }
        });
        informationFolders.add(new MySQLInformationFolder(this, "Global Variables") {
            public Collection getObjects(DBRProgressMonitor monitor) throws DBException
            {
                return getGlobalVariables(monitor);
            }
        });
        informationFolders.add(new MySQLInformationFolder(this, "Engines") {
            public Collection getObjects(DBRProgressMonitor monitor)
            {
                return getEngines();
            }
        });
        informationFolders.add(new MySQLInformationFolder(this, "User Privileges") {
            public Collection getObjects(DBRProgressMonitor monitor) throws DBException
            {
                return getPrivileges(monitor);
            }
        });
*/
    }

    @Override
    public boolean refreshObject(DBRProgressMonitor monitor)
        throws DBException
    {
        super.refreshObject(monitor);

        this.engines = null;
        this.catalogCache.clearCache();
        this.users = null;
        this.activeCatalogName = null;

        this.initialize(monitor);

        return true;
    }

    MySQLTable findTable(DBRProgressMonitor monitor, String catalogName, String tableName)
        throws DBException
    {
        if (CommonUtils.isEmpty(catalogName)) {
            return null;
        }
        MySQLCatalog catalog = getCatalog(catalogName);
        if (catalog == null) {
            log.error("Catalog " + catalogName + " not found");
            return null;
        }
        return catalog.getTable(monitor, tableName);
    }

    @Override
    public Collection<? extends MySQLCatalog> getChildren(DBRProgressMonitor monitor)
        throws DBException
    {
        return getCatalogs();
    }

    @Override
    public MySQLCatalog getChild(DBRProgressMonitor monitor, String childName)
        throws DBException
    {
        return getCatalog(childName);
    }

    @Override
    public Class<? extends MySQLCatalog> getChildType(DBRProgressMonitor monitor)
        throws DBException
    {
        return MySQLCatalog.class;
    }

    @Override
    public void cacheStructure(DBRProgressMonitor monitor, int scope)
        throws DBException
    {
        
    }

    @Override
    public boolean supportsObjectSelect()
    {
        return true;
    }

    @Override
    public MySQLCatalog getSelectedObject()
    {
        return getCatalog(activeCatalogName);
    }

    @Override
    public void selectObject(DBRProgressMonitor monitor, DBSObject object)
        throws DBException
    {
        final MySQLCatalog oldSelectedEntity = getSelectedObject();
        if (object == oldSelectedEntity) {
            return;
        }
        if (!(object instanceof MySQLCatalog)) {
            throw new IllegalArgumentException("Invalid object type: " + object);
        }
        JDBCExecutionContext context = openContext(monitor, DBCExecutionPurpose.META, "Set active catalog");
        try {
            JDBCPreparedStatement dbStat = context.prepareStatement("use " + object.getName());
            try {
                dbStat.execute();
            } finally {
                dbStat.close();
            }
        } catch (SQLException e) {
            throw new DBException(e);
        }
        finally {
            context.close();
        }
        activeCatalogName = object.getName();

        // Send notifications
        if (oldSelectedEntity != null) {
            DBUtils.fireObjectSelect(oldSelectedEntity, false);
        }
        if (this.activeCatalogName != null) {
            DBUtils.fireObjectSelect(object, true);
        }
    }

    @Override
    protected Connection openConnection() throws DBException {
        Connection mysqlConnection = super.openConnection();

        // Fix "errorMessageEncoding" error. Dirty hack.
        // characterSetMetadata -> errorMessageEncoding
        try {
            Field characterSetMetadataField = mysqlConnection.getClass().getDeclaredField("characterSetMetadata");
            Field errorMessageEncodingField = mysqlConnection.getClass().getDeclaredField("errorMessageEncoding");
            characterSetMetadataField.setAccessible(true);
            errorMessageEncodingField.setAccessible(true);
            errorMessageEncodingField.set(
                mysqlConnection,
                characterSetMetadataField.get(mysqlConnection));
        } catch (Throwable e) {
            log.debug(e);
        }

        {
            // Provide client info
            IProduct product = Platform.getProduct();
            if (product != null) {
                String appName = "DBeaver " + product.getDefiningBundle().getVersion().toString();
                try {
                    ((Connection)mysqlConnection).setClientInfo("ApplicationName", appName);
                } catch (Throwable e) {
                    // just ignore
                    log.debug(e);
                }
            }
        }

        return mysqlConnection;
    }


    public List<MySQLUser> getUsers(DBRProgressMonitor monitor)
        throws DBException
    {
        if (users == null) {
            users = loadUsers(monitor);
        }
        return users;
    }

    public MySQLUser getUser(DBRProgressMonitor monitor, String name)
        throws DBException
    {
        return DBUtils.findObject(getUsers(monitor), name);
    }

    private List<MySQLUser> loadUsers(DBRProgressMonitor monitor)
        throws DBException
    {
        JDBCExecutionContext context = getDataSource().openContext(monitor, DBCExecutionPurpose.META, "Load users");
        try {
            JDBCPreparedStatement dbStat = context.prepareStatement("SELECT * FROM mysql.user ORDER BY user");
            try {
                JDBCResultSet dbResult = dbStat.executeQuery();
                try {
                    List<MySQLUser> userList = new ArrayList<MySQLUser>();
                    while (dbResult.next()) {
                            MySQLUser user = new MySQLUser(this, dbResult);
                            userList.add(user);
                        }
                    return userList;
                } finally {
                    dbResult.close();
                }
            } finally {
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

    public List<MySQLEngine> getEngines()
    {
        return engines;
    }

    public MySQLEngine getEngine(String name)
    {
        return DBUtils.findObject(engines, name);
    }

    public MySQLEngine getDefaultEngine()
    {
        for (MySQLEngine engine : engines) {
            if (engine.getSupport() == MySQLEngine.Support.DEFAULT) {
                return engine;
            }
        }
        return null;
    }

    public Collection<MySQLCharset> getCharsets()
    {
        return charsets;
    }

    public MySQLCharset getCharset(String name)
    {
        for (MySQLCharset charset : charsets) {
            if (charset.getName().equals(name)) {
                return charset;
            }
        }
        return null;
    }

    public MySQLCollation getCollation(String name)
    {
        return collations.get(name);
    }

    public List<MySQLPrivilege> getPrivileges(DBRProgressMonitor monitor)
        throws DBException
    {
        if (privileges == null) {
            privileges = loadPrivileges(monitor);
        }
        return privileges;
    }

    public List<MySQLPrivilege> getPrivilegesByKind(DBRProgressMonitor monitor, MySQLPrivilege.Kind kind)
        throws DBException
    {
        List<MySQLPrivilege> privs = new ArrayList<MySQLPrivilege>();
        for (MySQLPrivilege priv : getPrivileges(monitor)) {
            if (priv.getKind() == kind) {
                privs.add(priv);
            }
        }
        return privs;
    }

    public MySQLPrivilege getPrivilege(DBRProgressMonitor monitor, String name)
        throws DBException
    {
        return DBUtils.findObject(getPrivileges(monitor), name);
    }

    private List<MySQLPrivilege> loadPrivileges(DBRProgressMonitor monitor)
        throws DBException
    {
        JDBCExecutionContext context = getDataSource().openContext(monitor, DBCExecutionPurpose.META, "Load privileges");
        try {
            JDBCPreparedStatement dbStat = context.prepareStatement("SHOW PRIVILEGES");
            try {
                JDBCResultSet dbResult = dbStat.executeQuery();
                try {
                    List<MySQLPrivilege> privileges = new ArrayList<MySQLPrivilege>();
                    while (dbResult.next()) {
                            MySQLPrivilege user = new MySQLPrivilege(this, dbResult);
                            privileges.add(user);
                        }
                    return privileges;
                } finally {
                    dbResult.close();
                }
            } finally {
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

    public List<MySQLParameter> getSessionStatus(DBRProgressMonitor monitor)
        throws DBException
    {
        return loadParameters(monitor, true, false);
    }

    public List<MySQLParameter> getGlobalStatus(DBRProgressMonitor monitor)
        throws DBException
    {
        return loadParameters(monitor, true, true);
    }

    public List<MySQLParameter> getSessionVariables(DBRProgressMonitor monitor)
        throws DBException
    {
        return loadParameters(monitor, false, false);
    }

    public List<MySQLParameter> getGlobalVariables(DBRProgressMonitor monitor)
        throws DBException
    {
        return loadParameters(monitor, false, true);
    }

    public List<MySQLDataSource> getInformation()
    {
        return Collections.singletonList(this);
    }

    private List<MySQLParameter> loadParameters(DBRProgressMonitor monitor, boolean status, boolean global) throws DBException
    {
        JDBCExecutionContext context = getDataSource().openContext(monitor, DBCExecutionPurpose.META, "Load status");
        try {
            JDBCPreparedStatement dbStat = context.prepareStatement(
                "SHOW " + 
                (global ? "GLOBAL " : "") + 
                (status ? "STATUS" : "VARIABLES"));
            try {
                JDBCResultSet dbResult = dbStat.executeQuery();
                try {
                    List<MySQLParameter> parameters = new ArrayList<MySQLParameter>();
                    while (dbResult.next()) {
                        MySQLParameter parameter = new MySQLParameter(
                            this,
                            JDBCUtils.safeGetString(dbResult, "variable_name"),
                            JDBCUtils.safeGetString(dbResult, "value"));
                        parameters.add(parameter);
                    }
                    return parameters;
                } finally {
                    dbResult.close();
                }
            } finally {
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

    @Override
    public DBCQueryTransformer createQueryTransformer(DBCQueryTransformType type) {
        if (type == DBCQueryTransformType.RESULT_SET_LIMIT) {
            return new QueryTransformerLimit();
        } else if (type == DBCQueryTransformType.FETCH_ALL_TABLE) {
            return new QueryTransformerFetchAll();
        }
        return super.createQueryTransformer(type);
    }

    @Override
    public DBCPlan planQueryExecution(DBCExecutionContext context, String query) throws DBCException
    {
        MySQLPlanAnalyser plan = new MySQLPlanAnalyser(this, query);
        plan.explain(context);
        return plan;
    }

    @Override
    public Object getAdapter(Class adapter)
    {
        if (adapter == DBSStructureAssistant.class) {
            return new MySQLStructureAssistant(this);
        } else if (adapter == DBAServerSessionManager.class) {
            return new MySQLSessionManager(this);
        }
        return null;
    }

    @Override
    public MySQLDataSource getDataSource() {
        return this;
    }

    @Override
    public Collection<? extends DBSDataType> getDataTypes()
    {
        return dataTypeCache.getCachedObjects();
    }

    @Override
    public DBSDataType getDataType(String typeName)
    {
        return dataTypeCache.getCachedObject(typeName);
    }

    static class CatalogCache extends JDBCObjectCache<MySQLDataSource, MySQLCatalog>
    {
        @Override
        protected JDBCStatement prepareObjectsStatement(JDBCExecutionContext context, MySQLDataSource owner) throws SQLException
        {
            StringBuilder catalogQuery = new StringBuilder("SELECT * FROM " + MySQLConstants.META_TABLE_SCHEMATA);
            DBSObjectFilter catalogFilters = owner.getContainer().getObjectFilter(MySQLCatalog.class, null, false);
            if (catalogFilters != null) {
                JDBCUtils.appendFilterClause(catalogQuery, catalogFilters, MySQLConstants.COL_SCHEMA_NAME, true);
            }
            JDBCPreparedStatement dbStat = context.prepareStatement(catalogQuery.toString());
            if (catalogFilters != null) {
                JDBCUtils.setFilterParameters(dbStat, 1, catalogFilters);
            }
            return dbStat;
        }

        @Override
        protected MySQLCatalog fetchObject(JDBCExecutionContext context, MySQLDataSource owner, ResultSet resultSet) throws SQLException, DBException
        {
            return new MySQLCatalog(owner, resultSet);
        }

    }

}
