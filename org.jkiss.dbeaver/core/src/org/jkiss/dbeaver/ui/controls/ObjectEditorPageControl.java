/*
 * Copyright (c) 2010, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ui.controls;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.part.MultiPageEditorSite;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.ext.ui.IDatabaseObjectEditor;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.DBRRunnableWithProgress;
import org.jkiss.dbeaver.ui.DBIcon;
import org.jkiss.dbeaver.ui.UIUtils;

import java.lang.reflect.InvocationTargetException;

public class ObjectEditorPageControl extends ProgressPageControl {

    private Button saveChangesButton;
/*
    private Button viewChangesButton;
    private Button resetChangesButton;
*/

    public ObjectEditorPageControl(Composite parent, int style, IDatabaseObjectEditor workbenchPart)
    {
        super(parent, style, workbenchPart);

        getMainEditorPart().addPropertyListener(new IPropertyListener() {
            public void propertyChanged(Object source, int propId)
            {
                if (propId == IEditorPart.PROP_DIRTY) {
                    boolean dirty = ((IEditorPart) source).isDirty();
                    saveChangesButton.setEnabled(dirty);
                    //viewChangesButton.setEnabled(dirty);
                    //resetChangesButton.setEnabled(dirty);
                }
            }
        });
    }

    public IDatabaseObjectEditor getEditorPart()
    {
        return (IDatabaseObjectEditor) getWorkbenchPart();
    }

    public IEditorPart getMainEditorPart()
    {
        IWorkbenchPart part = getWorkbenchPart();
        IWorkbenchPartSite site = part.getSite();
        if (site instanceof MultiPageEditorSite) {
            return ((MultiPageEditorSite)site).getMultiPageEditor();
        } else {
            return (IEditorPart)part;
        }
    }

    @Override
    protected Composite createProgressPanel(Composite container) {
        Composite panel = super.createProgressPanel(container);

/*
        viewChangesButton = new Button(panel, SWT.PUSH);
        viewChangesButton.setText("Preview");
        viewChangesButton.setImage(DBIcon.SQL_SCRIPT_EXECUTE.getImage());
        viewChangesButton.setToolTipText("View all changes as script");
        viewChangesButton.setEnabled(false);
        viewChangesButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {

            }
        });
*/
        saveChangesButton = new Button(panel, SWT.PUSH);
        saveChangesButton.setText("Save / Preview");
        saveChangesButton.setImage(DBIcon.SAVE_TO_DATABASE.getImage());
        saveChangesButton.setToolTipText("Persist all changes");
        saveChangesButton.setEnabled(false);
        saveChangesButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                try {
                    getMainEditorPart().getSite().getWorkbenchWindow().run(true, true, new IRunnableWithProgress() {
                        public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException
                        {
                            getMainEditorPart().doSave(monitor);
                        }
                    });
                } catch (InvocationTargetException e1) {
                    UIUtils.showErrorDialog(getShell(), "Save DB object", null, e1.getTargetException());
                } catch (InterruptedException e1) {
                    // do nothing
                }
/*
                DBeaverCore.getInstance().runAndWait(new DBRRunnableWithProgress() {
                    public void run(DBRProgressMonitor monitor) throws InvocationTargetException, InterruptedException
                    {

                    }
                });
*/
            }
        });
/*
        resetChangesButton = new Button(panel, SWT.PUSH);
        resetChangesButton.setText("Reset");
        resetChangesButton.setImage(DBIcon.REJECT.getImage());
        resetChangesButton.setToolTipText("Reset all changes");
        resetChangesButton.setEnabled(false);
        resetChangesButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                try {
                    DBeaverCore.getInstance().runAndWait2(new DBRRunnableWithProgress() {
                        public void run(DBRProgressMonitor monitor) throws InvocationTargetException, InterruptedException
                        {
                            getEditorPart().getObjectManager().resetChanges(monitor);
                            getEditorPart().resetObjectChanges();
                        }
                    });
                } catch (InvocationTargetException e1) {
                    log.error("Error resetting editor's content", e1.getTargetException());
                } catch (InterruptedException e1) {
                    // do nothing
                }
            }
        });
*/
        return panel;
    }

}
