/*
 * Copyright (c) 2010, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ui.editors.sql.plan;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.plan.DBCExecutionPlanBuilder;
import org.jkiss.dbeaver.model.exec.plan.DBCPlan;
import org.jkiss.dbeaver.model.exec.plan.DBCPlanNode;
import org.jkiss.dbeaver.ui.DBIcon;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.controls.resultset.ResultSetViewer;
import org.jkiss.dbeaver.ui.editors.sql.SQLEditor;

import java.util.Collection;

/**
 * ResultSetViewer
 */
public class ExplainPlanViewer extends Viewer implements IPropertyChangeListener
{
    static final Log log = LogFactory.getLog(ResultSetViewer.class);

    private SQLEditor editor;
    private Composite planPanel;
    private Tree planTree;
    private Label statusLabel;

    private ToolItem itemNext;
    private ToolItem itemPrevious;
    private ToolItem itemFirst;
    private ToolItem itemLast;
    private ToolItem itemRefresh;

    private DBCExecutionPlanBuilder planBuilder;

    public ExplainPlanViewer(SQLEditor editor, Composite parent)
    {
        super();
        this.editor = editor;
        this.planPanel = UIUtils.createPlaceholder(parent, 1);

        this.planTree = new Tree(
            planPanel,
            SWT.FULL_SELECTION | SWT.VIRTUAL | SWT.H_SCROLL | SWT.V_SCROLL);
        planTree.setHeaderVisible(true);
        planTree.setLinesVisible(true);
        GridData gd = new GridData(GridData.FILL_BOTH);
        planTree.setLayoutData(gd);

        planTree.addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent e)
            {
                dispose();
            }
        });
        planTree.addPaintListener(new PaintListener() {
            public void paintControl(PaintEvent e)
            {
                if (planBuilder == null) {
                    Rectangle bounds = planTree.getBounds();
                    String message;
                    if (getDataSource() != null) {
                        message = "Data provider doesn't support execution plan";
                    } else {
                        message = "Not connected to database";
                    }
                    Point ext = e.gc.textExtent(message);
                    e.gc.drawText(message, (bounds.width - ext.x) / 2, (bounds.height - planTree.getHeaderHeight()) / 2);
                }
            }
        });
        planTree.setLayout(new GridLayout(1, true));
        TreeColumn nameColumn = new TreeColumn(planTree, SWT.LEFT);
        nameColumn.setText("Name");

        TreeColumn objectColumn = new TreeColumn(planTree, SWT.LEFT);
        objectColumn.setText("Object");

        //createStatusBar(planPanel);

        nameColumn.pack();
        objectColumn.pack();
        planTree.pack();
    }

    private DBPDataSource getDataSource()
    {
        return editor.getDataSource();
    }

    private void createStatusBar(Composite parent)
    {
        Composite statusBar = new Composite(parent, SWT.NONE);
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        statusBar.setLayoutData(gd);
        GridLayout gl = new GridLayout(2, false);
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        statusBar.setLayout(gl);

        statusLabel = new Label(statusBar, SWT.NONE);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        statusLabel.setLayoutData(gd);

        {
            ToolBar toolBar = new ToolBar(statusBar, SWT.FLAT | SWT.HORIZONTAL);
            gd = new GridData(GridData.HORIZONTAL_ALIGN_END);
            toolBar.setLayoutData(gd);
            new ToolItem(toolBar, SWT.SEPARATOR);

            itemRefresh = UIUtils.createToolItem(toolBar, "Refresh", DBIcon.RS_REFRESH, new SelectionAdapter() {
                public void widgetSelected(SelectionEvent e)
                {
                    refresh();
                }
            });
        }
    }

    public void dispose()
    {
        if (!planTree.isDisposed()) {
            planTree.dispose();
        }
        //statusLabel.dispose();
    }

    public Control getControl()
    {
        return planPanel;
    }

    public Object getInput()
    {
        return null;
    }

    public void setInput(Object input)
    {
    }

    public ISelection getSelection()
    {
        return new StructuredSelection(planTree.getSelection());
    }

    public void setSelection(ISelection selection, boolean reveal)
    {
    }

    public void refresh()
    {
        // Refresh plan
        DBPDataSource dataSource = getDataSource();
        if (dataSource instanceof DBCExecutionPlanBuilder) {
            planBuilder = (DBCExecutionPlanBuilder)dataSource;
        } else {
            planBuilder = null;
        }
        planTree.removeAll();
        UIUtils.packColumns(planTree, false);
    }

    public void propertyChange(PropertyChangeEvent event)
    {
    }

    public void explainQueryPlan(DBCExecutionContext context, String query) throws DBCException
    {
        if (planBuilder == null) {
            throw new DBCException("This datasource doesn't support execution plans");
        }
        DBCPlan plan = planBuilder.prepareExecutionPlan(query);
        final Collection<? extends DBCPlanNode> nodes = plan.explain(context);
        Display.getDefault().asyncExec(new Runnable() {
            public void run()
            {
                planTree.removeAll(); 
                for (DBCPlanNode node : nodes) {
                    TreeItem item = new TreeItem(planTree, SWT.NONE);
                    item.setText(node.getObjectName());
                }
            }
        });
    }
}