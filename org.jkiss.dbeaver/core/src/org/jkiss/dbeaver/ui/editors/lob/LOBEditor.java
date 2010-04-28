/*
 * Copyright (c) 2010, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ui.editors.lob;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.EditorPart;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.ui.IDataSourceUser;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.data.DBDValueController;
import org.jkiss.dbeaver.model.data.DBDValueEditor;
import org.jkiss.dbeaver.model.dbc.DBCSession;
import org.jkiss.dbeaver.model.struct.DBSDataSourceContainer;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.SortedMap;

/**
 * LOBEditor
 */
public class LOBEditor extends EditorPart implements IDataSourceUser, DBDValueEditor
{
    static Log log = LogFactory.getLog(LOBEditor.class);

    private LOBEditorInput lobInput;
    private boolean valueEditorRegistered = false;

    private LOBTextEditor textEditor;

    public static boolean openEditor(DBDValueController valueController)
    {
        LOBEditorInput editorInput = new LOBEditorInput(valueController);
        try {
            valueController.getValueSite().getWorkbenchWindow().getActivePage().openEditor(
            editorInput,
            LOBEditor.class.getName());
        }
        catch (PartInitException e) {
            log.error("Could not open LOB editor", e);
            return false;
        }
        return true;
    }

    public void doSave(IProgressMonitor monitor)
    {
    }

    public void doSaveAs()
    {
    }

    public void init(IEditorSite site, IEditorInput input)
        throws PartInitException
    {
        if (!(input instanceof LOBEditorInput)) {
            throw new PartInitException("Invalid Input: Must be LOBEditorInput");
        }

        this.lobInput = (LOBEditorInput)input;
        // Save data to file
        try {
            site.getWorkbenchWindow().run(false, true, new IRunnableWithProgress() {
                public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                    try {
                        lobInput.saveDataToFile(monitor);
                    } catch (CoreException e) {
                        throw new InvocationTargetException(e);
                    }
                }
            });
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException) {
                e = ((InvocationTargetException)e).getTargetException();
            }
            throw new PartInitException("Could not extract LOB data", e);
        }

        setSite(site);
        setInput(input);
        setPartName(this.lobInput.getName());
        setTitleImage(this.lobInput.getImageDescriptor().createImage());

        getValueController().registerEditor(this);
        valueEditorRegistered = true;

        textEditor = new LOBTextEditor();
        textEditor.init(site, input);
    }

    public void dispose()
    {
        if (valueEditorRegistered) {
            getValueController().unregisterEditor(this);
            valueEditorRegistered = false;
        }
        if (lobInput != null) {
            // Release LOB input resources
            try {
                lobInput.release(new NullProgressMonitor());
            } catch (Throwable e) {
                log.warn("Error releasing LOB input", e);
            }
            lobInput = null;
        }
        if (textEditor != null) {
            textEditor.dispose();
            textEditor = null;
        }
        super.dispose();
    }

    public boolean isDirty()
    {
        return false;
    }

    public boolean isSaveAsAllowed()
    {
        return false;
    }

    public void createPartControl(Composite parent)
    {
        Composite panel = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        panel.setLayout(layout);
        GridData gd = new GridData(GridData.FILL_BOTH);
        panel.setLayoutData(gd);

/*
        infoPanel = new ColumnInfoPanel(panel, SWT.NONE, getValueController()) {
            @Override
            protected void createInfoItems(Tree infoTree, DBDValueController valueController)
            {
                TreeItem columnTypeItem = new TreeItem(infoTree, SWT.NONE);
                columnTypeItem.setText(new String[] {
                    "Maximum Length",
                    String.valueOf(valueController.getColumnMetaData().getDisplaySize()) });
            }

        };

*/
        textEditor.createPartControl(panel);

/*
        Text valuePanel = new Text(panel, SWT.BORDER);
        gd = new GridData(GridData.FILL_BOTH);
        valuePanel.setLayoutData(gd);
*/

        createToolbar(panel);
    }

    private void createToolbar(Composite panel) {
        Composite toolbarGroup = new Composite(panel, SWT.NONE);
        {
            GridData gd = new GridData(GridData.FILL_HORIZONTAL);
            gd.horizontalIndent = 0;
            gd.verticalIndent = 0;
            toolbarGroup.setLayoutData(gd);
            GridLayout layout = new GridLayout(2, false);
            layout.marginHeight = 0;
            layout.marginWidth = 0;
            toolbarGroup.setLayout(layout);
        }
        {
            Composite contentGroup = new Composite(toolbarGroup, SWT.NONE);

            GridData gd = new GridData(GridData.FILL_HORIZONTAL);
            contentGroup.setLayoutData(gd);
            RowLayout layout = new RowLayout(SWT.HORIZONTAL);
            layout.center = true;
            contentGroup.setLayout(layout);

            // Content length
            Label label = new Label(contentGroup, SWT.NONE);
            label.setText("Content Length: ");
            Text text = new Text(contentGroup, SWT.BORDER | SWT.READ_ONLY);
            text.setText("1000");

            // Content type
            label = new Label(contentGroup, SWT.NONE);
            label.setText("Content Type: ");
            Combo ctCombo = new Combo(contentGroup, SWT.READ_ONLY);
            ctCombo.add("Binary");
            ctCombo.add("Text");
            ctCombo.add("Image");
            ctCombo.select(0);

            // Content sub type
            label = new Label(contentGroup, SWT.NONE);
            label.setText("Sub Type: ");
            Combo subTypeCombo = new Combo(contentGroup, SWT.READ_ONLY);

            // Content sub type
            label = new Label(contentGroup, SWT.NONE);
            label.setText("Encoding: ");
            Combo encodingCombo = new Combo(contentGroup, SWT.READ_ONLY);
            encodingCombo.setVisibleItemCount(30);
            SortedMap<String,Charset> charsetMap = Charset.availableCharsets();
            int index = 0;
            int defIndex = -1;
            for (String csName : charsetMap.keySet()) {
                Charset charset = charsetMap.get(csName);
                encodingCombo.add(charset.displayName());
                if (charset.equals(Charset.defaultCharset())) {
                    defIndex = index;
                }
                index++;
            }
            if (defIndex >= 0) {
                encodingCombo.select(defIndex);
            }
        }

        {
            Composite controlGroup = new Composite(toolbarGroup, SWT.NONE);

            GridData gd = new GridData();
            gd.horizontalAlignment = SWT.RIGHT;
            controlGroup.setLayoutData(gd);
            RowLayout layout = new RowLayout(SWT.HORIZONTAL);
            layout.center = true;
            layout.fill = true;
            controlGroup.setLayout(layout);

            Button infoButton = new Button(controlGroup, SWT.PUSH);
            infoButton.setText("Info");

            Button saveButton = new Button(controlGroup, SWT.PUSH);
            saveButton.setText("Save");

            Button closeButton = new Button(controlGroup, SWT.PUSH);
            closeButton.setText("Close");
        }
    }

    public DBDValueController getValueController()
    {
        return lobInput == null ? null : lobInput.getValueController();
    }

    public void showValueEditor()
    {
        this.getEditorSite().getWorkbenchWindow().getActivePage().activate(this);
    }

    public void closeValueEditor()
    {
        IWorkbenchPage workbenchPage = this.getEditorSite().getWorkbenchWindow().getActivePage();
        if (workbenchPage != null) {
            workbenchPage.closeEditor(this, false);
        } else {
            if (valueEditorRegistered) {
                getValueController().unregisterEditor(this);
                valueEditorRegistered = false;
            }
        }
    }

    public void setFocus()
    {
    }

    public DBSDataSourceContainer getDataSourceContainer() {
        DBPDataSource dataSource = getDataSource();
        return dataSource == null ? null : dataSource.getContainer();
    }

    public DBPDataSource getDataSource() {
        try {
            return getSession().getDataSource();
        }
        catch (DBException e) {
            log.error("Could not obtain session reference", e);
            return null;
        }
    }

    public DBCSession getSession() throws DBException {
        DBDValueController valueController = getValueController();
        if (valueController == null) {
            throw new DBException("No value controller");
        }
        return valueController.getSession();
    }

}