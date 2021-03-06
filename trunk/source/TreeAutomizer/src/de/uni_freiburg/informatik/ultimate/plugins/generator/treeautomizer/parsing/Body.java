/*
 * Copyright (C) 2016 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2016 Mostafa M.A. (mostafa.amin93@gmail.com)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE TreeAutomizer Plugin.
 *
 * The ULTIMATE TreeAutomizer Plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TreeAutomizer Plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TreeAutomizer Plugin. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TreeAutomizer Plugin, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TreeAutomizer Plugin grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.treeautomizer.parsing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hornutil.HCSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hornutil.HornClause;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hornutil.HornClausePredicateSymbol;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;

/**
 * @author Mostafa M.A. (mostafa.amin93@gmail.com)
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public class Body {
	private final Cobody mCobody;
	private ApplicationTerm mHead;
	
	public Body() {
		mCobody = new Cobody();
	}

	public void addPredicateToCobody(ApplicationTerm literal) {
		mCobody.addPredicate(literal);
	}

	public HornClause convertToHornClause(final ManagedScript script, final HCSymbolTable symbolTable) {
		final Map<HornClausePredicateSymbol, List<TermVariable>> tt = getBodyPredicateToVars(symbolTable);
		assert tt.size() <= 1;
		final HornClausePredicateSymbol bodySymbol = tt.keySet().iterator().hasNext() ? tt.keySet().iterator().next()
				: symbolTable.getFalseHornClausePredicateSymbol();
//				: new HornClausePredicateSymbol.HornClauseFalsePredicateSymbol();
		return new HornClause(script, symbolTable, getTransitionFormula(script),
				tt.containsKey(bodySymbol) ? tt.get(bodySymbol) : new ArrayList<>(), bodySymbol,
				getCobodyPredicateToVars(symbolTable));

	}
	public boolean setHead(ApplicationTerm literal) {
		if (mHead == null) {
			mHead = literal;
			return true;
		} else {
			return false;
		}
	}

	public void mergeCobody(Cobody cobody) {
		this.mCobody.mergeCobody(cobody);
	}

	
	public void addTransitionFormula(Term formula) {
		mCobody.addTransitionFormula(formula);
	}

	@Override
	public String toString() {
		return '(' + mCobody.toString() + " ==> " + (mHead == null ? "false" : mHead.toString()) + ')';
	}

	// moved this to HCSymbolTable
//	private HornClausePredicateSymbol getHornPredicateSymbol(FunctionSymbol func,
//			Map<String, HornClausePredicateSymbol> predicateSymbols) {
//		if (!predicateSymbols.containsKey(func.getName())) {
//			predicateSymbols.put(func.getName(),
//					new HornClausePredicateSymbol(symbolTable, func.getName(), func.getParameterSorts().length));
//		}
//		return predicateSymbols.get(func.getName());
//	}

	private Map<HornClausePredicateSymbol, List<TermVariable>> getBodyPredicateToVars(
			final HCSymbolTable symbolTable) {

		final HashMap<HornClausePredicateSymbol, List<TermVariable>> res = new HashMap<>();
		if (mHead != null) {
			final ArrayList<TermVariable> vars = new ArrayList<>();
			for (final Term par : mHead.getParameters()) {
				vars.add((TermVariable) par);
			}

//			res.put(getHornPredicateSymbol(head.getFunction(), predicateSymbols), vars);
			res.put(symbolTable.getOrConstructHornClausePredicateSymbol(
						mHead.getFunction().getName(), mHead.getFunction().getParameterSorts()), 
					vars);
		}
		return res;
	}

	public Map<HornClausePredicateSymbol, List<TermVariable>> getCobodyPredicateToVars(
			HCSymbolTable symbolTable) {
		return mCobody.getPredicateToVars(symbolTable);
	}

	public Term getTransitionFormula(ManagedScript script) {
		return mCobody.getTransitionFormula(script);
	}
}
