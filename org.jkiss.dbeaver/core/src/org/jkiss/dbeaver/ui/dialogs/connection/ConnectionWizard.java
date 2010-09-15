/*
 * Copyright (c) 2010, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ui.dialogs.connection;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBPConnectionInfo;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceProvider;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.DBRRunnableWithProgress;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.registry.DriverDescriptor;
import org.jkiss.dbeaver.utils.DBeaverUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * This is a sample new wizard.
 */

public abstract class ConnectionWizard extends Wizard implements INewWizard
{
    protected ConnectionWizard() {
        setNeedsProgressMonitor(true);
    }

    public boolean performFinish()
    {
        if (getPageSettings() != null) {
            getPageSettings().saveSettings();
        }
        return true;
    }

    abstract ConnectionPageFinal getPageFinal();

    abstract DriverDescriptor getSelectedDriver();

    public abstract ConnectionPageSettings getPageSettings();

    public void testConnection(final DBPConnectionInfo connectionInfo)
    {
        DBRRunnableWithProgress op = new DBRRunnableWithProgress()
        {
            public void run(DBRProgressMonitor monitor)
                throws InvocationTargetException, InterruptedException
            {
                monitor.beginTask("Obtain connection", 3);
                Thread.currentThread().setName("Test datasource connection");

                DriverDescriptor driver = getSelectedDriver();
                DBPDataSourceProvider provider;
                try {
                    provider = driver.getProviderDescriptor().getInstance();
                }
                catch (DBException ex) {
                    throw new InvocationTargetException(ex);
                }
                DataSourceDescriptor container = new DataSourceDescriptor("test", driver, connectionInfo);
                try {
                    monitor.worked(1);
                    DBPDataSource dataSource = provider.openDataSource(monitor, container);
                    monitor.worked(1);
                    if (dataSource == null) {
                        throw new InvocationTargetException(
                            new DBException("Internal error: null datasource returned from provider " + provider));
                    } else {
                        monitor.subTask("Test connection");
                        try {
                            // test connection
                            dataSource.invalidateConnection(monitor);
                            monitor.done();
                        }
                        finally {
                            monitor.subTask("Close connection");
                            dataSource.close(monitor);
                        }
                    }
                    monitor.subTask("Success");
                }
                catch (DBException ex) {
                    throw new InvocationTargetException(ex);
                }
                finally {
                    container.dispose();
                }
            }
        };

        try {
            DBeaverUtils.run(getContainer(), true, true, op);

            MessageDialog.openInformation(
                getShell(), "Success", "Successfully connected!");
        }
        catch (InterruptedException ex) {
            DBeaverUtils.showErrorDialog(
                getShell(),
                "Interrupted",
                "Test interrupted");
        }
        catch (InvocationTargetException ex) {
            DBeaverUtils.showErrorDialog(
                getShell(),
                "Connection error",
                "Database connectivity error",
                ex.getTargetException());
        }
    }

    public void changePage(Object currentPage, Object targetPage)
    {
        if (currentPage instanceof ConnectionPageSettings) {
            ((ConnectionPageSettings) currentPage).deactivate();
        }

        if (targetPage instanceof ConnectionPageFinal) {
            ((ConnectionPageFinal) targetPage).activate();
        }
        if (targetPage instanceof ConnectionPageSettings) {
            ((ConnectionPageSettings) targetPage).activate();
        }
    }

}