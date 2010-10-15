/*******************************************************************************
 * Copyright (c) 2008, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.ui.internal.workbench.swt;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.IDisposable;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.services.contributions.IContributionFactory;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.core.services.log.Logger;
import org.eclipse.e4.core.services.statusreporter.StatusReporter;
import org.eclipse.e4.ui.bindings.keys.KeyBindingDispatcher;
import org.eclipse.e4.ui.css.core.util.impl.resources.OSGiResourceLocator;
import org.eclipse.e4.ui.css.swt.engine.CSSSWTEngineImpl;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.eclipse.e4.ui.css.swt.theme.IThemeManager;
import org.eclipse.e4.ui.internal.workbench.Activator;
import org.eclipse.e4.ui.internal.workbench.E4Workbench;
import org.eclipse.e4.ui.internal.workbench.Policy;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.MApplicationElement;
import org.eclipse.e4.ui.model.application.MContribution;
import org.eclipse.e4.ui.model.application.ui.MContext;
import org.eclipse.e4.ui.model.application.ui.MElementContainer;
import org.eclipse.e4.ui.model.application.ui.MGenericStack;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.advanced.MPlaceholder;
import org.eclipse.e4.ui.model.application.ui.basic.MTrimmedWindow;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.e4.ui.services.IStylingEngine;
import org.eclipse.e4.ui.workbench.IPresentationEngine;
import org.eclipse.e4.ui.workbench.IResourceUtilities;
import org.eclipse.e4.ui.workbench.IWorkbench;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.swt.factories.IRendererFactory;
import org.eclipse.e4.ui.workbench.swt.modeling.MenuServiceFilter;
import org.eclipse.emf.ecore.impl.EObjectImpl;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jface.bindings.keys.SWTKeySupport;
import org.eclipse.jface.bindings.keys.formatting.KeyFormatterFactory;
import org.eclipse.jface.databinding.swt.SWTObservables;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.testing.TestableObject;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.w3c.dom.Element;
import org.w3c.dom.css.CSSStyleDeclaration;

public class PartRenderingEngine implements IPresentationEngine {
	public static final String engineURI = "platform:/plugin/org.eclipse.e4.ui.workbench.swt/"
			+ "org.eclipse.e4.ui.internal.workbench.swt.PartRenderingEngine";

	private static final String defaultFactoryUrl = "platform:/plugin/org.eclipse.e4.ui.workbench.renderers.swt/"
			+ "org.eclipse.e4.ui.workbench.renderers.swt.WorkbenchRendererFactory";
	private String factoryUrl;

	IRendererFactory curFactory = null;

	MenuServiceFilter menuServiceFilter;

	org.eclipse.swt.widgets.Listener keyListener;

	// Life Cycle handlers
	private EventHandler toBeRenderedHandler = new EventHandler() {
		public void handleEvent(Event event) {

			MUIElement changedElement = (MUIElement) event
					.getProperty(UIEvents.EventTags.ELEMENT);
			MElementContainer<?> parent = changedElement.getParent();

			// Handle Detached Windows
			if (parent == null && changedElement instanceof MWindow) {
				parent = (MElementContainer<?>) ((EObjectImpl) changedElement)
						.eContainer();
			}

			// If the parent isn't displayed who cares?
			if (parent == null || parent.getWidget() == null)
				return;

			if (changedElement.isToBeRendered()) {
				Activator.trace(Policy.DEBUG_RENDERER, "visible -> true", null); //$NON-NLS-1$

				// Note that the 'createGui' protocol calls 'childAdded'
				Widget w = (Widget) createGui(changedElement);
				if (w instanceof Control && !(w instanceof Shell)) {
					fixZOrder(changedElement);
				}
			} else {
				Activator
						.trace(Policy.DEBUG_RENDERER, "visible -> false", null); //$NON-NLS-1$

				// Note that the 'removeGui' protocol calls 'childRemoved'
				removeGui(changedElement);
			}

		}
	};

	private EventHandler visibilityHandler = new EventHandler() {
		public void handleEvent(Event event) {
			MUIElement changedElement = (MUIElement) event
					.getProperty(UIEvents.EventTags.ELEMENT);
			if (!(changedElement.getWidget() instanceof Control))
				return;

			Control ctrl = (Control) changedElement.getWidget();
			MElementContainer<MUIElement> parent = changedElement.getParent();
			if (parent == null)
				return;

			AbstractPartRenderer renderer = (AbstractPartRenderer) parent
					.getRenderer();
			if (renderer == null)
				return;

			// Re-parent the control based on the visible state
			if (changedElement.isVisible()) {
				// Ensure that the control is under its 'real' parent if it's
				// visible
				Composite realComp = (Composite) renderer
						.getUIContainer(changedElement);
				ctrl.setParent(realComp);
				fixZOrder(changedElement);
				renderer.childRendered(parent, changedElement);
			} else {
				// Put the control under the 'limbo' shell
				ctrl.setParent(getLimboShell());
				renderer.hideChild(parent, changedElement);
			}
		}
	};

	private EventHandler trimHandler = new EventHandler() {
		public void handleEvent(Event event) {
			Object changedObj = event.getProperty(UIEvents.EventTags.ELEMENT);
			if (!(changedObj instanceof MTrimmedWindow))
				return;

			MTrimmedWindow window = (MTrimmedWindow) changedObj;
			if (window.getWidget() == null)
				return;

			String eventType = (String) event
					.getProperty(UIEvents.EventTags.TYPE);
			if (UIEvents.EventTypes.ADD.equals(eventType)) {
				MUIElement added = (MUIElement) event
						.getProperty(UIEvents.EventTags.NEW_VALUE);
				if (added.isToBeRendered())
					createGui(added, window.getWidget(), window.getContext());
			} else if (UIEvents.EventTypes.REMOVE.equals(eventType)) {
				MUIElement removed = (MUIElement) event
						.getProperty(UIEvents.EventTags.OLD_VALUE);
				if (removed.getRenderer() != null)
					removeGui(removed);
			}
		}
	};

	private EventHandler childrenHandler = new EventHandler() {
		public void handleEvent(Event event) {

			Object changedObj = event.getProperty(UIEvents.EventTags.ELEMENT);
			if (!(changedObj instanceof MElementContainer<?>))
				return;

			MElementContainer<MUIElement> changedElement = (MElementContainer<MUIElement>) changedObj;
			boolean isApplication = changedObj instanceof MApplication;

			// If the parent isn't in the UI then who cares?
			AbstractPartRenderer renderer = getRendererFor(changedElement);
			if (!isApplication && renderer == null)
				return;

			String eventType = (String) event
					.getProperty(UIEvents.EventTags.TYPE);
			if (UIEvents.EventTypes.ADD.equals(eventType)) {
				Activator.trace(Policy.DEBUG_RENDERER, "Child Added", null); //$NON-NLS-1$
				MUIElement added = (MUIElement) event
						.getProperty(UIEvents.EventTags.NEW_VALUE);

				// OK, we have a new -visible- part we either have to create
				// it or host it under the correct parent. Note that we
				// explicitly do *not* render non-selected elements in
				// stacks (to support lazy loading).
				boolean isStack = changedObj instanceof MGenericStack<?>;
				boolean hasWidget = added.getWidget() != null;
				boolean isSelected = added == changedElement
						.getSelectedElement();
				boolean renderIt = !isStack || hasWidget || isSelected;
				if (renderIt) {
					// NOTE: createGui will call 'childAdded' if successful
					Object w = createGui(added);
					if (w instanceof Control && !(w instanceof Shell)) {
						fixZOrder(added);
						((Control) w).getShell().layout(
								new Control[] { (Control) w }, SWT.DEFER);
					}
				} else {
					if (renderer != null)
						renderer.childRendered(changedElement, added);
				}
			} else if (UIEvents.EventTypes.REMOVE.equals(eventType)) {
				Activator.trace(Policy.DEBUG_RENDERER, "Child Removed", null); //$NON-NLS-1$
				MUIElement removed = (MUIElement) event
						.getProperty(UIEvents.EventTags.OLD_VALUE);
				// Removing invisible elements is a NO-OP as far as the
				// renderer is concerned
				if (!removed.isToBeRendered())
					return;

				if (removed.getWidget() instanceof Control) {
					Control ctrl = (Control) removed.getWidget();
					ctrl.setLayoutData(null);
					ctrl.getParent().layout(new Control[] { ctrl },
							SWT.CHANGED | SWT.DEFER);
				}

				if (renderer != null)
					renderer.hideChild(changedElement, removed);
			}
		}
	};

	private IEclipseContext appContext;

	protected Shell testShell;

	protected MApplication theApp;

	@Inject
	@Optional
	protected IEventBroker eventBroker;

	@Inject
	EModelService modelService;

	@Inject
	protected Logger logger;

	private Shell limbo;

	private List<MUIElement> renderedElements = new ArrayList<MUIElement>();

	private MUIElement removeRoot = null;

	@Inject
	public PartRenderingEngine(
			@Named(E4Workbench.RENDERER_FACTORY_URI) @Optional String factoryUrl) {
		if (factoryUrl == null) {
			factoryUrl = defaultFactoryUrl;
		}
		this.factoryUrl = factoryUrl;
	}

	protected void fixZOrder(MUIElement element) {
		MElementContainer<MUIElement> parent = element.getParent();
		if (parent == null || !(element.getWidget() instanceof Control))
			return;

		Control elementCtrl = (Control) element.getWidget();
		Control prevCtrl = null;
		for (MUIElement kid : parent.getChildren()) {
			if (kid == element) {
				if (prevCtrl != null)
					elementCtrl.moveBelow(prevCtrl);
				else
					elementCtrl.moveAbove(null);
				return;
			} else if (kid.getWidget() instanceof Control) {
				prevCtrl = (Control) kid.getWidget();
			}
		}
	}

	/**
	 * Initialize a part renderer from the extension point.
	 * 
	 * @param context
	 *            the context for the part factories
	 */
	@PostConstruct
	void initialize(IEclipseContext context) {
		this.appContext = context;

		// initialize the correct key-binding display formatter
		KeyFormatterFactory.setDefault(SWTKeySupport
				.getKeyFormatterForPlatform());

		// Add the renderer to the context
		context.set(IPresentationEngine.class.getName(), this);

		IRendererFactory factory = null;
		IContributionFactory contribFactory = context
				.get(IContributionFactory.class);
		try {
			factory = (IRendererFactory) contribFactory.create(factoryUrl,
					context);
		} catch (Exception e) {
			logger.warn(e, "Could not create rendering factory");
		}

		// Try to load the default one
		if (factory == null) {
			try {
				factory = (IRendererFactory) contribFactory.create(
						defaultFactoryUrl, context);
			} catch (Exception e) {
				logger.error(e, "Could not create default rendering factory");
			}
		}

		if (factory == null) {
			throw new IllegalStateException(
					"Could not create any rendering factory. Aborting ...");
		}

		curFactory = factory;
		context.set(IRendererFactory.class, curFactory);

		// Hook up the widget life-cycle subscriber
		if (eventBroker != null) {
			eventBroker.subscribe(UIEvents.buildTopic(UIEvents.UIElement.TOPIC,
					UIEvents.UIElement.TOBERENDERED), toBeRenderedHandler);
			eventBroker.subscribe(UIEvents.buildTopic(UIEvents.UIElement.TOPIC,
					UIEvents.UIElement.VISIBLE), visibilityHandler);
			eventBroker.subscribe(UIEvents.buildTopic(
					UIEvents.ElementContainer.TOPIC,
					UIEvents.ElementContainer.CHILDREN), childrenHandler);
			eventBroker.subscribe(UIEvents.buildTopic(
					UIEvents.TrimmedWindow.TOPIC,
					UIEvents.TrimmedWindow.TRIMBARS), trimHandler);
		}
	}

	@PreDestroy
	void contextDisposed() {
		if (eventBroker == null)
			return;
		eventBroker.unsubscribe(toBeRenderedHandler);
		eventBroker.unsubscribe(visibilityHandler);
		eventBroker.unsubscribe(childrenHandler);
		eventBroker.unsubscribe(trimHandler);
	}

	private static void populateModelInterfaces(MContext contextModel,
			IEclipseContext context, Class<?>[] interfaces) {
		for (Class<?> intf : interfaces) {
			Activator.trace(Policy.DEBUG_CONTEXTS,
					"Adding " + intf.getName() + " for " //$NON-NLS-1$ //$NON-NLS-2$
							+ contextModel.getClass().getName(), null);
			context.set(intf.getName(), contextModel);

			populateModelInterfaces(contextModel, context, intf.getInterfaces());
		}
	}

	private String getContextName(MUIElement element) {
		StringBuilder builder = new StringBuilder(element.getClass()
				.getSimpleName());
		String elementId = element.getElementId();
		if (elementId != null && elementId.length() != 0) {
			builder.append(" (").append(elementId).append(") ");
		}
		builder.append("Context");
		return builder.toString();
	}

	public Object createGui(MUIElement element, Object parentWidget,
			IEclipseContext parentContext) {
		if (!element.isToBeRendered())
			return null;

		if (!renderedElements.contains(element))
			renderedElements.add(element);

		// no creates while processing a remove
		if (removeRoot != null) {
			return null;
		}

		if (element.getWidget() != null) {
			if (element.getWidget() instanceof Control
					&& parentWidget instanceof Composite) {
				Control ctrl = (Control) element.getWidget();
				if (ctrl.getParent() != parentWidget) {
					ctrl.setParent((Composite) parentWidget);
				}
			}

			// Now that we have a widget let the parent (if any) know
			if (element.getParent() instanceof MUIElement) {
				MElementContainer<MUIElement> parentElement = element
						.getParent();
				AbstractPartRenderer parentRenderer = getRendererFor(parentElement);
				if (parentRenderer != null)
					parentRenderer.childRendered(parentElement, element);
			}
			return element.getWidget();
		}

		if (element instanceof MContext) {
			MContext ctxt = (MContext) element;
			// Assert.isTrue(ctxt.getContext() == null,
			// "Before rendering Context should be null");
			if (ctxt.getContext() == null) {
				IEclipseContext lclContext = parentContext
						.createChild(getContextName(element));
				populateModelInterfaces(ctxt, lclContext, element.getClass()
						.getInterfaces());
				ctxt.setContext(lclContext);

				// System.out.println("New Context: " + lclContext.toString()
				// + " parent: " + parentContext.toString());

				// make sure the context knows about these variables that have
				// been defined in the model
				for (String variable : ctxt.getVariables()) {
					lclContext.declareModifiable(variable);
				}

				Map<String, String> props = ctxt.getProperties();
				for (String key : props.keySet()) {
					lclContext.set(key, props.get(key));
				}

				E4Workbench.processHierarchy(element);
			}
		}

		// Create a control appropriate to the part
		Object newWidget = createWidget(element, parentWidget);

		// Remember that we've created the control
		if (newWidget != null) {
			AbstractPartRenderer renderer = getRendererFor(element);

			// Have the renderer hook up any widget specific listeners
			renderer.hookControllerLogic(element);

			// Process its internal structure through the renderer that created
			// it
			if (element instanceof MElementContainer) {
				renderer.processContents((MElementContainer<MUIElement>) element);
			}

			// Allow a final chance to set up
			renderer.postProcess(element);

			// Now that we have a widget let the parent (if any) know
			if (element.getParent() instanceof MUIElement) {
				MElementContainer<MUIElement> parentElement = element
						.getParent();
				AbstractPartRenderer parentRenderer = getRendererFor(parentElement);
				if (parentRenderer != null)
					parentRenderer.childRendered(parentElement, element);
			}
		} else {
			// failed to create the widget, dispose its context if necessary
			if (element instanceof MContext) {
				MContext ctxt = (MContext) element;
				IEclipseContext lclContext = ctxt.getContext();
				if (lclContext instanceof IDisposable) {
					((IDisposable) lclContext).dispose();
				}
			}
		}

		return newWidget;
	}

	private IEclipseContext getContext(MElementContainer<MUIElement> parent) {
		if (parent instanceof MContext) {
			return ((MContext) parent).getContext();
		}
		return modelService.getContainingContext(parent);
	}

	public Object createGui(MUIElement element) {
		// Obtain the necessary parent widget
		Object parent = null;
		MUIElement parentME = element.getParent();
		if (parentME == null)
			parentME = (MUIElement) ((EObjectImpl) element).eContainer();
		if (parentME != null) {
			AbstractPartRenderer renderer = getRendererFor(parentME);
			if (renderer != null) {
				if (!element.isVisible()) {
					parent = getLimboShell();
				} else {
					parent = renderer.getUIContainer(element);
				}
			}
		}

		// Obtain the necessary parent context
		IEclipseContext parentContext = null;
		if (element.getCurSharedRef() != null) {
			MPlaceholder ph = element.getCurSharedRef();
			parentContext = getContext(ph.getParent());
		} else if (parentContext == null && element.getParent() != null) {
			parentContext = getContext(element.getParent());
		} else if (parentContext == null && element.getParent() == null) {
			parentContext = getContext((MElementContainer<MUIElement>) ((EObjectImpl) element)
					.eContainer());
		}

		return createGui(element, parent, parentContext);
	}

	private Shell getLimboShell() {
		if (limbo == null) {
			limbo = new Shell(Display.getCurrent(), SWT.NONE);
			limbo.setBackgroundMode(SWT.INHERIT_DEFAULT);
			limbo.setData(ShellActivationListener.DIALOG_IGNORE_KEY,
					Boolean.TRUE);
		}
		return limbo;
	}

	/**
	 * @param element
	 */
	public void removeGui(final MUIElement element) {
		// wrap the handling in a SafeRunner so that exceptions do not prevent
		// the menu from being shown
		SafeRunner.run(new ISafeRunnable() {
			public void handleException(Throwable e) {
				if (e instanceof Error) {
					// errors are deadly, we shouldn't ignore these
					throw (Error) e;
				} else {
					// log exceptions otherwise
					if (logger != null) {
						logger.error(e);
					}
				}
			}

			public void run() throws Exception {
				safeRemoveGui(element);
			}
		});
	}

	private void safeRemoveGui(MUIElement element) {
		if (removeRoot == null)
			removeRoot = element;
		renderedElements.remove(element);

		// We call 'hideChild' *before* checking if the actual element
		// has been rendered in order to pick up cases of 'lazy loading'
		MUIElement parent = element.getParent();
		AbstractPartRenderer parentRenderer = parent != null ? getRendererFor(parent)
				: null;
		if (parentRenderer != null) {
			parentRenderer.hideChild(element.getParent(), element);
		}

		AbstractPartRenderer renderer = getRendererFor(element);

		// If the element hasn't been rendered then this is a NO-OP
		if (renderer != null) {

			if (element instanceof MElementContainer<?>) {
				MElementContainer<MUIElement> container = (MElementContainer<MUIElement>) element;
				MUIElement selectedElement = container.getSelectedElement();
				for (MUIElement child : container.getChildren()) {
					// remove stuff in the "back" first
					if (child != selectedElement) {
						removeGui(child);
					}
				}

				if (selectedElement != null) {
					// now remove the selected element
					removeGui(selectedElement);
				}
			}

			renderer.disposeWidget(element);

			// unset the client object
			if (element instanceof MContribution) {
				IEclipseContext parentContext = renderer.getContext(element);
				ContextInjectionFactory.uninject(
						((MContribution) element).getObject(), parentContext);
				((MContribution) element).setObject(null);
			}

			// dispose the context
			if (element instanceof MContext) {
				clearContext((MContext) element);
			}
		}

		if (removeRoot == element)
			removeRoot = null;
	}

	private void clearContext(MContext contextME) {
		MContext ctxt = (MContext) contextME;
		IEclipseContext lclContext = ctxt.getContext();
		if (lclContext != null) {
			IEclipseContext parentContext = lclContext.getParent();
			IEclipseContext child = parentContext.getActiveChild();
			if (child == lclContext) {
				child.deactivate();
			}

			ctxt.setContext(null);
			if (lclContext instanceof IDisposable) {
				((IDisposable) lclContext).dispose();
			}
		}
	}

	protected Object createWidget(MUIElement element, Object parent) {
		AbstractPartRenderer renderer = getRenderer(element, parent);
		if (renderer != null) {
			// Remember which renderer is responsible for this widget
			element.setRenderer(renderer);
			Object newWidget = renderer.createWidget(element, parent);
			if (newWidget != null) {
				renderer.bindWidget(element, newWidget);
				return newWidget;
			}
		}

		return null;
	}

	private AbstractPartRenderer getRenderer(MUIElement uiElement, Object parent) {
		return curFactory.getRenderer(uiElement, parent);
	}

	protected AbstractPartRenderer getRendererFor(MUIElement element) {
		return (AbstractPartRenderer) element.getRenderer();
	}

	public Object run(final MApplicationElement uiRoot,
			final IEclipseContext runContext) {
		final Display display = Display.getDefault();
		Realm.runWithDefault(SWTObservables.getRealm(display), new Runnable() {

			public void run() {
				initializeStyling(display, runContext);

				// Register an SWT resource handler
				runContext.set(IResourceUtilities.class.getName(),
						new ResourceUtility());

				// set up the keybinding manager
				KeyBindingDispatcher dispatcher = (KeyBindingDispatcher) ContextInjectionFactory
						.make(KeyBindingDispatcher.class, runContext);
				runContext.set(KeyBindingDispatcher.class.getName(), dispatcher);
				keyListener = dispatcher.getKeyDownFilter();
				display.addFilter(SWT.KeyDown, keyListener);
				display.addFilter(SWT.Traverse, keyListener);

				menuServiceFilter = ContextInjectionFactory.make(
						MenuServiceFilter.class, runContext);
				display.addFilter(SWT.Show, menuServiceFilter);
				display.addFilter(SWT.Hide, menuServiceFilter);
				display.addFilter(SWT.Dispose, menuServiceFilter);
				runContext.set(MenuServiceFilter.class, menuServiceFilter);

				// Show the initial UI

				// Create a 'limbo' shell (used to host controls that shouldn't
				// be in the current layout)
				Shell limbo = getLimboShell();
				runContext.set("limbo", limbo);

				// HACK!! we should loop until the display gets disposed...
				// ...then we listen for the last 'main' window to get disposed
				// and dispose the Display
				testShell = null;
				theApp = null;
				boolean spinOnce = true;
				if (uiRoot instanceof MApplication) {
					ShellActivationListener shellDialogListener = new ShellActivationListener(
							(MApplication) uiRoot);
					display.addFilter(SWT.Activate, shellDialogListener);
					display.addFilter(SWT.Deactivate, shellDialogListener);
					spinOnce = false; // loop until the app closes
					theApp = (MApplication) uiRoot;
					// long startTime = System.currentTimeMillis();
					MWindow selected = theApp.getSelectedElement();
					if (selected == null) {
						for (MWindow window : theApp.getChildren()) {
							createGui(window);
						}
					} else {
						// render the selected one first
						createGui(selected);
						for (MWindow window : theApp.getChildren()) {
							if (selected != window) {
								createGui(window);
							}
						}
					}
					// long endTime = System.currentTimeMillis();
					// System.out.println("Render: " + (endTime - startTime));
					// tell the app context we are starting so the splash is
					// torn down
					IApplicationContext ac = appContext
							.get(IApplicationContext.class);
					if (ac != null)
						ac.applicationRunning();
				} else if (uiRoot instanceof MUIElement) {
					if (uiRoot instanceof MWindow) {
						testShell = (Shell) createGui((MUIElement) uiRoot);
					} else {
						// Special handling for partial models (for testing...)
						testShell = new Shell(display, SWT.SHELL_TRIM);
						createGui((MUIElement) uiRoot, testShell, null);
					}
				}

				TestableObject testableObject = (TestableObject) runContext
						.get(TestableObject.class.getName());
				if (testableObject instanceof E4Testable) {
					((E4Testable) testableObject).init(display,
							(IWorkbench) runContext.get(IWorkbench.class
									.getName()));
				}

				// Spin the event loop until someone disposes the display
				while (((testShell != null && !testShell.isDisposed()) || (!theApp
						.getChildren().isEmpty() && someAreVisible(theApp
						.getChildren())))
						&& !display.isDisposed()) {
					try {
						if (!display.readAndDispatch()) {
							runContext.processWaiting();
							if (spinOnce)
								return;
							display.sleep();
						}
					} catch (ThreadDeath th) {
						throw th;
					} catch (Exception ex) {
						handle(ex, runContext);
					} catch (Error err) {
						handle(err, runContext);
					}
				}
				if (!spinOnce) {
					cleanUp();
				}
			}

			private void handle(Throwable ex, final IEclipseContext appContext) {
				StatusReporter statusReporter = (StatusReporter) appContext
						.get(StatusReporter.class.getName());
				if (statusReporter != null) {
					statusReporter.show(StatusReporter.ERROR, "Internal Error",
							ex);
				} else {
					if (logger != null) {
						logger.error(ex);
					}
				}
			}
		});

		return IApplication.EXIT_OK;
	}

	protected boolean someAreVisible(List<MWindow> windows) {
		for (MWindow win : windows) {
			if (win.isToBeRendered() && win.isVisible()
					&& win.getWidget() != null) {
				return true;
			}
		}
		return false;
	}

	public void stop() {
		// FIXME Without this call the test-suite fails
		cleanUp();
		if (theApp != null) {
			for (MWindow window : theApp.getChildren()) {
				if (window.getWidget() != null) {
					((Shell) window.getWidget()).close();
				}
			}
		} else if (testShell != null && !testShell.isDisposed()) {
			testShell.close();
		}
	}

	/*
	 * There are situations where this is called more than once until we know
	 * why this is needed we should make this safe for multiple calls
	 */
	private void cleanUp() {
		if (menuServiceFilter != null) {
			Display display = Display.getDefault();
			if (!display.isDisposed()) {
				display.removeFilter(SWT.Show, menuServiceFilter);
				display.removeFilter(SWT.Hide, menuServiceFilter);
				display.removeFilter(SWT.Dispose, menuServiceFilter);
				menuServiceFilter.dispose();
				menuServiceFilter = null;
				appContext.remove(MenuServiceFilter.class);
			}
		}
		if (keyListener != null) {
			Display display = Display.getDefault();
			if (!display.isDisposed()) {
				display.removeFilter(SWT.KeyDown, keyListener);
				display.removeFilter(SWT.Traverse, keyListener);
				keyListener = null;
			}
		}
	}

	public static void initializeStyling(Display display,
			IEclipseContext appContext) {
		String cssTheme = (String) appContext.get(E4Application.THEME_ID);
		String cssURI = (String) appContext.get(E4Workbench.CSS_URI_ARG);

		if (cssTheme != null) {
			String cssResourcesURI = (String) appContext
					.get(E4Workbench.CSS_RESOURCE_URI_ARG);

			Bundle bundle = WorkbenchSWTActivator.getDefault().getBundle();
			BundleContext context = bundle.getBundleContext();
			ServiceReference ref = context
					.getServiceReference(IThemeManager.class.getName());
			IThemeManager mgr = (IThemeManager) context.getService(ref);
			final IThemeEngine engine = mgr.getEngineForDisplay(display);

			// Store the app context
			IContributionFactory contribution = (IContributionFactory) appContext
					.get(IContributionFactory.class.getName());
			IEclipseContext cssContext = EclipseContextFactory.create();
			cssContext.set(IContributionFactory.class.getName(), contribution);
			display.setData("org.eclipse.e4.ui.css.context", cssContext); //$NON-NLS-1$

			// Create the OSGi resource locator
			if (cssResourcesURI != null) {
				// TODO: Should this be set through an extension as well?
				engine.registerResourceLocator(new OSGiResourceLocator(
						cssResourcesURI));
			}

			engine.restore(cssTheme);
			// TODO Should we create an empty default theme?

			appContext.set(IThemeEngine.class.getName(), engine);

			appContext.set(IStylingEngine.SERVICE_NAME, new IStylingEngine() {
				public void setClassname(Object widget, String classname) {
					((Widget) widget).setData(
							"org.eclipse.e4.ui.css.CssClassName", classname); //$NON-NLS-1$
					engine.applyStyles((Widget) widget, true);
				}

				public void setId(Object widget, String id) {
					((Widget) widget).setData("org.eclipse.e4.ui.css.id", id); //$NON-NLS-1$
					engine.applyStyles((Widget) widget, true);
				}

				public void style(Object widget) {
					engine.applyStyles((Widget) widget, true);
				}

				public CSSStyleDeclaration getStyle(Object widget) {
					return engine.getStyle((Widget) widget);
				}

				public void setClassnameAndId(Object widget, String classname,
						String id) {
					((Widget) widget).setData(
							"org.eclipse.e4.ui.css.CssClassName", classname); //$NON-NLS-1$
					((Widget) widget).setData("org.eclipse.e4.ui.css.id", id); //$NON-NLS-1$
					engine.applyStyles((Widget) widget, true);
				}

			});
		} else if (cssURI != null) {
			String cssResourcesURI = (String) appContext
					.get(E4Workbench.CSS_RESOURCE_URI_ARG);
			final CSSSWTEngineImpl engine = new CSSSWTEngineImpl(display, true);
			if (cssResourcesURI != null) {
				engine.getResourcesLocatorManager().registerResourceLocator(
						new OSGiResourceLocator(cssResourcesURI.toString()));
			}
			display.setData("org.eclipse.e4.ui.css.context", appContext); //$NON-NLS-1$
			appContext.set(IStylingEngine.SERVICE_NAME, new IStylingEngine() {
				public void setClassname(Object widget, String classname) {
					((Widget) widget).setData(
							"org.eclipse.e4.ui.css.CssClassName", classname); //$NON-NLS-1$
					engine.applyStyles((Widget) widget, true);
				}

				public void setId(Object widget, String id) {
					((Widget) widget).setData("org.eclipse.e4.ui.css.id", id); //$NON-NLS-1$
					engine.applyStyles((Widget) widget, true);
				}

				public void style(Object widget) {
					engine.applyStyles((Widget) widget, true);
				}

				public CSSStyleDeclaration getStyle(Object widget) {
					Element e = engine.getCSSElementContext(widget)
							.getElement();
					if (e == null) {
						return null;
					}
					return engine.getViewCSS().getComputedStyle(e, null);
				}

				public void setClassnameAndId(Object widget, String classname,
						String id) {
					((Widget) widget).setData(
							"org.eclipse.e4.ui.css.CssClassName", classname); //$NON-NLS-1$
					((Widget) widget).setData("org.eclipse.e4.ui.css.id", id); //$NON-NLS-1$
					engine.applyStyles((Widget) widget, true);
				}

			});

			URL url;
			InputStream stream = null;
			try {
				url = FileLocator.resolve(new URL(cssURI));
				stream = url.openStream();
				engine.parseStyleSheet(stream);
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				if (stream != null) {
					try {
						stream.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

			Shell[] shells = display.getShells();
			for (Shell s : shells) {
				try {
					s.setRedraw(false);
					s.reskin(SWT.ALL);
					engine.applyStyles(s, true);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					s.setRedraw(true);
				}
			}
		}

	}
}
