/*
 * Copyright (c) 2011, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.model.navigator;

import net.sf.jkiss.utils.CommonUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.IAdapterManager;
import org.eclipse.core.runtime.Platform;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.model.DBPObject;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DBNModel.
 * Contains all objects which are shown in navigator tree.
 * Also ties DBSObjects to thee model (DBNNode).
 *
 * It's strongly recommended to not put the same DBSObject in tree model multiple times.
 * It will work but some actions will not work well
 * (e.g. TreeViewer sometimes update only first TreeItem corresponding to model certain model object).
 */
public class DBNModel implements IResourceChangeListener {
    static final Log log = LogFactory.getLog(DBNModel.class);

    private DBNRoot root;
    private final List<IDBNListener> listeners = new ArrayList<IDBNListener>();
    private transient IDBNListener[] listenersCopy = null;
    private Map<DBSObject, Object> nodeMap = new HashMap<DBSObject, Object>();
    private DBNAdapterFactory nodesAdapter;

    public DBNModel()
    {
        this.root = new DBNRoot(this);

        // Add all existing projects to root node
        final DBeaverCore core = DBeaverCore.getInstance();
        for (IProject project : core.getLiveProjects()) {
            root.addProject(project, false);
        }
/*
        for (DataSourceDescriptor dataSource : registry.getDataSources()) {
            root.addProject(dataSource);
        }
*/

        core.getWorkspace().addResourceChangeListener(this);

        nodesAdapter = new DBNAdapterFactory();
        IAdapterManager mgr = Platform.getAdapterManager();
        mgr.registerAdapters(nodesAdapter, DBNNode.class);
        mgr.registerAdapters(nodesAdapter, DBPObject.class);
    }

    public void dispose()
    {
        Platform.getAdapterManager().unregisterAdapters(nodesAdapter);
        DBeaverCore.getInstance().getWorkspace().removeResourceChangeListener(this);
        this.root.dispose(false);
        this.nodeMap.clear();
        if (!listeners.isEmpty()) {
            for (IDBNListener listener : listeners) {
                log.warn("Listener '" + listener + "' is not unregistered from DBM model");
            }
        }
        this.listeners.clear();
        this.listenersCopy = null;
    }

    public DBNAdapterFactory getNodesAdapter()
    {
        return nodesAdapter;
    }

    public DBNRoot getRoot()
    {
        return root;
    }

    public DBNDatabaseNode findNode(DBSObject object)
    {
        if (object instanceof DBNDatabaseNode) {
            return (DBNDatabaseNode)object;
        } else {
            return this.getNodeByObject(object);
        }
    }

    public DBNDatabaseNode getNodeByObject(DBSObject object)
    {
        if (object instanceof DBNDatabaseNode) {
            return (DBNDatabaseNode)object;
        }
        Object obj = nodeMap.get(object);
        if (obj == null) {
            return null;
        } else if (obj instanceof DBNDatabaseNode) {
            return (DBNDatabaseNode)obj;
        } else if (obj instanceof List) {
            @SuppressWarnings("unchecked")
            List<DBNDatabaseNode> nodeList = (List<DBNDatabaseNode>) obj;
            if (nodeList.isEmpty()) {
                return null;
            }
            if (nodeList.size() > 1) {
                for (DBNDatabaseNode node : nodeList) {
                    if (node instanceof DBNDatabaseItem && !((DBNDatabaseItem)node).getMeta().isVirtual()) {
                        return node;
                    }
                }
            }
            // Get just first one
            return nodeList.get(0);
        } else {
            // Never be here
           throw new IllegalStateException();
        }
/*
        if (node == null) {
            log.warn("Can't find tree node for object " + object.getName() + " (" + object.getClass().getName() + ")");
        }
        return node;
*/
    }

    public DBNDatabaseNode getNodeByObject(DBRProgressMonitor monitor, DBSObject object, boolean load)
    {
        DBNDatabaseNode node = getNodeByObject(object);
        if (node != null || !load) {
            return node;
        }
        List<DBSObject> path = new ArrayList<DBSObject>();
        for (DBSObject item = object; item != null; item = item.getParentObject()) {
            path.add(0, item);
        }
        for (int i = 0; i < path.size() - 1; i++) {
            DBSObject item = path.get(i);
            DBSObject nextItem = path.get(i + 1);
            node = getNodeByObject(item);
            if (node == null) {
                log.warn("Can't find tree node for object " + item.getName() + " (" + item.getClass().getName() + ")");
                return null;
            }
            try {
                cacheNodeChildren(monitor, node, nextItem.getClass());
            } catch (DBException e) {
                log.error(e.getMessage());
                return null;
            }
        }
        return getNodeByObject(object);
    }

    private void cacheNodeChildren(DBRProgressMonitor monitor, DBNDatabaseNode node, Class<?> objectType) throws DBException
    {
        List<? extends DBNDatabaseNode> children = node.getChildren(monitor);
        if (!CommonUtils.isEmpty(children)) {
            for (DBNDatabaseNode child : children) {
                if (child instanceof DBNDatabaseFolder) {
                    Class<?> itemsClass = ((DBNDatabaseFolder) child).getChildrenClass();
                    if (itemsClass != null && itemsClass.isAssignableFrom(objectType)) {
                        cacheNodeChildren(monitor, child, objectType);
                    }
                }
            }
        }
    }

    public DBNDatabaseNode getParentNode(DBSObject object)
    {
        DBNDatabaseNode node = getNodeByObject(object);
        if (node != null) {
            if (node.getParentNode() instanceof DBNDatabaseNode) {
                return (DBNDatabaseNode) node.getParentNode();
            } else {
                log.error("Object's " + object.getName() + "' parent node is not a database node: " + node.getParentNode());
                return null;
            }
        }
        List<DBSObject> path = new ArrayList<DBSObject>();
        for (DBSObject item = object.getParentObject(); item != null; item = item.getParentObject()) {
            path.add(0, item);
        }
        for (int i = 0; i < path.size(); i++) {
            DBSObject item = path.get(i);
            node = getNodeByObject(item);
            if (node == null) {
                // Parent node read
                return null;
            }
            List<? extends DBNDatabaseNode> children = node.getChildNodes();
            if (CommonUtils.isEmpty(children)) {
                // Parent node is not read
                return null;
            }

            if (item == object.getParentObject()) {
                // Try to find parent node withing children
                for (DBNDatabaseNode child : children) {
                    if (child instanceof DBNDatabaseFolder) {
                        Class<?> itemsClass = ((DBNDatabaseFolder) child).getChildrenClass();
                        if (itemsClass != null && itemsClass.isAssignableFrom(object.getClass())) {
                            return child;
                        }
                    }
                }
            }
        }
        // Not found
        return null;
    }

    void addNode(DBNDatabaseNode node)
    {
        addNode(node, false);
    }

    void addNode(DBNDatabaseNode node, boolean reflect)
    {
        Object obj = nodeMap.get(node.getObject());
        if (obj == null) {
            // New node
            nodeMap.put(node.getObject(), node);
        } else if (obj instanceof DBNNode) {
            // Second node - make a list
            List<DBNNode> nodeList = new ArrayList<DBNNode>(2);
            nodeList.add((DBNNode)obj);
            nodeList.add(node);
            nodeMap.put(node.getObject(), nodeList);
        } else if (obj instanceof List) {
            // Multiple nodes
            @SuppressWarnings("unchecked")
            List<DBNNode> nodeList = (List<DBNNode>) obj;
            nodeList.add(node);
        }
        if (reflect) {
            this.fireNodeEvent(new DBNEvent(this, DBNEvent.Action.ADD, DBNEvent.NodeChange.LOAD, node));
        }
    }

    void removeNode(DBNDatabaseNode node)
    {
        removeNode(node, false);
    }

    void removeNode(DBNDatabaseNode node, boolean reflect)
    {
        Object obj = nodeMap.get(node.getObject());
        boolean badNode = false;
        if (obj == null) {
            // No found
            badNode = true;
        } else if (obj instanceof DBNNode) {
            // Just remove it
            if (nodeMap.remove(node.getObject()) != node) {
                badNode = true;
            }
        } else if (obj instanceof List) {
            // Multiple nodes
            @SuppressWarnings("unchecked")
            List<DBNNode> nodeList = (List<DBNNode>) obj;
            if (!nodeList.remove(node)) {
                badNode = true;
            }
            if (nodeList.isEmpty()) {
                nodeMap.remove(node.getObject());
            }
        }
        if (badNode) {
            log.warn("Remove unregistered meta node object " + node.getNodeName());
        } else {
            if (reflect) {
                this.fireNodeEvent(new DBNEvent(this, DBNEvent.Action.REMOVE, DBNEvent.NodeChange.UNLOAD, node));
            }
        }
    }

    public void addListener(IDBNListener listener)
    {
        synchronized (this.listeners) {
            if (this.listeners.contains(listener)) {
                log.warn("Listener " + listener + " already registered in model");
            } else {
                this.listeners.add(listener);
            }
            this.listenersCopy = this.listeners.toArray(new IDBNListener[this.listeners.size()]);
        }
    }

    public void removeListener(IDBNListener listener)
    {
        synchronized (this.listeners) {
            if (!this.listeners.remove(listener)) {
                log.warn("Listener " + listener + " wasn't registered in model");
            }
            this.listenersCopy = this.listeners.toArray(new IDBNListener[this.listeners.size()]);
        }
    }

    void fireNodeUpdate(Object source, DBNNode node, DBNEvent.NodeChange nodeChange)
    {
        this.fireNodeEvent(new DBNEvent(source, DBNEvent.Action.UPDATE, nodeChange, node));
    }

    void fireNodeEvent(final DBNEvent event)
    {
        IDBNListener[] listenersCopy;
        synchronized (this.listeners) {
            if (listeners.isEmpty()) {
                return;
            }
            listenersCopy = this.listenersCopy;
        }
        for (IDBNListener listener :  listenersCopy) {
            listener.nodeChanged(event);
        }
    }

    public void resourceChanged(IResourceChangeEvent event)
    {
        if (event.getType() == IResourceChangeEvent.POST_CHANGE) {
            IResourceDelta delta = event.getDelta();
            //IResource resource = delta.getResource();
            for (IResourceDelta childDelta : delta.getAffectedChildren()) {
                if (childDelta.getResource() instanceof IProject) {
                    IProject project = (IProject) childDelta.getResource();
                    DBNProject projectNode = getRoot().getProject(project);
                    if (projectNode == null) {
                        if (childDelta.getKind() == IResourceDelta.ADDED) {
                            // New projectNode
                            getRoot().addProject(project, true);
                        } else {
                            // Project not found - report an error
                            log.error("Project '" + childDelta.getResource().getName() + "' not found in navigator");
                        }
                    } else {
                        if (childDelta.getKind() == IResourceDelta.REMOVED) {
                            // Project deleted
                            getRoot().removeProject(project);
                        } else {
                            // Some resource changed within the projectNode
                            // Let it handle this event itself
                            projectNode.handleResourceChange(childDelta);
                        }
                    }
                }
            }
        }
    }
}
