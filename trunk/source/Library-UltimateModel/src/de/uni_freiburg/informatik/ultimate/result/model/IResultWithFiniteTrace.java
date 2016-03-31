/*
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE Core.
 * 
 * The ULTIMATE Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Core. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Core, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP), 
 * containing parts covered by the terms of the Eclipse Public License, the 
 * licensors of the ULTIMATE Core grant you additional permission 
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.result.model;

import java.util.List;

import de.uni_freiburg.informatik.ultimate.models.ILocation;
import de.uni_freiburg.informatik.ultimate.translation.IProgramExecution;

/**
 * {@link IResultWithFiniteTrace} describes results that contain a finite trace.
 * The trace consists of trace elements of type TE. Furthermore, the trace is
 * described by (a) a sequence of {@link ILocation} and (b) by a
 * {@link IProgramExecution} that may contain program states, which are
 * described by expressions of type E.
 * 
 * @author dietsch@informatik.uni-freiburg.de
 */
public interface IResultWithFiniteTrace<TE, E> extends IResult {

	public List<ILocation> getFailurePath();

	public IProgramExecution<TE, E> getProgramExecution();
}