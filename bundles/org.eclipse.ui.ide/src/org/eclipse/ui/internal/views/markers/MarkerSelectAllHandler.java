package org.eclipse.ui.internal.views.markers;
/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.IHandler;
import org.eclipse.ui.views.markers.MarkerViewHandler;

/**
 * MarkerSelectAllHandler is the handler for the select all action.
 * @since 3.4
 *
 */
public class MarkerSelectAllHandler extends MarkerViewHandler implements
		IHandler {

	/* (non-Javadoc)
	 * @see org.eclipse.core.commands.IHandler#execute(org.eclipse.core.commands.ExecutionEvent)
	 */
	public Object execute(ExecutionEvent event)  {
		ExtendedMarkersView view = getView(event);
		if(view != null)
			view.selectAll();
		return this;
	}

}
