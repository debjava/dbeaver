/*
 * Copyright (c) 2012, Serge Rieder and others. All Rights Reserved.
 */

/*
 * Created on Jul 20, 2004
 */
package org.jkiss.dbeaver.ext.erd.command;

import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.commands.Command;
import org.jkiss.dbeaver.ext.erd.part.NodePart;

/**
 * Command to move the bounds of an existing table. Only used with
 * XYLayoutEditPolicy (manual layout)
 * 
 * @author Serge Rieder
 */
public class NodeMoveCommand extends Command
{

	private NodePart nodePart;
	private Rectangle oldBounds;
	private Rectangle newBounds;

	public NodeMoveCommand(NodePart nodePart, Rectangle oldBounds, Rectangle newBounds)
	{
		super();
		this.nodePart = nodePart;
		this.oldBounds = oldBounds;
		this.newBounds = newBounds;
	}

	@Override
    public void execute()
	{
/*
        List tcList = nodePart.getTargetConnections();
        for (Object tc : tcList) {
            AssociationPart as = (AssociationPart)tc ;
            PolylineConnection pc = (PolylineConnection) as.getFigure();
            pc.getConnectionRouter().route(pc);
        }
*/
        nodePart.modifyBounds(newBounds);
	}

	@Override
    public void undo()
	{
		nodePart.modifyBounds(oldBounds);
	}

}