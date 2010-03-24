package org.jkiss.dbeaver.runtime;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.progress.IProgressConstants;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.meta.DBMModel;
import org.jkiss.dbeaver.model.meta.DBMNode;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.registry.event.IDataSourceListener;
import org.jkiss.dbeaver.ext.ui.IMetaModelView;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ui.DBeaverUtils;

import java.util.Iterator;

/**
 * ConnectJob
 */
public class RefreshJob extends Job
{
    static Log log = LogFactory.getLog(RefreshJob.class);

    private IWorkbenchPart targetPart;
    private IStructuredSelection structSelection;

    public RefreshJob(
        IWorkbenchPart targetPart,
        IStructuredSelection structSelection)
    {
        super("Refresh selected objects");
        setUser(true);
        //setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
        setProperty(IProgressConstants.KEEPONE_PROPERTY, Boolean.TRUE);
        this.targetPart = targetPart;
        this.structSelection = structSelection;
    }

    protected IStatus run(IProgressMonitor monitor)
    {
        monitor.beginTask("Refresh selected objects ...", 5);
        try {
            int count = 1;
            for (Iterator iter = structSelection.iterator(); iter.hasNext(); ){
                Object object = iter.next();
                monitor.beginTask("Refresh selected object (" + (count++) + ")", 5);
                refreshObject(monitor, object);
                monitor.done();
            }

            return new Status(
                Status.OK,
                DBeaverCore.getInstance().getPluginID(),
                "Refreshed");
        }
        catch (Exception ex) {
            return new Status(
                Status.ERROR,
                DBeaverCore.getInstance().getPluginID(),
                "Error refreshing object: " + ex.getMessage());
        }
    }

    private void refreshObject(IProgressMonitor monitor, Object object)
    {
        if (this.targetPart instanceof IMetaModelView) {
            IMetaModelView view = (IMetaModelView)this.targetPart;
            final DBMModel model = view.getMetaModel();
            DBMNode node = model.findNode(object);
            if (node != null) {
                try {
                    node = node.refreshNode(monitor);
                }
                catch (DBException ex) {
                    DBeaverUtils.showErrorDialog(
                        this.targetPart.getSite().getShell(),
                        "Refresh error",
                        "Can't refresh tree node",
                        ex);
                }
            }
            if (node != null) {
                final DBMNode refNode = node;
                new UIJob("Refresh tree node") {
                    public IStatus runInUIThread(IProgressMonitor monitor)
                    {
                        model.fireNodeRefresh(targetPart, refNode);
                        return Status.OK_STATUS;
                    }
                }.schedule();
            }
        }
    }

    public boolean belongsTo(Object family)
    {
        return structSelection == family;
    }

    protected void canceling()
    {
        getThread().interrupt();
    }

}