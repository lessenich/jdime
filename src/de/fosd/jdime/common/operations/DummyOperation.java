/*******************************************************************************
 * Copyright (c) 2013 Olaf Lessenich.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Olaf Lessenich - initial API and implementation
 ******************************************************************************/
/**
 * 
 */
package de.fosd.jdime.common.operations;

import java.io.IOException;

import de.fosd.jdime.common.MergeReport;
import de.fosd.jdime.common.NotYetImplementedException;
import de.fosd.jdime.engine.EngineNotFoundException;

/**
 * @author Olaf Lessenich
 * 
 */
public class DummyOperation extends Operation {

	/**
	 * 
	 */
	public DummyOperation() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public final MergeReport apply() throws EngineNotFoundException,
			IOException, InterruptedException, NotYetImplementedException {
		throw new NotYetImplementedException();
	}

	@Override
	public final String description() throws NotYetImplementedException {
		throw new NotYetImplementedException();
	}

}
