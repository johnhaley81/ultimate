/*
 * Copyright (C) 2015 Dirk Steinmetz
 * Copyright (C) 2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE TraceAbstraction plug-in.
 *
 * The ULTIMATE TraceAbstraction plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TraceAbstraction plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TraceAbstraction plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TraceAbstraction plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TraceAbstraction plug-in grant you additional permission
 * to convey the resulting work.
 */

package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.automata.Word;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedRun;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.ModifiableGlobalVariableManager;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IInternalAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.Settings;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.ProgramPoint;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.Activator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal.CFGInvariantsGenerator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal.ControlFlowGraph;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal.ControlFlowGraph.Location;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal.ControlFlowGraph.Transition;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal.IInvariantPatternProcessor;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal.IInvariantPatternProcessorFactory;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal.ILinearInequalityInvariantPatternStrategy;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal.LinearInequalityInvariantPatternProcessorFactory;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal.LocationIndependentLinearInequalityInvariantPatternStrategy;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.ISLPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singleTraceCheck.IInterpolantGenerator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singleTraceCheck.PredicateUnifier;

/**
 * Represents a map of invariants to a run, that has been generated using a {@link IInvariantPatternProcessor} on the
 * run-projected CFG.
 */
public final class PathInvariantsGenerator implements IInterpolantGenerator {

	private final NestedRun<? extends IAction, IPredicate> mRun;
	private final IPredicate mPrecondition;
	private final IPredicate mPostcondition;
	private final IPredicate[] mInterpolants;
	private final PredicateUnifier mPredicateUnifier;
	private final ILogger mLogger;
	private static boolean sUseLiveVariables = false;

	/**
	 * Creates a default factory.
	 *
	 * @param services
	 *            Service provider to use, for example for logging and timeouts
	 * @param storage
	 *            IToolchainstorage of the current Ultimate toolchain.
	 * @param predicateUnifier
	 *            the predicate unifier to unify final predicates with
	 * @param smtManager
	 *            the smt manager for constructing the default {@link IInvariantPatternProcessorFactory}
	 * @param simplicationTechnique
	 * @param xnfConversionTechnique
	 * @param axioms
	 * @return a default invariant pattern processor factory
	 */
	private static IInvariantPatternProcessorFactory<?> createDefaultFactory(final IUltimateServiceProvider services,
			final IToolchainStorage storage, final PredicateUnifier predicateUnifier, final ManagedScript smtManager,
			final boolean useNonlinerConstraints, final Settings solverSettings,
			final SimplificationTechnique simplicationTechnique, final XnfConversionTechnique xnfConversionTechnique,
			final Collection<Term> axioms) {
		final ILinearInequalityInvariantPatternStrategy strategy =
				new LocationIndependentLinearInequalityInvariantPatternStrategy(1, 1, 1, 1, 5);
		return new LinearInequalityInvariantPatternProcessorFactory(services, storage, predicateUnifier, smtManager,
				strategy, useNonlinerConstraints, solverSettings, simplicationTechnique, xnfConversionTechnique,
				axioms);
	}

	/**
	 * Generates a map of invariants to a given run, using an {@link IInvariantPatternProcessor} produced by the default
	 * {@link IInvariantPatternProcessorFactory} (with default settings).
	 *
	 * @param services
	 *            Service provider to use, for example for logging and timeouts
	 * @param storage
	 *            IToolchainstorage of the current Ultimate toolchain.
	 * @param run
	 *            an infeasible run to project into a CFG. Must only contain {@link ISLPredicate}s as states.
	 * @param precondition
	 *            the predicate to use for the first program point in the run
	 * @param postcondition
	 *            the predicate to use for the last program point in the run
	 * @param predicateUnifier
	 *            the predicate unifier to unify final predicates with
	 * @param smtManager
	 *            the smt manager for constructing the default {@link IInvariantPatternProcessorFactory}
	 * @param modGlobVarManager
	 *            reserved for future use.
	 * @param simplicationTechnique
	 * @param xnfConversionTechnique
	 * @param axioms
	 */
	public PathInvariantsGenerator(final IUltimateServiceProvider services, final IToolchainStorage storage,
			final NestedRun<? extends IAction, IPredicate> run, final IPredicate precondition,
			final IPredicate postcondition, final PredicateUnifier predicateUnifier, final ManagedScript smtManager,
			final ModifiableGlobalVariableManager modGlobVarManager, final boolean useNonlinerConstraints,
			final Settings solverSettings, final SimplificationTechnique simplicationTechnique,
			final XnfConversionTechnique xnfConversionTechnique, final Collection<Term> axioms) {
		this(services, run, precondition, postcondition, predicateUnifier, modGlobVarManager,
				createDefaultFactory(services, storage, predicateUnifier, smtManager, useNonlinerConstraints,
						solverSettings, simplicationTechnique, xnfConversionTechnique, axioms));
	}

	/**
	 * Generates a map of invariants to a given run, using an {@link IInvariantPatternProcessor} produced by a given
	 * {@link IInvariantPatternProcessorFactory}.
	 *
	 * @param services
	 *            Service provider to use, for example for logging and timeouts
	 * @param run
	 *            an infeasible run to project into a CFG. Must only contain {@link ISLPredicate}s as states.
	 * @param precondition
	 *            the predicate to use for the first program point in the run
	 * @param postcondition
	 *            the predicate to use for the last program point in the run
	 * @param predicateUnifier
	 *            the predicate unifier to unify final predicates with
	 * @param modGlobVarManager
	 *            reserved for future use.
	 * @param invPatternProcFactory
	 *            the factory to use with {@link CFGInvariantsGenerator}.
	 */
	public PathInvariantsGenerator(final IUltimateServiceProvider services,
			final NestedRun<? extends IAction, IPredicate> run, final IPredicate precondition,
			final IPredicate postcondition, final PredicateUnifier predicateUnifier,
			final ModifiableGlobalVariableManager modGlobVarManager,
			final IInvariantPatternProcessorFactory<?> invPatternProcFactory) {
		super();
		mRun = run;
		mPrecondition = precondition;
		mPostcondition = postcondition;
		mPredicateUnifier = predicateUnifier;

		final ILogger logService = services.getLoggingService().getLogger(Activator.PLUGIN_ID);

		mLogger = logService;

		logService.info("Started with a run of length " + mRun.getLength());

		// Project path to CFG
		final int len = mRun.getLength();
		final List<Location> locations = new ArrayList<>(len);
		final Map<ProgramPoint, Location> locationsForProgramPoint = new HashMap<ProgramPoint, Location>(len);
		final Collection<Transition> transitions = new ArrayList<>(len - 1);

		for (int i = 0; i < len; i++) {
			final ISLPredicate pred = (ISLPredicate) mRun.getStateAtPosition(i);
			final ProgramPoint programPoint = pred.getProgramPoint();

			Location location = locationsForProgramPoint.get(programPoint);
			if (location == null) {
				location = new Location(programPoint);
				locationsForProgramPoint.put(programPoint, location);
			}

			locations.add(location);

			if (i > 0) {
				if (!mRun.getWord().isInternalPosition(i - 1)) {
					throw new UnsupportedOperationException("interprocedural traces are not supported (yet)");
				}
				final UnmodifiableTransFormula transFormula =
						((IInternalAction) mRun.getSymbol(i - 1)).getTransformula();
				transitions.add(new Transition(transFormula, locations.get(i - 1), location));
			}
		}

		final ControlFlowGraph cfg =
				new ControlFlowGraph(locations.get(0), locations.get(len - 1), locations, transitions);
		logService.info("[PathInvariants] Built projected CFG, " + locations.size() + " states and "
				+ transitions.size() + " transitions.");

		// Generate invariants
		final CFGInvariantsGenerator generator = new CFGInvariantsGenerator(services, modGlobVarManager);
		final Map<ControlFlowGraph.Location, IPredicate> invariants;
		
		if (sUseLiveVariables) {
			invariants = null;
			// TODO: Compute the live variables and use them.
		} else {
			invariants = generator.generateInvariantsFromCFG(cfg, precondition, postcondition, invPatternProcFactory, false, null);
			logService.info("[PathInvariants] Generated invariant map.");
		}

		// Populate resulting array
		if (invariants != null) {
			mInterpolants = new IPredicate[len];
			for (int i = 0; i < len; i++) {
				mInterpolants[i] = invariants.get(locations.get(i));
				logService.info("[PathInvariants] Interpolant no " + i + " " + mInterpolants[i].toString());
			}
			logService.info("[PathInvariants] Invariants found and " + "processed.");
			logService.info("Got a Invariant map of length " + mInterpolants.length);
		} else {
			mInterpolants = null;
			logService.info("[PathInvariants] No invariants found.");
		}
	}

	@Override
	public Word<? extends IAction> getTrace() {
		return mRun.getWord();
	}

	@Override
	public IPredicate getPrecondition() {
		return mPrecondition;
	}

	@Override
	public IPredicate getPostcondition() {
		return mPostcondition;
	}

	@Override
	public Map<Integer, IPredicate> getPendingContexts() {
		throw new UnsupportedOperationException("Call/Return not supported yet");
	}

	@Override
	public PredicateUnifier getPredicateUnifier() {
		return mPredicateUnifier;
	}

	/**
	 * Returns a sequence of interpolants (see definition in {@link IInterpolantGenerator}) the trace which is
	 * mRun.getWord() with an additional property. If the ProgramPoint and position i and k coincide then the
	 * interpolants at position i and k coincide.
	 *
	 * @return sequence of interpolants according to the run provided in the constructor or null if no such sequence has
	 *         been found; without first interpolant (the precondition)
	 */
	@Override
	public IPredicate[] getInterpolants() {
		if (mInterpolants == null) {
			return null;
		}
		final IPredicate[] interpolantMapWithOutFirstInterpolant = new IPredicate[mInterpolants.length - 2];
		System.arraycopy(mInterpolants, 1, interpolantMapWithOutFirstInterpolant, 0, mInterpolants.length - 2);
		return interpolantMapWithOutFirstInterpolant;
	}
}
