/*******************************************************************************
 * Copyright (c) 2009, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.e4.ui.tests.reconciler;

import java.util.Collection;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.commands.MCommand;
import org.eclipse.e4.ui.model.application.commands.MHandler;
import org.eclipse.e4.ui.model.application.commands.impl.CommandsFactoryImpl;
import org.eclipse.e4.ui.workbench.modeling.ModelDelta;
import org.eclipse.e4.ui.workbench.modeling.ModelReconciler;

public abstract class ModelReconcilerHandlerContainerTest extends
		ModelReconcilerTest {

	public void testHandlerContainer_Handlers_Add_UnboundHandler() {
		MApplication application = createApplication();

		saveModel();

		ModelReconciler reconciler = createModelReconciler();
		reconciler.recordChanges(application);

		MHandler handler = CommandsFactoryImpl.eINSTANCE.createHandler();
		application.getHandlers().add(handler);

		Object state = reconciler.serialize();

		application = createApplication();

		Collection<ModelDelta> deltas = constructDeltas(application, state);

		assertEquals(0, application.getHandlers().size());

		applyAll(deltas);

		assertEquals(1, application.getHandlers().size());

		handler = application.getHandlers().get(0);
		assertEquals(null, handler.getCommand());
	}

	public void testHandlerContainer_Handlers_Add_BoundHandler() {
		MApplication application = createApplication();

		MCommand command = CommandsFactoryImpl.eINSTANCE.createCommand();
		application.getCommands().add(command);

		saveModel();

		ModelReconciler reconciler = createModelReconciler();
		reconciler.recordChanges(application);

		MHandler handler = CommandsFactoryImpl.eINSTANCE.createHandler();
		handler.setCommand(command);
		application.getHandlers().add(handler);

		Object state = reconciler.serialize();

		application = createApplication();
		command = application.getCommands().get(0);

		Collection<ModelDelta> deltas = constructDeltas(application, state);

		assertEquals(0, application.getHandlers().size());

		applyAll(deltas);

		assertEquals(1, application.getHandlers().size());

		handler = application.getHandlers().get(0);
		assertEquals(command, handler.getCommand());
	}

	public void testHandlerContainer_Handlers_Remove_UnboundHandler() {
		MApplication application = createApplication();

		MHandler handler = CommandsFactoryImpl.eINSTANCE.createHandler();
		application.getHandlers().add(handler);

		saveModel();

		ModelReconciler reconciler = createModelReconciler();
		reconciler.recordChanges(application);

		application.getHandlers().remove(0);

		Object state = reconciler.serialize();

		application = createApplication();
		handler = application.getHandlers().get(0);

		Collection<ModelDelta> deltas = constructDeltas(application, state);

		assertEquals(1, application.getHandlers().size());

		assertEquals(handler, application.getHandlers().get(0));

		applyAll(deltas);

		assertEquals(0, application.getHandlers().size());
	}

	public void testHandlerContainer_Handlers_Remove_BoundHandler() {
		MApplication application = createApplication();

		MCommand command = CommandsFactoryImpl.eINSTANCE.createCommand();
		application.getCommands().add(command);

		MHandler handler = CommandsFactoryImpl.eINSTANCE.createHandler();
		handler.setCommand(command);
		application.getHandlers().add(handler);

		saveModel();

		ModelReconciler reconciler = createModelReconciler();
		reconciler.recordChanges(application);

		application.getHandlers().remove(handler);

		Object state = reconciler.serialize();

		application = createApplication();
		command = application.getCommands().get(0);
		handler = application.getHandlers().get(0);

		Collection<ModelDelta> deltas = constructDeltas(application, state);

		assertEquals(1, application.getHandlers().size());

		handler = application.getHandlers().get(0);
		assertEquals(command, handler.getCommand());

		applyAll(deltas);

		assertEquals(0, application.getHandlers().size());
	}
}
