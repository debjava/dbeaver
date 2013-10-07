/*
 * Copyright (C) 2013      Denis Forveille titou10.titou10@gmail.com
 * Copyright (C) 2010-2013 Serge Rieder serge@jkiss.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jkiss.dbeaver.ext.db2.model;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.core.DBeaverUI;
import org.jkiss.dbeaver.ext.db2.DB2Constants;
import org.jkiss.dbeaver.ext.db2.DB2DataSourceProvider;
import org.jkiss.dbeaver.ext.db2.DB2Messages;
import org.jkiss.dbeaver.ext.db2.DB2Utils;
import org.jkiss.dbeaver.ext.db2.editors.DB2StructureAssistant;
import org.jkiss.dbeaver.ext.db2.editors.DB2TablespaceChooser;
import org.jkiss.dbeaver.ext.db2.info.DB2Parameter;
import org.jkiss.dbeaver.ext.db2.info.DB2XMLString;
import org.jkiss.dbeaver.ext.db2.model.fed.DB2RemoteServer;
import org.jkiss.dbeaver.ext.db2.model.fed.DB2UserMapping;
import org.jkiss.dbeaver.ext.db2.model.fed.DB2Wrapper;
import org.jkiss.dbeaver.ext.db2.model.plan.DB2PlanAnalyser;
import org.jkiss.dbeaver.ext.db2.model.security.DB2AuthIDType;
import org.jkiss.dbeaver.ext.db2.model.security.DB2Grantee;
import org.jkiss.dbeaver.ext.db2.model.security.DB2GranteeCache;
import org.jkiss.dbeaver.ext.db2.model.security.DB2Role;
import org.jkiss.dbeaver.model.DBPConnectionInfo;
import org.jkiss.dbeaver.model.DBPDataSourceInfo;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCDatabaseMetaData;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCExecutionContext;
import org.jkiss.dbeaver.model.exec.plan.DBCPlan;
import org.jkiss.dbeaver.model.exec.plan.DBCQueryPlanner;
import org.jkiss.dbeaver.model.impl.DBSObjectCache;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSource;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSourceInfo;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectSimpleCache;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSDataSourceContainer;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectSelector;
import org.jkiss.dbeaver.model.struct.DBSStructureAssistant;
import org.jkiss.dbeaver.runtime.RunnableWithResult;
import org.jkiss.dbeaver.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.ui.UIUtils;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * DB2 DataSource
 * 
 * @author Denis Forveille
 */
public class DB2DataSource extends JDBCDataSource implements DBSObjectSelector, DBCQueryPlanner, IAdaptable {

    private static final Log LOG = LogFactory.getLog(DB2DataSource.class);

    private static final String GET_CURRENT_SCHEMA = "VALUES(CURRENT SCHEMA)";
    private static final String SET_CURRENT_SCHEMA = "SET CURRENT SCHEMA = %s";
    private static final String GET_SESSION_USER = "VALUES(SESSION_USER)";

    private static final String C_SCHEMA = "SELECT * FROM SYSCAT.SCHEMATA ORDER BY SCHEMANAME WITH UR";
    private static final String C_DT = "SELECT * FROM SYSCAT.DATATYPES WHERE METATYPE = 'S' ORDER BY TYPESCHEMA,TYPENAME WITH UR";
    private static final String C_BP = "SELECT * FROM SYSCAT.BUFFERPOOLS ORDER BY BPNAME WITH UR";
    private static final String C_TS = "SELECT * FROM SYSCAT.TABLESPACES ORDER BY TBSPACE WITH UR";
    private static final String C_SG = "SELECT * FROM SYSCAT.STOGROUPS ORDER BY SGNAME WITH UR";
    private static final String C_RL = "SELECT * FROM SYSCAT.ROLES ORDER BY ROLENAME WITH UR";
    private static final String C_VR = "SELECT * FROM SYSCAT.VARIABLES WHERE VARMODULENAME IS NULL ORDER BY VARNAME WITH UR";

    private static final String C_SV = "SELECT * FROM SYSCAT.SERVERS ORDER BY SERVERNAME WITH UR";
    private static final String C_WR = "SELECT * FROM SYSCAT.WRAPPERS ORDER BY WRAPNAME WITH UR";
    private static final String C_UM = "SELECT * FROM SYSCAT.USEROPTIONS WHERE OPTION = 'REMOTE_AUTHID' ORDER BY SERVERNAME,AUTHID WITH UR";

    private final DBSObjectCache<DB2DataSource, DB2Schema> schemaCache = new JDBCObjectSimpleCache<DB2DataSource, DB2Schema>(
        DB2Schema.class, C_SCHEMA);
    private final DBSObjectCache<DB2DataSource, DB2DataType> dataTypeCache = new JDBCObjectSimpleCache<DB2DataSource, DB2DataType>(
        DB2DataType.class, C_DT);
    private final DBSObjectCache<DB2DataSource, DB2Bufferpool> bufferpoolCache = new JDBCObjectSimpleCache<DB2DataSource, DB2Bufferpool>(
        DB2Bufferpool.class, C_BP);
    private final DBSObjectCache<DB2DataSource, DB2Tablespace> tablespaceCache = new JDBCObjectSimpleCache<DB2DataSource, DB2Tablespace>(
        DB2Tablespace.class, C_TS);
    private final DBSObjectCache<DB2DataSource, DB2StorageGroup> storagegroupCache = new JDBCObjectSimpleCache<DB2DataSource, DB2StorageGroup>(
        DB2StorageGroup.class, C_SG);
    private final DBSObjectCache<DB2DataSource, DB2Role> roleCache = new JDBCObjectSimpleCache<DB2DataSource, DB2Role>(
        DB2Role.class, C_RL);
    private final DBSObjectCache<DB2DataSource, DB2Variable> variableCache = new JDBCObjectSimpleCache<DB2DataSource, DB2Variable>(
        DB2Variable.class, C_VR);

    private final DBSObjectCache<DB2DataSource, DB2RemoteServer> remoteServerCache = new JDBCObjectSimpleCache<DB2DataSource, DB2RemoteServer>(
        DB2RemoteServer.class, C_SV);
    private final DBSObjectCache<DB2DataSource, DB2Wrapper> wrapperCache = new JDBCObjectSimpleCache<DB2DataSource, DB2Wrapper>(
        DB2Wrapper.class, C_WR);
    private final DBSObjectCache<DB2DataSource, DB2UserMapping> userMappingCache = new JDBCObjectSimpleCache<DB2DataSource, DB2UserMapping>(
        DB2UserMapping.class, C_UM);

    private final DB2GranteeCache groupCache = new DB2GranteeCache(DB2AuthIDType.G);
    private final DB2GranteeCache userCache = new DB2GranteeCache(DB2AuthIDType.U);

    private List<DB2Parameter> listDBParameters;
    private List<DB2Parameter> listDBMParameters;
    private List<DB2XMLString> listXMLStrings;

    private String activeSchemaName;
    private Boolean isAuthorisedForAPPLICATIONS;

    private String schemaForExplainTables;

    private Double version; // Database Version

    // -----------------------
    // Constructors
    // -----------------------

    public DB2DataSource(DBRProgressMonitor monitor, DBSDataSourceContainer container) throws DBException
    {
        super(monitor, container);
    }

    // -----------------------
    // Initialisation/Structure
    // -----------------------

    @Override
    public void initialize(DBRProgressMonitor monitor) throws DBException
    {
        super.initialize(monitor);

        final JDBCExecutionContext context = openContext(monitor, DBCExecutionPurpose.META, "Load data source meta info");
        try {
            // Get active schema
            this.activeSchemaName = JDBCUtils.queryString(context, GET_CURRENT_SCHEMA);
            if (this.activeSchemaName != null) {
                this.activeSchemaName = this.activeSchemaName.trim();
            }

            this.isAuthorisedForAPPLICATIONS = DB2Utils.userIsAuthorisedForAPPLICATIONS(context, activeSchemaName);

            listDBMParameters = DB2Utils.readDBMCfg(monitor, context);
            listDBParameters = DB2Utils.readDBCfg(monitor, context);
            listXMLStrings = DB2Utils.readXMLStrings(monitor, context);

        } catch (SQLException e) {
            LOG.warn(e);
        } finally {
            context.close();
        }

        this.dataTypeCache.getObjects(monitor, this);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Object getAdapter(Class adapter)
    {
        if (adapter == DBSStructureAssistant.class) {
            return new DB2StructureAssistant(this);
        }
        return null;
    }

    @Override
    public void cacheStructure(DBRProgressMonitor monitor, int scope) throws DBException
    {
        // TODO DF: No idea what to do with this method, what it is used for...
    }

    // -----------------------
    // Connection related Info
    // -----------------------

    @Override
    protected String getConnectionUserName(DBPConnectionInfo connectionInfo)
    {
        return connectionInfo.getUserName();
    }

    @Override
    public DB2DataSource getDataSource()
    {
        return this;
    }

    @Override
    protected DBPDataSourceInfo makeInfo(JDBCDatabaseMetaData metaData)
    {
        final JDBCDataSourceInfo info = new JDBCDataSourceInfo(metaData);
        for (String kw : DB2Constants.ADVANCED_KEYWORDS) {
            info.addSQLKeyword(kw);
        }

        // Compute Database version
        version = DB2Constants.DB2v99_9;
        try {
            version = Integer.valueOf(metaData.getDatabaseMajorVersion()).doubleValue();
            version += Integer.valueOf(metaData.getDatabaseMinorVersion()).doubleValue() / 10;
        } catch (SQLException e) {
            LOG.warn("SQLException when reading database version. Set it to " + DB2Constants.DB2v99_9 + " : " + e.getMessage());
        }
        LOG.debug("Database version : " + version);

        return info;
    }

    @Override
    protected Map<String, String> getInternalConnectionProperties()
    {
        return DB2DataSourceProvider.getConnectionsProps();
    }

    @Override
    public boolean refreshObject(DBRProgressMonitor monitor) throws DBException
    {
        super.refreshObject(monitor);

        this.userCache.clearCache();
        this.groupCache.clearCache();
        this.roleCache.clearCache();
        this.variableCache.clearCache();

        this.tablespaceCache.clearCache();
        this.storagegroupCache.clearCache();
        this.bufferpoolCache.clearCache();
        this.schemaCache.clearCache();
        this.dataTypeCache.clearCache();

        this.remoteServerCache.clearCache();
        this.wrapperCache.clearCache();
        this.userMappingCache.clearCache();

        this.listDBMParameters = null;
        this.listDBParameters = null;

        this.initialize(monitor);

        return true;
    }

    @Override
    public Collection<DB2DataType> getDataTypes()
    {
        try {
            return getDataTypes(VoidProgressMonitor.INSTANCE);
        } catch (DBException e) {
            LOG.error("DBException occurred when reading system dataTypes: ", e);
            return null;
        }
    }

    @Override
    public DB2DataType getDataType(String typeName)
    {
        try {
            return getDataType(VoidProgressMonitor.INSTANCE, typeName);
        } catch (DBException e) {
            LOG.error("DBException occurred when reading system dataTYpe : " + typeName, e);
            return null;
        }
    }

    public boolean isAuthorisedForAPPLICATIONS()
    {
        return isAuthorisedForAPPLICATIONS;
    }

    // --------------------------
    // Manage Children: DB2Schema
    // --------------------------

    @Override
    public boolean supportsObjectSelect()
    {
        return true;
    }

    @Override
    public Class<? extends DB2Schema> getChildType(DBRProgressMonitor monitor) throws DBException
    {
        return DB2Schema.class;
    }

    @Override
    public Collection<DB2Schema> getChildren(DBRProgressMonitor monitor) throws DBException
    {
        return getSchemas(monitor);
    }

    @Override
    public DB2Schema getChild(DBRProgressMonitor monitor, String childName) throws DBException
    {
        return getSchema(monitor, childName);
    }

    @Override
    public DB2Schema getSelectedObject()
    {
        return activeSchemaName == null ? null : schemaCache.getCachedObject(activeSchemaName);
    }

    @Override
    public void selectObject(DBRProgressMonitor monitor, DBSObject object) throws DBException
    {
        final DB2Schema oldSelectedEntity = getSelectedObject();

        if (!(object instanceof DB2Schema)) {
            throw new IllegalArgumentException("Invalid object type: " + object);
        }

        activeSchemaName = object.getName();

        JDBCExecutionContext context = openContext(monitor, DBCExecutionPurpose.UTIL, "Set active schema");
        try {
            JDBCUtils.executeSQL(context, String.format(SET_CURRENT_SCHEMA, activeSchemaName));
        } catch (SQLException e) {
            throw new DBException(e);
        } finally {
            context.close();
        }

        // Send notifications
        if (oldSelectedEntity != null) {
            DBUtils.fireObjectSelect(oldSelectedEntity, false);
        }
        if (this.activeSchemaName != null) {
            DBUtils.fireObjectSelect(object, true);
        }
    }

    // --------------
    // Plan Tables
    // --------------

    @Override
    public DBCPlan planQueryExecution(DBCExecutionContext context, String query) throws DBCException
    {
        String ptSchemaname = getExplainTablesSchemaName(context);
        if (ptSchemaname == null) {
            throw new DBCException(DB2Messages.dialog_explain_no_tables_found_ex);
        }
        DB2PlanAnalyser plan = new DB2PlanAnalyser(query, ptSchemaname);
        plan.explain((JDBCExecutionContext) context);
        return plan;
    }

    private String getExplainTablesSchemaName(DBCExecutionContext context) throws DBCException
    {
        // // Schema for explain tables has already been verified. Use it as-is
        // if (CommonUtils.isNotEmpty(schemaForExplainTables)) {
        // return schemaForExplainTables;
        // }

        DBRProgressMonitor monitor = context.getProgressMonitor();

        // Verify explain table from current authorization id
        String sessionUserSchema;
        try {
            sessionUserSchema = JDBCUtils.queryString((JDBCExecutionContext) context, GET_SESSION_USER).trim();
        } catch (SQLException e) {
            throw new DBCException(e);
        }
        Boolean ok = DB2Utils.checkExplainTables(monitor, this, sessionUserSchema);
        if (ok) {
            LOG.debug("Valid explain tables found in " + sessionUserSchema);
            schemaForExplainTables = sessionUserSchema;
            return schemaForExplainTables;
        }

        // Verify explain table from SYSTOOLS
        ok = DB2Utils.checkExplainTables(monitor, this, DB2Constants.EXPLAIN_SCHEMA_NAME_DEFAULT);
        if (ok) {
            LOG.debug("Valid explain tables found in " + DB2Constants.EXPLAIN_SCHEMA_NAME_DEFAULT);
            schemaForExplainTables = DB2Constants.EXPLAIN_SCHEMA_NAME_DEFAULT;
            return schemaForExplainTables;
        }

        // No valid explain tables found, propose to create them in current authId
        String msg = String.format(DB2Messages.dialog_explain_ask_to_create, sessionUserSchema);
        if (!UIUtils.confirmAction(DBeaverUI.getActiveWorkbenchShell(), DB2Messages.dialog_explain_no_tables, msg)) {
            return null;
        }

        // Ask the user in what tablespace to create the Explain tables
        try {
            List<String> listTablespaces = DB2Utils.getListOfUsableTsForExplain(monitor, (JDBCExecutionContext) context);

            // NO Usable Tablespace found: End of the game..
            if (listTablespaces.isEmpty()) {
                UIUtils.showErrorDialog(DBeaverUI.getActiveWorkbenchShell(), DB2Messages.dialog_explain_no_tablespace_found_title,
                    DB2Messages.dialog_explain_no_tablespace_found_title);
                return null;
            }

            // Build a dialog with the list of usable tablespaces for the user to choose
            final DB2TablespaceChooser tsChooserDialog = new DB2TablespaceChooser(DBeaverUI.getActiveWorkbenchShell(),
                listTablespaces);
            RunnableWithResult<Integer> confirmer = new RunnableWithResult<Integer>() {
                @Override
                public void run()
                {
                    result = tsChooserDialog.open();
                }
            };
            UIUtils.runInUI(DBeaverUI.getActiveWorkbenchShell(), confirmer);
            if (confirmer.getResult() != IDialogConstants.OK_ID) {
                return null;
            }

            String tablespaceName = tsChooserDialog.getSelectedTablespace();

            // Try to create explain tables within current authorizartionID in given tablespace
            DB2Utils.createExplainTables(context.getProgressMonitor(), this, sessionUserSchema, tablespaceName);

            // Hourra!
            schemaForExplainTables = sessionUserSchema;
        } catch (SQLException e) {
            throw new DBCException(e);
        }

        return sessionUserSchema;
    }

    // --------------
    // Associations
    // --------------

    @Association
    public Collection<DB2Schema> getSchemas(DBRProgressMonitor monitor) throws DBException
    {
        return schemaCache.getObjects(monitor, this);
    }

    public DB2Schema getSchema(DBRProgressMonitor monitor, String name) throws DBException
    {
        return schemaCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DB2DataType> getDataTypes(DBRProgressMonitor monitor) throws DBException
    {
        return dataTypeCache.getObjects(monitor, this);
    }

    public DB2DataType getDataType(DBRProgressMonitor monitor, String name) throws DBException
    {
        return dataTypeCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DB2Tablespace> getTablespaces(DBRProgressMonitor monitor) throws DBException
    {
        return tablespaceCache.getObjects(monitor, this);
    }

    public DB2Tablespace getTablespace(DBRProgressMonitor monitor, String name) throws DBException
    {
        return tablespaceCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DB2StorageGroup> getStorageGroups(DBRProgressMonitor monitor) throws DBException
    {
        return storagegroupCache.getObjects(monitor, this);
    }

    public DB2StorageGroup getStorageGroup(DBRProgressMonitor monitor, String name) throws DBException
    {
        return storagegroupCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DB2Bufferpool> getBufferpools(DBRProgressMonitor monitor) throws DBException
    {
        return bufferpoolCache.getObjects(monitor, this);
    }

    public DB2Bufferpool getBufferpool(DBRProgressMonitor monitor, String name) throws DBException
    {
        return bufferpoolCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DB2Wrapper> getWrappers(DBRProgressMonitor monitor) throws DBException
    {
        return wrapperCache.getObjects(monitor, this);
    }

    public DB2Wrapper getWrapper(DBRProgressMonitor monitor, String name) throws DBException
    {
        return wrapperCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DB2UserMapping> getUserMappings(DBRProgressMonitor monitor) throws DBException
    {
        return userMappingCache.getObjects(monitor, this);
    }

    public DB2UserMapping getUserMapping(DBRProgressMonitor monitor, String name) throws DBException
    {
        return userMappingCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DB2RemoteServer> getRemoteServers(DBRProgressMonitor monitor) throws DBException
    {
        return remoteServerCache.getObjects(monitor, this);
    }

    public DB2RemoteServer getRemoteServer(DBRProgressMonitor monitor, String name) throws DBException
    {
        return remoteServerCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DB2Grantee> getUsers(DBRProgressMonitor monitor) throws DBException
    {
        return userCache.getObjects(monitor, this);
    }

    public DB2Grantee getUser(DBRProgressMonitor monitor, String name) throws DBException
    {
        return userCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DB2Grantee> getGroups(DBRProgressMonitor monitor) throws DBException
    {
        return groupCache.getObjects(monitor, this);
    }

    public DB2Grantee getGroup(DBRProgressMonitor monitor, String name) throws DBException
    {
        return groupCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DB2Role> getRoles(DBRProgressMonitor monitor) throws DBException
    {
        return roleCache.getObjects(monitor, this);
    }

    public DB2Role getRole(DBRProgressMonitor monitor, String name) throws DBException
    {
        return roleCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DB2Variable> getVariables(DBRProgressMonitor monitor) throws DBException
    {
        return variableCache.getObjects(monitor, this);
    }

    public DB2Variable getVariable(DBRProgressMonitor monitor, String name) throws DBException
    {
        return variableCache.getObject(monitor, this, name);
    }

    // -------------
    // Dynamic Data
    // -------------

    public List<DB2Parameter> getDbParameters(DBRProgressMonitor monitor) throws DBException
    {
        return listDBParameters;
    }

    public List<DB2Parameter> getDbmParameters(DBRProgressMonitor monitor) throws DBException
    {
        return listDBMParameters;
    }

    public List<DB2XMLString> getXmlStrings(DBRProgressMonitor monitor) throws DBException
    {
        return listXMLStrings;
    }

    // -------------------------
    // Version Testing
    // -------------------------

    public boolean isAtLeastV9_5()
    {
        return version >= DB2Constants.DB2v9_5;
    }

    public boolean isAtLeastV9_7()
    {
        return version >= DB2Constants.DB2v9_7;
    }

    public boolean isAtLeastV10_1()
    {
        return version >= DB2Constants.DB2v10_1;
    }

    public boolean isAtLeastV10_5()
    {
        return version >= DB2Constants.DB2v10_5;
    }

    // -------------------------
    // Standards Getters
    // -------------------------

    public DBSObjectCache<DB2DataSource, DB2Bufferpool> getBufferpoolCache()
    {
        return bufferpoolCache;
    }

    public DBSObjectCache<DB2DataSource, DB2RemoteServer> getRemoteServerCache()
    {
        return remoteServerCache;
    }

    public DBSObjectCache<DB2DataSource, DB2Schema> getSchemaCache()
    {
        return schemaCache;
    }

    public DBSObjectCache<DB2DataSource, DB2DataType> getDataTypeCache()
    {
        return dataTypeCache;
    }

    public DBSObjectCache<DB2DataSource, DB2Tablespace> getTablespaceCache()
    {
        return tablespaceCache;
    }

    public DBSObjectCache<DB2DataSource, DB2StorageGroup> getStorageGroupCache()
    {
        return storagegroupCache;
    }

    public DBSObjectCache<DB2DataSource, DB2Variable> getVariableCache()
    {
        return variableCache;
    }

    public DBSObjectCache<DB2DataSource, DB2Role> getRoleCache()
    {
        return roleCache;
    }

    public DBSObjectCache<DB2DataSource, DB2Wrapper> getWrapperCache()
    {
        return wrapperCache;
    }

}
