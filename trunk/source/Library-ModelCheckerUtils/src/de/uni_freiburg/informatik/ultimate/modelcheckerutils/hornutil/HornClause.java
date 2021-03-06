package de.uni_freiburg.informatik.ultimate.modelcheckerutils.hornutil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IInternalAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormulaBuilder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula.Infeasibility;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.TermTransferrer;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;

/**
 * This is our internal representation of a Horn clause. A Horn clause consists of - a body with -- n uninterpreted
 * predicate symbols (n >= 0) -- a transition formula - a head with either -- an uninterpreted predicate symbol or --
 * false - a mapping that assigns each of the argument positions of the uninterpreted predicate a free variable in the
 * transition formula
 *
 * Note that the uninterpreted predicate symbols may only have an arity and a name. If in the formula there was a
 * complex expression in one of the arguments of the corresponding atom, this has to be encoded into the transition
 * formula.
 *
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public class HornClause implements IInternalAction {

	/**
	 * Stores for each predicate symbol in the body and, every argument position of the represented atom, which
	 * TermVariable in the transition formula represents that argument in the represented atom.
	 */
	Map<HornClausePredicateSymbol, List<TermVariable>> mBodyPredToTermVariables;

	/**
	 * Stores for the predicate symbol in the head at every argument position of the represented atom, which
	 * TermVariable in the transition formula represents that argument in the represented atom.
	 */
	List<TermVariable> mHeadPredTermVariables;
	HornClausePredicateSymbol mHeadPredicate;

	UnmodifiableTransFormula mTransitionFormula;
	
	public HornClause(final ManagedScript script, final IIcfgSymbolTable symbolTable, 
			final Term transitionFormula, final List<TermVariable> bodyVars, 
			final HornClausePredicateSymbol body, final Map<HornClausePredicateSymbol, List<TermVariable>> cobodyPredToTermVariables) {

		TermTransferrer ttf = new TermTransferrer(script.getScript());
		
		mHeadPredTermVariables = bodyVars.stream().map(var -> (TermVariable) ttf.transform(var)).collect(Collectors.toList());
		mHeadPredicate = body;
		mBodyPredToTermVariables = cobodyPredToTermVariables.entrySet().stream().collect(
				Collectors.toMap(
						en -> en.getKey(), 
						en -> en.getValue().stream()
							.map(tv -> (TermVariable) ttf.transform(tv))
							.collect(Collectors.toList())));
		
		final Term convertedFormula = ttf.transform(transitionFormula);

		final Map<IProgramVar, TermVariable> outVars = new HashMap<>();
		for (int i = 0; i < mHeadPredTermVariables.size(); ++i) {
			outVars.put(body.getHCVars().get(i), mHeadPredTermVariables.get(i));
		}
	
		final Map<IProgramVar, TermVariable> inVars = new HashMap<>();
		for (final Entry<HornClausePredicateSymbol, List<TermVariable>> en : mBodyPredToTermVariables.entrySet()) {
			final List<TermVariable> vars = en.getValue();
	
			for (int i = 0; i < vars.size(); ++i) {
				inVars.put(en.getKey().getHCVars().get(i), vars.get(i));
			}
	
		}

		final TransFormulaBuilder tb = new TransFormulaBuilder(inVars, outVars, true, null, true, null, true);
		tb.setFormula(convertedFormula);
		tb.setInfeasibility(Infeasibility.NOT_DETERMINED);
		mTransitionFormula = tb.finishConstruction(script);
	}

	@Override
	public UnmodifiableTransFormula getTransformula() {
		return mTransitionFormula;
	}
	
	public HornClausePredicateSymbol getHeadPredicate() {
		return mHeadPredicate;
	}
	
	public Set<HornClausePredicateSymbol> getTailPredicates() {
		return mBodyPredToTermVariables.keySet();
	}
	
	@Override
	public String toString() {
		String cobody = "";

		for (final HornClausePredicateSymbol symbol : mBodyPredToTermVariables.keySet()) {
			cobody += " " + symbol.getName() + mBodyPredToTermVariables.get(symbol);
		}
		if (cobody.length() > 0) {
			cobody = "and" + cobody;
		} else {
			cobody = "true";
		}

		final String body = mHeadPredicate.getName() + mHeadPredTermVariables;

		return mTransitionFormula.getFormula().toString();
		//return String.format("(%s) ^^ (%s) ~~> (%s) || in : %s || out : %s ", cobody, mTransitionFormula, body,
		//return String.format("(%s) ^^ (%s) ~~> (%s)", cobody, mTransitionFormula.getFormula(), body);
	}

	/**
	 * This method is added only for fulfilling the IInternalAction interface.
	 */
	@Override
	public String getPrecedingProcedure() {
		return HornUtilConstants.HORNCLAUSEMETHODNAME;
//		return null;
	}

	/**
	 * This method is added only for fulfilling the IInternalAction interface.
	 */
	@Override
	public String getSucceedingProcedure() {
		return HornUtilConstants.HORNCLAUSEMETHODNAME;
//		return null;
	}


}
