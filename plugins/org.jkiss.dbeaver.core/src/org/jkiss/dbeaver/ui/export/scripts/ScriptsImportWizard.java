/*
 * Copyright (c) 2012, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ui.export.scripts;

import org.jkiss.utils.CommonUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.core.CoreMessages;
import org.jkiss.dbeaver.model.impl.project.ScriptsHandlerImpl;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.DBRRunnableWithProgress;
import org.jkiss.dbeaver.runtime.RuntimeUtils;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.editors.sql.SQLEditorInput;
import org.jkiss.dbeaver.utils.ContentUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

public class ScriptsImportWizard extends Wizard implements IImportWizard {

    static final Log log = LogFactory.getLog(ScriptsImportWizard.class);
    private ScriptsImportWizardPage pageMain;

    public ScriptsImportWizard() {
	}

	@Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        setWindowTitle(CoreMessages.dialog_scripts_import_wizard_window_title);
        setNeedsProgressMonitor(true);
    }

    @Override
    public void addPages() {
        super.addPages();
        pageMain = new ScriptsImportWizardPage();
        addPage(pageMain);
        //addPage(new ProjectImportWizardPageFinal(data));
    }

	@Override
	public boolean performFinish() {
        final ScriptsImportData importData = pageMain.getImportData();
        final ScriptsImporter importer = new ScriptsImporter(importData);
        try {
            RuntimeUtils.run(getContainer(), true, true, importer);
        }
        catch (InterruptedException ex) {
            return false;
        }
        catch (InvocationTargetException ex) {
            UIUtils.showErrorDialog(
                getShell(),
                CoreMessages.dialog_scripts_import_wizard_dialog_error_title,
                CoreMessages.dialog_scripts_import_wizard_dialog_error_text,
                ex.getTargetException());
            return false;
        }
        if (importer.getImportedCount() <= 0) {
            UIUtils.showMessageBox(getShell(), CoreMessages.dialog_scripts_import_wizard_dialog_message_title, CoreMessages.dialog_scripts_import_wizard_dialog_message_no_scripts, SWT.ICON_WARNING);
            return false;
        } else {
            UIUtils.showMessageBox(getShell(), CoreMessages.dialog_scripts_import_wizard_dialog_message_title, importer.getImportedCount() + CoreMessages.dialog_scripts_import_wizard_dialog_message_success_imported, SWT.ICON_INFORMATION);
            return true;
        }
	}

    private int importScripts(DBRProgressMonitor monitor, ScriptsImportData importData) throws IOException, DBException, CoreException
    {
        List<Pattern> masks = new ArrayList<Pattern>();
        StringTokenizer st = new StringTokenizer(importData.getFileMasks(), ",; "); //$NON-NLS-1$
        while (st.hasMoreTokens()) {
            String mask = st.nextToken().trim();
            if (!CommonUtils.isEmpty(mask)) {
                mask = mask.replace("*", ".*"); //$NON-NLS-1$ //$NON-NLS-2$
                masks.add(Pattern.compile(mask));
            }
        }
        List<File> filesToImport = new ArrayList<File>();
        collectFiles(importData.getInputDir(), masks, filesToImport);
        if (filesToImport.isEmpty()) {
            return 0;
        }
        // Use null monitor for resource actions to not break our main monitor
        final IProgressMonitor nullMonitor = new NullProgressMonitor();
        // Import scripts
        monitor.beginTask(CoreMessages.dialog_scripts_import_wizard_monitor_import_scripts, filesToImport.size());
        for (File file : filesToImport) {
            // Create dirs
            monitor.subTask(file.getName());
            List<File> path = new ArrayList<File>();
            for (File parent = file.getParentFile(); !parent.equals(importData.getInputDir()); parent = parent.getParentFile()) {
                path.add(0, parent);
            }
            // Get target dir
            IFolder targetDir = (IFolder)importData.getImportDir().getResource();
            for (File folder : path) {
                targetDir = targetDir.getFolder(folder.getName());
                if (!targetDir.exists()) {
                    targetDir.create(true, true, nullMonitor);
                }
            }
            String targetName = file.getName();
            if (!targetName.toLowerCase().endsWith("." + ScriptsHandlerImpl.SCRIPT_FILE_EXTENSION)) { //$NON-NLS-1$
                targetName += "." + ScriptsHandlerImpl.SCRIPT_FILE_EXTENSION; //$NON-NLS-1$
            }

            final IFile targetFile = targetDir.getFile(targetName);
            // Copy file
            FileInputStream in = new FileInputStream(file);
            try {
                targetFile.create(in, true, nullMonitor);
            } finally {
                ContentUtils.close(in);
            }
            // Set datasource
            if (importData.getDataSourceContainer() != null) {
                SQLEditorInput.setScriptDataSource(targetFile, importData.getDataSourceContainer());
            }
            // Done
            monitor.worked(1);
        }
        monitor.done();

        return filesToImport.size();
    }

    private void collectFiles(File inputDir, List<Pattern> masks, List<File> filesToImport)
    {
        for (File file : inputDir.listFiles()) {
            if (file.isDirectory()) {
                collectFiles(file, masks, filesToImport);
            } else {
                boolean matched = false;
                for (Pattern mask : masks) {
                    if (mask.matcher(file.getName()).matches()) {
                        matched = true;
                        break;
                    }
                }
                if (matched) {
                    filesToImport.add(file);
                }
            }
        }
    }


    private class ScriptsImporter implements DBRRunnableWithProgress {
        private final ScriptsImportData importData;
        private int importedCount;

        public ScriptsImporter(ScriptsImportData importData)
        {
            this.importData = importData;
        }

        public int getImportedCount()
        {
            return importedCount;
        }

        @Override
        public void run(DBRProgressMonitor monitor) throws InvocationTargetException, InterruptedException
        {
            try {
                importedCount = importScripts(monitor, importData);
            } catch (Exception e) {
                throw new InvocationTargetException(e);
            }
        }
    }
}
