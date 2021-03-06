/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.ui.tests.session;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.themes.IThemeManager;

import junit.framework.TestCase;

/**
 * Tests various persistent theme properties.
 * 
 * @since 3.4
 */
public class ThemeStateTest extends TestCase {

	public ThemeStateTest(final String name) {
		super(name);
	}

	/**
	 * Tests to ensure that the workbench still comes up if there's a bad theme
	 * ID in the preference store.
	 */
	public void testBadPreference() {
		String themeId = PlatformUI.getWorkbench().getThemeManager()
				.getCurrentTheme().getId();
		assertNotNull(themeId);
		assertEquals(IThemeManager.DEFAULT_THEME, themeId);
	}
}
