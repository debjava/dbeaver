/*
 * Copyright (c) 2011, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ext.oracle.model;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.IDatabasePersistAction;
import org.jkiss.dbeaver.ext.oracle.model.source.OracleSourceObject;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCExecutionContext;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.LazyProperty;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.meta.PropertyGroup;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityConstraintType;
import org.jkiss.utils.CommonUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * OracleView
 */
public class OracleView extends OracleTableBase implements OracleSourceObject
{

    public static final DBSEntityConstraintType CONSTRAINT_WITH_CHECK_OPTION = new DBSEntityConstraintType("V", "With Check Option", false, false);
    public static final DBSEntityConstraintType CONSTRAINT_WITH_READ_ONLY = new DBSEntityConstraintType("O", "With Read Only", false, false);

    public static class AdditionalInfo extends TableAdditionalInfo {
        private String text;
        private String typeText;
        private String oidText;
        private String typeName;
        private OracleView superView;

        @Property(name = "Definition", hidden = true, editable = true, updatable = true, order = -1)
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getTypeText() { return typeText; }
        public void setTypeText(String typeText) { this.typeText = typeText; }
        public String getOidText() { return oidText; }
        public void setOidText(String oidText) { this.oidText = oidText; }
        public String getTypeName() { return typeName; }
        public void setTypeName(String typeName) { this.typeName = typeName; }
        @Property(name = "Super View", viewable = false, editable = true, order = 5)
        public OracleView getSuperView() { return superView; }
        public void setSuperView(OracleView superView) { this.superView = superView; }
    }

    private final AdditionalInfo additionalInfo = new AdditionalInfo();

    public OracleView(OracleSchema schema, String name)
    {
        super(schema, name, false);
    }

    public OracleView(
        OracleSchema schema,
        ResultSet dbResult)
    {
        super(schema, dbResult);
    }

    @Property(name = "View Name", viewable = true, editable = true, valueTransformer = DBObjectNameCaseTransformer.class, order = 1)
    @Override
    public String getName()
    {
        return super.getName();
    }

    public boolean isView()
    {
        return true;
    }

    public OracleSchema getSchema()
    {
        return getContainer();
    }

    public OracleSourceType getSourceType()
    {
        return OracleSourceType.VIEW;
    }

    @Property(name = "Declaration", hidden = true, editable = true, updatable = true, order = -1)
    public String getSourceDeclaration(DBRProgressMonitor monitor) throws DBException
    {
        return getAdditionalInfo(monitor).getText();
    }

    public void setSourceDeclaration(String source)
    {
        if (source == null) {
            additionalInfo.loaded = false;
        } else {
            additionalInfo.setText(source);
        }
    }

    public AdditionalInfo getAdditionalInfo()
    {
        return additionalInfo;
    }

    @Override
    protected String getTableTypeName()
    {
        return "VIEW";
    }

    @PropertyGroup()
    @LazyProperty(cacheValidator = AdditionalInfoValidator.class)
    public AdditionalInfo getAdditionalInfo(DBRProgressMonitor monitor) throws DBException
    {
        synchronized (additionalInfo) {
            if (!additionalInfo.loaded && monitor != null) {
                loadAdditionalInfo(monitor);
            }
            return additionalInfo;
        }
    }

    @Override
    public boolean refreshEntity(DBRProgressMonitor monitor) throws DBException
    {
        additionalInfo.loaded = false;
        super.refreshEntity(monitor);
        return true;
    }

    public void refreshObjectState(DBRProgressMonitor monitor) throws DBCException
    {
        this.valid = OracleUtils.getObjectStatus(monitor, this, OracleObjectType.VIEW);
    }

    private void loadAdditionalInfo(DBRProgressMonitor monitor) throws DBException
    {
        if (!isPersisted()) {
            additionalInfo.loaded = true;
            return;
        }
        JDBCExecutionContext context = getDataSource().openContext(monitor, DBCExecutionPurpose.META, "Load table status");
        try {
            JDBCPreparedStatement dbStat = context.prepareStatement(
                "SELECT TEXT,TYPE_TEXT,OID_TEXT,VIEW_TYPE_OWNER,VIEW_TYPE,SUPERVIEW_NAME\n" +
                "FROM SYS.ALL_VIEWS WHERE OWNER=? AND VIEW_NAME=?");
            try {
                dbStat.setString(1, getContainer().getName());
                dbStat.setString(2, getName());
                JDBCResultSet dbResult = dbStat.executeQuery();
                try {
                    if (dbResult.next()) {
                        additionalInfo.setText(JDBCUtils.safeGetString(dbResult, "TEXT"));
                        additionalInfo.setTypeText(JDBCUtils.safeGetString(dbResult, "TYPE_TEXT"));
                        additionalInfo.setOidText(JDBCUtils.safeGetString(dbResult, "OID_TEXT"));
                        additionalInfo.setTypeName(JDBCUtils.safeGetString(dbResult, "VIEW_TYPE"));

                        String superViewName = JDBCUtils.safeGetString(dbResult, "SUPERVIEW_NAME");
                        if (!CommonUtils.isEmpty(superViewName)) {
                            additionalInfo.setSuperView(getContainer().getView(monitor, superViewName));
                        }
                    }
                    additionalInfo.loaded = true;
                } finally {
                    dbResult.close();
                }
            } finally {
                dbStat.close();
            }
        }
        catch (SQLException e) {
            throw new DBCException(e);
        }
        finally {
            context.close();
        }
    }

    public IDatabasePersistAction[] getCompileActions()
    {
        return new IDatabasePersistAction[] {
            new OracleObjectPersistAction(
                OracleObjectType.VIEW,
                "Compile view",
                "ALTER VIEW " + getFullQualifiedName() + " COMPILE"
            )};
    }

}
