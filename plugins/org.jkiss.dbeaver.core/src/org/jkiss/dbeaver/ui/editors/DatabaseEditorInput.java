/*
 * Copyright (c) 2011, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ui.editors;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.model.IWorkbenchAdapter;
import org.eclipse.ui.model.WorkbenchAdapter;
import org.jkiss.dbeaver.ext.IDatabaseEditorInput;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * DatabaseEditorInput
 */
public abstract class DatabaseEditorInput<NODE extends DBNDatabaseNode> implements IDatabaseEditorInput
{
    private NODE node;
    private String defaultPageId;

    protected DatabaseEditorInput(NODE node)
    {
        this.node = node;
    }

    public boolean exists()
    {
        return false;
    }

    public ImageDescriptor getImageDescriptor()
    {
        return ImageDescriptor.createFromImage(node.getNodeIconDefault());
    }

    public String getName()
    {
        return node.getNodePathName();
    }

    public IPersistableElement getPersistable()
    {
        return null;
    }

    public String getToolTipText()
    {
        return node.getNodeDescription();
    }

    public Object getAdapter(Class adapter)
    {
        if (IWorkbenchAdapter.class.equals(adapter)) {
            return new WorkbenchAdapter() {
                public ImageDescriptor getImageDescriptor(Object object) {
                    return ImageDescriptor.createFromImage(node.getNodeIconDefault());
                }
                public String getLabel(Object o) {
                    return node.getName();
                }
                public Object getParent(Object o) {
                    return node.getParentNode();
                }
            };
        }

        return null;
    }

    public DBPDataSource getDataSource() {
        DBSObject object = node.getObject();
        return object == null ? null : object.getDataSource();
    }

    public NODE getTreeNode()
    {
        return node;
    }

    public DBSObject getDatabaseObject()
    {
        return node.getObject();
    }

    public String getDefaultPageId()
    {
        return defaultPageId;
    }

    public void setDefaultPageId(String defaultPageId)
    {
        this.defaultPageId = defaultPageId;
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj == this ||
            (obj instanceof DatabaseEditorInput && ((DatabaseEditorInput<?>)obj).node.equals(node));
    }

}
