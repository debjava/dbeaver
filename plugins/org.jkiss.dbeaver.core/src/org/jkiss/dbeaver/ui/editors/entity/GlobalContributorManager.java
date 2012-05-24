/*
 * Copyright (c) 2012, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ui.editors.entity;

import org.eclipse.ui.IEditorActionBarContributor;
import org.eclipse.ui.IEditorPart;
import org.jkiss.dbeaver.ext.IDatabaseEditorContributorManager;

import java.util.*;

/**
 * Global contributor manager
 */
public class GlobalContributorManager implements IDatabaseEditorContributorManager{

    private static GlobalContributorManager instance = new GlobalContributorManager();

    private static class ActionContributorInfo {
        final IEditorActionBarContributor contributor;
        final List<IEditorPart> editors = new ArrayList<IEditorPart>();

        private ActionContributorInfo(IEditorActionBarContributor contributor)
        {
            this.contributor = contributor;
        }
    }

    private Map<Class<? extends IEditorActionBarContributor>, ActionContributorInfo> contributorMap = new HashMap<Class<? extends IEditorActionBarContributor>, ActionContributorInfo>();

    public static GlobalContributorManager getInstance()
    {
        return instance;
    }

    @Override
    public IEditorActionBarContributor getContributor(Class<? extends IEditorActionBarContributor> type)
    {
        ActionContributorInfo info = contributorMap.get(type);
        return info == null ? null : info.contributor;
    }

    public void addContributor(IEditorActionBarContributor contributor, IEditorPart editor)
    {
        ActionContributorInfo info = contributorMap.get(contributor.getClass());
        if (info == null) {
            contributor.init(editor.getEditorSite().getActionBars(), editor.getSite().getPage());
            info = new ActionContributorInfo(contributor);
            contributorMap.put(contributor.getClass(), info);
        }
        info.editors.add(editor);
    }

    public void removeContributor(IEditorActionBarContributor contributor, IEditorPart editor)
    {
        ActionContributorInfo info = contributorMap.get(contributor.getClass());
        if (info == null) {
            throw new IllegalStateException("Contributor is not registered");
        }
        if (!info.editors.remove(editor)) {
            throw new IllegalStateException("Contributor editor is not registered");
        }
        if (info.editors.isEmpty()) {
            contributorMap.remove(contributor.getClass());
            contributor.dispose();
        }
    }

}
