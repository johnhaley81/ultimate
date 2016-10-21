/*
 * Copyright (C) 2013-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2012-2015 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.boogie.BoogieUtils;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Annotation;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.ConstantTerm;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.LoggingScript;
import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.SMTLIBException;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Util;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.ModelCheckerUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.arrays.ArrayIndex;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.bdd.SimplifyBdd;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.linearTerms.AffineRelation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.linearTerms.AffineTerm;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.linearTerms.AffineTermTransformer;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.linearTerms.NotAffineException;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.normalForms.Cnf;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.normalForms.Dnf;
import de.uni_freiburg.informatik.ultimate.util.DebugMessage;

public final class SmtUtils {

	public enum XnfConversionTechnique {
		BDD_BASED, BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION
	};

	public enum SimplificationTechnique {
		SIMPLIFY_BDD_PROP, SIMPLIFY_BDD_FIRST_ORDER, SIMPLIFY_QUICK, SIMPLIFY_DDA
	};

	private SmtUtils() {
		// Prevent instantiation of this utility class
	}

	/**
	 * Avoid the construction of "bvadd" with more than two arguments and use nested "bvadd" terms instead.
	 */
	private static final boolean BINARY_BITVECTOR_SUM_WORKAROUND = false;

	public static Term simplify(final ManagedScript script, final Term formula, final IUltimateServiceProvider services,
			final SimplificationTechnique simplificationTechnique) {
		final ILogger logger = services.getLoggingService().getLogger(ModelCheckerUtils.PLUGIN_ID);
		if (logger.isDebugEnabled()) {
			logger.debug(new DebugMessage("simplifying formula of DAG size {0}", new DagSizePrinter(formula)));
		}
		final Term simplified;
		switch (simplificationTechnique) {
		case SIMPLIFY_BDD_PROP:
			simplified = (new SimplifyBdd(services, script)).transform(formula);
			break;
		case SIMPLIFY_BDD_FIRST_ORDER:
			simplified = (new SimplifyBdd(services, script)).transformWithImplications(formula);
			break;
		case SIMPLIFY_DDA:
			simplified = (new SimplifyDDAWithTimeout(script.getScript(), services)).getSimplifiedTerm(formula);
			break;
		case SIMPLIFY_QUICK:
			simplified = (new SimplifyQuick(script.getScript(), services)).getSimplifiedTerm(formula);
			break;
		default:
			throw new AssertionError("unknown enum constant");
		}
		if (logger.isDebugEnabled()) {
			logger.debug(new DebugMessage("DAG size before simplification {0}, DAG size after simplification {1}",
					new DagSizePrinter(formula), new DagSizePrinter(simplified)));
		}
		return simplified;
	}

	public static LBool checkSatTerm(final Script script, final Term formula) {
		return Util.checkSat(script, formula);
	}

	/**
	 * If term is a conjunction return all conjuncts, otherwise return term.
	 */
	public static Term[] getConjuncts(final Term term) {
		if (term instanceof ApplicationTerm) {
			final ApplicationTerm appTerm = (ApplicationTerm) term;
			if (appTerm.getFunction().getName().equals("and")) {
				return appTerm.getParameters();
			}
		}
		final Term[] result = new Term[1];
		result[0] = term;
		return result;
	}

	/**
	 * If term is a disjunction return all disjuncts, otherwise return term.
	 */
	public static Term[] getDisjuncts(final Term term) {
		if (term instanceof ApplicationTerm) {
			final ApplicationTerm appTerm = (ApplicationTerm) term;
			if (appTerm.getFunction().getName().equals("or")) {
				return appTerm.getParameters();
			}
		}
		final Term[] result = new Term[1];
		result[0] = term;
		return result;
	}

	/**
	 * Takes an ApplicationTerm with pairwise function symbol (e.g. distinct) or chainable function symbol (e.g.
	 * equality) and return a conjunction of pairwise applications of the function symbol. E.g. the ternary equality (=
	 * a b c) becomes (and (= a b) (= a c) (= b c)).
	 */
	public static Term binarize(final Script script, final ApplicationTerm term) {
		final FunctionSymbol functionSymbol = term.getFunction();
		if (!functionSymbol.isPairwise() && !functionSymbol.isChainable()) {
			throw new IllegalArgumentException("can only binarize pairwise terms");
		}
		final String functionName = functionSymbol.getApplicationString();
		final Term[] params = term.getParameters();
		assert params.length > 1;
		final List<Term> conjuncts = new ArrayList<Term>();
		for (int i = 0; i < params.length; i++) {
			for (int j = i + 1; j < params.length; j++) {
				conjuncts.add(script.term(functionName, params[i], params[j]));
			}
		}
		return Util.and(script, conjuncts.toArray(new Term[conjuncts.size()]));
	}

	public static boolean firstParamIsBool(final ApplicationTerm term) {
		final Term[] params = term.getParameters();
		final boolean result = params[0].getSort().getName().equals("Bool");
		return result;
	}

	public static boolean allParamsAreBool(final ApplicationTerm term) {
		for (final Term param : term.getParameters()) {
			if (!param.getSort().getName().equals("Bool")) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Given Term lhs and Term rhs of Sort "Bool". Returns a Term that is equivalent to (= lhs rhs) but uses only the
	 * boolean connectives "and" and "or".
	 */
	public static Term binaryBooleanEquality(final Script script, final Term lhs, final Term rhs) {
		assert lhs.getSort().getName().equals("Bool");
		assert rhs.getSort().getName().equals("Bool");
		final Term bothTrue = Util.and(script, lhs, rhs);
		final Term bothFalse = Util.and(script, SmtUtils.not(script, lhs), SmtUtils.not(script, rhs));
		return Util.or(script, bothTrue, bothFalse);
	}

	/**
	 * Given Term lhs and Term rhs of Sort "Bool". Returns a Term that is equivalent to (not (= lhs rhs)) but uses only
	 * the boolean connectives "and" and "or".
	 */
	public static Term binaryBooleanNotEquals(final Script script, final Term lhs, final Term rhs) {
		assert lhs.getSort().getName().equals("Bool");
		assert rhs.getSort().getName().equals("Bool");
		final Term oneIsTrue = Util.or(script, lhs, rhs);
		final Term oneIsFalse = Util.or(script, SmtUtils.not(script, lhs), SmtUtils.not(script, rhs));
		return Util.and(script, oneIsTrue, oneIsFalse);
	}

	/**
	 * Given a list of Terms term1, ... ,termn returns a new list that contains (not term1), ... ,(not termn) in this
	 * order.
	 */
	public static List<Term> negateElementwise(final Script script, final List<Term> terms) {
		final List<Term> result = new ArrayList<>(terms.size());
		for (final Term term : terms) {
			result.add(SmtUtils.not(script, term));
		}
		return result;
	}

	/**
	 * Returns the term that selects the element at index from (possibly) multi dimensional array a. E.g. If the array
	 * has Sort (Int -> Int -> Int) and index is [23, 42], this method returns the term ("select" ("select" a 23) 42).
	 */
	public static Term multiDimensionalSelect(final Script script, final Term a, final ArrayIndex index) {
		assert a.getSort().isArraySort();
		Term result = a;
		for (int i = 0; i < index.size(); i++) {
			result = script.term("select", result, index.get(i));
		}
		return result;
	}

	/**
	 * Returns the term that stores the element at index from (possibly) multi dimensional array a. E.g. If the array
	 * has Sort (Int -> Int -> Int) and we store the value val at index [23, 42], this method returns the term (store a
	 * 23 (store (select a 23) 42 val)).
	 */
	public static Term multiDimensionalStore(final Script script, final Term a, final ArrayIndex index,
			final Term value) {
		assert index.size() > 0;
		assert a.getSort().isArraySort();
		Term result = value;
		for (int i = index.size() - 1; i >= 0; i--) {
			final Term selectUpToI = multiDimensionalSelect(script, a, index.getFirst(i));
			result = script.term("store", selectUpToI, index.get(i), result);
		}
		return result;
	}

	/**
	 * Returns true iff each key and each value is non-null.
	 */
	public static <K, V> boolean neitherKeyNorValueIsNull(final Map<K, V> map) {
		for (final Entry<K, V> entry : map.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Given the array of terms [lhs_1, ..., lhs_n] and the array of terms [rhs_1, ..., rhs_n], return the conjunction
	 * of the following equalities lhs_1 = rhs_1, ... , lhs_n = rhs_n.
	 */
	public static Term pairwiseEquality(final Script script, final List<Term> lhs, final List<Term> rhs) {
		if (lhs.size() != rhs.size()) {
			throw new IllegalArgumentException("must have same length");
		}
		final Term[] equalities = new Term[lhs.size()];
		for (int i = 0; i < lhs.size(); i++) {
			equalities[i] = binaryEquality(script, lhs.get(i), rhs.get(i));
		}
		return Util.and(script, equalities);
	}

	/**
	 * Construct the following term. (index1 == index2) ==> (value1 == value2)
	 */
	public static Term indexEqualityImpliesValueEquality(final Script script, final ArrayIndex index1,
			final ArrayIndex index2, final Term value1, final Term value2) {
		assert index1.size() == index2.size();
		final Term lhs = pairwiseEquality(script, index1, index2);
		final Term rhs = binaryEquality(script, value1, value2);
		return Util.or(script, not(script, lhs), rhs);
	}

	/**
	 * Return term that represents the sum of all summands. Return the neutral element for sort sort if summands is
	 * empty.
	 */
	public static Term sum(final Script script, final Sort sort, final Term... summands) {
		assert sort.isNumericSort() || BitvectorUtils.isBitvectorSort(sort);
		if (summands.length == 0) {
			if (sort.toString().equals("Int")) {
				return script.numeral(BigInteger.ZERO);
			} else if (sort.toString().equals("Real")) {
				return script.decimal(BigDecimal.ZERO);
			} else if (BitvectorUtils.isBitvectorSort(sort)) {
				return BitvectorUtils.constructTerm(script, BigInteger.ZERO, sort);
			} else {
				throw new UnsupportedOperationException("unkown sort " + sort);
			}
		} else if (summands.length == 1) {
			return summands[0];
		} else {
			if (sort.isNumericSort()) {
				return script.term("+", summands);
			} else if (BitvectorUtils.isBitvectorSort(sort)) {
				if (BINARY_BITVECTOR_SUM_WORKAROUND) {
					return binaryBitvectorSum(script, sort, summands);
				} else {
					return script.term("bvadd", summands);
				}
			} else {
				throw new UnsupportedOperationException("unkown sort " + sort);
			}
		}
	}

	/**
	 * Construct nested binary "bvadd" terms.
	 *
	 * @param sort
	 *            bitvector sort of the arguments (required if summands is empty)
	 * @param summands
	 *            bitvector terms that each have the same sort
	 */
	public static Term binaryBitvectorSum(final Script script, final Sort sort, final Term... summands) {
		if (summands.length == 0) {
			return BitvectorUtils.constructTerm(script, BigInteger.ZERO, sort);
		} else if (summands.length == 0) {
			return summands[0];
		} else {
			Term result = script.term("bvadd", summands[0], summands[1]);
			for (int i = 2; i < summands.length; i++) {
				result = script.term("bvadd", result, summands[i]);
			}
			return result;
		}
	}

	/**
	 * Return term that represents the product of all factors. Return the neutral element for sort sort if factors is
	 * empty.
	 */
	public static Term mul(final Script script, final Sort sort, final Term... factors) {
		assert sort.isNumericSort() || BitvectorUtils.isBitvectorSort(sort);
		if (factors.length == 0) {
			if (sort.toString().equals("Int")) {
				return script.numeral(BigInteger.ONE);
			} else if (sort.toString().equals("Real")) {
				return script.decimal(BigDecimal.ONE);
			} else if (BitvectorUtils.isBitvectorSort(sort)) {
				return BitvectorUtils.constructTerm(script, BigInteger.ONE, sort);
			} else {
				throw new UnsupportedOperationException("unkown sort " + sort);
			}
		} else if (factors.length == 1) {
			return factors[0];
		} else {
			if (sort.isNumericSort()) {
				return script.term("*", factors);
			} else if (BitvectorUtils.isBitvectorSort(sort)) {
				return script.term("bvmul", factors);
			} else {
				throw new UnsupportedOperationException("unkown sort " + sort);
			}
		}
	}

	/**
	 * Return sum, in affine representation if possible.
	 *
	 * @param funcname
	 *            either "+" or "bvadd".
	 */
	public static Term sum(final Script script, final String funcname, final Term... summands) {
		assert funcname.equals("+") || funcname.equals("bvadd");
		final Term sum = script.term(funcname, summands);
		final AffineTerm affine = (AffineTerm) (new AffineTermTransformer(script)).transform(sum);
		if (affine.isErrorTerm()) {
			return sum;
		} else {
			return affine.toTerm(script);
		}
	}

	/**
	 * Return term that represents negation (unary minus).
	 */
	public static Term neg(final Script script, final Sort sort, final Term operand) {
		assert sort.isNumericSort() || BitvectorUtils.isBitvectorSort(sort);
		if (sort.isNumericSort()) {
			return script.term("-", operand);
		} else if (BitvectorUtils.isBitvectorSort(sort)) {
			return script.term("bvneg", operand);
		} else {
			throw new UnsupportedOperationException("unkown sort " + sort);
		}
	}

	/**
	 * Return term that represents negation of boolean term.
	 */
	public static Term not(final Script script, final Term term) {
		if (term instanceof ApplicationTerm) {
			final ApplicationTerm appTerm = (ApplicationTerm) term;
			if (appTerm.getFunction().getName().equals("distinct") && appTerm.getParameters().length == 2) {
				return SmtUtils.binaryEquality(script, appTerm.getParameters()[0], appTerm.getParameters()[1]);
			} else {
				return Util.not(script, term);
			}
		} else {
			return Util.not(script, term);
		}
	}

	/**
	 * Returns the equality ("=" lhs rhs), or true resp. false if some simple checks detect validity or unsatisfiablity
	 * of the equality.
	 */
	public static Term binaryEquality(final Script script, final Term lhs, final Term rhs) {
		if (lhs == rhs) {
			return script.term("true");
		} else if (twoConstantTermsWithDifferentValue(lhs, rhs)) {
			return script.term("false");
		} else if (lhs.getSort().getName().equals("Bool")) {
			return booleanEquality(script, lhs, rhs);
		} else {
			return script.term("=", lhs, rhs);
		}
	}

	/**
	 * Returns the equality ("=" lhs rhs), but checks if one of the arguments is true/false and simplifies accordingly.
	 */
	private static Term booleanEquality(final Script script, final Term lhs, final Term rhs) {
		final Term trueTerm = script.term("true");
		final Term falseTerm = script.term("false");
		if (lhs.equals(trueTerm)) {
			return rhs;
		} else if (lhs.equals(falseTerm)) {
			return SmtUtils.not(script, rhs);
		} else if (rhs.equals(trueTerm)) {
			return lhs;
		} else if (rhs.equals(falseTerm)) {
			return SmtUtils.not(script, lhs);
		} else {
			return script.term("=", lhs, rhs);
		}
	}

	/**
	 * Returns true iff. fst and snd are different literals of the same numeric sort ("Int" or "Real").
	 *
	 * @exception Throws
	 *                UnsupportedOperationException if both arguments do not have the same Sort.
	 */
	private static boolean twoConstantTermsWithDifferentValue(final Term fst, final Term snd) {
		if (!fst.getSort().equals(snd.getSort())) {
			throw new UnsupportedOperationException("arguments sort different");
		}
		final BitvectorConstant fstbw = BitvectorUtils.constructBitvectorConstant(fst);
		if (fstbw != null) {
			final BitvectorConstant sndbw = BitvectorUtils.constructBitvectorConstant(snd);
			if (sndbw != null) {
				return !fstbw.equals(sndbw);
			}
		}
		if (!(fst instanceof ConstantTerm)) {
			return false;
		}
		if (!(snd instanceof ConstantTerm)) {
			return false;
		}
		if (!fst.getSort().isNumericSort()) {
			return false;
		}
		final ConstantTerm fstConst = (ConstantTerm) fst;
		final ConstantTerm sndConst = (ConstantTerm) snd;
		Object fstValue = fstConst.getValue();
		Object sndValue = sndConst.getValue();
		if (fstValue instanceof BigInteger && sndValue instanceof Rational) {
			fstValue = Rational.valueOf((BigInteger) fstValue, BigInteger.ONE);
		} else if (fstValue instanceof Rational && sndValue instanceof BigInteger) {
			sndValue = Rational.valueOf((BigInteger) sndValue, BigInteger.ONE);
		}
		if (fstValue.getClass() != sndValue.getClass()) {
			throw new UnsupportedOperationException("First value is " + fstValue.getClass().getSimpleName()
					+ " second value is " + sndValue.getClass().getSimpleName());
		}
		return !fstValue.equals(sndValue);
	}

	public static List<Term> substitutionElementwise(final List<Term> subtituents, final Substitution subst) {
		final List<Term> result = new ArrayList<Term>();
		for (int i = 0; i < subtituents.size(); i++) {
			result.add(subst.transform(subtituents.get(i)));
		}
		return result;
	}

	/**
	 * Removes vertical bars from a String. In SMT-LIB identifiers can be quoted using | (vertical bar) and vertical
	 * bars must not be nested.
	 */
	public static String removeSmtQuoteCharacters(final String string) {
		final String result = string.replaceAll("\\|", "");
		return result;
	}

	public static Map<Term, Term> termVariables2Constants(final Script script,
			final Collection<TermVariable> termVariables, final boolean declareConstants) {
		final Map<Term, Term> mapping = new HashMap<Term, Term>();
		for (final TermVariable tv : termVariables) {
			final Term constant = termVariable2constant(script, tv, declareConstants);
			mapping.put(tv, constant);
		}
		return mapping;
	}

	public static Term termVariable2constant(final Script script, final TermVariable tv,
			final boolean declareConstant) {
		final String name = removeSmtQuoteCharacters(tv.getName());
		if (declareConstant) {
			final Sort resultSort = tv.getSort();
			script.declareFun(name, new Sort[0], resultSort);
		}
		return script.term(name);
	}

	/**
	 * Returns true, iff the term contains an application of the given functionName
	 */
	public static boolean containsFunctionApplication(final Term term, final String functionName) {
		return containsFunctionApplication(term, Arrays.asList(functionName));
	}

	/**
	 * Returns true, iff the term contains an application of at least one of the the given functionNames
	 */
	public static boolean containsFunctionApplication(final Term term, final Iterable<String> functionNames) {
		for (final String f : functionNames) {
			if (!new ApplicationTermFinder(f, true).findMatchingSubterms(term).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	public static boolean containsArrayVariables(final Term... terms) {
		for (final Term term : terms) {
			for (final TermVariable tv : term.getFreeVars()) {
				if (tv.getSort().isArraySort()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns true, iff the term is array-free. This is the case, if no array variables, no select- and no
	 * store-expressions are found in it.
	 */
	public static boolean isArrayFree(final Term term) {
		return !containsArrayVariables(term) && !containsFunctionApplication(term, Arrays.asList("select", "store"));
	}

	/**
	 * Returns true, iff the term contains an UF-application
	 */
	public static boolean containsUninterpretedFunctioApplication(final Term term) {
		for (final NonTheorySymbol<?> s : new NonTheorySymbolFinder().findNonTheorySymbols(term)) {
			if (s instanceof NonTheorySymbol.Function) {
				return true;
			}
		}
		return false;
	}

	public static boolean isFalse(final Term term) {
		if (term instanceof ApplicationTerm) {
			final ApplicationTerm appTerm = (ApplicationTerm) term;
			final FunctionSymbol fun = appTerm.getFunction();
			return fun.getApplicationString().equals("false");
		} else {
			return false;
		}
	}

	public static boolean isTrue(final Term term) {
		if (term instanceof ApplicationTerm) {
			final ApplicationTerm appTerm = (ApplicationTerm) term;
			final FunctionSymbol fun = appTerm.getFunction();
			return fun.getApplicationString().equals("true");
		} else {
			return false;
		}
	}

	/**
	 * A constant is an ApplicationTerm with zero parameters whose function symbol is not intern.
	 */
	public static boolean isConstant(final Term term) {
		if (term instanceof ApplicationTerm) {
			final ApplicationTerm appTerm = (ApplicationTerm) term;
			return appTerm.getParameters().length == 0 && !appTerm.getFunction().isIntern();
		} else {
			return false;
		}
	}

	/**
	 * Returns true iff the given term is an atomic formula, which means it does not contain any logical symbols (and,
	 * or, not, quantifiers)
	 */
	public static boolean isAtomicFormula(final Term term) {
		if (isTrue(term) || isFalse(term) || isConstant(term)) {
			return true;
		}
		if (term instanceof ApplicationTerm) {
			return !allParamsAreBool((ApplicationTerm) term);
		}
		return term instanceof TermVariable;
	}

	/**
	 * Return all free TermVariables that occur in a set of Terms.
	 */
	public static Set<TermVariable> getFreeVars(final Collection<Term> terms) {
		final Set<TermVariable> freeVars = new HashSet<TermVariable>();
		for (final Term term : terms) {
			freeVars.addAll(Arrays.asList(term.getFreeVars()));
		}
		return freeVars;
	}

	public static Term and(final Script script, final Collection<Term> terms) {
		return Util.and(script, terms.toArray(new Term[terms.size()]));
	}

	public static Term or(final Script script, final Collection<Term> terms) {
		return Util.or(script, terms.toArray(new Term[terms.size()]));
	}

	/**
	 * @return term that is equivalent to lhs <= rhs
	 */
	public static Term leq(final Script script, final Term lhs, final Term rhs) {
		return comparison(script, "<=", lhs, rhs);
	}

	/**
	 * @return term that is equivalent to lhs >= rhs
	 */
	public static Term geq(final Script script, final Term lhs, final Term rhs) {
		return comparison(script, ">=", lhs, rhs);
	}

	/**
	 * @return term that is equivalent to lhs < rhs
	 */
	public static Term less(final Script script, final Term lhs, final Term rhs) {
		return comparison(script, "<", lhs, rhs);
	}

	/**
	 * @return term that is equivalent to lhs > rhs
	 */
	public static Term greater(final Script script, final Term lhs, final Term rhs) {
		return comparison(script, ">", lhs, rhs);
	}

	/**
	 * @return term that is equivalent to lhs X rhs where X is either leq, less, geq, or greater.
	 */
	private static Term comparison(final Script script, final String functionSymbol, final Term lhs, final Term rhs) {
		final Term rawTerm = script.term(functionSymbol, lhs, rhs);
		try {
			final AffineRelation ar = new AffineRelation(script, rawTerm);
			return ar.positiveNormalForm(script);
		} catch (final NotAffineException e) {
			return rawTerm;
		}
	}

	/**
	 * Declare and return a new constant. A constant is a 0-ary application term.
	 *
	 * @param name
	 *            name of the resulting constant
	 * @param sort
	 *            the sort of the resulting constant
	 * @return resulting constant as a ApplicationTerm
	 * @throws SMTLIBException
	 *             if declaration of constant fails, e.g. the name is already defined
	 */
	public static ApplicationTerm buildNewConstant(final Script script, final String name, final String sortname)
			throws SMTLIBException {
		script.declareFun(name, new Sort[0], script.sort(sortname));
		return (ApplicationTerm) script.term(name);
	}

	/**
	 * Convert a BigDecimal into a Rational. Stolen from Jochen's code
	 * de.uni_freiburg.informatik.ultimate.smtinterpol.convert.ConvertFormula.
	 */
	private static Rational decimalToRational(final BigDecimal d) {
		Rational rat;
		if (d.scale() <= 0) {
			final BigInteger num = d.toBigInteger();
			rat = Rational.valueOf(num, BigInteger.ONE);
		} else {
			final BigInteger num = d.unscaledValue();
			final BigInteger denom = BigInteger.TEN.pow(d.scale());
			rat = Rational.valueOf(num, denom);
		}
		return rat;
	}

	/**
	 * Convert a constant term to Rational.
	 *
	 * @param ct
	 *            constant term that represents a Rational
	 * @return Rational from the value of ct
	 * @throws IllegalArgumentException
	 *             if ct does not represent a Rational.
	 */
	public static Rational convertCT(final ConstantTerm ct) throws IllegalArgumentException {
		if (ct.getSort().getName().equals("Real")) {
			if (ct.getValue() instanceof Rational) {
				return (Rational) ct.getValue();
			} else if (ct.getValue() instanceof BigDecimal) {
				return decimalToRational((BigDecimal) ct.getValue());
			} else {
				throw new UnsupportedOperationException("ConstantTerm's value has to be either Rational or BigDecimal");
			}
		} else if (ct.getSort().getName().equals("Int")) {
			if (ct.getValue() instanceof Rational) {
				return (Rational) ct.getValue();
			} else {
				final Rational r = Rational.valueOf((BigInteger) ct.getValue(), BigInteger.ONE);
				return r;
			}
		} else {
			throw new IllegalArgumentException("Trying to convert a ConstantTerm of unknown sort." + ct);
		}
	}

	/**
	 * Construct term but simplify it using lightweight simplification techniques if applicable.
	 */
	public static Term termWithLocalSimplification(final Script script, final String funcname,
			final BigInteger[] indices, final Term... params) {
		final Term result;
		switch (funcname) {
		case "and":
			result = Util.and(script, params);
			break;
		case "or":
			result = Util.or(script, params);
			break;
		case "not":
			if (params.length != 1) {
				throw new IllegalArgumentException("no not term");
			} else {
				result = SmtUtils.not(script, params[0]);
			}
			break;
		case "=":
			if (params.length != 2) {
				throw new UnsupportedOperationException("not yet implemented");
			} else {
				result = binaryEquality(script, params[0], params[1]);
			}
			break;
		case "distinct":
			if (params.length != 2) {
				throw new UnsupportedOperationException("not yet implemented");
			} else {
				result = SmtUtils.not(script, binaryEquality(script, params[0], params[1]));
			}
			break;
		case "=>":
			result = Util.implies(script, params);
			break;
		case "ite":
			if (params.length != 3) {
				throw new IllegalArgumentException("no ite");
			} else {
				result = Util.ite(script, params[0], params[1], params[2]);
			}
			break;
		case "+":
		case "bvadd": {
			result = SmtUtils.sum(script, funcname, params);
		}
			break;
		case "div":
			if (params.length != 2) {
				throw new IllegalArgumentException("no div");
			} else {
				result = div(script, params[0], params[1]);
			}
			break;
		case "mod":
			if (params.length != 2) {
				throw new IllegalArgumentException("no mod");
			} else {
				result = mod(script, params[0], params[1]);
			}
			break;
		case ">=":
		case "<=":
		case ">":
		case "<": {
			if (params.length != 2) {
				throw new IllegalArgumentException("no comparison");
			} else {
				result = comparison(script, funcname, params[0], params[1]);
			}
			break;
		}
		case "zero_extend":
		case "extract":
		case "bvsub":
		case "bvmul":
		case "bvudiv":
		case "bvurem":
		case "bvsdiv":
		case "bvsrem":
		case "bvand":
		case "bvor":
		case "bvxor":
		case "bvnot":
		case "bvneg":
		case "bvshl":
		case "bvlshr":
		case "bvashr":
		case "bvult":
		case "bvule":
		case "bvugt":
		case "bvuge":
		case "bvslt":
		case "bvsle":
		case "bvsgt":
		case "bvsge":
			result = BitvectorUtils.termWithLocalSimplification(script, funcname, indices, params);
			break;
		default:
			// if (BitvectorUtils.allTermsAreBitvectorConstants(params)) {
			// throw new AssertionError("wasted optimization " + funcname);
			// }
			result = script.term(funcname, indices, null, params);
			break;
		}
		return result;
	}

	/**
	 * Returns a possibly simplified version of the Term (div dividend divisor). If dividend and divisor are both
	 * literals the returned Term is a literal which is equivalent to the result of the operation
	 */
	public static Term div(final Script script, final Term dividend, final Term divisor) {
		if ((dividend instanceof ConstantTerm) && dividend.getSort().isNumericSort()
				&& (divisor instanceof ConstantTerm) && divisor.getSort().isNumericSort()) {
			final Rational dividentAsRational = convertConstantTermToRational((ConstantTerm) dividend);
			final Rational divisorAsRational = convertConstantTermToRational((ConstantTerm) divisor);
			final Rational quotientAsRational = dividentAsRational.div(divisorAsRational);
			Rational result;
			if (divisorAsRational.isNegative()) {
				result = quotientAsRational.ceil();
			} else {
				result = quotientAsRational.floor();
			}
			return result.toTerm(dividend.getSort());
		} else {
			return script.term("div", dividend, divisor);
		}
	}

	/**
	 * Returns a possibly simplified version of the Term (mod dividend divisor). If dividend and divisor are both
	 * literals the returned Term is a literal which is equivalent to the result of the operation. If only the divisor
	 * is a literal we apply modulo to all coefficients of the dividend (helpful simplification in case where
	 * coefficient becomes zero).
	 */
	public static Term mod(final Script script, final Term divident, final Term divisor) {
		final AffineTerm affineDivident = (AffineTerm) (new AffineTermTransformer(script)).transform(divident);
		final AffineTerm affineDivisor = (AffineTerm) (new AffineTermTransformer(script)).transform(divisor);
		if (affineDivident.isErrorTerm() || affineDivisor.isErrorTerm()) {
			return script.term("mod", divident, divisor);
		}
		if (affineDivisor.isZero()) {
			// pass the problem how to deal with division by zero to the
			// subsequent analysis
			return script.term("mod", divident, divisor);
		}
		if (affineDivisor.isConstant()) {
			final BigInteger bigIntDivisor = toInt(affineDivisor.getConstant());
			if (affineDivident.isConstant()) {
				final BigInteger bigIntDivident = toInt(affineDivident.getConstant());
				final BigInteger modulus = BoogieUtils.euclideanMod(bigIntDivident, bigIntDivisor);
				return script.numeral(modulus);
			} else {
				final AffineTerm moduloApplied = AffineTerm.applyModuloToAllCoefficients(script, affineDivident,
						bigIntDivisor);
				return script.term("mod", moduloApplied.toTerm(script), affineDivisor.toTerm(script));
			}
		} else {
			return script.term("mod", affineDivident.toTerm(script), affineDivisor.toTerm(script));
		}
	}

	public static BigInteger toInt(final Rational integralRational) {
		if (!integralRational.isIntegral()) {
			throw new IllegalArgumentException("divident has to be integral");
		}
		if (!integralRational.denominator().equals(BigInteger.ONE)) {
			throw new IllegalArgumentException("denominator has to be zero");
		}
		return integralRational.numerator();
	}

	public static Rational toRational(final BigInteger bigInt) {
		return Rational.valueOf(bigInt, BigInteger.ONE);
	}

	public static Term rational2Term(final Script script, final Rational rational, final Sort sort) {
		if (sort.isNumericSort()) {
			return rational.toTerm(sort);
		} else if (BitvectorUtils.isBitvectorSort(sort)) {
			if (rational.isIntegral() && rational.isRational()) {
				return BitvectorUtils.constructTerm(script, rational.numerator(), sort);
			} else {
				throw new IllegalArgumentException("unable to convert rational to bitvector if not integer");
			}
		} else {
			throw new AssertionError("unknown sort " + sort);
		}
	}

	/**
	 * Check if {@link Term} which may contain free {@link TermVariable}s is satisfiable with respect to the current
	 * assertion stack of {@link Script}. Compute unsat core if unsatisfiable. Use {@link LoggingScript} to see the
	 * input. TODO: Show values of satisfying assignment (including array access) if satisfiable.
	 *
	 * @param term
	 *            may contain free variables
	 */
	public static LBool checkSat_DebuggingVersion(final Script script, final Term term) {
		script.push(1);
		try {
			final TermVariable[] vars = term.getFreeVars();
			final Map<Term, Term> substitutionMapping = new HashMap<>();
			for (final TermVariable var : vars) {
				final Term substituent = termVariable2PseudofreshConstant(script, var);
				substitutionMapping.put(var, substituent);
			}
			final Map<Term, Term> ucMapping = new HashMap<>();
			final Term[] conjuncts = getConjuncts(term);
			for (int i = 0; i < conjuncts.length; i++) {
				final Term conjunct = (new Substitution(script, substitutionMapping)).transform(conjuncts[i]);
				final String name = "conjunct" + i;
				final Annotation annot = new Annotation(":named", name);
				final Term annotTerm = script.annotate(conjunct, annot);
				ucMapping.put(script.term(name), conjuncts[i]);
				script.assertTerm(annotTerm);
			}
			final LBool result = script.checkSat();
			if (result == LBool.UNSAT) {
				final Term[] ucTerms = script.getUnsatCore();
				for (final Term ucTerm : ucTerms) {
					final Term conjunct = ucMapping.get(ucTerm);
					System.out.println("in uc: " + conjunct);
				}
			}
			script.pop(1);
			return result;
		} catch (final Exception e) {
			// unable to recover because assertion stack is modified
			// doing the script.pop(1) in finally block does not make sense
			// since the solver might not be able to respond this will raise
			// another Exception, and we will not see Exception e any more.
			throw new AssertionError("Exception during satisfiablity check: " + e.getMessage());
		}
	}

	private static Term termVariable2PseudofreshConstant(final Script script, final TermVariable tv) {
		final String name = tv.getName() + "_const_" + tv.hashCode();
		final Sort resultSort = tv.getSort();
		script.declareFun(name, new Sort[0], resultSort);
		return script.term(name);
	}

	/**
	 * Convert a {@link ConstantTerm} which has numeric {@link Sort} into a {@literal Rational}.
	 *
	 * @return a Rational which represents the input constTerm
	 * @throws UnsupportedOperationException
	 *             if ConstantTerm cannot converted to Rational
	 */
	public static Rational convertConstantTermToRational(final ConstantTerm constTerm) {
		Rational rational;
		assert constTerm.getSort().isNumericSort();
		final Object value = constTerm.getValue();
		if (constTerm.getSort().getName().equals("Int")) {
			if (value instanceof BigInteger) {
				rational = Rational.valueOf((BigInteger) value, BigInteger.ONE);
			} else if (value instanceof Rational) {
				rational = (Rational) value;
			} else {
				throw new UnsupportedOperationException();
			}
		} else if (constTerm.getSort().getName().equals("Real")) {
			if (value instanceof BigDecimal) {
				rational = decimalToRational((BigDecimal) value);
			} else if (value instanceof Rational) {
				rational = (Rational) value;
			} else {
				throw new UnsupportedOperationException();
			}
		} else {
			throw new UnsupportedOperationException();
		}
		return rational;
	}

	/**
	 * @return true iff tv does not occur in appTerm, or appTerm has two parameters, tv is the left parameter and tv
	 *         does not occur in the right prarameter.
	 */
	public static boolean occursAtMostAsLhs(final TermVariable tv, final ApplicationTerm appTerm) {
		if (appTerm.getParameters().length != 2) {
			return !Arrays.asList(appTerm.getFreeVars()).contains(tv);
		} else {
			if (Arrays.asList(appTerm.getParameters()[1].getFreeVars()).contains(tv)) {
				// occurs on rhs
				return false;
			} else {
				if (appTerm.getParameters()[0].equals(tv)) {
					return true;
				} else {
					return !Arrays.asList(appTerm.getParameters()[0].getFreeVars()).contains(tv);
				}
			}
		}
	}

	/**
	 * Returns quantified formula. Drops quantifiers for variables that do not occur in formula. If subformula is
	 * quantified formula with same quantifier both are merged.
	 */
	public static Term quantifier(final Script script, final int quantifier, final Collection<TermVariable> vars,
			final Term body) {
		if (vars.isEmpty()) {
			return body;
		}
		final Collection<TermVariable> resultVars = filterToVarsThatOccurFreelyInTerm(vars, body);
		if (resultVars.isEmpty()) {
			return body;
		} else {
			final QuantifiedFormula innerQuantifiedFormula = isQuantifiedFormulaWithSameQuantifier(quantifier, body);
			if (innerQuantifiedFormula == null) {
				return script.quantifier(quantifier, resultVars.toArray(new TermVariable[resultVars.size()]), body);
			} else {
				final Set<TermVariable> resultQuantifiedVars = new HashSet<>(
						Arrays.asList(innerQuantifiedFormula.getVariables()));
				resultQuantifiedVars.addAll(vars);
				return script.quantifier(quantifier,
						resultQuantifiedVars.toArray(new TermVariable[resultQuantifiedVars.size()]),
						innerQuantifiedFormula.getSubformula());
			}
		}
	}

	/**
	 * Returns a new HashSet that contains all variables that are contained in vars and occur freely in term.
	 */
	public static HashSet<TermVariable> filterToVarsThatOccurFreelyInTerm(final Collection<TermVariable> vars,
			final Term term) {
		final HashSet<TermVariable> result = new HashSet<>();
		for (final TermVariable tv : Arrays.asList(term.getFreeVars())) {
			if (vars.contains(tv)) {
				result.add(tv);
			}
		}
		return result;
	}

	/**
	 * If term is QuantifiedFormula whose quantifier is quant we return term as QuantifiedFormula otherwise we return
	 * null;
	 */
	public static QuantifiedFormula isQuantifiedFormulaWithSameQuantifier(final int quant, final Term term) {
		if (term instanceof QuantifiedFormula) {
			final QuantifiedFormula quantifiedFormula = (QuantifiedFormula) term;
			if (quant == quantifiedFormula.getQuantifier()) {
				return quantifiedFormula;
			}
		}
		return null;
	}

	/**
	 * Given a quantified formula, rename all variables that are bound by the quantifier and occur in the set toRename
	 * to fresh variables.
	 *
	 * @param freshVarPrefix
	 *            prefix of the fresh variables
	 */
	public static Term renameQuantifiedVariables(final ManagedScript mgdScript, final QuantifiedFormula qFormula,
			final Set<TermVariable> toRename, final String freshVarPrefix) {
		final Map<Term, Term> substitutionMapping = new HashMap<>();
		for (final TermVariable var : toRename) {
			final TermVariable freshVariable = mgdScript.constructFreshTermVariable(freshVarPrefix, var.getSort());
			substitutionMapping.put(var, freshVariable);
		}
		final Term newBody = (new Substitution(mgdScript, substitutionMapping)).transform(qFormula.getSubformula());

		final TermVariable[] vars = new TermVariable[qFormula.getVariables().length];
		for (int i = 0; i < vars.length; i++) {
			final TermVariable renamed = (TermVariable) substitutionMapping.get(qFormula.getVariables()[i]);
			if (renamed != null) {
				vars[i] = renamed;
			} else {
				vars[i] = qFormula.getVariables()[i];
			}
		}
		final Term result = mgdScript.getScript().quantifier(qFormula.getQuantifier(), vars, newBody);
		return result;
	}

	/**
	 * @return true iff term is {@link ApplicationTerm} with functionName.
	 */
	public static boolean isFunctionApplication(final Term term, final String functionName) {
		if (term instanceof ApplicationTerm) {
			final FunctionSymbol fun = ((ApplicationTerm) term).getFunction();
			if (fun.getName().equals(functionName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return logically equivalent term in disjunctive normal form (DNF)
	 */
	public static Term toDnf(final IUltimateServiceProvider services, final ManagedScript mgdScript, final Term term,
			final XnfConversionTechnique xnfConversionTechnique) {
		final Term result;
		switch (xnfConversionTechnique) {
		case BDD_BASED:
			result = (new SimplifyBdd(services, mgdScript)).transformToDNF(term);
			break;
		case BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION:
			result = (new Dnf(mgdScript, services)).transform(term);
			break;
		default:
			throw new AssertionError("unknown enum constant");
		}
		return result;
	}

	/**
	 * @return logically equivalent term in conjunctive normal form (CNF)
	 */
	public static Term toCnf(final IUltimateServiceProvider services, final ManagedScript mgdScript, final Term term,
			final XnfConversionTechnique xnfConversionTechnique) {
		final Term result;
		switch (xnfConversionTechnique) {
		case BDD_BASED:
			result = (new SimplifyBdd(services, mgdScript)).transformToCNF(term);
			break;
		case BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION:
			result = (new Cnf(mgdScript, services)).transform(term);
			break;
		default:
			throw new AssertionError("unknown enum constant");
		}
		return result;
	}

	/**
	 * Returns true for {@link Sorts} for which we can obtain values. E.g. for arrays we cannot get values that our
	 * analysis can process, since arrays are infinite in general. However, if the range Sort of an array is bitvector
	 * sort we can get values for array cells (resp. the corresponding select term).
	 */
	public static boolean isSortForWhichWeCanGetValues(final Sort sort) {
		return sort.isNumericSort() || sort.getRealSort().getName().equals("Bool")
				|| sort.getRealSort().getName().equals("BitVec")
				|| sort.getRealSort().getName().equals("FloatingPoint");
	}

	/**
	 * Get values from script and transform them try to simplify them.
	 *
	 * @param script
	 *            Script that is in a state where it can provide values, e.g., after a check-sat where the response was
	 *            sat.
	 * @param terms
	 *            Collection of term for which we want to have possible values in the current satisfying model
	 * @return Mapping that maps to each term for which we want a value a possible value in the current satisfying
	 *         model.
	 */
	public static Map<Term, Term> getValues(final Script script, final Collection<Term> terms) {
		if (terms.isEmpty()) {
			return Collections.emptyMap();
		} else {
			final Term[] asArray = terms.toArray(new Term[terms.size()]);
			final Map<Term, Term> mapFromSolver = script.getValue(asArray);
			/*
			 * Some solvers, e.g., Z3 return -1 not as a literal but as a unary minus of a positive literal. We use our
			 * affine term to obtain the negative literal.
			 */
			final Map<Term, Term> copyWithNiceValues = new HashMap<Term, Term>();
			for (final Entry<Term, Term> entry : mapFromSolver.entrySet()) {
				copyWithNiceValues.put(entry.getKey(), makeAffineIfPossible(script, entry.getValue()));
			}
			return Collections.unmodifiableMap(copyWithNiceValues);
		}
	}

	private static Term makeAffineIfPossible(final Script script, final Term term) {
		final AffineTerm affineTerm = (AffineTerm) (new AffineTermTransformer(script)).transform(term);
		if (affineTerm.isErrorTerm()) {
			return term;
		} else {
			return affineTerm.toTerm(script);
		}
	}
}
