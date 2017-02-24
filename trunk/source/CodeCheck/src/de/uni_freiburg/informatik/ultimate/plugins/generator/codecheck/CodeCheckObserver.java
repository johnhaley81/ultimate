/*
 * Copyright (C) 2013-2015 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2013-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2013-2015 Mostafa Mahmoud Amin
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE CodeCheck plug-in.
 *
 * The ULTIMATE CodeCheck plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE CodeCheck plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE CodeCheck plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE CodeCheck plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE CodeCheck plug-in grant you additional permission
 * to convey the resulting work.
 */

package de.uni_freiburg.informatik.ultimate.plugins.generator.codecheck;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedRun;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWord;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.WitnessInvariant;
import de.uni_freiburg.informatik.ultimate.core.lib.results.AllSpecificationsHoldResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.BenchmarkResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.CounterExampleResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.GenericResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.InvariantResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.PositiveResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.ResultUtil;
import de.uni_freiburg.informatik.ultimate.core.lib.results.TimeoutResultAtElement;
import de.uni_freiburg.informatik.ultimate.core.lib.results.UnprovabilityReason;
import de.uni_freiburg.informatik.ultimate.core.lib.results.UnprovableResult;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.core.model.models.ModelType;
import de.uni_freiburg.informatik.ultimate.core.model.observers.IUnmanagedObserver;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResult;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResultWithSeverity.Severity;
import de.uni_freiburg.informatik.ultimate.core.model.services.IBacktranslationService;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IProgressAwareTimer;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IcfgUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgElement;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgReturnTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.Settings;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.SolverMode;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.TermTransferrer;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.MonolithicHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.AbstractInterpreter;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.IAbstractInterpretationResult;
import de.uni_freiburg.informatik.ultimate.plugins.generator.appgraph.AnnotatedProgramPoint;
import de.uni_freiburg.informatik.ultimate.plugins.generator.appgraph.AppEdge;
import de.uni_freiburg.informatik.ultimate.plugins.generator.appgraph.AppHyperEdge;
import de.uni_freiburg.informatik.ultimate.plugins.generator.appgraph.ImpRootNode;
import de.uni_freiburg.informatik.ultimate.plugins.generator.appgraph.RCFG2AnnotatedRCFG;
import de.uni_freiburg.informatik.ultimate.plugins.generator.codecheck.preferences.CodeCheckPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.codecheck.preferences.CodeCheckPreferenceInitializer.Checker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.codecheck.preferences.CodeCheckPreferenceInitializer.EdgeCheckOptimization;
import de.uni_freiburg.informatik.ultimate.plugins.generator.codecheck.preferences.CodeCheckPreferenceInitializer.PredicateUnification;
import de.uni_freiburg.informatik.ultimate.plugins.generator.codecheck.preferences.CodeCheckPreferenceInitializer.RedirectionStrategy;
import de.uni_freiburg.informatik.ultimate.plugins.generator.emptinesscheck.IEmptinessCheck;
import de.uni_freiburg.informatik.ultimate.plugins.generator.emptinesscheck.NWAEmptinessCheck;
import de.uni_freiburg.informatik.ultimate.plugins.generator.impulse.ImpulseChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.kojak.UltimateChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Summary;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.util.IcfgProgramExecution;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.CegarLoopStatisticsDefinitions;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.CegarLoopStatisticsGenerator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.PredicateFactory;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.AssertCodeBlockOrder;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.InterpolationTechnique;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.UnsatCores;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.InterpolantConsolidation;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.InterpolatingTraceChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.InterpolatingTraceCheckerCraig;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.PredicateUnifier;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.TraceCheckerSpWp;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.TraceCheckerUtils;
import de.uni_freiburg.informatik.ultimate.util.csv.ICsvProviderProvider;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.IsContained;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.NestedMap3;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.NestedMap4;

/**
 *
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public class CodeCheckObserver implements IUnmanagedObserver {

	private static final boolean DEBUG = false;
	private static final boolean OUTPUT_HOARE_ANNOTATION = true;
	private static final SimplificationTechnique SIMPLIFICATION_TECHNIQUE = SimplificationTechnique.SIMPLIFY_DDA;
	private static final XnfConversionTechnique XNF_CONVERSION_TECHNIQUE =
			XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION;

	private final ILogger mLogger;
	private final IUltimateServiceProvider mServices;
	private final IToolchainStorage mToolchainStorage;

	private PredicateUnifier mPredicateUnifier;

	private CodeChecker mCodeChecker;

	private IIcfg<IcfgLocation> mOriginalRoot;
	private ImpRootNode mGraphRoot;

	private CodeCheckSettings mGlobalSettings;

	private CfgSmtToolkit mCsToolkit;

	private GraphWriter mGraphWriter;

	private NestedMap3<IPredicate, IIcfgTransition<?>, IPredicate, IsContained> mSatTriples;
	private NestedMap3<IPredicate, IIcfgTransition<?>, IPredicate, IsContained> mUnsatTriples;
	private NestedMap4<IPredicate, IPredicate, IIcfgTransition<?>, IPredicate, IsContained> mSatQuadruples;
	private NestedMap4<IPredicate, IPredicate, IIcfgTransition<?>, IPredicate, IsContained> mUnsatQuadruples;

	private Collection<IcfgLocation> mErrNodesOfAllProc;

	private boolean mLoopForever = true;
	private int mIterationsLimit = -1;
	private PredicateFactory mPredicateFactory;

	CodeCheckObserver(final IUltimateServiceProvider services, final IToolchainStorage toolchainStorage) {
		mServices = services;
		mLogger = mServices.getLoggingService().getLogger(Activator.PLUGIN_ID);
		mToolchainStorage = toolchainStorage;
	}

	/**
	 * Initialize all the required objects in the implementation.
	 *
	 * @param root
	 * @return
	 */
	private boolean initialize(final IIcfg<IcfgLocation> root) {
		readPreferencePage();
		mOriginalRoot = root;
		mCsToolkit = mOriginalRoot.getCfgSmtToolkit();
		mPredicateFactory = new PredicateFactory(mServices, mCsToolkit.getManagedScript(), mCsToolkit.getSymbolTable(),
				SIMPLIFICATION_TECHNIQUE, XNF_CONVERSION_TECHNIQUE);

		mPredicateUnifier = new PredicateUnifier(mServices, mCsToolkit.getManagedScript(), mPredicateFactory,
				mOriginalRoot.getCfgSmtToolkit().getSymbolTable(), SIMPLIFICATION_TECHNIQUE, XNF_CONVERSION_TECHNIQUE);

		final MonolithicHoareTripleChecker edgeChecker = new MonolithicHoareTripleChecker(mCsToolkit);

		final Map<String, Set<IcfgLocation>> proc2errNodes = mOriginalRoot.getProcedureErrorNodes();
		mErrNodesOfAllProc = new ArrayList<>();
		for (final Set<IcfgLocation> errNodeOfProc : proc2errNodes.values()) {
			mErrNodesOfAllProc.addAll(errNodeOfProc);
		}

		if (mGlobalSettings.isMemoizeNormalEdgeChecks()) {
			mSatTriples = new NestedMap3<>();
			mUnsatTriples = new NestedMap3<>();
		}
		if (mGlobalSettings.isMemoizeReturnEdgeChecks()) {
			mSatQuadruples = new NestedMap4<>();
			mUnsatQuadruples = new NestedMap4<>();
		}
		mGraphWriter = new GraphWriter(mGlobalSettings.getDotGraphPath(), true, true, true);

		// AI Module
		final boolean usePredicatesFromAbstractInterpretation = mGlobalSettings.getUseAbstractInterpretation();
		Map<IcfgLocation, Term> initialPredicates = null;
		if (usePredicatesFromAbstractInterpretation) {

			// Run for 20% of the complete time.
			final IProgressAwareTimer timer = mServices.getProgressMonitorService().getChildTimer(0.2);

			final IAbstractInterpretationResult<?, IcfgEdge, IProgramVarOrConst, IcfgLocation> result =
					AbstractInterpreter.runFuture(mOriginalRoot, timer, mServices, false, mLogger);

			if (result == null) {
				mLogger.warn(
						"was not able to retrieve initial predicates from abstract interpretation --> wrong toolchain?? (using \"true\")");
			} else {
				initialPredicates = result.getLoc2Term().entrySet().stream()
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			}
		}
		// End AI Module

		final RCFG2AnnotatedRCFG r2ar = new RCFG2AnnotatedRCFG(mCsToolkit, mPredicateFactory, mLogger,
				mPredicateUnifier.getTruePredicate(), initialPredicates);
		mGraphRoot = r2ar.convert(mOriginalRoot);

		mGraphWriter.writeGraphAsImage(mGraphRoot,
				String.format("graph_%s_originalAfterConversion", mGraphWriter.getGraphCounter()));

		removeSummaryEdges();

		mGraphWriter.writeGraphAsImage(mGraphRoot,
				String.format("graph_%s_originalSummaryEdgesRemoved", mGraphWriter.getGraphCounter()));

		if (mGlobalSettings.getChecker() == Checker.IMPULSE) {
			mCodeChecker = new ImpulseChecker(root, mCsToolkit, mOriginalRoot, mGraphRoot, mGraphWriter, edgeChecker,
					mPredicateUnifier, mLogger, mGlobalSettings);
		} else {
			mCodeChecker = new UltimateChecker(root, mCsToolkit, mOriginalRoot, mGraphRoot, mGraphWriter, edgeChecker,
					mPredicateUnifier, mLogger, mGlobalSettings);
		}
		mIterationsLimit = mGlobalSettings.getIterations();
		mLoopForever = mIterationsLimit == -1;

		return false;
	}

	private void readPreferencePage() {
		final IPreferenceProvider prefs = mServices.getPreferenceProvider(Activator.PLUGIN_ID);

		mGlobalSettings = new CodeCheckSettings();

		mGlobalSettings.setChecker(prefs.getEnum(CodeCheckPreferenceInitializer.LABEL_CHECKER, Checker.class));

		mGlobalSettings.setMemoizeNormalEdgeChecks(
				prefs.getBoolean(CodeCheckPreferenceInitializer.LABEL_MEMOIZENORMALEDGECHECKS,
						CodeCheckPreferenceInitializer.DEF_MEMOIZENORMALEDGECHECKS));
		mGlobalSettings.setMemoizeReturnEdgeChecks(
				prefs.getBoolean(CodeCheckPreferenceInitializer.LABEL_MEMOIZERETURNEDGECHECKS,
						CodeCheckPreferenceInitializer.DEF_MEMOIZERETURNEDGECHECKS));

		mGlobalSettings.setInterpolationMode(
				prefs.getEnum(CodeCheckPreferenceInitializer.LABEL_INTERPOLATIONMODE, InterpolationTechnique.class));

		mGlobalSettings.setUseInterpolantconsolidation(
				prefs.getBoolean(CodeCheckPreferenceInitializer.LABEL_INTERPOLANTCONSOLIDATION,
						CodeCheckPreferenceInitializer.DEF_INTERPOLANTCONSOLIDATION));

		mGlobalSettings.setPredicateUnification(
				prefs.getEnum(CodeCheckPreferenceInitializer.LABEL_PREDICATEUNIFICATION, PredicateUnification.class));

		mGlobalSettings.setEdgeCheckOptimization(
				prefs.getEnum(CodeCheckPreferenceInitializer.LABEL_EDGECHECKOPTIMIZATION, EdgeCheckOptimization.class));

		mGlobalSettings.setIterations(prefs.getInt(CodeCheckPreferenceInitializer.LABEL_ITERATIONS,
				CodeCheckPreferenceInitializer.DEF_ITERATIONS));

		mGlobalSettings.setDotGraphPath(prefs.getString(CodeCheckPreferenceInitializer.LABEL_GRAPHWRITERPATH,
				CodeCheckPreferenceInitializer.DEF_GRAPHWRITERPATH));

		mGlobalSettings.setRedirectionStrategy(
				prefs.getEnum(CodeCheckPreferenceInitializer.LABEL_REDIRECTION, RedirectionStrategy.class));

		mGlobalSettings.setDefaultRedirection(prefs.getBoolean(CodeCheckPreferenceInitializer.LABEL_DEF_RED,
				CodeCheckPreferenceInitializer.DEF_DEF_RED));
		mGlobalSettings.setRemoveFalseNodes(prefs.getBoolean(CodeCheckPreferenceInitializer.LABEL_RmFALSE,
				CodeCheckPreferenceInitializer.DEF_RmFALSE));
		mGlobalSettings.setCheckSatisfiability(prefs.getBoolean(CodeCheckPreferenceInitializer.LABEL_CHK_SAT,
				CodeCheckPreferenceInitializer.DEF_CHK_SAT));

		/*
		 * Settings concerning the solver for trace checks
		 */
		mGlobalSettings.setUseSeparateSolverForTracechecks(
				prefs.getBoolean(CodeCheckPreferenceInitializer.LABEL_USESEPARATETRACECHECKSOLVER,
						CodeCheckPreferenceInitializer.DEF_USESEPARATETRACECHECKSOLVER));

		mGlobalSettings.setUseFallbackForSeparateSolverForTracechecks(
				prefs.getBoolean(CodeCheckPreferenceInitializer.LABEL_USEFALLBACKFORSEPARATETRACECHECKSOLVER,
						CodeCheckPreferenceInitializer.DEF_USEFALLBACKFORSEPARATETRACECHECKSOLVER));

		mGlobalSettings.setChooseSeparateSolverForTracechecks(
				prefs.getEnum(CodeCheckPreferenceInitializer.LABEL_CHOOSESEPARATETRACECHECKSOLVER, SolverMode.class));

		mGlobalSettings.setSeparateSolverForTracechecksCommand(
				prefs.getString(CodeCheckPreferenceInitializer.LABEL_SEPARATETRACECHECKSOLVERCOMMAND,
						CodeCheckPreferenceInitializer.DEF_SEPARATETRACECHECKSOLVERCOMMAND));

		mGlobalSettings.setSeparateSolverForTracechecksTheory(
				prefs.getString(CodeCheckPreferenceInitializer.LABEL_SEPARATETRACECHECKSOLVERTHEORY,
						CodeCheckPreferenceInitializer.DEF_SEPARATETRACECHECKSOLVERTHEORY));

		/*
		 * Settings concerning betim interpolation
		 */
		mGlobalSettings
				.setUseLiveVariables(prefs.getBoolean(CodeCheckPreferenceInitializer.LABEL_LIVE_VARIABLES, true));
		mGlobalSettings
				.setUseUnsatCores(prefs.getEnum(CodeCheckPreferenceInitializer.LABEL_UNSAT_CORES, UnsatCores.class));

		/*
		 * Abstract interpretataion settings
		 */
		mGlobalSettings.setUseAbstractInterpretation(
				prefs.getBoolean(CodeCheckPreferenceInitializer.LABEL_USE_ABSTRACT_INTERPRETATION,
						CodeCheckPreferenceInitializer.DEF_USE_ABSTRACT_INTERPRETATION));

	}

	private void removeSummaryEdges() {
		final Deque<AnnotatedProgramPoint> stack = new ArrayDeque<>();
		final Set<AnnotatedProgramPoint> visited = new HashSet<>();
		visited.add(mGraphRoot);
		stack.add(mGraphRoot);
		while (!stack.isEmpty()) {
			final AnnotatedProgramPoint current = stack.pop();
			for (final AppEdge outEdge : new ArrayList<>(current.getOutgoingEdges())) {
				final AnnotatedProgramPoint successor = outEdge.getTarget();
				if (outEdge.getStatement() instanceof Summary
						&& ((Summary) outEdge.getStatement()).calledProcedureHasImplementation()) {
					outEdge.disconnect();
				}

				if (visited.add(successor)) {
					stack.add(successor);
				}
			}
		}
	}

	@Override
	public boolean process(final IElement root) {
		if (!(root instanceof IIcfg<?>)) {
			return false;
		}
		final IIcfg<IcfgLocation> icfg = (IIcfg<IcfgLocation>) root;
		initialize(icfg);

		mGraphWriter.writeGraphAsImage(mGraphRoot, String.format("graph_%s_original", mGraphWriter.getGraphCounter()));

		final ImpRootNode originalGraphCopy = copyGraph(mGraphRoot);

		mGraphWriter.writeGraphAsImage(originalGraphCopy,
				String.format("graph_%s_originalCopied", mGraphWriter.getGraphCounter()));

		final ArrayList<AnnotatedProgramPoint> procRootsToCheck = new ArrayList<>();

		// check for Ultimate.start -- assuming that if such a procedure exists,
		// it comes from the c translation
		for (final AnnotatedProgramPoint procRoot : mGraphRoot.getOutgoingNodes()) {
			if (procRoot.getProgramPoint().getProcedure().startsWith("ULTIMATE.start")) {
				procRootsToCheck.add(procRoot);
				break;
			}
		}
		if (procRootsToCheck.isEmpty()) { // -> no Ultimate.start present
			procRootsToCheck.addAll(mGraphRoot.getOutgoingNodes());
		}

		Result overallResult = Result.UNKNOWN;
		boolean allSafe = true;
		boolean verificationInterrupted = false;
		IcfgProgramExecution realErrorProgramExecution = null;

		// benchmark data collector variables
		final CegarLoopStatisticsGenerator benchmarkGenerator = new CegarLoopStatisticsGenerator();
		benchmarkGenerator.start(CegarLoopStatisticsDefinitions.OverallTime.toString());

		int iterationsCount = 0;

		InterpolatingTraceChecker traceChecker = null;

		for (final AnnotatedProgramPoint procedureRoot : procRootsToCheck) {
			if (!mServices.getProgressMonitorService().continueProcessing()) {
				verificationInterrupted = true;
				break;
			}

			mLogger.debug("Exploring : " + procedureRoot);

			final IEmptinessCheck emptinessCheck = new NWAEmptinessCheck(mServices);

			if (DEBUG) {
				mCodeChecker.debug();
			}
			while (mLoopForever || iterationsCount++ < mIterationsLimit) {
				if (!mServices.getProgressMonitorService().continueProcessing()) {
					verificationInterrupted = true;
					break;
				}

				benchmarkGenerator.announceNextIteration();

				mLogger.debug(String.format("Iterations = %d%n", iterationsCount));
				final NestedRun<IIcfgTransition<?>, AnnotatedProgramPoint> errorRun =
						emptinessCheck.checkForEmptiness(procedureRoot);

				if (errorRun == null) {
					// TODO: this only works for the case where we have 1 procedure in procRootsToCheck, right??
					mGraphWriter.writeGraphAsImage(procedureRoot, String.format("graph_%s_%s_noEP",
							mGraphWriter.getGraphCounter(), procedureRoot.toString().substring(0, 5)));
					// if an error trace doesn't exist, return safe
					mLogger.warn("This Program is SAFE, Check terminated with " + iterationsCount + " iterations.");
					break;
				}
				mLogger.info("Error Path is FOUND.");
				mGraphWriter.writeGraphAsImage(procedureRoot,
						String.format("graph_%s_%s_foundEP", mGraphWriter.getGraphCounter(),
								procedureRoot.toString().substring(0, 5)),
						errorRun.getStateSequence().toArray(new AnnotatedProgramPoint[] {}));

				if (mGlobalSettings.getPredicateUnification() == PredicateUnification.PER_ITERATION) {
					mPredicateUnifier =
							new PredicateUnifier(mServices, mCsToolkit.getManagedScript(), mPredicateFactory,
									mCsToolkit.getSymbolTable(), SIMPLIFICATION_TECHNIQUE, XNF_CONVERSION_TECHNIQUE);
				}

				ManagedScript mgdScriptTracechecks;
				if (mGlobalSettings.isUseSeparateSolverForTracechecks()) {
					final String filename = mOriginalRoot.getIdentifier() + "_TraceCheck_Iteration" + iterationsCount;
					final SolverMode solverMode = mGlobalSettings.getChooseSeparateSolverForTracechecks();
					final String commandExternalSolver = mGlobalSettings.getSeparateSolverForTracechecksCommand();
					final boolean dumpSmtScriptToFile = false;
					final String pathOfDumpedScript = "";
					final boolean fakeNonIncrementalScript = false;
					final Settings solverSettings = SolverBuilder.constructSolverSettings(filename, solverMode,
							fakeNonIncrementalScript, commandExternalSolver, dumpSmtScriptToFile, pathOfDumpedScript);
					final Script tcSolver = SolverBuilder.buildAndInitializeSolver(mServices, mToolchainStorage,
							mGlobalSettings.getChooseSeparateSolverForTracechecks(), solverSettings, false, false,
							mGlobalSettings.getSeparateSolverForTracechecksTheory(),
							"TraceCheck_Iteration" + iterationsCount);

					mgdScriptTracechecks = new ManagedScript(mServices, tcSolver);
					final TermTransferrer tt = new TermTransferrer(tcSolver);
					final Term axioms = mOriginalRoot.getCfgSmtToolkit().getAxioms().getFormula();
					tcSolver.assertTerm(tt.transform(axioms));
				} else {
					mgdScriptTracechecks = mCsToolkit.getManagedScript();
				}

				traceChecker = createTraceChecker(errorRun, mgdScriptTracechecks);

				if (mGlobalSettings.isUseSeparateSolverForTracechecks()) {
					mgdScriptTracechecks.getScript().exit();
				}

				if (traceChecker.getToolchainCanceledExpection() != null) {
					throw traceChecker.getToolchainCanceledExpection();
				}

				final LBool isSafe = traceChecker.isCorrect();
				benchmarkGenerator.addTraceCheckerData(traceChecker.getTraceCheckerBenchmark());

				if (isSafe == LBool.UNSAT) { // trace is infeasible
					IPredicate[] interpolants = null;

					if (mGlobalSettings.isUseInterpolantconsolidation()) {
						try {

							final InterpolantConsolidation<?> interpConsoli = new InterpolantConsolidation<>(
									mPredicateUnifier.getTruePredicate(), mPredicateUnifier.getFalsePredicate(),
									new TreeMap<Integer, IPredicate>(), NestedWord.nestedWord(errorRun.getWord()),
									mCsToolkit, mCsToolkit.getModifiableGlobalsTable(), mServices, mLogger,
									mPredicateUnifier, traceChecker, null// mtaPrefs
							);
							// Add benchmark data of interpolant consolidation
							// mCegarLoopBenchmark.addInterpolationConsolidationData(interpConsoli.getInterpolantConsolidationBenchmarks());
							// mInterpolantGenerator = interpConsoli;
							interpolants = interpConsoli.getInterpolants();
						} catch (final AutomataOperationCanceledException e) {
							// Timeout
							mLogger.error("Timeout during automata operation: ", e);
						}
					} else {
						interpolants = traceChecker.getInterpolants();
					}

					if (mGlobalSettings.isMemoizeNormalEdgeChecks() && mGlobalSettings.isMemoizeReturnEdgeChecks()) {
						mCodeChecker.codeCheck(errorRun, interpolants, procedureRoot, mSatTriples, mUnsatTriples,
								mSatQuadruples, mUnsatQuadruples);
					} else if (mGlobalSettings.isMemoizeNormalEdgeChecks()
							&& !mGlobalSettings.isMemoizeReturnEdgeChecks()) {
						mCodeChecker.codeCheck(errorRun, interpolants, procedureRoot, mSatTriples, mUnsatTriples);
					} else {
						mCodeChecker.codeCheck(errorRun, interpolants, procedureRoot);
					}

					benchmarkGenerator.addEdgeCheckerData(mCodeChecker.mEdgeChecker.getEdgeCheckerBenchmark());

				} else if (isSafe == LBool.SAT) { // trace is feasible
					mLogger.warn("This program is UNSAFE, Check terminated with " + iterationsCount + " iterations.");
					allSafe = false;
					if (traceChecker.providesRcfgProgramExecution()) {
						realErrorProgramExecution = traceChecker.getRcfgProgramExecution();
					} else {
						realErrorProgramExecution =
								TraceCheckerUtils.computeSomeIcfgProgramExecutionWithoutValues(errorRun.getWord());
					}

					if (DEBUG) {
						mCodeChecker.debug();
					}
					break;
				} else {
					assert isSafe == LBool.UNKNOWN;
					throw new UnsupportedOperationException("Solver said unknown");
				}
				if (DEBUG) {
					mCodeChecker.debug();
				}
			}
			// we need a fresh copy for each iteration because..??
			mGraphRoot = copyGraph(originalGraphCopy);
			mCodeChecker.mGraphRoot = mGraphRoot;

			if (!allSafe) {
				break;
			}
		}

		if (DEBUG) {
			mCodeChecker.debug();
		}

		if (!verificationInterrupted) {
			if (allSafe) {
				overallResult = Result.CORRECT;
			} else {
				overallResult = Result.INCORRECT;
			}
		} else {
			reportTimeoutResult(mErrNodesOfAllProc);
		}

		mLogger.debug("MemoizationHitsSat: " + mCodeChecker.mMemoizationHitsSat);
		mLogger.debug("MemoizationHitsUnsat: " + mCodeChecker.mMemoizationHitsUnsat);
		mLogger.debug("MemoizationReturnHitsSat: " + mCodeChecker.mMemoizationReturnHitsSat);
		mLogger.debug("MemoizationReturnHitsUnsat: " + mCodeChecker.mMemoizationReturnHitsUnsat);

		// benchmark stuff
		benchmarkGenerator.setResult(overallResult);
		benchmarkGenerator.stop(CegarLoopStatisticsDefinitions.OverallTime.toString());

		final CodeCheckBenchmarks ccb = new CodeCheckBenchmarks(mOriginalRoot);
		ccb.aggregateBenchmarkData(benchmarkGenerator);

		reportBenchmark(ccb);

		if (overallResult == Result.CORRECT) {
			reportPositiveResults(mErrNodesOfAllProc);

			if (OUTPUT_HOARE_ANNOTATION) {
				createInvariantResults(procRootsToCheck, icfg, icfg.getCfgSmtToolkit(),
						mServices.getBacktranslationService());
			}
		} else if (overallResult == Result.INCORRECT) {
			reportCounterexampleResult(realErrorProgramExecution);
		} else {
			final String shortDescription = "Unable to decide if program is safe!";
			final String longDescription = "Unable to decide if program is safe!";
			final GenericResult result =
					new GenericResult(Activator.PLUGIN_NAME, shortDescription, longDescription, Severity.INFO);
			mServices.getResultService().reportResult(Activator.PLUGIN_ID, result);
		}

		return false;
	}

	private void createInvariantResults(final List<AnnotatedProgramPoint> procRootsToCheck,
			final IIcfg<IcfgLocation> icfg, final CfgSmtToolkit csToolkit,
			final IBacktranslationService backTranslatorService) {
		final Term trueterm = csToolkit.getManagedScript().getScript().term("true");

		final Set<IcfgLocation> locsForLoopLocations = new HashSet<>();

		locsForLoopLocations.addAll(IcfgUtils.getPotentialCycleProgramPoints(icfg));
		locsForLoopLocations.addAll(icfg.getLoopLocations());
		// find all locations that have outgoing edges which are annotated with LoopEntry, i.e., all loop candidates

		for (final AnnotatedProgramPoint pr : procRootsToCheck) {
			final Map<IcfgLocation, Term> ha = computeHoareAnnotation(pr);
			for (final Entry<IcfgLocation, Term> kvp : ha.entrySet()) {
				final IcfgLocation locNode = kvp.getKey();
				if (!locsForLoopLocations.contains(locNode)) {
					// only compute loop invariants
					continue;
				}

				final Term invariant = kvp.getValue();
				if (invariant == null) {
					continue;
				}

				final InvariantResult<IIcfgElement, Term> invResult =
						new InvariantResult<>(Activator.PLUGIN_NAME, locNode, backTranslatorService, invariant);
				reportResult(invResult);

				if (trueterm.equals(invariant)) {
					continue;
				}
				final String invStr = backTranslatorService.translateExpressionToString(invariant, Term.class);
				new WitnessInvariant(invStr).annotate(locNode);

			}
		}
	}

	private InterpolatingTraceChecker createTraceChecker(
			final NestedRun<IIcfgTransition<?>, AnnotatedProgramPoint> errorRun,
			final ManagedScript mgdScriptTracechecks) {
		switch (mGlobalSettings.getInterpolationMode()) {
		case Craig_TreeInterpolation:
		case Craig_NestedInterpolation:
			try {
				final InterpolatingTraceChecker tc = new InterpolatingTraceCheckerCraig(
						mPredicateUnifier.getTruePredicate(), mPredicateUnifier.getFalsePredicate(),
						new TreeMap<Integer, IPredicate>(), errorRun.getWord(), mCsToolkit,
						AssertCodeBlockOrder.NOT_INCREMENTALLY, mServices, true, mPredicateUnifier,
						mGlobalSettings.getInterpolationMode(), mgdScriptTracechecks, true, XNF_CONVERSION_TECHNIQUE,
						SIMPLIFICATION_TECHNIQUE,
						TraceCheckerUtils.getSequenceOfProgramPoints(NestedWord.nestedWord(errorRun.getWord())), false);
				if (tc.getInterpolantComputationStatus().wasComputationSuccesful()) {
					return tc;
				}
			} catch (final Exception e) {
				mLogger.error("First Tracecheck threw exception " + e.getMessage());
				if (!mGlobalSettings.isUseFallbackForSeparateSolverForTracechecks()) {
					throw e;
				}
			}
			/*
			 * The fallback tracechecker is always the normal solver (i.e. the csToolkit that was set in RCFGBuilder
			 * settings with forward predicates betim interpolation.
			 *
			 * The fallback interpolation mode is hardcoded for now
			 */
			return new TraceCheckerSpWp(mPredicateUnifier.getTruePredicate(), mPredicateUnifier.getFalsePredicate(),
					new TreeMap<Integer, IPredicate>(), errorRun.getWord(), mCsToolkit,
					AssertCodeBlockOrder.NOT_INCREMENTALLY, UnsatCores.CONJUNCT_LEVEL, true, mServices, true,
					mPredicateUnifier, InterpolationTechnique.ForwardPredicates, mCsToolkit.getManagedScript(),
					XNF_CONVERSION_TECHNIQUE, SIMPLIFICATION_TECHNIQUE,
					TraceCheckerUtils.getSequenceOfProgramPoints(NestedWord.nestedWord(errorRun.getWord())));
		case ForwardPredicates:
		case BackwardPredicates:
		case FPandBP:
		case FPandBPonlyIfFpWasNotPerfect:
			// return LBool.UNSAT if trace is infeasible
			try {
				return new TraceCheckerSpWp(mPredicateUnifier.getTruePredicate(), mPredicateUnifier.getFalsePredicate(),
						new TreeMap<Integer, IPredicate>(), errorRun.getWord(), mCsToolkit,
						AssertCodeBlockOrder.NOT_INCREMENTALLY, mGlobalSettings.getUseUnsatCores(),
						mGlobalSettings.isUseLiveVariables(), mServices, true, mPredicateUnifier,
						mGlobalSettings.getInterpolationMode(), mgdScriptTracechecks, XNF_CONVERSION_TECHNIQUE,
						SIMPLIFICATION_TECHNIQUE,
						TraceCheckerUtils.getSequenceOfProgramPoints(NestedWord.nestedWord(errorRun.getWord())));
			} catch (final Exception e) {
				if (!mGlobalSettings.isUseFallbackForSeparateSolverForTracechecks()) {
					throw e;
				}

				return new TraceCheckerSpWp(mPredicateUnifier.getTruePredicate(), mPredicateUnifier.getFalsePredicate(),
						new TreeMap<Integer, IPredicate>(), errorRun.getWord(), mCsToolkit,
						AssertCodeBlockOrder.NOT_INCREMENTALLY, UnsatCores.CONJUNCT_LEVEL, true, mServices, true,
						mPredicateUnifier, mGlobalSettings.getInterpolationMode(), mCsToolkit.getManagedScript(),
						XNF_CONVERSION_TECHNIQUE, SIMPLIFICATION_TECHNIQUE,
						TraceCheckerUtils.getSequenceOfProgramPoints(NestedWord.nestedWord(errorRun.getWord())));
			}
		default:
			throw new UnsupportedOperationException(
					"Unsupported interpolation mode: " + mGlobalSettings.getInterpolationMode());
		}
	}

	private static String prettyPrintProgramPoint(final IcfgLocation pp) {
		final ILocation loc = ILocation.getAnnotation(pp);
		final int startLine = loc.getStartLine();
		final int endLine = loc.getEndLine();
		final StringBuilder sb = new StringBuilder();
		sb.append(pp);
		if (startLine == endLine) {
			sb.append("(line " + startLine + ")");
		} else {
			sb.append("(lines " + startLine + " " + endLine + ")");
		}
		return sb.toString();
	}

	private Map<IcfgLocation, Term> computeHoareAnnotation(final AnnotatedProgramPoint pr) {
		final Map<IcfgLocation, Term> pp2HoareAnnotation = new HashMap<>();
		final Map<IcfgLocation, Set<AnnotatedProgramPoint>> pp2app = computeProgramPointToAnnotatedProgramPoints(pr);

		final IPredicate falsePred =
				mPredicateFactory.newPredicate(mCsToolkit.getManagedScript().getScript().term("false"));

		for (final Entry<IcfgLocation, Set<AnnotatedProgramPoint>> kvp : pp2app.entrySet()) {
			IPredicate annot = falsePred;
			for (final AnnotatedProgramPoint app : kvp.getValue()) {
				final Term tvp = mPredicateFactory.or(false, annot, app.getPredicate());
				annot = mPredicateFactory.newSPredicate(kvp.getKey(), tvp);
			}
			pp2HoareAnnotation.put(kvp.getKey(), annot.getFormula());
		}
		return pp2HoareAnnotation;
	}

	/**
	 * fill up the map programPointToAnnotatedProgramPoints with the reachable part of the CFG
	 *
	 * @param annotatedProgramPoint
	 * @param programPointToAnnotatedProgramPoints
	 */
	private static Map<IcfgLocation, Set<AnnotatedProgramPoint>>
			computeProgramPointToAnnotatedProgramPoints(final AnnotatedProgramPoint entry) {
		final Set<AnnotatedProgramPoint> visited = new HashSet<>();
		final Deque<AnnotatedProgramPoint> queue = new ArrayDeque<>();
		final Map<IcfgLocation, Set<AnnotatedProgramPoint>> rtr = new HashMap<>();
		queue.push(entry);

		while (!queue.isEmpty()) {
			final AnnotatedProgramPoint current = queue.pop();

			Set<AnnotatedProgramPoint> apps = rtr.get(current.getProgramPoint());
			if (apps == null) {
				apps = new HashSet<>();
				rtr.put(current.getProgramPoint(), apps);
			}
			apps.add(current);

			for (final AppEdge outEdge : current.getOutgoingEdges()) {
				if (!visited.contains(outEdge.getTarget())) {
					queue.push(outEdge.getTarget());
					visited.add(outEdge.getTarget());
				}
			}
		}
		return rtr;
	}

	/**
	 * Given a graph root, copy all the nodes and the corresponding connections.
	 *
	 * @param root
	 * @return
	 */
	public ImpRootNode copyGraph(final ImpRootNode root) {
		final HashMap<AnnotatedProgramPoint, AnnotatedProgramPoint> copy = new HashMap<>();
		// FIXME: 2016-11-05 Matthias: I cannot solve this, passing null.
		final ImpRootNode newRoot = new ImpRootNode();
		copy.put(root, newRoot);
		final Deque<AnnotatedProgramPoint> stack = new ArrayDeque<>();
		for (final AnnotatedProgramPoint child : root.getOutgoingNodes()) {
			stack.add(child);
		}
		while (!stack.isEmpty()) {
			final AnnotatedProgramPoint current = stack.pop();
			if (copy.containsKey(current)) {
				continue;
			}
			copy.put(current, new AnnotatedProgramPoint(current));
			final List<AnnotatedProgramPoint> nextNodes = current.getOutgoingNodes();
			for (final AnnotatedProgramPoint nextNode : nextNodes) {
				if (!copy.containsKey(nextNode)) {
					stack.add(nextNode);
				}
			}
		}
		for (final Entry<AnnotatedProgramPoint, AnnotatedProgramPoint> entry : copy.entrySet()) {
			final AnnotatedProgramPoint oldNode = entry.getKey();
			final AnnotatedProgramPoint newNode = entry.getValue();
			for (final AppEdge outEdge : oldNode.getOutgoingEdges()) {
				if (outEdge instanceof AppHyperEdge) {
					final AppHyperEdge outHypEdge = (AppHyperEdge) outEdge;
					final AnnotatedProgramPoint hier = copy.get(outHypEdge.getHier());
					if (hier != null) {
						newNode.connectOutgoingReturn(hier, (IIcfgReturnTransition<?, ?>) outHypEdge.getStatement(),
								copy.get(outHypEdge.getTarget()));
					}
				} else {
					newNode.connectOutgoing(outEdge.getStatement(), copy.get(outEdge.getTarget()));
				}
			}
		}
		return newRoot;
	}

	private <T> void reportBenchmark(final ICsvProviderProvider<T> benchmark) {
		final String shortDescription = "Ultimate CodeCheck benchmark data";
		final BenchmarkResult<T> res = new BenchmarkResult<>(Activator.PLUGIN_NAME, shortDescription, benchmark);
		// s_Logger.warn(res.getLongDescription());

		reportResult(res);
	}

	private void reportPositiveResults(final Collection<IcfgLocation> errorLocs) {
		final String longDescription;
		if (errorLocs.isEmpty()) {
			longDescription = "We were not able to verify any"
					+ " specifiation because the program does not contain any specification.";
		} else {
			longDescription = errorLocs.size() + " specifications checked. All of them hold";
			for (final IcfgLocation errorLoc : errorLocs) {
				final PositiveResult<IIcfgElement> pResult =
						new PositiveResult<>(Activator.PLUGIN_NAME, errorLoc, mServices.getBacktranslationService());
				reportResult(pResult);
			}
		}
		final AllSpecificationsHoldResult result =
				new AllSpecificationsHoldResult(Activator.PLUGIN_NAME, longDescription);
		reportResult(result);
		mLogger.info(result.getShortDescription() + " " + result.getLongDescription());
	}

	private void reportCounterexampleResult(final IcfgProgramExecution pe) {
		if (!pe.getOverapproximations().isEmpty()) {
			reportUnproveableResult(pe, pe.getUnprovabilityReasons());
			return;
		}
		reportResult(new CounterExampleResult<>(getErrorPP(pe), Activator.PLUGIN_NAME,
				mServices.getBacktranslationService(), pe));
	}

	private void reportUnproveableResult(final IcfgProgramExecution pe,
			final List<UnprovabilityReason> unproabilityReasons) {
		final IcfgLocation errorPP = getErrorPP(pe);
		reportResult(new UnprovableResult<>(Activator.PLUGIN_NAME, errorPP, mServices.getBacktranslationService(), pe,
				unproabilityReasons));
	}

	public IcfgLocation getErrorPP(final IcfgProgramExecution rcfgProgramExecution) {
		final int lastPosition = rcfgProgramExecution.getLength() - 1;
		final IIcfgTransition<?> last = rcfgProgramExecution.getTraceElement(lastPosition).getTraceElement();
		final IcfgLocation errorPP = last.getTarget();
		return errorPP;
	}

	private void reportTimeoutResult(final Collection<IcfgLocation> errorLocs) {
		for (final IcfgLocation errorIpp : errorLocs) {
			final ILocation origin = ILocation.getAnnotation(errorIpp);
			String timeOutMessage =
					"Unable to prove that " + ResultUtil.getCheckedSpecification(errorIpp).getPositiveMessage();
			timeOutMessage += " (line " + origin.getStartLine() + ")";
			final TimeoutResultAtElement<IIcfgElement> timeOutRes = new TimeoutResultAtElement<>(errorIpp,
					Activator.PLUGIN_NAME, mServices.getBacktranslationService(), timeOutMessage);
			reportResult(timeOutRes);
		}
	}

	private void reportResult(final IResult res) {
		mServices.getResultService().reportResult(Activator.PLUGIN_ID, res);
	}

	public ImpRootNode getRoot() {
		return mCodeChecker.mGraphRoot;
	}

	@Override
	public void finish() {
		// not needed
	}

	@Override
	public void init(final ModelType modelType, final int currentModelIndex, final int numberOfModels) {
		// not needed
	}

	@Override
	public boolean performedChanges() {
		return false;
	}

	private enum Result {
		CORRECT, TIMEOUT, MAXEDITERATIONS, UNKNOWN, INCORRECT
	}
}
