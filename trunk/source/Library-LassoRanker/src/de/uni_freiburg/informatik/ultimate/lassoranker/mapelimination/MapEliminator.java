/*
 * Copyright (C) 2016 Frank Schüssele (schuessf@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.lassoranker.mapelimination;

import static de.uni_freiburg.informatik.ultimate.lassoranker.variables.TransFormulaLRUtils.allVariablesAreInVars;
import static de.uni_freiburg.informatik.ultimate.lassoranker.variables.TransFormulaLRUtils.allVariablesAreOutVars;
import static de.uni_freiburg.informatik.ultimate.lassoranker.variables.TransFormulaLRUtils.allVariablesAreVisible;
import static de.uni_freiburg.informatik.ultimate.lassoranker.variables.TransFormulaLRUtils.translateTermVariablesToDefinitions;

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

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lassoranker.Activator;
import de.uni_freiburg.informatik.ultimate.lassoranker.preprocessors.rewriteArrays.IndexAnalyzer;
import de.uni_freiburg.informatik.ultimate.lassoranker.variables.ReplacementVarFactory;
import de.uni_freiburg.informatik.ultimate.lassoranker.variables.ReplacementVarUtils;
import de.uni_freiburg.informatik.ultimate.lassoranker.variables.TransFormulaLR;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Util;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Boogie2SmtSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.ApplicationTermFinder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.NonTheorySymbol;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.NonTheorySymbolFinder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.PartialQuantifierElimination;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.Substitution;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.arrays.ArrayIndex;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.arrays.MultiDimensionalSelect;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.equalityanalysis.EqualityAnalysisResult;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.util.datastructures.Doubleton;
import de.uni_freiburg.informatik.ultimate.util.datastructures.UnionFind;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 * @author Frank Schüssele (schuessf@informatik.uni-freiburg.de)
 */
public class MapEliminator {
	private final IUltimateServiceProvider mServices;
	private final Script mScript;
	private final ManagedScript mManagedScript;
	private final ReplacementVarFactory mReplacementVarFactory;
	private final ILogger mLogger;
	private final Boogie2SmtSymbolTable mSymbolTable;

	// Stores for each variable, which indices contain it
	private final HashRelation<Term, ArrayIndex> mVariablesToIndices;

	// Stores for each map, which indices access it (bidirectional)
	private final HashRelation<MapTemplate, ArrayIndex> mMapsToIndices;
	private final HashRelation<ArrayIndex, MapTemplate> mIndicesToMaps;

	// The created aux-vars (needed for quantifier-elimination)
	private final Set<TermVariable> mAuxVars;

	// Stores information about the arrays that get assigned to another array (then these arrays have the same indices)
	private final Set<Doubleton<Term>> mRelatedArays;

	// Stores all doubletons of terms, which might be compared
	private final Set<Doubleton<Term>> mDoubletons;

	// Stores all function-names of the uninterpreted functions (to know, what function-calls have to be replaced)^
	private final Set<String> mUninterpretedFunctions;

	// Stores for each transformula, which arrays/uf-calls are accssed in it
	private final Map<TransFormulaLR, HashRelation<MapTemplate, ArrayIndex>> mTransFormulasToLocalIndices;

	private final MapEliminationSettings mSettings;

	/**
	 * Creates a new map eliminator and preprocesses (stores the indices and arrays used in the {@code transformulas}).
	 *
	 * @param services
	 *            UltimateServices
	 * @param managedScript
	 *            ManagedScript
	 * @param symbolTable
	 *            Boogie2SmtSymbolTable
	 * @param replacementVarFactory
	 *            ReplacementVarFactory
	 * @param transformulas
	 *            The transformulas that should be processed
	 * @param settings
	 *            Settings for the map-elimination
	 */
	public MapEliminator(final IUltimateServiceProvider services, final ManagedScript managedScript,
			final Boogie2SmtSymbolTable symbolTable, final ReplacementVarFactory replacementVarFactory,
			final Collection<TransFormulaLR> transformulas, final MapEliminationSettings settings) {
		mSettings = settings;
		mServices = services;
		mScript = managedScript.getScript();
		mLogger = mServices.getLoggingService().getLogger(Activator.s_PLUGIN_ID);
		mLogger.info("Using MapEliminator with " + mSettings);
		mManagedScript = managedScript;
		mReplacementVarFactory = replacementVarFactory;
		mSymbolTable = symbolTable;

		mTransFormulasToLocalIndices = new HashMap<>();

		mVariablesToIndices = new HashRelation<>();
		mMapsToIndices = new HashRelation<>();
		mIndicesToMaps = new HashRelation<>();

		mAuxVars = new HashSet<>();

		mRelatedArays = new HashSet<>();
		mUninterpretedFunctions = new HashSet<>();

		findAllIndices(transformulas);
		mDoubletons = computeDoubletons(mMapsToIndices);
	}

	/**
	 * Finds the array accesses in the transformulas and merges the indices if necessary.
	 */
	private void findAllIndices(final Collection<TransFormulaLR> transformulas) {
		// Get all array indices from each transformula (only necessary, if it contains arrays)
		for (final TransFormulaLR tf : transformulas) {
			mTransFormulasToLocalIndices.put(tf, new HashRelation<>());
			findIndices(tf);
		}
		// Merge the indices of the related arrays using union-find
		final UnionFind<Term> unionFind = new UnionFind<>();
		final Map<Term, ArrayTemplate> templates = new HashMap<>();
		for (final Doubleton<Term> doubleton : mRelatedArays) {
			final Term array1 = doubleton.getOneElement();
			final Term array2 = doubleton.getOtherElement();
			templates.put(array1, new ArrayTemplate(array1, mScript));
			templates.put(array2, new ArrayTemplate(array2, mScript));
			unionFind.findAndConstructEquivalenceClassIfNeeded(array1);
			unionFind.findAndConstructEquivalenceClassIfNeeded(array2);
			unionFind.union(array1, array2);
		}
		for (final Set<Term> equivalenceClass : unionFind.getAllEquivalenceClasses()) {
			final Set<ArrayIndex> indices = new HashSet<>();
			for (final Term array : equivalenceClass) {
				indices.addAll(mMapsToIndices.getImage(templates.get(array)));
			}
			for (final Term array : equivalenceClass) {
				for (final ArrayIndex index : indices) {
					mMapsToIndices.addPair(templates.get(array), index);
					mIndicesToMaps.addPair(index, templates.get(array));
				}
			}
		}
	}

	/**
	 * A method that finds arrays and their indices in the given {@code transformula} and stores them in the maps.
	 */
	private void findIndices(final TransFormulaLR transformula) {
		final Term term = transformula.getFormula();
		for (final MultiDimensionalSelect select : MultiDimensionalSelect.extractSelectDeep(term, false)) {
			final ArrayWrite arrayWrite = new ArrayWrite(select.getArray());
			findIndicesArrayWrite(arrayWrite, transformula);
			addArrayAccessToRelation(arrayWrite.getOldArray(), select.getIndex(), transformula);
		}
		for (final ApplicationTerm t : new ApplicationTermFinder("=", false).findMatchingSubterms(term)) {
			if (t.getParameters()[0].getSort().isArraySort()) {
				final ArrayWrite arrayWrite = new ArrayWrite(t);
				// The new array can be also a store-term, so also find indices in this term
				final ArrayWrite arrayWrite2 = new ArrayWrite(arrayWrite.getNewArray());
				findIndicesArrayWrite(arrayWrite, transformula);
				findIndicesArrayWrite(arrayWrite2, transformula);
				final Term array1 = arrayWrite.getOldArray();
				final Term array2 = arrayWrite2.getOldArray();
				if (allVariablesAreVisible(array1, transformula) && allVariablesAreVisible(array2, transformula)) {
					final Term globalArray1 = translateTermVariablesToDefinitions(mScript, transformula, array1);
					final Term globalArray2 = translateTermVariablesToDefinitions(mScript, transformula, array2);
					// If the two arrays are different, add them to the set of related arrays
					// (the indices need to be shared then)
					if (globalArray1 != globalArray2) {
						mRelatedArays.add(new Doubleton<>(globalArray1, globalArray2));
					}
				}
			}
		}
		for (final NonTheorySymbol<?> s : new NonTheorySymbolFinder().findNonTheorySymbols(term)) {
			final Object symbol = s.getSymbol();
			if (symbol instanceof FunctionSymbol) {
				final String function = ((FunctionSymbol) symbol).getName();
				for (final ApplicationTerm t : new ApplicationTermFinder(function, false).findMatchingSubterms(term)) {
					final ArrayIndex index = new ArrayIndex(Arrays.asList(t.getParameters()));
					addCallToRelation(function, index, transformula);
				}
			}
		}
	}

	private void findIndicesArrayWrite(final ArrayWrite arrayWrite, final TransFormulaLR transformula) {
		for (final Pair<ArrayIndex, Term> pair : arrayWrite.getIndexValuePairs()) {
			addArrayAccessToRelation(arrayWrite.getOldArray(), pair.getFirst(), transformula);
		}
	}

	/**
	 * Adds the info, that {@code array} is accessed by {@code index} to the hash relations.
	 */
	private void addArrayAccessToRelation(final Term array, final ArrayIndex index, final TransFormulaLR transformula) {
		if (!allVariablesAreVisible(array, transformula)) {
			return;
		}
		for (final Term t : index) {
			if (SmtUtils.containsFunctionApplication(t, "store")) {
				return;
			}
		}
		final Term globalArray = translateTermVariablesToDefinitions(mScript, transformula, array);
		final Term inVarArray = getLocalTerm(globalArray, transformula, true);
		final Term outVarArray = getLocalTerm(globalArray, transformula, false);
		mTransFormulasToLocalIndices.get(transformula).addPair(new ArrayTemplate(inVarArray, mScript), index);
		mTransFormulasToLocalIndices.get(transformula).addPair(new ArrayTemplate(outVarArray, mScript), index);
		if (allVariablesAreVisible(index, transformula)) {
			final ArrayIndex globalIndex = new ArrayIndex(
					translateTermVariablesToDefinitions(mScript, transformula, index));
			for (final TermVariable var : globalIndex.getFreeVars()) {
				mVariablesToIndices.addPair(var, globalIndex);
			}
			final ArrayTemplate template = new ArrayTemplate(globalArray, mScript);
			mMapsToIndices.addPair(template, globalIndex);
			mIndicesToMaps.addPair(globalIndex, template);
		}
	}

	/**
	 * Adds the info, that the function with name {@code functionName} is applied to {@code index} to the hash
	 * relations.
	 */
	private void addCallToRelation(final String functionName, final ArrayIndex index,
			final TransFormulaLR transformula) {
		if (index.isEmpty()) {
			return;
		}
		for (final Term t : index) {
			if (SmtUtils.containsFunctionApplication(t, "store")) {
				return;
			}
		}
		final UFTemplate template = new UFTemplate(functionName, mScript);
		mTransFormulasToLocalIndices.get(transformula).addPair(template, index);
		mUninterpretedFunctions.add(functionName);
		if (allVariablesAreVisible(index, transformula)) {
			final ArrayIndex globalIndex = new ArrayIndex(
					translateTermVariablesToDefinitions(mScript, transformula, index));
			for (final TermVariable var : globalIndex.getFreeVars()) {
				mVariablesToIndices.addPair(var, globalIndex);
			}
			mMapsToIndices.addPair(template, globalIndex);
			mIndicesToMaps.addPair(globalIndex, template);
		}
	}

	/**
	 * Given a TransFormula with possibly array accesses or calls of uninterpreted functions, returns a new
	 * TransFormula, where these are replaced. In general an overapproximation is returned.
	 * <p>
	 * The given TransFormula has to be in the collection given to the constructor and its formula has to be in NNF.
	 * <p>
	 * This method ignores the index analysis
	 *
	 * @param transformula
	 *            The old TransFormulaLR, which might contain maps
	 * @return A TransFormulaLR, where array accesses and calls of uninterpreted functions are replaced
	 */
	public TransFormulaLR getRewrittenTransFormula(final TransFormulaLR transformula) {
		final EqualityAnalysisResult emptyResult = new EqualityAnalysisResult(mDoubletons);
		return getRewrittenTransFormula(transformula, emptyResult, emptyResult);
	}

	/**
	 * Given a TransFormula with possibly array accesses or calls of uninterpreted functions, returns a new
	 * TransFormula, where these are replaced. In general an overapproximation is returned.
	 * <p>
	 * The given TransFormula has to be in the collection given to the constructor and its formula has to be in NNF.
	 *
	 * @param transformula
	 *            The old TransFormulaLR, which might contain maps
	 * @param equalityAnalysisBefore
	 *            The invariants that are valid before the transformula
	 * @param equalityAnalysisAfter
	 *            The invariants that are valid after the transformula
	 * @return A TransFormulaLR, where array accesses and calls of uninterpreted functions are replaced
	 */
	public TransFormulaLR getRewrittenTransFormula(final TransFormulaLR transformula,
			final EqualityAnalysisResult equalityAnalysisBefore, final EqualityAnalysisResult equalityAnalysisAfter) {
		assert mTransFormulasToLocalIndices.containsKey(transformula) : "This transformula wasn't preprocessed";
		final TransFormulaLR newTF = new TransFormulaLR(transformula);
		final Term originalTerm = newTF.getFormula();
		final HashRelation<MapTemplate, ArrayIndex> localIndices = getLocalIndices(newTF,
				mTransFormulasToLocalIndices.get(transformula));
		final IndexAnalyzer indexAnalyzer = new IndexAnalyzer(originalTerm, computeDoubletons(localIndices),
				mSymbolTable, newTF, equalityAnalysisBefore, equalityAnalysisAfter, mLogger, mReplacementVarFactory);
		final EqualityAnalysisResult invariants = indexAnalyzer.getResult();
		final Term storeFreeTerm = replaceStoreTerms(originalTerm, newTF, invariants);
		assert !SmtUtils.containsFunctionApplication(storeFreeTerm, "store") : "The formula contains still store-terms";
		final List<Term> conjuncts = new ArrayList<>();
		conjuncts.addAll(Arrays.asList(SmtUtils.getConjuncts(storeFreeTerm)));
		conjuncts.addAll(getAdditionalEqualities(localIndices, invariants));
		if (!mSettings.onlyTrivialImplicationsForModifiedArguments()) {
			conjuncts.addAll(getAllImplicationsForIndexAssignment(newTF, invariants));
		}
		conjuncts.addAll(invariants.constructListOfEqualities(mScript));
		if (mSettings.addInequalities()) {
			conjuncts.addAll(invariants.constructListOfNotEquals(mScript));
		}
		final Term mapFreeTerm = replaceMapReads(newTF, SmtUtils.and(mScript, conjuncts));
		assert SmtUtils.isArrayFree(mapFreeTerm) : "The formula contains still arrays";
		assert !SmtUtils.containsUninterpretedFunctioApplication(mapFreeTerm) : "The formula contains still UFs";
		setFormulaAndSimplify(newTF, mapFreeTerm);
		return newTF;
	}

	private HashRelation<MapTemplate, ArrayIndex> getLocalIndices(final TransFormulaLR transformula,
			final HashRelation<MapTemplate, ArrayIndex> occurringIndices) {
		final HashRelation<MapTemplate, ArrayIndex> result = new HashRelation<>();
		result.addAll(occurringIndices);
		for (final MapTemplate template : mMapsToIndices.getDomain()) {
			for (final ArrayIndex index : getInAndOutVarIndices(mMapsToIndices.getImage(template), transformula)) {
				if (template instanceof ArrayTemplate) {
					final Term array = (Term) template.getIdentifier();
					final ArrayTemplate inVarTemplate = new ArrayTemplate(getLocalTerm(array, transformula, true),
							mScript);
					final ArrayTemplate outVarTemplate = new ArrayTemplate(getLocalTerm(array, transformula, false),
							mScript);
					result.addPair(inVarTemplate, index);
					result.addPair(outVarTemplate, index);
				} else {
					result.addPair(template, index);
				}
			}
		}
		return result;
	}

	/**
	 * Returns all equalities of based on the index analysis. To reduce the number of conjuncts, UnionFind is used.
	 *
	 * @param localIndices
	 *            A HashRelation, which maps all MapTemplates, which are considered to the needed local indices.
	 * @param invariants
	 *            The valid invariants at this transformula
	 * @return A list of terms (= conjuncts) with equalities that are valid at this transformula
	 */
	private List<Term> getAdditionalEqualities(final HashRelation<MapTemplate, ArrayIndex> localIndices,
			final EqualityAnalysisResult invariants) {
		final List<Term> result = new ArrayList<>();
		for (final MapTemplate template : localIndices.getDomain()) {
			final UnionFind<ArrayIndex> unionFind = new UnionFind<>();
			final Set<ArrayIndex> indicesSet = localIndices.getImage(template);
			final ArrayIndex[] indices = indicesSet.toArray(new ArrayIndex[indicesSet.size()]);
			for (int i = 0; i < indices.length; i++) {
				for (int j = i + 1; j < indices.length; j++) {
					if (areIndicesEqual(indices[i], indices[j], invariants)) {
						unionFind.findAndConstructEquivalenceClassIfNeeded(indices[i]);
						unionFind.findAndConstructEquivalenceClassIfNeeded(indices[j]);
						unionFind.union(indices[i], indices[j]);
					}
				}
			}
			for (final ArrayIndex index1 : unionFind.getAllRepresentatives()) {
				for (final ArrayIndex index2 : unionFind.getEquivalenceClassMembers(index1)) {
					if (index1 == index2) {
						continue;
					}
					final Term term1 = template.getTerm(index1);
					final Term term2 = template.getTerm(index2);
					if (!term1.getSort().isArraySort()) {
						result.add(SmtUtils.binaryEquality(mScript, term1, term2));
					}
				}
			}
		}
		return result;
	}

	/**
	 * This methods eliminates aux-var from the term, sets it to the transformula and simplifies the transformula then.
	 */
	private void setFormulaAndSimplify(final TransFormulaLR transformula, final Term term) {
		Term newTerm;
		if (mAuxVars.isEmpty()) {
			newTerm = term;
		} else {
			// If aux-vars have been created, eliminate them
			newTerm = PartialQuantifierElimination.elim(mManagedScript, Script.EXISTS, mAuxVars, term, mServices,
					mLogger, mSettings.getSimplificationTechnique(), mSettings.getXnfConversionTechnique());
			// Add the remaining aux-vars to the transformula
			transformula.addAuxVars(mAuxVars);
			mAuxVars.clear();
		}
		transformula.setFormula(
				SmtUtils.simplify(mManagedScript, newTerm, mServices, mSettings.getSimplificationTechnique()));
		clearTransFormula(transformula);
	}

	/**
	 * Removes unnecessary variables from the transformula.
	 */
	private static void clearTransFormula(final TransFormulaLR transformula) {
		final List<IProgramVar> inVarsToRemove = new ArrayList<>();
		final List<IProgramVar> outVarsToRemove = new ArrayList<>();
		final List<TermVariable> auxVarsToRemove = new ArrayList<>();
		final Set<TermVariable> freeVars = new HashSet<>(Arrays.asList(transformula.getFormula().getFreeVars()));
		for (final Entry<IProgramVar, Term> entry : transformula.getInVars().entrySet()) {
			final Term inVar = entry.getValue();
			final IProgramVar var = entry.getKey();
			if (inVar.getSort().isArraySort()) {
				inVarsToRemove.add(var);
			} else if (!freeVars.contains(inVar) && transformula.getOutVars().get(var) == inVar
					&& !SmtUtils.isConstant(inVar)) {
				inVarsToRemove.add(var);
				outVarsToRemove.add(var);
			}
		}
		for (final Entry<IProgramVar, Term> entry : transformula.getOutVars().entrySet()) {
			final Term outVar = entry.getValue();
			if (outVar.getSort().isArraySort()) {
				outVarsToRemove.add(entry.getKey());
			}
		}
		for (final TermVariable tv : transformula.getAuxVars()) {
			if (!freeVars.contains(tv)) {
				auxVarsToRemove.add(tv);
			}
		}
		for (final IProgramVar var : inVarsToRemove) {
			transformula.removeInVar(var);
		}
		for (final IProgramVar var : outVarsToRemove) {
			transformula.removeOutVar(var);
		}
		for (final TermVariable tv : auxVarsToRemove) {
			transformula.removeAuxVar(tv);
		}
	}

	/**
	 * Replaces all read-accesses of maps (select-terms without store and UF-calls) in {@code term} with replacement- or
	 * aux-vars. So this method produces a map-free term.
	 *
	 * @param transformula
	 *            A TransFormulaLR
	 * @param term
	 *            A store-free term
	 * @return A new map-free term
	 */
	private Term replaceMapReads(final TransFormulaLR transformula, final Term term) {
		addReplacementVarsToTransFormula(transformula);
		final Map<Term, Term> substitution = new HashMap<>();
		for (final ApplicationTerm select : new ApplicationTermFinder("select", true).findMatchingSubterms(term)) {
			if (!select.getSort().isArraySort()) {
				substitution.put(select, getReplacementVar(select, transformula));
			}
		}
		for (final String functionName : mUninterpretedFunctions) {
			for (final Term functionCall : new ApplicationTermFinder(functionName, true).findMatchingSubterms(term)) {
				substitution.put(functionCall, getReplacementVar(functionCall, transformula));
			}
		}
		return new Substitution(mManagedScript, substitution).transform(term);
	}

	/**
	 * Adds all replacement-vars as in- and out-vars to the transformula.
	 */
	private void addReplacementVarsToTransFormula(final TransFormulaLR transformula) {
		for (final MapTemplate template : mMapsToIndices.getDomain()) {
			for (final ArrayIndex index : mMapsToIndices.getImage(template)) {
				final Term term = template.getTerm(index);
				final IProgramVar var = mReplacementVarFactory.getOrConstuctReplacementVar(term);
				boolean containsAssignedVar = false;
				for (final TermVariable tv : term.getFreeVars()) {
					final IProgramVar progVar = mSymbolTable.getBoogieVar(tv);
					if (transformula.getInVars().get(progVar) != transformula.getOutVars().get(progVar)) {
						containsAssignedVar = true;
						break;
					}
				}
				final Term termVar = getFreshTermVar(term);
				if (!transformula.getInVars().containsKey(var)) {
					transformula.addInVar(var, termVar);
				}
				if (!transformula.getOutVars().containsKey(var)) {
					// If the term contains an assigned var, different in- and out-vars are created, otherwise the same
					if (containsAssignedVar) {
						transformula.addOutVar(var, getFreshTermVar(term));
					} else {
						transformula.addOutVar(var, termVar);
					}
				}
			}
		}
	}

	/**
	 * Returns the corresponding replacementVar (or auxVar) for the given {@code term}. The replacementVars have to be
	 * already to it added by calling {@code addReplacementVarsToTransFormula}.
	 */
	private Term getReplacementVar(final Term term, final TransFormulaLR transformula) {
		if (!allVariablesAreInVars(term, transformula) && !allVariablesAreOutVars(term, transformula)) {
			return getAndAddAuxVar(term);
		}
		final Term definition = translateTermVariablesToDefinitions(mScript, transformula, term);
		final IProgramVar var = mReplacementVarFactory.getOrConstuctReplacementVar(definition);
		assert transformula.getInVars().containsKey(var) && transformula.getOutVars().containsKey(var) : var
				+ " was not added to the transformula!";
		if (allVariablesAreInVars(term, transformula)) {
			return transformula.getInVars().get(var);
		}
		return transformula.getOutVars().get(var);
	}

	/**
	 * This methods replaces all store-terms and array-equalities in the {@code term} and adds the neeeded in-/out-vars
	 * to the {@code transformula} (The returned term can still contain select-terms or uninterpreted functions).
	 *
	 * @param term
	 *            The term to be replaced
	 * @param transformula
	 *            The new TransFormulaLR (in-/out-vars are added)
	 * @param invariants
	 *            The valid invariants
	 * @return A term, that doen't contain store-terms
	 */
	private Term replaceStoreTerms(final Term term, final TransFormulaLR transformula,
			final EqualityAnalysisResult invariants) {
		final Map<Term, Term> substitutionMap = new HashMap<>();
		final List<Term> auxVarEqualities = new ArrayList<>();
		// First remove all array inequalities by replacing them with true as an overapproximation
		final Term newTerm = replaceArrayInequalities(term);
		for (final MultiDimensionalSelect select : MultiDimensionalSelect.extractSelectDeep(newTerm, false)) {
			if (SmtUtils.isFunctionApplication(select.getArray(), "store")) {
				final Term selectTerm = select.getSelectTerm();
				substitutionMap.put(selectTerm,
						replaceSelectStoreTerm(selectTerm, transformula, invariants, auxVarEqualities));
			}
		}
		for (final ApplicationTerm t : new ApplicationTermFinder("=", false).findMatchingSubterms(newTerm)) {
			if (t.getParameters()[0].getSort().isArraySort()) {
				substitutionMap.put(t, replaceArrayEquality(t, transformula, invariants));
			}
		}
		final List<Term> conjuncts = new ArrayList<>();
		conjuncts.addAll(Arrays.asList(SmtUtils.getConjuncts(newTerm)));
		conjuncts.addAll(auxVarEqualities);
		final Substitution substitution = new Substitution(mManagedScript, substitutionMap);
		return substitution.transform(substitution.transform(SmtUtils.and(mScript, conjuncts)));
	}

	/**
	 * Replaces all array inequalities with true as an overapproximation to avoid unsoundness. This requires a formula
	 * in NNF.
	 */
	private Term replaceArrayInequalities(final Term term) {
		final Map<Term, Term> substitutionMap = new HashMap<>();
		for (final ApplicationTerm t : new ApplicationTermFinder("not", false).findMatchingSubterms(term)) {
			final Term subterm = t.getParameters()[0];
			assert SmtUtils.isAtomicFormula(subterm) : "The term is not in NNF";
			if (SmtUtils.isFunctionApplication(subterm, "=")) {
				final ApplicationTerm equality = (ApplicationTerm) subterm;
				if (equality.getParameters()[0].getSort().isArraySort()) {
					substitutionMap.put(t, mScript.term("true"));
				}
			}
		}
		return new Substitution(mManagedScript, substitutionMap).transform(term);
	}

	/**
	 * Replaces a select-store-term (e.g. (select (store a i x) j)) with an aux-var. For this aux-var additional
	 * equalities are added to as conjuncts to {@code auxVarEqualities}.
	 */
	private TermVariable replaceSelectStoreTerm(final Term term, final TransFormulaLR transformula,
			final EqualityAnalysisResult invariants, final List<Term> auxVarEqualities) {
		final MultiDimensionalSelect multiDimensionalSelect = new MultiDimensionalSelect(term);
		final ArrayIndex index = multiDimensionalSelect.getIndex();
		final ArrayWrite arrayWrite = new ArrayWrite(multiDimensionalSelect.getArray());
		final Set<ArrayIndex> processedIndices = new HashSet<>();
		final TermVariable auxVar = getAndAddAuxVar(term);
		for (final Pair<ArrayIndex, Term> pair : arrayWrite.getIndexValuePairs()) {
			final ArrayIndex assignedIndex = pair.getFirst();
			if (processedIndices.contains(assignedIndex)) {
				continue;
			}
			final Term value = pair.getSecond();
			final Term newTerm = indexEqualityInequalityImpliesValueEquality(index, assignedIndex, processedIndices,
					auxVar, value, invariants, transformula);
			// If the implication is not trivial (no "or"-term) and onlyTrivialImplicationsArrayWrite is enabled,
			// don't add the implication to the conjuncts
			if (!SmtUtils.isFunctionApplication(newTerm, "or") || !mSettings.onlyTrivialImplicationsArrayWrite()) {
				auxVarEqualities.add(newTerm);
			}
			processedIndices.add(assignedIndex);
		}
		final Term selectTerm = SmtUtils.multiDimensionalSelect(mScript, arrayWrite.getOldArray(), index);
		final Term arrayRead = indexEqualityInequalityImpliesValueEquality(index, index, processedIndices, auxVar,
				selectTerm, invariants, transformula);
		if (!SmtUtils.isFunctionApplication(arrayRead, "or") || !mSettings.onlyTrivialImplicationsArrayWrite()) {
			auxVarEqualities.add(arrayRead);
		}
		return auxVar;
	}

	/**
	 * Replaces an array-equality with a term that only contains select-terms.
	 *
	 * @param term
	 *            A term of the form: (= b (store ... (store a i_1 x_1) i_n x_n))
	 * @param transformula
	 *            A TransFormulaLR
	 * @param invariants
	 *            The valid invariants
	 * @return A term store-free term
	 */
	private Term replaceArrayEquality(final Term term, final TransFormulaLR transformula,
			final EqualityAnalysisResult invariants) {
		final ArrayWrite arrayWrite = new ArrayWrite(term);
		final Term oldArray = arrayWrite.getOldArray();
		final Term newArray = arrayWrite.getNewArray();
		// If the old or new array is an aux-var, just return true
		// If both arrays are store-terms, also just return true
		if (!allVariablesAreVisible(oldArray, transformula) || !allVariablesAreVisible(newArray, transformula)
				|| SmtUtils.isFunctionApplication(newArray, "store")) {
			return mScript.term("true");
		}
		final List<Term> result = new ArrayList<>();
		final boolean oldIsInVar = transformula.getInVarsReverseMapping().containsKey(oldArray);
		final boolean newIsInVar = transformula.getInVarsReverseMapping().containsKey(newArray);
		final Term globalOldArray = translateTermVariablesToDefinitions(mScript, transformula, oldArray);
		final Term globalNewArray = translateTermVariablesToDefinitions(mScript, transformula, newArray);
		final ArrayTemplate oldTemplate = new ArrayTemplate(globalOldArray, mScript);
		final ArrayTemplate newTemplate = new ArrayTemplate(globalNewArray, mScript);
		final Set<ArrayIndex> processedIndices = new HashSet<>();
		for (final Pair<ArrayIndex, Term> pair : arrayWrite.getIndexValuePairs()) {
			final ArrayIndex assignedIndex = pair.getFirst();
			if (processedIndices.contains(assignedIndex)) {
				continue;
			}
			final Term value = pair.getSecond();
			for (final ArrayIndex globalIndex : mMapsToIndices.getImage(newTemplate)) {
				final ArrayIndex index = getLocalIndex(globalIndex, transformula, newIsInVar);
				if (processedIndices.contains(index)) {
					continue;
				}
				final Term select = getLocalTerm(newTemplate.getTerm(globalIndex), transformula, newIsInVar);
				final Term newTerm = indexEqualityInequalityImpliesValueEquality(index, assignedIndex, processedIndices,
						select, value, invariants, transformula);
				// If the implication is not trivial (no "or"-term) and onlyTrivialImplicationsArrayWrite is enabled,
				// don't add the implication to the conjuncts
				if (!SmtUtils.isFunctionApplication(newTerm, "or") || !mSettings.onlyTrivialImplicationsArrayWrite()) {
					result.add(newTerm);
				}

			}
			processedIndices.add(assignedIndex);
		}
		// For un-assigned indices i add: newArray[i] = oldArray[i]
		for (final ArrayIndex globalIndex : mMapsToIndices.getImage(oldTemplate)) {
			final Term selectOld = getLocalTerm(oldTemplate.getTerm(globalIndex), transformula, oldIsInVar);
			final Term selectNew = getLocalTerm(newTemplate.getTerm(globalIndex), transformula, newIsInVar);
			final ArrayIndex index1 = getLocalIndex(globalIndex, transformula, oldIsInVar);
			final ArrayIndex index2 = getLocalIndex(globalIndex, transformula, newIsInVar);
			final Term newTerm = indexEqualityInequalityImpliesValueEquality(index1, index2, processedIndices,
					selectNew, selectOld, invariants, transformula);
			// If the implication is not trivial (no "or"-term) and onlyTrivialImplicationsArrayWrite is enabled,
			// don't add the implication to the conjuncts
			if (!SmtUtils.isFunctionApplication(newTerm, "or") || !mSettings.onlyTrivialImplicationsArrayWrite()) {
				result.add(newTerm);
			}
		}
		return SmtUtils.and(mScript, result);
	}

	/**
	 * Returns for all assigned terms additional conjuncts. If an index i contains an assigned var and i and j are
	 * indices of a, the implication: (i = j) => (a[i] = a[j]) is contained in the result
	 */
	private List<Term> getAllImplicationsForIndexAssignment(final TransFormulaLR transformula,
			final EqualityAnalysisResult invariants) {
		final List<Term> result = new ArrayList<>();
		for (final IProgramVar var : transformula.getAssignedVars()) {
			final Term definition = ReplacementVarUtils.getDefinition(var);
			for (final ArrayIndex globalIndexWritten : mVariablesToIndices.getImage(definition)) {
				final ArrayIndex indexWrittenIn = getLocalIndex(globalIndexWritten, transformula, true);
				final ArrayIndex indexWrittenOut = getLocalIndex(globalIndexWritten, transformula, false);
				for (final MapTemplate template : mIndicesToMaps.getImage(globalIndexWritten)) {
					final Term written = template.getTerm(globalIndexWritten);
					final Term writtenIn = getLocalTerm(written, transformula, true);
					final Term writtenOut = getLocalTerm(written, transformula, false);
					final Term unchanged = indexEqualityImpliesValueEquality(indexWrittenOut, indexWrittenIn,
							writtenOut, writtenIn, invariants, transformula);
					result.add(unchanged);
					for (final ArrayIndex globalIndexRead : mMapsToIndices.getImage(template)) {
						if (globalIndexWritten == globalIndexRead) {
							continue;
						}
						// Compare with the other indices (in- and out-var-version)
						final Term read = template.getTerm(globalIndexRead);
						final Term readIn = getLocalTerm(read, transformula, true);
						final Term readOut = getLocalTerm(read, transformula, false);
						final ArrayIndex indexReadIn = getLocalIndex(globalIndexRead, transformula, true);
						final ArrayIndex indexReadOut = getLocalIndex(globalIndexRead, transformula, false);
						final Term assignmentIn = indexEqualityImpliesValueEquality(indexWrittenOut, indexReadIn,
								writtenOut, readIn, invariants, transformula);
						result.add(assignmentIn);
						final Term assignmentOut = indexEqualityImpliesValueEquality(indexWrittenOut, indexReadOut,
								writtenOut, readOut, invariants, transformula);
						result.add(assignmentOut);
					}
				}
			}
		}
		return result;
	}

	/**
	 * Given a global term (=definition), adds the in- and out-vars to the transformula and returns the term with in- or
	 * out-vars.
	 *
	 * @param term
	 *            A SMT-Term with global variables
	 * @param transformula
	 *            A TransFormulaLR
	 * @param returnInVar
	 *            Switch to return only in- or out-vars
	 * @return The local term (with in- or out-vars) for the given global term
	 */
	private Term getLocalTerm(final Term term, final TransFormulaLR transformula, final boolean returnInVar) {
		final Map<Term, Term> substitution = new HashMap<>();
		for (final TermVariable var : term.getFreeVars()) {
			final IProgramVar programVar = mSymbolTable.getBoogieVar(var);
			// Add the missing in-/out-vars to the transformula if necessary
			final TermVariable freshTermVar = getFreshTermVar(var);
			if (!transformula.getInVars().containsKey(programVar)) {
				transformula.addInVar(programVar, freshTermVar);
			}
			if (!transformula.getOutVars().containsKey(programVar)) {
				transformula.addOutVar(programVar, freshTermVar);
			}
			// Put the in-/out-var-version of this variable to the substitution-map
			if (returnInVar) {
				substitution.put(var, transformula.getInVars().get(programVar));
			} else {
				substitution.put(var, transformula.getOutVars().get(programVar));
			}
		}
		return new Substitution(mManagedScript, substitution).transform(term);
	}

	/**
	 * Given an array index with global terms (=definitions), adds the in- and out-vars to the transformula and returns
	 * the index with in- or out-vars.
	 *
	 * @param term
	 *            An array-index with global variables
	 * @param transformula
	 *            A TransFormulaLR
	 * @param returnInVar
	 *            Switch to return only in- or out-vars
	 * @return The local index (with in- or out-vars) for the given global index
	 */
	private ArrayIndex getLocalIndex(final ArrayIndex index, final TransFormulaLR transformula,
			final boolean returnInVar) {
		final List<Term> list = new ArrayList<>();
		for (final Term t : index) {
			list.add(getLocalTerm(t, transformula, returnInVar));
		}
		return new ArrayIndex(list);
	}

	/**
	 * Return for a given set of global indices, all in- and out-var-versions of this index for the given transformula.
	 */
	private Set<ArrayIndex> getInAndOutVarIndices(final Set<ArrayIndex> indices, final TransFormulaLR transformula) {
		final Set<ArrayIndex> result = new HashSet<>();
		for (final ArrayIndex index : indices) {
			result.add(getLocalIndex(index, transformula, true));
			result.add(getLocalIndex(index, transformula, false));
		}
		return result;
	}

	/**
	 * Get a fresh TermVariable with the given term as name (in a nicer representation, especially for select-terms).
	 */
	private TermVariable getFreshTermVar(final Term term) {
		return mManagedScript.constructFreshTermVariable(niceTermString(term), term.getSort());
	}

	/**
	 * Get an aux-var with the given term as name (in a nicer representation, especially for select-terms) and add it to
	 * the set of aux-vars. The method returns the same aux-var for two terms iff they're equal.
	 */
	private TermVariable getAndAddAuxVar(final Term term) {
		final TermVariable auxVar = mReplacementVarFactory.getOrConstructAuxVar(niceTermString(term), term.getSort());
		mAuxVars.add(auxVar);
		return auxVar;
	}

	private static String niceTermString(final Term term) {
		if (SmtUtils.isFunctionApplication(term, "select")) {
			final StringBuilder stringBuilder = new StringBuilder();
			final MultiDimensionalSelect select = new MultiDimensionalSelect(term);
			stringBuilder.append("array_").append(niceTermString(select.getArray())).append('[');
			final ArrayIndex index = select.getIndex();
			for (int i = 0; i < index.size(); i++) {
				stringBuilder.append(niceTermString(index.get(i))).append(i == index.size() - 1 ? ']' : ',');
			}
			return stringBuilder.toString();
		}
		if (term instanceof ApplicationTerm && !SmtUtils.isConstant(term)) {
			final StringBuilder stringBuilder = new StringBuilder();
			final ApplicationTerm applicationTerm = (ApplicationTerm) term;
			final FunctionSymbol function = applicationTerm.getFunction();
			if (!function.isIntern()) {
				stringBuilder.append("uf_");
			}
			stringBuilder.append('(').append(function.getName()).append(' ');
			final Term[] params = applicationTerm.getParameters();
			for (int i = 0; i < params.length; i++) {
				stringBuilder.append(niceTermString(params[i])).append(i == params.length - 1 ? ')' : ' ');
			}
			return stringBuilder.toString();
		}
		return SmtUtils.removeSmtQuoteCharacters(term.toString());
	}

	private static Set<Doubleton<Term>> computeDoubletons(final HashRelation<MapTemplate, ArrayIndex> hashRelation) {
		final Set<Doubleton<Term>> result = new HashSet<>();
		for (final MapTemplate template : hashRelation.getDomain()) {
			final Set<ArrayIndex> indicesSet = hashRelation.getImage(template);
			final ArrayIndex[] indices = indicesSet.toArray(new ArrayIndex[indicesSet.size()]);
			for (int i = 0; i < indices.length; i++) {
				for (int j = i + 1; j < indices.length; j++) {
					final ArrayIndex index1 = indices[i];
					final ArrayIndex index2 = indices[j];
					for (int k = 0; k < index1.size(); k++) {
						final Term term1 = index1.get(k);
						final Term term2 = index2.get(k);
						if (term1 != term2) {
							result.add(new Doubleton<>(term1, term2));
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * Return set of unordered pairs ({@link Doubleton}s) of all Terms {x,y} such that x and y occur as entry of a
	 * (potentially multi-dimentional) argument i_x i_y of the same (or equivalent) map.
	 */
	public Set<Doubleton<Term>> getDoubletons() {
		return mDoubletons;
	}

	private static boolean areIndicesEqual(final ArrayIndex index1, final ArrayIndex index2,
			final EqualityAnalysisResult invariants) {
		for (int i = 0; i < index1.size(); i++) {
			final Term term1 = index1.get(i);
			final Term term2 = index2.get(i);
			if (term1 != term2 && !invariants.getEqualDoubletons().contains(new Doubleton<>(term1, term2))) {
				return false;
			}
		}
		return true;
	}

	private Term getEqualTerm(final ArrayIndex index1, final ArrayIndex index2,
			final EqualityAnalysisResult invariants) {
		final List<Term> result = new ArrayList<>();
		for (int i = 0; i < index1.size(); i++) {
			final Term term1 = index1.get(i);
			final Term term2 = index2.get(i);
			if (term1 == term2) {
				continue;
			}
			final Doubleton<Term> doubleton = new Doubleton<>(term1, term2);
			if (invariants.getDistinctDoubletons().contains(doubleton)) {
				return mScript.term("false");
			}
			if (!invariants.getEqualDoubletons().contains(doubleton)) {
				result.add(SmtUtils.binaryEquality(mScript, term1, term2));
			}
		}
		return SmtUtils.and(mScript, result);
	}

	private Term indexEqualityInequalityImpliesValueEquality(final ArrayIndex index, final ArrayIndex equal,
			final Collection<ArrayIndex> unequal, final Term value1, final Term value2,
			final EqualityAnalysisResult invariants, final TransFormulaLR transformula) {
		final List<TermVariable> freeVarsFormula = Arrays.asList(transformula.getFormula().getFreeVars());
		final Term inequality = Util.not(mScript, getEqualTerm(index, equal, invariants));
		final List<TermVariable> freeVarsInequality = Arrays.asList(inequality.getFreeVars());
		if (!freeVarsFormula.containsAll(freeVarsInequality) && mSettings.onlyArgumentsInFormula()) {
			return mScript.term("true");
		}
		final List<Term> disjuncts = new ArrayList<>();
		disjuncts.add(inequality);
		for (final ArrayIndex i : unequal) {
			final Term equality = getEqualTerm(index, i, invariants);
			final List<TermVariable> freeVarsEquality = Arrays.asList(equality.getFreeVars());
			if (!freeVarsFormula.containsAll(freeVarsEquality) && mSettings.onlyArgumentsInFormula()) {
				return mScript.term("true");
			}
			disjuncts.add(equality);
		}
		disjuncts.add(SmtUtils.binaryEquality(mScript, value1, value2));
		return SmtUtils.or(mScript, disjuncts);
	}

	private Term indexEqualityImpliesValueEquality(final ArrayIndex index, final ArrayIndex equal, final Term value1,
			final Term value2, final EqualityAnalysisResult invariants, final TransFormulaLR transformula) {
		final List<ArrayIndex> emptyList = Collections.emptyList();
		return indexEqualityInequalityImpliesValueEquality(index, equal, emptyList, value1, value2, invariants,
				transformula);
	}
}
