/*
 * Copyright (c) 2011, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ext.oracle;

import org.eclipse.core.runtime.Platform;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.oracle.model.OracleConstants;
import org.jkiss.dbeaver.ext.oracle.model.OracleDataSource;
import org.jkiss.dbeaver.ext.oracle.oci.OCIUtils;
import org.jkiss.dbeaver.ext.oracle.oci.OracleHomeDescriptor;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSourceProvider;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSDataSourceContainer;
import org.jkiss.utils.CommonUtils;

import java.util.*;

public class OracleDataSourceProvider extends JDBCDataSourceProvider implements DBPClientManager {

    private static Map<String,String> connectionsProps;

    static {
        connectionsProps = new HashMap<String, String>();

        // Program name
        connectionsProps.put("v$session.program", "DBeaver " + Platform.getProduct().getDefiningBundle().getVersion());
    }

    public static Map<String,String> getConnectionsProps() {
        return connectionsProps;
    }

    public OracleDataSourceProvider()
    {
    }

    @Override
    protected String getConnectionPropertyDefaultValue(String name, String value) {
        String ovrValue = connectionsProps.get(name);
        return ovrValue != null ? ovrValue : super.getConnectionPropertyDefaultValue(name, value);
    }

    public long getFeatures()
    {
        return FEATURE_SCHEMAS;
    }

    @Override
    public String getConnectionURL(DBPDriver driver, DBPConnectionInfo connectionInfo)
    {
        boolean isOCI = OCIUtils.isOciDriver(driver);
        OracleConstants.ConnectionType connectionType;
        Object conTypeProperty = connectionInfo.getProperties().get(OracleConstants.PROP_CONNECTION_TYPE);
        if (conTypeProperty != null) {
            connectionType = OracleConstants.ConnectionType.valueOf(CommonUtils.toString(conTypeProperty));
        } else {
            connectionType = OracleConstants.ConnectionType.BASIC;
        }

        StringBuilder url = new StringBuilder(100);
        url.append("jdbc:oracle:"); //$NON-NLS-1$
        if (isOCI) {
            url.append("oci"); //$NON-NLS-1$
        } else {
            url.append("thin"); //$NON-NLS-1$
        }
        url.append(":@"); //$NON-NLS-1$
        if ((connectionType == OracleConstants.ConnectionType.TNS || CommonUtils.isEmpty(connectionInfo.getHostName())) && !CommonUtils.isEmpty(connectionInfo.getDatabaseName())) {
            // TNS name specified
            url.append(connectionInfo.getDatabaseName());
        } else {
            // Basic connection info specified
            if (!isOCI) {
                url.append("//"); //$NON-NLS-1$
            }
            if (!CommonUtils.isEmpty(connectionInfo.getHostName())) {
                url.append(connectionInfo.getHostName());
            }
            if (!CommonUtils.isEmpty(connectionInfo.getHostPort())) {
                url.append(":"); //$NON-NLS-1$
                url.append(connectionInfo.getHostPort());
            }
            if (isOCI) {
                url.append(":"); //$NON-NLS-1$
            } else {
                url.append("/"); //$NON-NLS-1$
            }
            if (!CommonUtils.isEmpty(connectionInfo.getDatabaseName())) {
                url.append(connectionInfo.getDatabaseName());
            }
        }
        return url.toString();
    }

    public DBPDataSource openDataSource(
        DBRProgressMonitor monitor, DBSDataSourceContainer container)
        throws DBException
    {
        return new OracleDataSource(container);
    }

    //////////////////////////////////////
    // Client manager

    public Collection<String> findClientHomeIds()
    {
        List<String> homeIds = new ArrayList<String>();
        for (OracleHomeDescriptor home : OCIUtils.getOraHomes()) {
            homeIds.add(home.getHomeId());
        }
        return homeIds;
    }

    public String getDefaultClientHomeId()
    {
        List<OracleHomeDescriptor> oraHomes = OCIUtils.getOraHomes();
        if (!oraHomes.isEmpty()) {
            return oraHomes.get(0).getHomeId();
        }
        return null;
    }

    public DBPClientHome getClientHome(String homeId)
    {
        return new OracleHomeDescriptor(homeId);
    }

}
