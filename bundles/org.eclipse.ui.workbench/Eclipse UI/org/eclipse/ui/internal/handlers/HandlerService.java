/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.ui.internal.handlers;

import org.eclipse.core.commands.CommandManager;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.ui.ISourceProvider;
import org.eclipse.ui.ISources;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;

/**
 * <p>
 * Provides services related to activating and deactivating handlers within the
 * workbench.
 * </p>
 * <p>
 * <em>EXPERIMENTAL</em>. The commands architecture is currently under
 * development for Eclipse 3.1. This class -- its existence, its name and its
 * methods -- are in flux. Do not use this class yet.
 * </p>
 * 
 * @since 3.1
 */
public class HandlerService implements IHandlerService {

	/**
	 * The central authority for determining which handler we should use.
	 */
	private final HandlerAuthority handlerAuthority;

	/**
	 * Constructs a new instance of <code>CommandService</code> using a
	 * command manager.
	 * 
	 * @param commandManager
	 *            The command manager to use; must not be <code>null</code>.
	 * @param context
	 *            The basic set-up of the context; must not be <code>null</code>.
	 */
	public HandlerService(final CommandManager commandManager,
			final IEvaluationContext context) {
		this.handlerAuthority = new HandlerAuthority(commandManager, context);
	}

	public final IHandlerActivation activateHandler(final String commandId,
			final IHandler handler) {
		final IHandlerActivation activation = new HandlerActivation(commandId,
				handler, null, ISources.WORKBENCH, this);
		handlerAuthority.activateHandler(activation);
		return activation;
	}

	public final IHandlerActivation activateHandler(final String commandId,
			final IHandler handler, final Expression expression,
			final int sourcePriority) {
		if (expression == null) {
			throw new NullPointerException("The expression cannot be null"); //$NON-NLS-1$
		}
		// TODO Need to figure out priorities.
		final IHandlerActivation activation = new HandlerActivation(commandId,
				handler, expression, sourcePriority, this);
		handlerAuthority.activateHandler(activation);
		return activation;
	}

	public final void addSourceProvider(final ISourceProvider provider) {
		handlerAuthority.addSourceProvider(provider);
	}

	public final void deactivateHandler(final IHandlerActivation activation) {
		if (activation.getHandlerService() == this) {
			handlerAuthority.deactivateHandler(activation);
		}
	}

	public final void readRegistry() {
		HandlerPersistence.read(this);
	}

	public final void removeSourceProvider(final ISourceProvider provider) {
		handlerAuthority.removeSourceProvider(provider);
	}
}
