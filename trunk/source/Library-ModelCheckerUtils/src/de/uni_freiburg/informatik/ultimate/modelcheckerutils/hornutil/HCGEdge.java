/*
 * Copyright (C) 2016 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2016 Mostafa M.A. (mostafa.amin93@gmail.com)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE ModelCheckerUtils Library.
 *
 * The ULTIMATE ModelCheckerUtils Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE ModelCheckerUtils Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ModelCheckerUtils Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ModelCheckerUtils Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE ModelCheckerUtils Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.modelcheckerutils.hornutil;

import java.util.HashMap;

import de.uni_freiburg.informatik.ultimate.logic.Term;

/**
 * This class is the edge class in a Horn clause graph.
 * It represents a hyper edge that is labelled with a transition formula.
 * The hyper edge may have many sources but has only one target.
 * Additionally there has to be a mapping from the sources to the variables in the
 * transition formula.
 * 
 * (The source is a predicate symbol, so the mapping should say something like
 *  "The variable x in my formula is the i-th argument of the HornClausePredicateSymbol
 *   P that is the j-th source element of this hyper edge" or so..)
 * 
 * @author alex
 *
 */
public class HCGEdge {
	
	Term mFormula;

	HashMap<HornClausePredicateSymbol, Object> mSources;
	HornClausePredicateSymbol mTarget;
	
}
