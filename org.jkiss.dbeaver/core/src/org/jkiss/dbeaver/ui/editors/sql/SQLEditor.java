package org.jkiss.dbeaver.ui.editors.sql;


import net.sf.jkiss.utils.CommonUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.text.*;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotationModel;
import org.eclipse.jface.text.source.projection.ProjectionSupport;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.ISaveablePart2;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.texteditor.DefaultRangeIndicator;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.TextOperationAction;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.core.DBeaverActivator;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.dbc.DBCException;
import org.jkiss.dbeaver.model.dbc.DBCSession;
import org.jkiss.dbeaver.model.struct.DBSDataSourceContainer;
import org.jkiss.dbeaver.registry.DataSourceRegistry;
import org.jkiss.dbeaver.registry.event.DataSourceEvent;
import org.jkiss.dbeaver.registry.event.IDataSourceListener;
import org.jkiss.dbeaver.runtime.sql.SQLQueryJob;
import org.jkiss.dbeaver.runtime.sql.SQLQueryListener;
import org.jkiss.dbeaver.runtime.sql.SQLQueryResult;
import org.jkiss.dbeaver.ui.DBeaverUtils;
import org.jkiss.dbeaver.ui.ICommandIds;
import org.jkiss.dbeaver.ui.preferences.PrefConstants;
import org.jkiss.dbeaver.ui.actions.sql.ExecuteScriptAction;
import org.jkiss.dbeaver.ui.actions.sql.ExecuteStatementAction;
import org.jkiss.dbeaver.ui.controls.resultset.ResultSetProvider;
import org.jkiss.dbeaver.ui.controls.resultset.ResultSetViewer;
import org.jkiss.dbeaver.ui.editors.sql.syntax.SQLDelimiterToken;
import org.jkiss.dbeaver.ui.editors.sql.syntax.SQLSyntaxManager;
import org.jkiss.dbeaver.ui.editors.sql.util.SQLSymbolInserter;
import org.jkiss.dbeaver.ui.editors.sql.plan.ExplainPlanViewer;
import org.jkiss.dbeaver.ui.editors.sql.log.SQLLogViewer;
import org.jkiss.dbeaver.ui.views.console.ConsoleManager;
import org.jkiss.dbeaver.ui.views.console.ConsoleMessageType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * SQL Executor
 */
public class SQLEditor extends TextEditor
    implements
        IResourceChangeListener, IDataSourceListener, ResultSetProvider, ISaveablePart2
{
    static Log log = LogFactory.getLog(SQLEditor.class);

    private static final String ACTION_CONTENT_ASSIST_PROPOSAL = "ContentAssistProposal";
    private static final String ACTION_CONTENT_ASSIST_TIP = "ContentAssistTip";
    private static final String ACTION_CONTENT_FORMAT_PROPOSAL = "ContentFormatProposal";
    private static final String ACTION_DEFINE_FOLDING_REGION = "DefineFoldingRegion";

    private SashForm sashForm;
    private Control editorControl;
    private CTabFolder resultTabs;
    private ResultSetViewer resultsView;
    private ExplainPlanViewer planView;
    private SQLLogViewer logViewer;
    private SQLSyntaxManager syntaxManager;

    private ExecuteStatementAction execStatementAction;
    private ExecuteScriptAction execScriptAction;

    private ProjectionSupport projectionSupport;

    private ProjectionAnnotationModel annotationModel;
    private Map<Annotation, Position> curAnnotations;

    private SQLSymbolInserter symbolInserter = null;

    private DBCSession curSession;
    private SQLQueryJob curJob;
    private boolean curJobRunning;

    private static Image imgDataGrid;
    private static Image imgExplainPlan;
    private static Image imgLog;

    {
        imgDataGrid = DBeaverActivator.getImageDescriptor("/icons/sql/page_data_grid.png").createImage();
        imgExplainPlan = DBeaverActivator.getImageDescriptor("/icons/sql/page_explain_plan.png").createImage();
        imgLog = DBeaverActivator.getImageDescriptor("/icons/sql/page_error.png").createImage();
    }

    public SQLEditor()
    {
        super();
        setDocumentProvider(new SQLDocumentProvider());
        ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
        DataSourceRegistry.getDefault().addDataSourceListener(this);
    }

    public DBCSession getSession()
        throws DBException
    {
        checkSession();
        return curSession;
    }

    public DBPDataSource getDataSource()
    {
        return getDataSourceContainer().getDataSource();
    }

    public DBSDataSourceContainer getDataSourceContainer()
    {
        return getEditorInput().getDataSourceContainer();
    }

    public void setDataSourceContainer(DBSDataSourceContainer container)
    {
        if (container == getDataSourceContainer()) {
            return;
        }
        closeSession();
        getEditorInput().setDataSourceContainer(container);
        checkConnected();

        refreshSyntax();
    }

    public ISourceViewer getSV() {
        return getSourceViewer();
    }

    public SQLSyntaxManager getSyntaxManager()
    {
        return syntaxManager;
    }

    public ResultSetViewer getResultsView()
    {
        return resultsView;
    }

    public IDocument getDocument()
    {
        IDocumentProvider provider = getDocumentProvider();
        return provider == null ? null : provider.getDocument(getEditorInput());
    }

    public ProjectionAnnotationModel getAnnotationModel()
    {
        return annotationModel;
    }

    public boolean isDirty()
    {
        return super.isDirty();
    }

    public void close(boolean save)
    {
        super.close(save);
    }

    private boolean checkConnected()
    {
        // Connect to datasource
        DBSDataSourceContainer dataSourceContainer = getDataSourceContainer();
        if (dataSourceContainer != null) {
            if (!dataSourceContainer.isConnected()) {
                try {
                    dataSourceContainer.connect(this);
                } catch (DBException ex) {
                    log.error(ex.getMessage());
                }
            }
        }
        setPartName(getEditorInput().getName());
        return dataSourceContainer != null && dataSourceContainer.isConnected();
    }

    public void createPartControl(Composite parent)
    {
        syntaxManager = new SQLSyntaxManager(this);
        setRangeIndicator(new DefaultRangeIndicator());

        // Load syntax from datasource
        refreshSyntax();

        // Check connection
        checkConnected();

        sashForm = new SashForm(parent, SWT.VERTICAL | SWT.SMOOTH);
        sashForm.setSashWidth(10);

        super.createPartControl(sashForm);
        editorControl = sashForm.getChildren()[0];

        {
            resultTabs = new CTabFolder(sashForm, SWT.TOP | SWT.FLAT);
            resultTabs.setLayoutData(new GridData(GridData.FILL_BOTH));
            resultTabs.addSelectionListener(new SelectionAdapter() {
                public void widgetSelected(SelectionEvent e)
                {
                    int newPageIndex = resultTabs.indexOf((CTabItem) e.item);
                    //pageChange(newPageIndex);
                }
            });
            resultTabs.setSimple(true);

            resultsView = new ResultSetViewer(resultTabs, getSite());
            resultsView.setResultSetProvider(this);

            planView = new ExplainPlanViewer(resultTabs);
            logViewer = new SQLLogViewer(resultTabs);

            // Create tabs
            CTabItem item = new CTabItem(resultTabs, SWT.NONE, 0);
            item.setControl(resultsView.getControl());
            item.setText("Data Grid");
            item.setImage(imgDataGrid);

            item = new CTabItem(resultTabs, SWT.NONE, 1);
            item.setControl(planView.getControl());
            item.setText("Explain Plan");
            item.setImage(imgExplainPlan);

            item = new CTabItem(resultTabs, SWT.NONE, 2);
            item.setControl(logViewer.getControl());
            item.setText("Execute Log");
            item.setImage(imgLog);

            resultTabs.setSelection(0);
        }

        ProjectionViewer viewer = (ProjectionViewer) getSourceViewer();
        projectionSupport = new ProjectionSupport(viewer, getAnnotationAccess(), getSharedColors());
        projectionSupport.addSummarizableAnnotationType("org.eclipse.ui.workbench.texteditor.error");
        projectionSupport.addSummarizableAnnotationType("org.eclipse.ui.workbench.texteditor.warning");
        projectionSupport.install();

        viewer.doOperation(ProjectionViewer.TOGGLE);

        annotationModel = viewer.getProjectionAnnotationModel();

        // Symbol inserter
        {
            symbolInserter = new SQLSymbolInserter(this);

            IPreferenceStore preferenceStore = DBeaverCore.getInstance().getGlobalPreferenceStore();
            boolean closeSingleQuotes = preferenceStore.getBoolean(SQLPreferenceConstants.SQLEDITOR_CLOSE_SINGLE_QUOTES);
            boolean closeDoubleQuotes = preferenceStore.getBoolean(SQLPreferenceConstants.SQLEDITOR_CLOSE_DOUBLE_QUOTES);
            boolean closeBrackets = preferenceStore.getBoolean(SQLPreferenceConstants.SQLEDITOR_CLOSE_BRACKETS);

            symbolInserter.setCloseSingleQuotesEnabled(closeSingleQuotes);
            symbolInserter.setCloseDoubleQuotesEnabled(closeDoubleQuotes);
            symbolInserter.setCloseBracketsEnabled(closeBrackets);

            ISourceViewer sourceViewer = getSourceViewer();
            if (sourceViewer instanceof ITextViewerExtension) {
                ((ITextViewerExtension) sourceViewer).prependVerifyKeyListener(symbolInserter);
            }
        }
    }

    protected void initializeEditor()
    {
        super.initializeEditor();
        setSourceViewerConfiguration(new SQLEditorSourceViewerConfiguration(this));
    }

    protected void createActions()
    {
        super.createActions();

        ResourceBundle bundle = DBeaverCore.getInstance().getPlugin().getResourceBundle();

        IAction a = new TextOperationAction(bundle, "ContentAssistProposal.", this, ISourceViewer.CONTENTASSIST_PROPOSALS);
        a.setActionDefinitionId(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS);
        setAction(ACTION_CONTENT_ASSIST_PROPOSAL, a);

        a = new TextOperationAction(bundle, "ContentAssistTip.", this, ISourceViewer.CONTENTASSIST_CONTEXT_INFORMATION);
        a.setActionDefinitionId(ITextEditorActionDefinitionIds.CONTENT_ASSIST_CONTEXT_INFORMATION);
        setAction(ACTION_CONTENT_ASSIST_TIP, a);

        a = new TextOperationAction(bundle, "ContentFormatProposal.", this, ISourceViewer.FORMAT);
        setAction(ACTION_CONTENT_FORMAT_PROPOSAL, a);

/*
        // Add the task action to the Edit pulldown menu (bookmark action is  'free')
        ResourceAction ra = new AddTaskAction(bundle, "AddTask.", this);
        ra.setHelpContextId(ITextEditorHelpContextIds.ADD_TASK_ACTION);
        ra.setActionDefinitionId(ITextEditorActionDefinitionIds.ADD_TASK);
        setAction(IDEActionFactory.ADD_TASK.getId(), ra);
*/

        // Add execution actions
        execStatementAction = new ExecuteStatementAction();
        setAction(ICommandIds.CMD_EXECUTE_STATEMENT, execStatementAction);
        execStatementAction.setProcessor(this);

        execScriptAction = new ExecuteScriptAction();
        setAction(ICommandIds.CMD_EXECUTE_SCRIPT, execScriptAction);
        execScriptAction.setProcessor(this);
    }

    public void editorContextMenuAboutToShow(IMenuManager menu)
    {
        super.editorContextMenuAboutToShow(menu);

        boolean connected = getDataSourceContainer().isConnected();
        execStatementAction.setEnabled(connected);
        execScriptAction.setEnabled(connected);

        addAction(menu, ACTION_CONTENT_ASSIST_PROPOSAL);
        addAction(menu, ACTION_CONTENT_ASSIST_TIP);
        addAction(menu, ACTION_CONTENT_FORMAT_PROPOSAL);
        //addAction(menu, ACTION_DEFINE_FOLDING_REGION);
        menu.add(new Separator());
        addAction(menu, ICommandIds.CMD_EXECUTE_STATEMENT);
        addAction(menu, ICommandIds.CMD_EXECUTE_SCRIPT);
    }

    protected ISourceViewer createSourceViewer(Composite parent,
        IVerticalRuler ruler, int styles)
    {
        fAnnotationAccess = createAnnotationAccess();
        fOverviewRuler = createOverviewRuler(getSharedColors());

        ISourceViewer viewer = new SQLEditorSourceViewer(
            parent,
            ruler,
            getOverviewRuler(),
            isOverviewRulerVisible(),
            styles);
        // ensure decoration support has been created and configured.
        getSourceViewerDecorationSupport(viewer);

        return viewer;
    }

/*
    protected void adjustHighlightRange(int offset, int length)
    {
        ISourceViewer viewer = getSourceViewer();
        if (viewer instanceof ITextViewerExtension5) {
            ITextViewerExtension5 extension = (ITextViewerExtension5) viewer;
            extension.exposeModelRange(new Region(offset, length));
        }
    }
*/

    public Object getAdapter(Class required)
    {
        if (projectionSupport != null) {
            Object adapter = projectionSupport.getAdapter(
                getSourceViewer(), required);
            if (adapter != null)
                return adapter;
        }

        return super.getAdapter(required);
    }

    public SQLEditorInput getEditorInput()
    {
        return (SQLEditorInput) super.getEditorInput();
    }

    public void init(IEditorSite site, IEditorInput editorInput)
        throws PartInitException
    {
        if (!(editorInput instanceof SQLEditorInput)) {
            throw new PartInitException("Invalid Input: Must be SQLEditorInput");
        }
        super.init(site, editorInput);
    }

    public void resourceChanged(final IResourceChangeEvent event)
    {
    }

    public void processSQL(boolean script)
    {
        IDocument document = getDocument();
        if (document == null) {
            setStatus("Can't obtain editor's document", ConsoleMessageType.ERROR);
            return;
        }
        if (script) {
            // Execute all SQL statements consequently
            List<SQLScriptLine> scriptLines;
            ITextSelection selection = (ITextSelection) getSelectionProvider().getSelection();
            if (selection.getLength() > 1) {
                scriptLines = extractScriptQueries(selection.getOffset(), selection.getLength());
            } else {
                scriptLines = extractScriptQueries(0, document.getLength());
            }
            processQuery(scriptLines);
        } else {
            // Execute statement under cursor or selected text (if selection present)
            SQLScriptLine sqlQuery = extractActiveQuery();
            if (sqlQuery == null) {
                setStatus("Empty query string", ConsoleMessageType.ERROR);
            } else {
                processQuery(Collections.singletonList(sqlQuery));
            }
        }
    }

    private SQLScriptLine extractActiveQuery()
    {
        SQLScriptLine sqlQuery;
        ITextSelection selection = (ITextSelection) getSelectionProvider().getSelection();
        String selText = selection.getText().trim();
        selText = selText.trim();
        if (selText.endsWith(syntaxManager.getStatementDelimiter())) {
            selText = selText.substring(0, selText.length() - syntaxManager.getStatementDelimiter().length());
        }
        if (!CommonUtils.isEmpty(selText)) {
            sqlQuery = new SQLScriptLine(selText, selection.getOffset(), selection.getLength());
        } else {
            sqlQuery = extractQueryAtPos(selection.getOffset());
        }
        // Check query do not ends with delimiter
        // (this may occur if user selected statement including delimiter)
        if (sqlQuery == null || CommonUtils.isEmpty(sqlQuery.getQuery())) {
            return null;
        }
        return sqlQuery;
    }

    private static boolean isEmptyLine(IDocument document, int line)
        throws BadLocationException
    {
        IRegion region = document.getLineInformation(line);
        if (region == null || region.getLength() == 0) {
            return true;
        }
        String str = document.get(region.getOffset(), region.getLength());
        return str.trim().length() == 0;
    }

    public SQLScriptLine extractQueryAtPos(int currentPos)
    {
        IDocument document = getDocument();
        if (document.getLength() == 0) {
            return null;
        }
        // Extract part of document between empty lines
        int startPos = 0;
        int endPos = document.getLength();
        try {
            int currentLine = document.getLineOfOffset(currentPos);
            int linesCount = document.getNumberOfLines();
            int firstLine = currentLine, lastLine = currentLine;
            while (firstLine > 0) {
                if (isEmptyLine(document, firstLine)) {
                    break;
                }
                firstLine--;
            }
            while (lastLine < linesCount) {
                if (isEmptyLine(document, lastLine)) {
                    break;
                }
                lastLine++;
            }
            if (lastLine >= linesCount) {
                lastLine = linesCount - 1;
            }
            startPos = document.getLineOffset(firstLine);
            endPos = document.getLineOffset(lastLine) + document.getLineLength(lastLine);
            String lastDelimiter = document.getLineDelimiter(lastLine);
            //if (lastDelimiter != null) {
            //    endPos += lastDelimiter.length();
            //}
        }
        catch (BadLocationException e) {
            log.warn(e);
        }
        // Parse range
        syntaxManager.setRange(document, startPos, endPos - startPos);
        int statementStart = startPos;
        for (;;) {
            IToken token = syntaxManager.nextToken();
            int tokenOffset = syntaxManager.getTokenOffset();
            if (token.isEOF() ||
                (token instanceof SQLDelimiterToken && tokenOffset > currentPos)||
                tokenOffset > endPos) 
            {
                // get position before last token start
                if (tokenOffset > endPos) {
                    tokenOffset = endPos;
                }

                if (tokenOffset >= document.getLength()) {
                    // Sometimes (e.g. when comment finishing script text)
                    // last token offset is beyon document range
                    tokenOffset = document.getLength();
                }
                assert (tokenOffset >= currentPos);
                try {
                    // remove leading spaces
                    while (statementStart < tokenOffset && Character.isWhitespace(document.getChar(statementStart))) {
                        statementStart++;
                    }
                    // remove trailing spaces
                    while (statementStart < tokenOffset && Character.isWhitespace(document.getChar(tokenOffset - 1))) {
                        tokenOffset--;
                    }
                    String queryText = document.get(statementStart, tokenOffset - statementStart);
                    queryText = queryText.trim();
                    if (queryText.endsWith(syntaxManager.getStatementDelimiter())) {
                        queryText = queryText.substring(0, queryText.length() - syntaxManager.getStatementDelimiter().length()); 
                    }
                    // make script line
                    return new SQLScriptLine(
                        queryText.trim(),
                        statementStart,
                        tokenOffset - statementStart);
                } catch (BadLocationException ex) {
                    log.warn("Can't extract query", ex);
                    return null;
                }
            }
            if (token instanceof SQLDelimiterToken) {
                statementStart = tokenOffset + syntaxManager.getTokenLength();
            }
            if (token.isEOF()) {
                return null;
            }
        }
    }

    private List<SQLScriptLine> extractScriptQueries(int startOffset, int length)
    {
        IDocument document = getDocument();
/*
        {
            Collection<? extends Position> selectedPositions = syntaxManager.getPositions(startOffset, length);
            for (Position position : selectedPositions) {
                try {
                    String query = document.get(position.getOffset(), position.getLength());
                    System.out.println(query);
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }
        }
*/

        List<SQLScriptLine> queryList = new ArrayList<SQLScriptLine>();
        syntaxManager.setRange(document, startOffset, length);
        int statementStart = startOffset;
        for (;;) {
            IToken token = syntaxManager.nextToken();
            if (token.isEOF() || token instanceof SQLDelimiterToken) {
                int tokenOffset = syntaxManager.getTokenOffset();
                if (tokenOffset >= document.getLength()) {
                    tokenOffset = document.getLength();
                }
                try {
                    while (statementStart < tokenOffset && Character.isWhitespace(document.getChar(statementStart))) {
                        statementStart++;
                    }
                    int queryLength = tokenOffset - statementStart;
                    String query = document.get(statementStart, queryLength);
                    query = query.trim();
                    if (query.length() > 0) {
                        queryList.add(new SQLScriptLine(query, statementStart, queryLength));
                    }
                } catch (BadLocationException ex) {
                    log.error("Error extracting script query", ex);
                }
                statementStart = tokenOffset + 1;
            }
            if (token.isEOF()) {
                break;
            }
        }
        return queryList;
    }

    private void setStatus(String status, ConsoleMessageType messageType)
    {
        resultsView.setStatus(status, messageType == ConsoleMessageType.ERROR);
        ConsoleManager.writeMessage(status, messageType);
    }

    private void processQuery(List<SQLScriptLine> queries)
    {
        if (queries.isEmpty()) {
            // Nothing to process
            return;
        }
        if (curJobRunning) {
            DBeaverUtils.showErrorDialog(
                getSite().getShell(),
                "Can't execute query",
                "Can't execute more than one query in one editor simultaneously");
            return;
        }
        try {
            checkSession();
        } catch (DBException ex) {
            this.setStatus(ex.getMessage(), ConsoleMessageType.ERROR);
            DBeaverUtils.showErrorDialog(
                getSite().getShell(),
                "Can't obtain session",
                ex.getMessage());
            return;
        }

        // Prepare execution job
        {
            final ITextSelection originalSelection = (ITextSelection) getSelectionProvider().getSelection();
            final boolean isSingleQuery = (queries.size() == 1);
            final SQLQueryJob job = new SQLQueryJob(
                isSingleQuery ? "Execute query" : "Execute script",
                curSession,
                queries);
            job.addQueryListener(new SQLQueryListener() {
                public void onStartJob()
                {
                    curJobRunning = true;
                    asyncExec(new Runnable() {
                        public void run()
                        {
                            if (!isSingleQuery) {
                                sashForm.setMaximizedControl(editorControl);
                                ConsoleManager.writeMessage("Start script execution", ConsoleMessageType.COMMENT);
                            }
                        }
                    });
                }
                public void onStartQuery(final SQLScriptLine query)
                {
                    asyncExec(new Runnable() {
                        public void run()
                        {
                            selectAndReveal(query.getOffset(), query.getLength());
                            setStatus(query.getQuery(), ConsoleMessageType.CODE);
                        }
                    });
                }

                public void onEndQuery(final SQLQueryResult result)
                {
                    if (isDisposed()) {
                        return;
                    }
                    asyncExec(new Runnable() {
                        public void run()
                        {
                            if (result.getError() == null) {
                                String status;
                                if (result.getRows() != null) {
                                    resultsView.setData(result.getMetaData(), result.getRows());
                                    status = String.valueOf(result.getRows().size()) + " row(s) fetched";
                                } else if (result.getUpdateCount() != null) {
                                    if (result.getUpdateCount() == 0) {
                                        status = "No rows updated";
                                    } else {
                                        status = String.valueOf(result.getUpdateCount()) + " row(s) updated";
                                    }
                                } else {
                                    status = "Statement executed";
                                }
                                status += " (" + result.getQueryTime() + " ms)";
                                setStatus(status, ConsoleMessageType.INFO);
                            } else {
                                setStatus(result.getError().getMessage(), ConsoleMessageType.ERROR);
                            }
                        }
                    });
                }
                public void onEndJob(final boolean hasErrors)
                {
                    curJobRunning = false;

                    if (isDisposed()) {
                        return;
                    }
                    asyncExec(new Runnable() {
                        public void run()
                        {
                            if (!hasErrors) {
                                getSelectionProvider().setSelection(originalSelection);
                            }
                            if (!isSingleQuery) {
                                ConsoleManager.writeMessage("Script completed", ConsoleMessageType.COMMENT);
                                sashForm.setMaximizedControl(null);
                            }
                        }
                    });
                }
            });
            if (isSingleQuery) {
                curJob = job;
                resultsView.refresh();
            } else {
                job.schedule();
            }
        }
    }

    private void checkSession()
        throws DBException
    {
        if (curSession == null) {
            if (getDataSourceContainer() == null || !getDataSourceContainer().isConnected()) {
                throw new DBException("Not connected to database");
            }
            curSession = getDataSourceContainer().getDataSource().getSession(false);
            try {
// Change autocommit state
                curSession.setAutoCommit(
                    getDataSourceContainer().getPreferenceStore().getBoolean(PrefConstants.DEFAULT_AUTO_COMMIT));
            }
            catch (DBCException e) {
                log.error("Can't change session autocommit state", e);
            }
        }
    }

    private void closeSession()
    {
        if (curSession != null) {
            try {
                curSession.close();
            } catch (DBCException e) {
                log.error(e);
            }
            curSession = null;
        }
        if (curJob != null) {
            curJob = null;
        }
    }

    private void refreshSyntax()
    {
        if (syntaxManager != null) {
            syntaxManager.changeDataSource();
        }
        ProjectionViewer projectionViewer = (ProjectionViewer)getSourceViewer();
        IDocument document = getDocument();
        if (projectionViewer != null && document != null && document.getLength() > 0) {
            // Refresh viewer
            //projectionViewer.getTextWidget().redraw();
            try {
                projectionViewer.reinitializeProjection();
            } catch (BadLocationException ex) {
                log.warn("Error refreshing projection", ex);
            }
        }
    }

    public void dispose()
    {
        closeSession();

        DataSourceRegistry.getDefault().removeDataSourceListener(this);
        if (syntaxManager != null) {
            syntaxManager.dispose();
            syntaxManager = null;
        }
        if (planView != null) {
            planView.dispose();
            planView = null;
        }
        if (resultsView != null) {
            resultsView.dispose();
            resultsView = null;
        }
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);

        super.dispose();
    }

    public void dataSourceChanged(DataSourceEvent event, IProgressMonitor monitor)
    {
        if (event.getDataSource() == getDataSourceContainer()) {
            switch (event.getAction()) {
                case DISCONNECT:
                    closeSession();
                case CONNECT:
                    refreshSyntax();
                    break;
                case REMOVE:
                    getSite().getWorkbenchWindow().getActivePage().closeEditor(this, false);
                    break;
            }
        }
    }

    public boolean isConnected()
    {
        return getDataSourceContainer() != null && getDataSourceContainer().isConnected();
    }

    public void extractResultSetData(int offset)
    {
        if (curJobRunning) {
            DBeaverUtils.showErrorDialog(
                getSite().getShell(),
                "Can't refresh",
                "Can't refresh resultset - another query is beng executed");
            return;
        }
        if (curJob != null) {
            curJob.schedule();
        }
    }

    public synchronized void updateFoldingStructure(int offset, int length, List<Position> positions)
    {
        if (curAnnotations == null) {
            curAnnotations = new HashMap<Annotation, Position>();
        }
        List<Annotation> deletedAnnotations = new ArrayList<Annotation>();
        Map<Annotation, Position> newAnnotations = new HashMap<Annotation, Position>();

        // Delete all annotations if specified range
        for (Map.Entry<Annotation,Position> entry : curAnnotations.entrySet()) {
            int entryOffset = entry.getValue().getOffset();
            if (entryOffset >= offset && entryOffset < offset + length) {
                deletedAnnotations.add(entry.getKey());
            }
        }
        for (Annotation annotation : deletedAnnotations) {
            curAnnotations.remove(annotation);
        }

        // Add new annotations
        for (int i = 0; i < positions.size(); i++) {
            ProjectionAnnotation annotation = new ProjectionAnnotation();
            newAnnotations.put(annotation, positions.get(i));
        }

        // Modify annotation set
        annotationModel.modifyAnnotations(
            deletedAnnotations.toArray(new Annotation[deletedAnnotations.size()]),
            newAnnotations,
            null);

        // Update current annotations
        curAnnotations.putAll(newAnnotations);
    }

    private void asyncExec(Runnable runnable)
    {
        getSite().getShell().getDisplay().asyncExec(runnable);
    }

    public boolean isDisposed()
    {
        return
            getSourceViewer() == null ||
            getSourceViewer().getTextWidget() == null ||
            getSourceViewer().getTextWidget().isDisposed();
    }

    public int promptToSaveOnClose()
    {
        if (curJobRunning) {
            MessageBox messageBox = new MessageBox(getSite().getShell(), SWT.ICON_WARNING | SWT.OK);
            messageBox.setMessage("Editor can't be closed while SQL query is being executed");
            messageBox.setText("Query is being executed");
            messageBox.open();
            return ISaveablePart2.CANCEL;
        }
        return ISaveablePart2.YES;
    }

}
