/*
 * Copyright (C) 2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE AbstractInterpretationV2 plug-in.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE AbstractInterpretationV2 plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE AbstractInterpretationV2 plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE AbstractInterpretationV2 plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm;

import java.util.Deque;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Boogie2SMT;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.model.IAbstractState;

/**
 * Interface that describes how abstract states can be stored and retrieved relative to actions of a (program) model.
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public interface IAbstractStateStorage<STATE extends IAbstractState<STATE, ACTION, VARDECL>, ACTION, VARDECL, LOCATION> {

	AbstractMultiState<STATE, ACTION, VARDECL> getAbstractPostStates(ACTION transition);

	AbstractMultiState<STATE, ACTION, VARDECL> addAbstractPostState(ACTION transition,
			AbstractMultiState<STATE, ACTION, VARDECL> state);

	IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION> createStorage(ACTION scope);

	Map<LOCATION, Term> getLoc2Term(final ACTION initialTransition, final Script script, final Boogie2SMT bpl2smt);

	Map<LOCATION, Set<AbstractMultiState<STATE, ACTION, VARDECL>>> getLoc2States(final ACTION initialTransition);

	Map<LOCATION, STATE> getLoc2SingleStates(final ACTION initialTransition);

	Set<Term> getTerms(final ACTION initialTransition, final Script script, final Boogie2SMT bpl2smt);

	Set<STATE> getAbstractPostStates(Deque<ACTION> callStack, ACTION symbol);

	void scopeFixpointReached();

	void saveSummarySubstituion(ACTION action, AbstractMultiState<STATE, ACTION, VARDECL> summaryPostState,
			ACTION summaryAction);
}
