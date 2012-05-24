/*
 * Copyright (c) 2012, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ext.mysql.edit;

import org.jkiss.dbeaver.model.impl.DBSObjectCache;
import org.jkiss.utils.CommonUtils;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.IDatabasePersistAction;
import org.jkiss.dbeaver.ext.mysql.MySQLMessages;
import org.jkiss.dbeaver.ext.mysql.model.MySQLCatalog;
import org.jkiss.dbeaver.ext.mysql.model.MySQLDataSource;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectRenamer;
import org.jkiss.dbeaver.model.impl.edit.AbstractDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.jdbc.edit.struct.JDBCObjectEditor;
import org.jkiss.dbeaver.ui.dialogs.EnterNameDialog;

/**
 * MySQLCatalogManager
 */
public class MySQLCatalogManager extends JDBCObjectEditor<MySQLCatalog, MySQLDataSource> implements DBEObjectRenamer<MySQLCatalog> {

    @Override
    public long getMakerOptions()
    {
        return FEATURE_SAVE_IMMEDIATELY;
    }

    @Override
    protected DBSObjectCache<MySQLDataSource, MySQLCatalog> getObjectsCache(MySQLCatalog object)
    {
        return object.getDataSource().getCatalogCache();
    }

    @Override
    protected MySQLCatalog createDatabaseObject(IWorkbenchWindow workbenchWindow, IEditorPart activeEditor, DBECommandContext context, MySQLDataSource parent, Object copyFrom)
    {
        String schemaName = EnterNameDialog.chooseName(workbenchWindow.getShell(), MySQLMessages.edit_catalog_manager_dialog_schema_name);
        if (CommonUtils.isEmpty(schemaName)) {
            return null;
        }
        MySQLCatalog newCatalog = new MySQLCatalog(parent, null);
        newCatalog.setName(schemaName);
        return newCatalog;
    }

    @Override
    protected IDatabasePersistAction[] makeObjectCreateActions(ObjectCreateCommand command)
    {
        return new IDatabasePersistAction[] {
            new AbstractDatabasePersistAction(MySQLMessages.edit_catalog_manager_action_create_schema, "CREATE SCHEMA " + command.getObject().getName()) //$NON-NLS-2$
        };
    }

    @Override
    protected IDatabasePersistAction[] makeObjectDeleteActions(ObjectDeleteCommand command)
    {
        return new IDatabasePersistAction[] {
            new AbstractDatabasePersistAction(MySQLMessages.edit_catalog_manager_action_drop_schema, "DROP SCHEMA " + command.getObject().getName()) //$NON-NLS-2$
        };
    }

    @Override
    public void renameObject(DBECommandContext commandContext, MySQLCatalog catalog, String newName) throws DBException
    {
        throw new DBException("Direct database rename is not yet implemented in MySQL. You should use export/import functions for that.");
        //super.addCommand(new CommandRenameCatalog(newName), null);
        //saveChanges(monitor);
    }

/*
    private class CommandRenameCatalog extends DBECommandAbstract<MySQLCatalog> {
        private String newName;

        protected CommandRenameCatalog(MySQLCatalog catalog, String newName)
        {
            super(catalog, "Rename catalog");
            this.newName = newName;
        }
        public IDatabasePersistAction[] getPersistActions()
        {
            return new IDatabasePersistAction[] {
                new AbstractDatabasePersistAction("Rename catalog", "RENAME SCHEMA " + getObject().getName() + " TO " + newName)
            };
        }

        @Override
        public void updateModel()
        {
            getObject().setName(newName);
            getObject().getDataSource().getContainer().fireEvent(
                new DBPEvent(DBPEvent.Action.OBJECT_UPDATE, getObject()));
        }
    }
*/

    /*
http://www.artfulsoftware.com/infotree/queries.php#112
Rename Database
It's sometimes necessary to rename a database. MySQL 5.0 has no command for it. Simply bringing down the server to rename a database directory is not safe. MySQL 5.1.7 introduced a RENAME DATABASE command, but the command left several unchanged database objects behind, and was found to lose data, so it was dropped in 5.1.23.

It seems a natural for a stored procedure using dynamic (prepared) statements. PREPARE supports CREATE | RENAME TABLE. As precautions:

    Before calling the sproc, the new database must have been created.
    The procedure refuses to rename the mysql database.
    The old database is left behind, minus what was moved.


DROP PROCEDURE IF EXISTS RenameDatabase;
DELIMITER go
CREATE PROCEDURE RenameDatabase( oldname CHAR (64), newname CHAR(64) )
BEGIN
  DECLARE version CHAR(32);
  DECLARE sname CHAR(64) DEFAULT NULL;
  DECLARE rows INT DEFAULT 1;
  DECLARE changed INT DEFAULT 0;
  IF STRCMP( oldname, 'mysql' ) <> 0 THEN
    REPEAT
      SELECT table_name INTO sname
      FROM information_schema.tables AS t
      WHERE t.table_type='BASE TABLE' AND t.table_schema = oldname
      LIMIT 1;
      SET rows = FOUND_ROWS();
      IF rows = 1 THEN
        SET @scmd = CONCAT( 'RENAME TABLE `', oldname, '`.`', sname,
                            '` TO `', newname, '`.`', sname, '`' );
        PREPARE cmd FROM @scmd;
        EXECUTE cmd;
        DEALLOCATE PREPARE cmd;
        SET changed = 1;
      END IF;
    UNTIL rows = 0 END REPEAT;
    IF changed > 0 THEN
      SET @scmd = CONCAT( "UPDATE mysql.db SET Db = '",
                          newname,
                          "' WHERE Db = '", oldname, "'" );
      PREPARE cmd FROM @scmd;
      EXECUTE cmd;
      DROP PREPARE cmd;
      SET @scmd = CONCAT( "UPDATE mysql.proc SET Db = '",
                          newname,
                          "' WHERE Db = '", oldname, "'" );
      PREPARE cmd FROM @scmd;
      EXECUTE cmd;
      DROP PREPARE cmd;
      SELECT version() INTO version;
      IF version >= '5.1.7' THEN
        SET @scmd = CONCAT( "UPDATE mysql.event SET db = '",
                            newname,
                            "' WHERE db = '", oldname, "'" );
        PREPARE cmd FROM @scmd;
        EXECUTE cmd;
        DROP PREPARE cmd;
      END IF;
      SET @scmd = CONCAT( "UPDATE mysql.columns_priv SET Db = '",
                          newname,
                          "' WHERE Db = '", oldname, "'" );
      PREPARE cmd FROM @scmd;
      EXECUTE cmd;
      DROP PREPARE cmd;
      FLUSH PRIVILEGES;
    END IF;
  END IF;
END;
go
DELIMITER ;

     */

}

