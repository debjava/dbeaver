/*
 * Copyright (c) 2010, Serge Rieder and others. All Rights Reserved.
 */

/*
 * Created on Jul 13, 2004
 */
package org.jkiss.dbeaver.ext.erd.model;

import org.jkiss.dbeaver.model.struct.DBSForeignKey;

/**
 * Relates one table to another
 * 
 * @author Serge Rieder
 */
public class ERDAssociation extends ERDObject<DBSForeignKey>
{

	private ERDTable primaryKeyTable;
	private ERDTable foreignKeyTable;

	public ERDAssociation(DBSForeignKey object, ERDTable foreignTable, ERDTable primaryTable)
	{
		super(object);
		this.primaryKeyTable = primaryTable;
		this.foreignKeyTable = foreignTable;
		this.primaryKeyTable.addPrimaryKeyRelationship(this);
		this.foreignKeyTable.addForeignKeyRelationship(this);
	}

	/**
	 * @return Returns the foreignKeyTable.
	 */
	public ERDTable getForeignKeyTable()
	{
		return foreignKeyTable;
	}

	/**
	 * @return Returns the primaryKeyTable.
	 */
	public ERDTable getPrimaryKeyTable()
	{
		return primaryKeyTable;
	}

	public void setPrimaryKeyTable(ERDTable targetPrimaryKey)
	{
		this.primaryKeyTable = targetPrimaryKey;
	}	
	
	/**
	 * @param sourceForeignKey the foreign key table you are connecting from
	 */
	public void setForeignKeyTable(ERDTable sourceForeignKey)
	{
		this.foreignKeyTable = sourceForeignKey;
	}
}