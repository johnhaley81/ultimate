/*
 * Copyright (C) 2013-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2011-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
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
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.Automaton2UltimateModel;
import de.uni_freiburg.informatik.ultimate.automata.Word;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.IDoubleDeckerAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedRun;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWord;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.Accepts;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.Difference;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.IsEmpty;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.IsEmpty.SearchStrategy;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.PowersetDeterminizer;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.RemoveDeadEnds;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.RemoveUnreachable;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.oldapi.ComplementDD;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.oldapi.DeterminizeDD;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.oldapi.IOpWithDelayedDeadEndRemoval;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.oldapi.IntersectDD;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.senwa.DifferenceSenwa;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingCallTransition;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.ModifiableGlobalVariableManager;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IncrementalHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.Settings;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.SolverMode;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.TermTransferrer;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.MonolithicHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.ProgramPoint;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.RootNode;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.benchmark.LineCoverageCalculator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.builders.IInterpolantAutomatonBuilder;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.builders.InterpolantAutomatonBuilderFactory;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.transitionappender.BestApproximationDeterminizer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.transitionappender.DeterministicInterpolantAutomaton;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.transitionappender.NondeterministicInterpolantAutomaton;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.transitionappender.SelfloopDeterminizer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.CachingHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.CachingHoareTripleChecker_Map;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.EfficientHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.ISLPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.InductivityCheck;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.SmtManager;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences.Artifact;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences.InterpolantAutomatonEnhancement;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.AssertCodeBlockOrder;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.CounterexampleSearchStrategy;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.HoareAnnotationPositions;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.HoareTripleChecks;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.InterpolantAutomaton;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.InterpolationTechnique;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.Minimization;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.UnsatCores;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singleTraceCheck.InterpolantConsolidation;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singleTraceCheck.InterpolatingTraceChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singleTraceCheck.InterpolatingTraceCheckerCraig;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singleTraceCheck.InterpolatingTraceCheckerPathInvariantsWithFallback;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singleTraceCheck.PredicateUnifier;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singleTraceCheck.TraceCheckerSpWp;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.witnesschecking.WitnessProductAutomaton;
import de.uni_freiburg.informatik.ultimate.util.HistogramOfIterable;
import de.uni_freiburg.informatik.ultimate.util.ToolchainCanceledException;
import de.uni_freiburg.informatik.ultimate.witnessparser.graph.WitnessEdge;
import de.uni_freiburg.informatik.ultimate.witnessparser.graph.WitnessNode;

/**
 * Subclass of AbstractCegarLoop which provides all algorithms for checking safety (not termination).
 *
 * @author heizmann@informatik.uni-freiburg.de
 */
public class BasicCegarLoop extends AbstractCegarLoop {
	
	protected static final int MINIMIZE_EVERY_KTH_ITERATION = 10;
	private static final boolean DIFFERENCE_INSTEAD_OF_INTERSECTION = true;
	protected static final boolean REMOVE_DEAD_ENDS = true;
	protected static final boolean TRACE_HISTOGRAMM_BAILOUT = false;
	protected static final int MINIMIZATION_TIMEOUT = 1_000;
	
	protected HoareAnnotationFragments mHaf;
	
	protected final PredicateFactoryRefinement mStateFactoryForRefinement;
	protected final PredicateFactoryForInterpolantAutomata mPredicateFactoryInterpolantAutomata;
	protected final PredicateFactoryResultChecking mPredicateFactoryResultChecking;
	
	private final AbstractInterpretationRunner mAbsIntRunner;
	private final InterpolantAutomatonBuilderFactory mInterpolantAutomatonBuilderFactory;
	
	protected boolean mFallbackToFpIfInterprocedural = true;
	protected final InterpolationTechnique mInterpolation;
	protected final InterpolantAutomaton mInterpolantAutomatonConstructionProcedure;
	protected final UnsatCores mUnsatCores;
	protected final boolean mUseLiveVariables;
	
	protected final boolean mComputeHoareAnnotation;
	
	private final boolean mUseInterpolantConsolidation;
	
	protected final AssertCodeBlockOrder mAssertCodeBlocksIncrementally;
	
	private INestedWordAutomatonSimple<WitnessEdge, WitnessNode> mWitnessAutomaton;
	
	private final boolean mDoFaultLocalizationNonFlowSensitive;
	private final boolean mDoFaultLocalizationFlowSensitive;
	private HashSet<ProgramPoint> mHoareAnnotationPositions;
	
	protected final Collection<INestedWordAutomatonSimple<CodeBlock, IPredicate>> mStoredRawInterpolantAutomata;
	
	private final SearchStrategy mSearchStrategy;
	
	public BasicCegarLoop(final String name, final RootNode rootNode, final SmtManager smtManager,
			final TAPreferences taPrefs, final Collection<ProgramPoint> errorLocs,
			final InterpolationTechnique interpolation, final boolean computeHoareAnnotation,
			final IUltimateServiceProvider services, final IToolchainStorage storage) {
		
		super(services, storage, name, rootNode, smtManager, taPrefs, errorLocs,
				services.getLoggingService().getLogger(Activator.PLUGIN_ID));
		mUseInterpolantConsolidation = mServices.getPreferenceProvider(Activator.PLUGIN_ID)
				.getBoolean(TraceAbstractionPreferenceInitializer.LABEL_INTERPOLANTS_CONSOLIDATION);
		if (mFallbackToFpIfInterprocedural && rootNode.getRootAnnot().getEntryNodes().size() > 1) {
			if (interpolation == InterpolationTechnique.FPandBP) {
				mLogger.info("fallback from FPandBP to FP because CFG is interprocedural");
				mInterpolation = InterpolationTechnique.ForwardPredicates;
			} else {
				mInterpolation = interpolation;
			}
			if (mPref.interpolantAutomaton() == InterpolantAutomaton.TWOTRACK) {
				mInterpolantAutomatonConstructionProcedure = InterpolantAutomaton.CANONICAL;
			} else {
				mInterpolantAutomatonConstructionProcedure = mPref.interpolantAutomaton();
			}
		} else {
			mInterpolation = interpolation;
			mInterpolantAutomatonConstructionProcedure = mPref.interpolantAutomaton();
		}
		// InterpolationPreferenceChecker.check(Activator.s_PLUGIN_NAME, interpolation);
		mComputeHoareAnnotation = computeHoareAnnotation;
		if (mPref.getHoareAnnotationPositions() == HoareAnnotationPositions.LoopsAndPotentialCycles) {
			mHoareAnnotationPositions = new HashSet<>();
			mHoareAnnotationPositions.addAll(rootNode.getRootAnnot().getLoopLocations().keySet());
			// mHoareAnnotationPositions.addAll(rootNode.getRootAnnot().getExitNodes().values());
			mHoareAnnotationPositions.addAll(rootNode.getRootAnnot().getPotentialCycleProgramPoints());
		}
		mHaf = new HoareAnnotationFragments(mLogger, mHoareAnnotationPositions, mPref.getHoareAnnotationPositions());
		mStateFactoryForRefinement = new PredicateFactoryRefinement(mRootNode.getRootAnnot().getProgramPoints(),
				super.mSmtManager, mPref.computeHoareAnnotation(), mHaf, mHoareAnnotationPositions,
				mPref.getHoareAnnotationPositions());
		mPredicateFactoryInterpolantAutomata =
				new PredicateFactoryForInterpolantAutomata(super.mSmtManager, mPref.computeHoareAnnotation());
		
		mAssertCodeBlocksIncrementally = mServices.getPreferenceProvider(Activator.PLUGIN_ID).getEnum(
				TraceAbstractionPreferenceInitializer.LABEL_ASSERT_CODEBLOCKS_INCREMENTALLY,
				AssertCodeBlockOrder.class);
		
		mPredicateFactoryResultChecking = new PredicateFactoryResultChecking(smtManager);
		
		mCegarLoopBenchmark = new CegarLoopStatisticsGenerator();
		mCegarLoopBenchmark.start(CegarLoopStatisticsDefinitions.OverallTime.toString());
		
		final IPreferenceProvider prefs = mServices.getPreferenceProvider(Activator.PLUGIN_ID);
		mUnsatCores = prefs.getEnum(TraceAbstractionPreferenceInitializer.LABEL_UNSAT_CORES, UnsatCores.class);
		mUseLiveVariables = prefs.getBoolean(TraceAbstractionPreferenceInitializer.LABEL_LIVE_VARIABLES);
		mDoFaultLocalizationNonFlowSensitive = prefs.getBoolean(
				TraceAbstractionPreferenceInitializer.LABEL_ERROR_TRACE_RELEVANCE_ANALYSIS_NonFlowSensitive);
		mDoFaultLocalizationFlowSensitive = prefs
				.getBoolean(TraceAbstractionPreferenceInitializer.LABEL_ERROR_TRACE_RELEVANCE_ANALYSIS_FlowSensitive);
		
		mAbsIntRunner = new AbstractInterpretationRunner(services, mCegarLoopBenchmark, rootNode,
				mSimplificationTechnique, mXnfConversionTechnique, mSmtManager);
		mInterpolantAutomatonBuilderFactory = new InterpolantAutomatonBuilderFactory(mServices, mSmtManager,
				mPredicateFactoryInterpolantAutomata, mRootNode, mAbsIntRunner, taPrefs, mInterpolation,
				mInterpolantAutomatonConstructionProcedure, mCegarLoopBenchmark);
		
		mSearchStrategy = getSearchStrategy(prefs);
		
		mStoredRawInterpolantAutomata = checkStoreCounterExamples(mPref) ? new ArrayList<>() : null;
	}
	
	@Override
	protected void getInitialAbstraction() throws AutomataLibraryException {
		final CFG2NestedWordAutomaton cFG2NestedWordAutomaton =
				new CFG2NestedWordAutomaton(mServices, mPref.interprocedural(), super.mSmtManager, mLogger);
		
		mAbstraction = cFG2NestedWordAutomaton.getNestedWordAutomaton(super.mRootNode, mStateFactoryForRefinement,
				super.mErrorLocs);
		if (mPref.getHoareAnnotationPositions() == HoareAnnotationPositions.LoopsAndPotentialCycles) {
			final INestedWordAutomaton<CodeBlock, IPredicate> nwa =
					(INestedWordAutomaton<CodeBlock, IPredicate>) mAbstraction;
			for (final IPredicate pred : nwa.getStates()) {
				for (final OutgoingCallTransition<CodeBlock, IPredicate> trans : nwa.callSuccessors(pred)) {
					mHoareAnnotationPositions.add(((ISLPredicate) pred).getProgramPoint());
					mHoareAnnotationPositions.add(((ISLPredicate) trans.getSucc()).getProgramPoint());
				}
			}
		}
		if (mWitnessAutomaton != null) {
			final WitnessProductAutomaton wpa = new WitnessProductAutomaton(mServices,
					(INestedWordAutomatonSimple<CodeBlock, IPredicate>) mAbstraction, mWitnessAutomaton, mSmtManager);
			final INestedWordAutomatonSimple<CodeBlock, IPredicate> test =
					new RemoveUnreachable<>(new AutomataLibraryServices(mServices), wpa).getResult();
			mLogger.info("Full witness product has " + test.sizeInformation());
			mLogger.info(wpa.generateBadWitnessInformation());
			final LineCoverageCalculator origCoverage = new LineCoverageCalculator(mServices, mAbstraction);
			mAbstraction = new RemoveDeadEnds<>(new AutomataLibraryServices(mServices), wpa).getResult();
			new LineCoverageCalculator(mServices, mAbstraction, origCoverage).reportCoverage("Witness product");
		}
	}
	
	@Override
	protected boolean isAbstractionCorrect() throws AutomataOperationCanceledException {
		final INestedWordAutomatonSimple<CodeBlock, IPredicate> abstraction =
				(INestedWordAutomatonSimple<CodeBlock, IPredicate>) mAbstraction;
		mCounterexample =
				new IsEmpty<>(new AutomataLibraryServices(mServices), abstraction, mSearchStrategy).getNestedRun();
		
		if (mCounterexample == null) {
			return true;
		}
		
		mAbsIntRunner.generateFixpoints(mCounterexample, abstraction);
		
		if (mPref.dumpAutomata()) {
			mDumper.dumpNestedRun(mCounterexample);
		}
		// mRunAnalyzer = new RunAnalyzer(mCounterexample);
		mLogger.info("Found error trace");
		if (mLogger.isDebugEnabled()) {
			mLogger.debug(mCounterexample.getWord());
		}
		final HistogramOfIterable<CodeBlock> traceHistogram = new HistogramOfIterable<>(mCounterexample.getWord());
		mCegarLoopBenchmark.reportTraceHistogramMaximum(traceHistogram.getVisualizationArray()[0]);
		if (mLogger.isInfoEnabled()) {
			mLogger.info("trace histogram " + traceHistogram.toString());
		}
		if (TRACE_HISTOGRAMM_BAILOUT) {
			if (traceHistogram.getVisualizationArray()[0] > traceHistogram.getVisualizationArray().length) {
				final String message = "bailout by trace histogram " + traceHistogram.toString();
				final String taskDescription = "trying to verify (iteration " + mIteration + ")";
				throw new ToolchainCanceledException(message, getClass(), taskDescription);
			}
		}
		// s_Logger.info("Cutpoints: " + mRunAnalyzer.getCutpoints());
		// s_Logger.debug(mRunAnalyzer.getOccurence());
		return false;
	}
	
	@Override
	protected LBool isCounterexampleFeasible() {
		final PredicateUnifier predicateUnifier = new PredicateUnifier(mServices, mSmtManager.getManagedScript(),
				mSmtManager.getPredicateFactory(), mSmtManager.getBoogie2Smt().getBoogie2SmtSymbolTable(),
				mSimplificationTechnique, mXnfConversionTechnique);
		final IPredicate truePredicate = predicateUnifier.getTruePredicate();
		final IPredicate falsePredicate = predicateUnifier.getFalsePredicate();
		
		InterpolatingTraceChecker interpolatingTraceChecker = null;
		final SmtManager smtMangerTracechecks;
		if (mPref.useSeparateSolverForTracechecks()) {
			final String filename = mRootNode.getFilename() + "_TraceCheck_Iteration" + mIteration;
			final SolverMode solverMode = mPref.solverMode();
			final boolean fakeNonIncrementalSolver = mPref.fakeNonIncrementalSolver();
			final String commandExternalSolver = mPref.commandExternalSolver();
			final boolean dumpSmtScriptToFile = mPref.dumpSmtScriptToFile();
			final String pathOfDumpedScript = mPref.pathOfDumpedScript();
			final Settings solverSettings = SolverBuilder.constructSolverSettings(filename, solverMode,
					fakeNonIncrementalSolver, commandExternalSolver, dumpSmtScriptToFile, pathOfDumpedScript);
			final Script tcSolver = SolverBuilder.buildAndInitializeSolver(mServices, mToolchainStorage,
					mPref.solverMode(), solverSettings, false, false, mPref.logicForExternalSolver(),
					"TraceCheck_Iteration" + mIteration);
			smtMangerTracechecks = new SmtManager(tcSolver, mRootNode.getRootAnnot().getBoogie2SMT(),
					mRootNode.getRootAnnot().getModGlobVarManager(), mServices, false,
					mRootNode.getRootAnnot().getManagedScript(), mSimplificationTechnique, mXnfConversionTechnique);
			final TermTransferrer tt = new TermTransferrer(tcSolver);
			for (final Term axiom : mRootNode.getRootAnnot().getBoogie2SMT().getAxioms()) {
				tcSolver.assertTerm(tt.transform(axiom));
			}
		} else {
			smtMangerTracechecks = mSmtManager;
		}
		
		final LBool feasibility;
		switch (mInterpolation) {
		case Craig_NestedInterpolation:
		case Craig_TreeInterpolation: {
			interpolatingTraceChecker = new InterpolatingTraceCheckerCraig(truePredicate, falsePredicate,
					new TreeMap<Integer, IPredicate>(), NestedWord.nestedWord(mCounterexample.getWord()),
					mSmtManager.getManagedScript(), mRootNode.getRootAnnot().getModGlobVarManager(),
					mAssertCodeBlocksIncrementally, mServices, true, predicateUnifier, mInterpolation,
					smtMangerTracechecks.getManagedScript(), true, mXnfConversionTechnique, mSimplificationTechnique,
					mSmtManager.getBoogie2Smt().getBoogie2SmtSymbolTable());
		}
			break;
		case ForwardPredicates:
		case BackwardPredicates:
		case FPandBP:
			interpolatingTraceChecker = new TraceCheckerSpWp(truePredicate, falsePredicate,
					new TreeMap<Integer, IPredicate>(), NestedWord.nestedWord(mCounterexample.getWord()),
					mSmtManager.getManagedScript(), mRootNode.getRootAnnot().getModGlobVarManager(),
					mAssertCodeBlocksIncrementally, mUnsatCores, mUseLiveVariables, mServices, true, predicateUnifier,
					mInterpolation, smtMangerTracechecks.getManagedScript(), mXnfConversionTechnique,
					mSimplificationTechnique, mSmtManager.getBoogie2Smt().getBoogie2SmtSymbolTable());
			
			break;
		case PathInvariants: {
			final boolean useNonlinerConstraints = true;
			final boolean dumpSmtScriptToFile = mPref.dumpSmtScriptToFile();
			final String pathOfDumpedScript = mPref.pathOfDumpedScript();
			final String baseNameOfDumpedScript = "InVarSynth_" + mRootNode.getFilename() + "_Iteration" + mIteration;
			final String solverCommand;
			if (useNonlinerConstraints) {
				// solverCommand = "yices-smt2 --incremental";
				// solverCommand = "/home/matthias/ultimate/barcelogic/barcelogic-NIRA -tlimit 5";
				solverCommand = "z3 -smt2 -in SMTLIB2_COMPLIANT=true -t:42000";
				// solverCommand = "z3 -smt2 -in SMTLIB2_COMPLIANT=true -t:1000";
			} else {
				solverCommand = "yices-smt2 --incremental";
			}
			final boolean fakeNonIncrementalSolver = false;
			final Settings settings = new Settings(fakeNonIncrementalSolver, true, solverCommand, -1, null,
					dumpSmtScriptToFile, pathOfDumpedScript, baseNameOfDumpedScript);
			interpolatingTraceChecker =
					new InterpolatingTraceCheckerPathInvariantsWithFallback(truePredicate, falsePredicate,
							new TreeMap<Integer, IPredicate>(), (NestedRun<CodeBlock, IPredicate>) mCounterexample,
							mSmtManager.getManagedScript(), mModGlobVarManager, mAssertCodeBlocksIncrementally,
							mServices, mToolchainStorage, true, predicateUnifier, useNonlinerConstraints, settings,
							mXnfConversionTechnique, mSimplificationTechnique, mSmtManager.getBoogie2Smt().getAxioms(),
							mSmtManager.getBoogie2Smt().getBoogie2SmtSymbolTable());
		}
			break;
		default:
			throw new UnsupportedOperationException("unsupported interpolation");
		}
		mCegarLoopBenchmark.addTraceCheckerData(interpolatingTraceChecker.getTraceCheckerBenchmark());
		if (interpolatingTraceChecker.getToolchainCancelledExpection() != null) {
			throw interpolatingTraceChecker.getToolchainCancelledExpection();
		} else if (mPref.useSeparateSolverForTracechecks()) {
			smtMangerTracechecks.getScript().exit();
		}
		
		feasibility = interpolatingTraceChecker.isCorrect();
		if (feasibility != LBool.UNSAT) {
			mLogger.info("Counterexample might be feasible");
			final NestedWord<CodeBlock> counterexample = NestedWord.nestedWord(mCounterexample.getWord());
			String indentation = "";
			indentation += "  ";
			for (int j = 0; j < counterexample.length(); j++) {
				// String stmts =
				// counterexample.getSymbol(j).getPrettyPrintedStatements();
				// System.out.println(indentation + stmts);
				// s_Logger.info(indentation + stmts);
				if (counterexample.isCallPosition(j)) {
					indentation += "    ";
				}
				if (counterexample.isReturnPosition(j)) {
					indentation = indentation.substring(0, indentation.length() - 4);
				}
			}
			mRcfgProgramExecution = interpolatingTraceChecker.getRcfgProgramExecution();
			if ((mDoFaultLocalizationNonFlowSensitive || mDoFaultLocalizationFlowSensitive)
					&& feasibility == LBool.SAT) {
				final CFG2NestedWordAutomaton cFG2NestedWordAutomaton =
						new CFG2NestedWordAutomaton(mServices, mPref.interprocedural(), super.mSmtManager, mLogger);
				final INestedWordAutomaton<CodeBlock, IPredicate> cfg = cFG2NestedWordAutomaton
						.getNestedWordAutomaton(super.mRootNode, mStateFactoryForRefinement, super.mErrorLocs);
				final FlowSensitiveFaultLocalizer a = new FlowSensitiveFaultLocalizer(mCounterexample, cfg, mServices,
						mSmtManager, mModGlobVarManager, predicateUnifier, mDoFaultLocalizationNonFlowSensitive,
						mDoFaultLocalizationFlowSensitive, mSimplificationTechnique, mXnfConversionTechnique);
				mRcfgProgramExecution = mRcfgProgramExecution.addRelevanceInformation(a.getRelevanceInformation());
			}
			// s_Logger.info("Trace with values");
			// s_Logger.info(interpolatingTraceChecker.getRcfgProgramExecution());
		}
		mCegarLoopBenchmark.addTraceCheckerData(interpolatingTraceChecker.getTraceCheckerBenchmark());
		// mTraceCheckerBenchmark.aggregateBenchmarkData(interpolatingTraceChecker.getTraceCheckerBenchmark());
		mInterpolantGenerator = interpolatingTraceChecker;
		if (mUseInterpolantConsolidation) {
			try {
				final InterpolantConsolidation interpConsoli =
						new InterpolantConsolidation(truePredicate, falsePredicate, new TreeMap<Integer, IPredicate>(),
								NestedWord.nestedWord(mCounterexample.getWord()), mSmtManager,
								mRootNode.getRootAnnot().getModGlobVarManager(), mServices, mLogger, predicateUnifier,
								interpolatingTraceChecker, mPref);
				// Add benchmark data of interpolant consolidation
				mCegarLoopBenchmark
						.addInterpolationConsolidationData(interpConsoli.getInterpolantConsolidationBenchmarks());
				mInterpolantGenerator = interpConsoli;
			} catch (final AutomataOperationCanceledException e) {
				// Timeout
				e.printStackTrace();
				throw new AssertionError("react on timeout, not yet implemented");
			}
		}
		return feasibility;
	}
	
	@Override
	protected void constructInterpolantAutomaton() throws AutomataOperationCanceledException {
		
		final IInterpolantAutomatonBuilder<CodeBlock, IPredicate> builder =
				mInterpolantAutomatonBuilderFactory.createBuilder(mAbstraction, mInterpolantGenerator, mCounterexample);
		mInterpolAutomaton = builder.getResult();
		
		assert accepts(mServices, mInterpolAutomaton, mCounterexample.getWord()) : "Interpolant automaton broken!";
		assert new InductivityCheck(mServices, mInterpolAutomaton, false, true,
				new IncrementalHoareTripleChecker(mRootNode.getRootAnnot().getManagedScript(), mModGlobVarManager))
						.getResult();
	}
	
	@Override
	protected boolean refineAbstraction() throws AutomataLibraryException {
		final PredicateUnifier predUnifier = mInterpolantGenerator.getPredicateUnifier();
		if (mAbsIntRunner.hasShownInfeasibility()) {
			return mAbsIntRunner.refine(predUnifier, mInterpolAutomaton, mCounterexample,
					this::refineWithGivenAutomaton);
		}
		mAbsIntRunner.refineAnyways(mInterpolantGenerator, (INestedWordAutomaton<CodeBlock, IPredicate>) mAbstraction,
				mCounterexample, this::refineWithGivenAutomaton);
		return refineWithGivenAutomaton(mInterpolAutomaton, predUnifier);
	}
	
	private boolean refineWithGivenAutomaton(final NestedWordAutomaton<CodeBlock, IPredicate> interpolAutomaton,
			final PredicateUnifier predicateUnifier)
			throws AssertionError, AutomataOperationCanceledException, AutomataLibraryException {
		mStateFactoryForRefinement.setIteration(super.mIteration);
		// howDifferentAreInterpolants(interpolAutomaton.getStates());
		
		mCegarLoopBenchmark.start(CegarLoopStatisticsDefinitions.AutomataDifference.toString());
		final boolean explointSigmaStarConcatOfIA = !mComputeHoareAnnotation;
		
		final INestedWordAutomaton<CodeBlock, IPredicate> oldAbstraction =
				(INestedWordAutomaton<CodeBlock, IPredicate>) mAbstraction;
		// Map<IPredicate, Set<IPredicate>> removedDoubleDeckers = null;
		// Map<IPredicate, IPredicate> context2entry = null;
		
		final CachingHoareTripleChecker htc;
		if (mInterpolantGenerator instanceof InterpolantConsolidation) {
			htc = ((InterpolantConsolidation) mInterpolantGenerator).getHoareTripleChecker();
		} else {
			final IHoareTripleChecker ehtc = getEfficientHoareTripleChecker(mServices, mPref.getHoareTripleChecks(),
					mSmtManager, mModGlobVarManager, mInterpolantGenerator.getPredicateUnifier());
			htc = new CachingHoareTripleChecker_Map(mServices, ehtc, mInterpolantGenerator.getPredicateUnifier());
		}
		try {
			if (DIFFERENCE_INSTEAD_OF_INTERSECTION) {
				mLogger.debug("Start constructing difference");
				// assert(oldAbstraction.getStateFactory() ==
				// interpolAutomaton.getStateFactory());
				
				IOpWithDelayedDeadEndRemoval<CodeBlock, IPredicate> diff;
				
				switch (mPref.interpolantAutomatonEnhancement()) {
				case NONE:
					final PowersetDeterminizer<CodeBlock, IPredicate> psd =
							new PowersetDeterminizer<>(interpolAutomaton, true, mPredicateFactoryInterpolantAutomata);
					if (mPref.differenceSenwa()) {
						diff = new DifferenceSenwa<>(new AutomataLibraryServices(mServices), oldAbstraction,
								interpolAutomaton, psd, false);
					} else {
						diff = new Difference<>(new AutomataLibraryServices(mServices), oldAbstraction,
								interpolAutomaton, psd, mStateFactoryForRefinement, explointSigmaStarConcatOfIA);
					}
					break;
				case BESTAPPROXIMATION_DEPRECATED:
					final BestApproximationDeterminizer bed = new BestApproximationDeterminizer(mSmtManager, mPref,
							interpolAutomaton, mPredicateFactoryInterpolantAutomata);
					diff = new Difference<>(new AutomataLibraryServices(mServices), oldAbstraction, interpolAutomaton,
							bed, mStateFactoryForRefinement, explointSigmaStarConcatOfIA);
					
					mLogger.info("Internal Transitions: " + bed.getmAnswerInternalAutomaton()
							+ " answers given by automaton " + bed.getmAnswerInternalCache()
							+ " answers given by cache " + bed.getmAnswerInternalSolver() + " answers given by solver");
					mLogger.info("Call Transitions: " + bed.getmAnswerCallAutomaton() + " answers given by automaton "
							+ bed.getmAnswerCallCache() + " answers given by cache " + bed.getmAnswerCallSolver()
							+ " answers given by solver");
					mLogger.info("Return Transitions: " + bed.getmAnswerReturnAutomaton()
							+ " answers given by automaton " + bed.getmAnswerReturnCache() + " answers given by cache "
							+ bed.getmAnswerReturnSolver() + " answers given by solver");
					break;
				case SELFLOOP:
					final SelfloopDeterminizer sed = new SelfloopDeterminizer(mSmtManager, mPref, interpolAutomaton,
							mPredicateFactoryInterpolantAutomata);
					if (mPref.differenceSenwa()) {
						diff = new DifferenceSenwa<>(new AutomataLibraryServices(mServices), oldAbstraction,
								interpolAutomaton, sed, false);
					} else {
						diff = new Difference<>(new AutomataLibraryServices(mServices), oldAbstraction,
								interpolAutomaton, sed, mStateFactoryForRefinement, explointSigmaStarConcatOfIA);
					}
					mLogger.info("Internal Selfloops: " + sed.mInternalSelfloop + " Internal NonSelfloops "
							+ sed.mInternalNonSelfloop);
					mLogger.info("Call Selfloops: " + sed.mCallSelfloop + " Call NonSelfloops " + sed.mCallNonSelfloop);
					mLogger.info("Return Selfloops: " + sed.mReturnSelfloop + " Return NonSelfloops "
							+ sed.mReturnNonSelfloop);
					break;
				case PREDICATE_ABSTRACTION:
				case PREDICATE_ABSTRACTION_CONSERVATIVE:
				case PREDICATE_ABSTRACTION_CANNIBALIZE:
					if (mPref.differenceSenwa()) {
						throw new UnsupportedOperationException();
					} else {
						final boolean conservativeSuccessorCandidateSelection = mPref
								.interpolantAutomatonEnhancement() == InterpolantAutomatonEnhancement.PREDICATE_ABSTRACTION_CONSERVATIVE;
						final boolean cannibalize = mPref
								.interpolantAutomatonEnhancement() == InterpolantAutomatonEnhancement.PREDICATE_ABSTRACTION_CANNIBALIZE;
						final DeterministicInterpolantAutomaton determinized =
								new DeterministicInterpolantAutomaton(mServices, mSmtManager, mModGlobVarManager, htc,
										oldAbstraction, interpolAutomaton, mInterpolantGenerator.getPredicateUnifier(),
										mLogger, conservativeSuccessorCandidateSelection, cannibalize);
						// NondeterministicInterpolantAutomaton determinized =
						// new NondeterministicInterpolantAutomaton(
						// mServices, mSmtManager, mModGlobVarManager, htc,
						// oldAbstraction, interpolAutomaton,
						// mTraceChecker.getPredicateUnifier(), mLogger);
						// ComplementDeterministicNwa<CodeBlock, IPredicate>
						// cdnwa = new ComplementDeterministicNwa<>(dia);
						final PowersetDeterminizer<CodeBlock, IPredicate> psd2 =
								new PowersetDeterminizer<>(determinized, true, mPredicateFactoryInterpolantAutomata);
						try {
							diff = new Difference<>(new AutomataLibraryServices(mServices), oldAbstraction,
									determinized, psd2, mStateFactoryForRefinement, explointSigmaStarConcatOfIA);
						} catch (final AutomataOperationCanceledException aoce) {
							throw aoce;
						} finally {
							determinized.switchToReadonlyMode();
						}
						final INestedWordAutomaton<CodeBlock, IPredicate> completelyBuiltInterpolantAutomaton =
								new RemoveUnreachable<>(new AutomataLibraryServices(mServices), determinized)
										.getResult();
						if (mPref.dumpAutomata()) {
							final String filename = "EnhancedInterpolantAutomaton_Iteration" + mIteration;
							super.writeAutomatonToFile(completelyBuiltInterpolantAutomaton, filename);
						}
						if (mAbsIntRunner.isDisabled()) {
							// check only if AI did not run
							final boolean ctxAccepted = new Accepts<>(new AutomataLibraryServices(mServices),
									completelyBuiltInterpolantAutomaton,
									(NestedWord<CodeBlock>) mCounterexample.getWord(), true, false).getResult();
							if (!ctxAccepted) {
								throw new AssertionError("enhanced interpolant automaton in iteration " + mIteration
										+ " broken: counterexample of length " + mCounterexample.getLength()
										+ " not accepted");
							}
						}
						assert new InductivityCheck(mServices, completelyBuiltInterpolantAutomaton, false, true,
								new IncrementalHoareTripleChecker(mRootNode.getRootAnnot().getManagedScript(),
										mModGlobVarManager)).getResult();
					}
					break;
				case EAGER:
				case NO_SECOND_CHANCE:
				case EAGER_CONSERVATIVE: {
					final boolean conservativeSuccessorCandidateSelection = mPref
							.interpolantAutomatonEnhancement() == InterpolantAutomatonEnhancement.EAGER_CONSERVATIVE;
					final boolean secondChance =
							mPref.interpolantAutomatonEnhancement() != InterpolantAutomatonEnhancement.NO_SECOND_CHANCE;
					final NondeterministicInterpolantAutomaton nondet =
							new NondeterministicInterpolantAutomaton(mServices, mSmtManager, mModGlobVarManager, htc,
									(INestedWordAutomatonSimple<CodeBlock, IPredicate>) mAbstraction, interpolAutomaton,
									predicateUnifier, mLogger, conservativeSuccessorCandidateSelection, secondChance);
					final PowersetDeterminizer<CodeBlock, IPredicate> psd2 =
							new PowersetDeterminizer<>(nondet, true, mPredicateFactoryInterpolantAutomata);
					try {
						diff = new Difference<>(new AutomataLibraryServices(mServices), oldAbstraction, nondet, psd2,
								mStateFactoryForRefinement, explointSigmaStarConcatOfIA);
					} catch (final AutomataOperationCanceledException aoce) {
						throw aoce;
					} finally {
						nondet.switchToReadonlyMode();
					}
					final INestedWordAutomaton<CodeBlock, IPredicate> test =
							new RemoveUnreachable<>(new AutomataLibraryServices(mServices), nondet).getResult();
					if (mPref.dumpAutomata()) {
						final String filename = "EnhancedInterpolantAutomaton_Iteration" + mIteration;
						super.writeAutomatonToFile(test, filename);
					}
					final boolean ctxAccepted = new Accepts<>(new AutomataLibraryServices(mServices), test,
							(NestedWord<CodeBlock>) mCounterexample.getWord(), true, false).getResult();
					if (!ctxAccepted) {
						throw new AssertionError("enhanced interpolant automaton in iteration " + mIteration
								+ " broken: counterexample of length " + mCounterexample.getLength() + " not accepted");
					}
					assert new InductivityCheck(mServices, test, false, true, new IncrementalHoareTripleChecker(
							mRootNode.getRootAnnot().getManagedScript(), mModGlobVarManager)).getResult();
				}
					break;
				default:
					throw new UnsupportedOperationException();
				}
				if (REMOVE_DEAD_ENDS) {
					if (mComputeHoareAnnotation) {
						final Difference<CodeBlock, IPredicate> difference = (Difference<CodeBlock, IPredicate>) diff;
						mHaf.updateOnIntersection(difference.getFst2snd2res(), difference.getResult());
					}
					diff.removeDeadEnds();
					if (mComputeHoareAnnotation) {
						mHaf.addDeadEndDoubleDeckers(diff);
					}
				}
				
				mAbstraction = diff.getResult();
				// mDeadEndRemovalTime = diff.getDeadEndRemovalTime();
				
				if (mPref.dumpAutomata()) {
					final String filename = "InterpolantAutomaton_Iteration" + mIteration;
					super.writeAutomatonToFile(interpolAutomaton, filename);
				}
			} else {// complement and intersection instead of difference
				
				final INestedWordAutomatonSimple<CodeBlock, IPredicate> dia =
						determinizeInterpolantAutomaton(interpolAutomaton);
				
				mLogger.debug("Start complementation");
				final INestedWordAutomatonSimple<CodeBlock, IPredicate> nia =
						new ComplementDD<>(new AutomataLibraryServices(mServices), mPredicateFactoryInterpolantAutomata,
								dia).getResult();
				assert !accepts(mServices, nia, mCounterexample.getWord());
				mLogger.info("Complemented interpolant automaton has " + nia.size() + " states");
				
				if (mIteration <= mPref.watchIteration() && mPref.artifact() == Artifact.NEG_INTERPOLANT_AUTOMATON) {
					mArtifactAutomaton = nia;
				}
				assert oldAbstraction.getStateFactory() == interpolAutomaton.getStateFactory();
				mLogger.debug("Start intersection");
				final IntersectDD<CodeBlock, IPredicate> intersect =
						new IntersectDD<>(new AutomataLibraryServices(mServices), false, oldAbstraction, nia);
				if (REMOVE_DEAD_ENDS && mComputeHoareAnnotation) {
					throw new AssertionError("not supported any more");
					// mHaf.wipeReplacedContexts();
					// mHaf.addDeadEndDoubleDeckers(intersect);
				}
				if (REMOVE_DEAD_ENDS) {
					intersect.removeDeadEnds();
				}
				mAbstraction = intersect.getResult();
			}
		} finally {
			mCegarLoopBenchmark.addEdgeCheckerData(htc.getEdgeCheckerBenchmark());
			mCegarLoopBenchmark.addPredicateUnifierData(predicateUnifier.getPredicateUnifierBenchmark());
			mCegarLoopBenchmark.stop(CegarLoopStatisticsDefinitions.AutomataDifference.toString());
		}
		
		// if(mRemoveDeadEnds && mComputeHoareAnnotation) {
		// mHaf.wipeReplacedContexts();
		// mHaf.addDoubleDeckers(removedDoubleDeckers,
		// oldAbstraction.getEmptyStackState());
		// mHaf.addContext2Entry(context2entry);
		// }
		
		// (new RemoveDeadEnds<CodeBlock,
		// IPredicate>((INestedWordAutomatonOldApi<CodeBlock, IPredicate>)
		// mAbstraction)).getResult();
		mLogger.info(predicateUnifier.collectPredicateUnifierStatistics());
		
		final Minimization minimization = mPref.minimize();
		switch (minimization) {
		case NONE:
			break;
		case DFA_HOPCROFT_LISTS:
		case DFA_HOPCROFT_ARRAYS:
		case MINIMIZE_SEVPA:
		case SHRINK_NWA:
		case NWA_MAX_SAT:
		case NWA_MAX_SAT2:
		case RAQ_DIRECT_SIMULATION:
		case RAQ_DIRECT_SIMULATION_B:
		case NWA_COMBINATOR_PATTERN:
		case NWA_COMBINATOR_EVERY_KTH:
		case NWA_OVERAPPROXIMATION:
		case NWA_COMBINATOR_MULTI_DEFAULT:
		case NWA_COMBINATOR_MULTI_SIMULATION:
			minimizeAbstraction(mStateFactoryForRefinement, mPredicateFactoryResultChecking, minimization);
			break;
		default:
			throw new AssertionError();
		}
		
		// MinimizeSevpa<CodeBlock, Predicate> sev = new
		// MinimizeSevpa<CodeBlock, Predicate>(abstraction);
		// new MinimizeSevpa<CodeBlock, Predicate>.Partitioning(0);
		
		// for (Predicate p : a.getStates()) {
		// assert a.numberOfOutgoingInternalTransitions(p) <= 2 : p + " has "
		// +a.numberOfOutgoingInternalTransitions(p);
		// assert a.numberOfIncomingInternalTransitions(p) <= 25 : p + " has "
		// +a.numberOfIncomingInternalTransitions(p);
		// }
		final boolean stillAccepted = new Accepts<>(new AutomataLibraryServices(mServices),
				(INestedWordAutomatonSimple<CodeBlock, IPredicate>) mAbstraction,
				(NestedWord<CodeBlock>) mCounterexample.getWord()).getResult();
		return !stillAccepted;
	}
	
	public static IHoareTripleChecker getEfficientHoareTripleChecker(final IUltimateServiceProvider services,
			final HoareTripleChecks hoareTripleChecks, final SmtManager smtManager,
			final ModifiableGlobalVariableManager modGlobVarManager, final PredicateUnifier predicateUnifier)
			throws AssertionError {
		final IHoareTripleChecker solverHtc;
		switch (hoareTripleChecks) {
		case MONOLITHIC:
			solverHtc = new MonolithicHoareTripleChecker(smtManager.getManagedScript(), modGlobVarManager);
			break;
		case INCREMENTAL:
			solverHtc = new IncrementalHoareTripleChecker(smtManager.getManagedScript(), modGlobVarManager);
			break;
		default:
			throw new AssertionError("unknown value");
		}
		final IHoareTripleChecker htc =
				new EfficientHoareTripleChecker(solverHtc, modGlobVarManager, predicateUnifier, smtManager);
		return htc;
	}
	
	/**
	 * Automata theoretic minimization of the automaton stored in mAbstraction. Expects that mAbstraction does not have
	 * dead ends.
	 *
	 * @param predicateFactoryRefinement
	 *            PredicateFactory for the construction of the new (minimized) abstraction.
	 * @param resultCheckPredFac
	 *            PredicateFactory used for auxiliary automata used for checking correctness of the result (if
	 *            assertions are enabled).
	 */
	protected void minimizeAbstraction(final PredicateFactoryForInterpolantAutomata predicateFactoryRefinement,
			final PredicateFactoryResultChecking resultCheckPredFac, final Minimization minimization)
			throws AutomataOperationCanceledException, AutomataLibraryException, AssertionError {
		if (mPref.dumpAutomata()) {
			final String filename = mRootNode.getFilename() + "_DiffAutomatonBeforeMinimization_Iteration" + mIteration;
			super.writeAutomatonToFile(mAbstraction, filename);
		}
		mCegarLoopBenchmark.start(CegarLoopStatisticsDefinitions.AutomataMinimizationTime.toString());
		final Function<ISLPredicate, ProgramPoint> lcsProvider = x -> x.getProgramPoint();
		try {
			final AutomataMinimization<ProgramPoint, ISLPredicate> am = new AutomataMinimization<>(mServices,
					(INestedWordAutomaton<CodeBlock, IPredicate>) mAbstraction, minimization, mComputeHoareAnnotation,
					mIteration, predicateFactoryRefinement, MINIMIZE_EVERY_KTH_ITERATION, mStoredRawInterpolantAutomata,
					mInterpolAutomaton, MINIMIZATION_TIMEOUT, resultCheckPredFac, lcsProvider);
			final boolean wasMinimized = am.wasMinimized();
			if (am.wasMinimizationAttempted()) {
				mCegarLoopBenchmark.reportMinimizationAttempt();
			}

			if (wasMinimized) {
				// postprocessing after minimization
				final IDoubleDeckerAutomaton<CodeBlock, IPredicate> newAbstraction = am.getMinimizedAutomaton();
				
				// extract Hoare annotation
				if (mComputeHoareAnnotation) {
					final Map<IPredicate, IPredicate> oldState2newState = am.getOldState2newStateMapping();
					if (oldState2newState == null) {
						throw new AssertionError("Hoare annotation and " + minimization + " incompatible");
					}
					mHaf.updateOnMinimization(oldState2newState, newAbstraction);
				}
				
				// statistics
				final int oldSize = mAbstraction.size();
				final int newSize = newAbstraction.size();
				assert oldSize == 0 || oldSize >= newSize : "Minimization increased state space";
				mCegarLoopBenchmark.announceStatesRemovedByMinimization(oldSize - newSize);

				// use result
				mAbstraction = newAbstraction;
			}
		} finally {
			mCegarLoopBenchmark.stop(CegarLoopStatisticsDefinitions.AutomataMinimizationTime.toString());
		}
	}
	
	// private static Collection<Set<IPredicate>>
	// computePartitionDistinguishFinalNonFinal(
	// INestedWordAutomatonOldApi<CodeBlock, IPredicate> automaton, ILogger
	// logger) {
	// logger.info("Start computation of initial partition.");
	// Collection<IPredicate> states = automaton.getStates();
	// Map<ProgramPoint, Set<IPredicate>> pp2pFin = new HashMap<ProgramPoint,
	// Set<IPredicate>>();
	// Map<ProgramPoint, Set<IPredicate>> pp2pNonFin = new HashMap<ProgramPoint,
	// Set<IPredicate>>();
	// for (IPredicate p : states) {
	// ISLPredicate sp = (ISLPredicate) p;
	// if (automaton.isFinal(sp)) {
	// pigeonHole(pp2pFin, sp);
	// } else {
	// pigeonHole(pp2pNonFin, sp);
	// }
	//
	// }
	// Collection<Set<IPredicate>> partition = new ArrayList<Set<IPredicate>>();
	// for (ProgramPoint pp : pp2pFin.keySet()) {
	// Set<IPredicate> statesWithSamePP = pp2pFin.get(pp);
	// partition.add(statesWithSamePP);
	// }
	// for (ProgramPoint pp : pp2pNonFin.keySet()) {
	// Set<IPredicate> statesWithSamePP = pp2pNonFin.get(pp);
	// partition.add(statesWithSamePP);
	// }
	// logger.info("Finished computation of initial partition.");
	// return partition;
	// }
	
	// private boolean eachStateIsFinal(Collection<IPredicate> states,
	// INestedWordAutomatonOldApi<CodeBlock, IPredicate> automaton) {
	// boolean result = true;
	// for (IPredicate p : states) {
	// result &= automaton.isFinal(p);
	// }
	// return result;
	// }
	
	protected INestedWordAutomaton<CodeBlock, IPredicate>
			determinizeInterpolantAutomaton(final INestedWordAutomaton<CodeBlock, IPredicate> interpolAutomaton)
					throws AutomataOperationCanceledException {
		mLogger.debug("Start determinization");
		INestedWordAutomaton<CodeBlock, IPredicate> dia;
		switch (mPref.interpolantAutomatonEnhancement()) {
		case NONE:
			final PowersetDeterminizer<CodeBlock, IPredicate> psd =
					new PowersetDeterminizer<>(interpolAutomaton, true, mPredicateFactoryInterpolantAutomata);
			final DeterminizeDD<CodeBlock, IPredicate> dabps =
					new DeterminizeDD<>(new AutomataLibraryServices(mServices), interpolAutomaton, psd);
			dia = dabps.getResult();
			break;
		case BESTAPPROXIMATION_DEPRECATED:
			final BestApproximationDeterminizer bed = new BestApproximationDeterminizer(mSmtManager, mPref,
					(NestedWordAutomaton<CodeBlock, IPredicate>) interpolAutomaton,
					mPredicateFactoryInterpolantAutomata);
			final DeterminizeDD<CodeBlock, IPredicate> dab =
					new DeterminizeDD<>(new AutomataLibraryServices(mServices), interpolAutomaton, bed);
			dia = dab.getResult();
			break;
		case SELFLOOP:
			final SelfloopDeterminizer sed = new SelfloopDeterminizer(mSmtManager, mPref, interpolAutomaton,
					mPredicateFactoryInterpolantAutomata);
			final DeterminizeDD<CodeBlock, IPredicate> dabsl =
					new DeterminizeDD<>(new AutomataLibraryServices(mServices), interpolAutomaton, sed);
			dia = dabsl.getResult();
			break;
		default:
			throw new UnsupportedOperationException();
		}
		
		if (mComputeHoareAnnotation) {
			assert new InductivityCheck(mServices, dia, false, true,
					new IncrementalHoareTripleChecker(mRootNode.getRootAnnot().getManagedScript(), mModGlobVarManager))
							.getResult() : "Not inductive";
		}
		if (mPref.dumpAutomata()) {
			final String filename = "InterpolantAutomatonDeterminized_Iteration" + mIteration;
			writeAutomatonToFile(dia, filename);
		}
		assert accepts(mServices, dia, mCounterexample.getWord());
		mLogger.debug("Sucessfully determinized");
		return dia;
	}
	
	@Override
	protected void computeCFGHoareAnnotation() {
		if (mSmtManager.isLocked()) {
			throw new AssertionError("SMTManager must not be locked at the beginning of Hoare annotation computation");
		}
		final INestedWordAutomaton<CodeBlock, IPredicate> abstraction =
				(INestedWordAutomaton<CodeBlock, IPredicate>) mAbstraction;
		new HoareAnnotationExtractor(mServices, abstraction, mHaf);
		new HoareAnnotationWriter(mRootNode.getRootAnnot(), mSmtManager, mHaf, mServices, mSimplificationTechnique,
				mXnfConversionTechnique).addHoareAnnotationToCFG();
	}
	
	@Override
	public IElement getArtifact() {
		if (mPref.artifact() == Artifact.ABSTRACTION || mPref.artifact() == Artifact.INTERPOLANT_AUTOMATON
				|| mPref.artifact() == Artifact.NEG_INTERPOLANT_AUTOMATON) {
			
			if (mArtifactAutomaton == null) {
				mLogger.warn("Preferred Artifact not available," + " visualizing the RCFG instead");
				return mRootNode;
			}
			try {
				return Automaton2UltimateModel.ultimateModel(new AutomataLibraryServices(mServices),
						mArtifactAutomaton);
			} catch (final AutomataOperationCanceledException e) {
				return null;
			}
		} else if (mPref.artifact() == Artifact.RCFG) {
			return mRootNode;
		} else {
			throw new IllegalArgumentException();
		}
	}
	
	// private void howDifferentAreInterpolants(final Collection<IPredicate> predicates) {
	// int implications = 0;
	// int biimplications = 0;
	// final IPredicate[] array = predicates.toArray(new IPredicate[0]);
	// for (int i = 0; i < array.length; i++) {
	// for (int j = 0; j < i; j++) {
	// final boolean implies = (mSmtManager.isCovered(array[i], array[j]) == LBool.UNSAT);
	// final boolean explies = (mSmtManager.isCovered(array[j], array[i]) == LBool.UNSAT);
	// if (implies && explies) {
	// biimplications++;
	// } else if (implies ^ explies) {
	// implications++;
	// }
	//
	// }
	// }
	// mLogger.warn(
	// array.length + "Interpolants. " + implications + " implications " + biimplications + " biimplications");
	// }
	
	protected static boolean accepts(final IUltimateServiceProvider services,
			final INestedWordAutomatonSimple<CodeBlock, IPredicate> nia, final Word<CodeBlock> word)
			throws AutomataOperationCanceledException {
		try {
			return new Accepts<>(new AutomataLibraryServices(services), nia, NestedWord.nestedWord(word), false, false)
					.getResult();
		} catch (final AutomataOperationCanceledException e) {
			throw e;
		} catch (final AutomataLibraryException e) {
			throw new AssertionError(e);
		}
	}
	
	public CegarLoopStatisticsGenerator getCegarLoopBenchmark() {
		return mCegarLoopBenchmark;
	}
	
	/**
	 * method called at the end of the cegar loop
	 */
	public void finish() {
		// do nothing
	}
	
	public void setWitnessAutomaton(final INestedWordAutomatonSimple<WitnessEdge, WitnessNode> witnessAutomaton) {
		mWitnessAutomaton = witnessAutomaton;
		
	}
	
	private final static boolean checkStoreCounterExamples(final TAPreferences pref) {
		return pref.minimize() == Minimization.NWA_OVERAPPROXIMATION;
	}
	
	private static SearchStrategy getSearchStrategy(final IPreferenceProvider mPrefs) {
		switch (mPrefs.getEnum(TraceAbstractionPreferenceInitializer.LABEL_COUNTEREXAMPLE_SEARCH_STRATEGY,
				CounterexampleSearchStrategy.class)) {
		case BFS:
			return SearchStrategy.BFS;
		case DFS:
			return SearchStrategy.DFS;
		default:
			throw new IllegalArgumentException();
		}
	}
}
