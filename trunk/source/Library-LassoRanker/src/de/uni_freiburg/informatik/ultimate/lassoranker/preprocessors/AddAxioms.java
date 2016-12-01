/*
 * Copyright (C) 2014-2015 Jan Leike (leike@informatik.uni-freiburg.de)
 * Copyright (C) 2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2012-2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE LassoRanker Library.
 * 
 * The ULTIMATE LassoRanker Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE LassoRanker Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE LassoRanker Library. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE LassoRanker Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE LassoRanker Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.lassoranker.preprocessors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.lassoranker.exceptions.TermException;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.Util;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transformations.IReplacementVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transformations.ReplacementVarFactory;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.ModifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.ConstantFinder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.Substitution;

/**
 * Adds axioms to the stem and loop transition
 * 
 * @author Jan Leike
 */
public class AddAxioms extends TransitionPreprocessor {
	public static final String s_Description = "Add axioms to the transition";
	
	private final Term[] mAxioms;
	
	private final Set<ApplicationTerm> mConstants = new HashSet<>();
	
	private final ReplacementVarFactory mReplacementVarFactory;
	
	/**
	 * @param axioms
	 *            the axioms that should be added to stem and loop
	 */
	public AddAxioms(final ReplacementVarFactory replacementVarFactory, final Term[] axioms) {
		mReplacementVarFactory = replacementVarFactory;
		if (axioms == null) {
			mAxioms = new Term[0];
		} else {
			mAxioms = axioms;
		}
		for (final Term axiom : mAxioms) {
			// TODO: Check if the boolean parameter is correct; it was necessary to restore the build
			mConstants.addAll(new ConstantFinder().findConstants(axiom, false));
		}
	}
	
	@Override
	public ModifiableTransFormula process(final Script script, final ModifiableTransFormula tf) throws TermException {
		final Map<Term, Term> substitutionMapping = new HashMap<>();
		// Add constant variables as in- and outVars
		for (final ApplicationTerm constVar : mConstants) {
			final IReplacementVar repVar = mReplacementVarFactory.getOrConstuctReplacementVar(constVar);
			tf.addInVar(repVar, repVar.getTermVariable());
			tf.addOutVar(repVar, repVar.getTermVariable());
			substitutionMapping.put(constVar, repVar.getTermVariable());
		}
		final Term axioms = new Substitution(script, substitutionMapping).transform(Util.and(script, mAxioms));
		Term formula = tf.getFormula();
		formula = Util.and(script, formula, axioms);
		tf.setFormula(formula);
		return tf;
	}
	
	@Override
	public String getDescription() {
		return s_Description;
	}
}
