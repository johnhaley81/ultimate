/*
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
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BoogieASTNode;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check.Spec;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.WitnessInvariant;
import de.uni_freiburg.informatik.ultimate.core.lib.results.AllSpecificationsHoldResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.BenchmarkResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.CounterExampleResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.InvariantResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.PositiveResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.ProcedureContractResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.ResultUtil;
import de.uni_freiburg.informatik.ultimate.core.lib.results.TimeoutResultAtElement;
import de.uni_freiburg.informatik.ultimate.core.lib.results.UnprovabilityReason;
import de.uni_freiburg.informatik.ultimate.core.lib.results.UnprovableResult;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.core.model.models.annotation.IAnnotations;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResult;
import de.uni_freiburg.informatik.ultimate.core.model.services.IBacktranslationService;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.SolverMode;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.ProgramPoint;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.RCFGEdge;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.RcfgElement;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.RootAnnot;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.RootNode;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.preferences.RcfgPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.util.RcfgProgramExecution;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.AbstractCegarLoop.Result;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.HoareAnnotationChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.SmtManager;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.InterpolantAutomaton;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.LanguageOperation;
import de.uni_freiburg.informatik.ultimate.util.IRunningTaskStackProvider;
import de.uni_freiburg.informatik.ultimate.util.csv.ICsvProviderProvider;
import de.uni_freiburg.informatik.ultimate.witnessparser.graph.WitnessEdge;
import de.uni_freiburg.informatik.ultimate.witnessparser.graph.WitnessNode;

public class TraceAbstractionStarter {
	
	private final ILogger mLogger;
	private final IUltimateServiceProvider mServices;
	private final IToolchainStorage mToolchainStorage;
	
	/**
	 * Root Node of this Ultimate model. I use this to store information that should be passed to the next plugin. The
	 * Successors of this node exactly the initial nodes of procedures.
	 */
	private IElement mRootOfNewModel;
	private Result mOverallResult;
	private IElement mArtifact;
	
	public TraceAbstractionStarter(final IUltimateServiceProvider services, final IToolchainStorage storage,
			final RootNode rcfgRootNode, final INestedWordAutomatonSimple<WitnessEdge, WitnessNode> witnessAutomaton) {
		mServices = services;
		mToolchainStorage = storage;
		mLogger = mServices.getLoggingService().getLogger(Activator.PLUGIN_ID);
		runCegarLoops(rcfgRootNode, witnessAutomaton);
	}
	
	private void runCegarLoops(final RootNode rcfgRootNode,
			final INestedWordAutomatonSimple<WitnessEdge, WitnessNode> witnessAutomaton) {
		final RootAnnot rootAnnot = rcfgRootNode.getRootAnnot();
		final TAPreferences taPrefs = new TAPreferences(mServices);
		
		String settings = "Automizer settings:";
		settings += " Hoare:" + taPrefs.computeHoareAnnotation();
		settings += " " + (taPrefs.differenceSenwa() ? "SeNWA" : "NWA");
		settings += " Interpolation:" + taPrefs.interpolation();
		settings += " Determinization: " + taPrefs.interpolantAutomatonEnhancement();
		mLogger.info(settings);
		
		final SmtManager smtManager =
				new SmtManager(rootAnnot.getScript(), rootAnnot.getBoogie2SMT(), rootAnnot.getModGlobVarManager(),
						mServices, interpolationModeSwitchNeeded(), rootAnnot.getManagedScript(),
						taPrefs.getSimplificationTechnique(), taPrefs.getXnfConversionTechnique());
		final TraceAbstractionBenchmarks traceAbstractionBenchmark = new TraceAbstractionBenchmarks(rootAnnot);
		
		final Map<String, Collection<ProgramPoint>> proc2errNodes = rootAnnot.getErrorNodes();
		final Collection<ProgramPoint> errNodesOfAllProc = new ArrayList<>();
		for (final Collection<ProgramPoint> errNodeOfProc : proc2errNodes.values()) {
			errNodesOfAllProc.addAll(errNodeOfProc);
		}
		
		mOverallResult = Result.SAFE;
		mArtifact = null;
		
		if (taPrefs.allErrorLocsAtOnce()) {
			final String name = "AllErrorsAtOnce";
			iterate(name, rcfgRootNode, taPrefs, smtManager, traceAbstractionBenchmark, errNodesOfAllProc,
					witnessAutomaton);
		} else {
			for (final ProgramPoint errorLoc : errNodesOfAllProc) {
				final String name = errorLoc.getPosition();
				final ArrayList<ProgramPoint> errorLocs = new ArrayList<>(1);
				errorLocs.add(errorLoc);
				mServices.getProgressMonitorService().setSubtask(errorLoc.toString());
				iterate(name, rcfgRootNode, taPrefs, smtManager, traceAbstractionBenchmark, errorLocs,
						witnessAutomaton);
			}
		}
		logNumberOfWitnessInvariants(errNodesOfAllProc);
		if (mOverallResult == Result.SAFE) {
			final String longDescription;
			if (errNodesOfAllProc.isEmpty()) {
				longDescription = "We were not able to verify any"
						+ " specifiation because the program does not contain any specification.";
			} else {
				longDescription = errNodesOfAllProc.size() + " specifications checked. All of them hold";
			}
			final AllSpecificationsHoldResult result =
					new AllSpecificationsHoldResult(Activator.PLUGIN_NAME, longDescription);
			reportResult(result);
		}
		
		mLogger.debug("Compute Hoare Annotation: " + taPrefs.computeHoareAnnotation());
		mLogger.debug("Overall result: " + mOverallResult);
		mLogger.debug("Continue processing: " + mServices.getProgressMonitorService().continueProcessing());
		if (taPrefs.computeHoareAnnotation() && mOverallResult != Result.TIMEOUT
				&& mServices.getProgressMonitorService().continueProcessing()) {
			assert new HoareAnnotationChecker(mServices, rcfgRootNode, smtManager)
					.isInductive() : "incorrect Hoare annotation";
			
			final IBacktranslationService backTranslatorService = mServices.getBacktranslationService();
			final Term trueterm = smtManager.getScript().term("true");
			
			final Set<ProgramPoint> locsForLoopLocations = new HashSet<>();
			locsForLoopLocations.addAll(rootAnnot.getPotentialCycleProgramPoints());
			locsForLoopLocations.addAll(rootAnnot.getLoopLocations().keySet());
			// find all locations that have outgoing edges which are annotated with LoopEntry, i.e., all loop candidates
			
			for (final ProgramPoint locNode : locsForLoopLocations) {
				final HoareAnnotation hoare = getHoareAnnotation(locNode);
				if (hoare == null) {
					continue;
				}
				final Term formula = hoare.getFormula();
				final InvariantResult<RcfgElement, Term> invResult =
						new InvariantResult<>(Activator.PLUGIN_NAME, locNode, backTranslatorService, formula);
				reportResult(invResult);
				
				if (formula.equals(trueterm)) {
					continue;
				}
				final String inv = backTranslatorService.translateExpressionToString(formula, Term.class);
				new WitnessInvariant(inv).annotate(locNode);
				
			}
			
			final Map<String, ProgramPoint> finalNodes = rootAnnot.getExitNodes();
			for (final Entry<String, ProgramPoint> proc : finalNodes.entrySet()) {
				final String procName = proc.getKey();
				if (isAuxilliaryProcedure(procName)) {
					continue;
				}
				final ProgramPoint finalNode = proc.getValue();
				final HoareAnnotation hoare = getHoareAnnotation(finalNode);
				if (hoare != null) {
					final Term formula = hoare.getFormula();
					final ProcedureContractResult<RcfgElement, Term> result = new ProcedureContractResult<>(
							Activator.PLUGIN_NAME, finalNode, backTranslatorService, procName, formula);
					
					reportResult(result);
					// TODO: Add setting that controls the generation of those witness invariants
				}
			}
		}
		reportBenchmark(traceAbstractionBenchmark);
		switch (mOverallResult) {
		case SAFE:
		case UNSAFE:
			break;
		case TIMEOUT:
			mLogger.warn("Timeout");
			break;
		case UNKNOWN:
			mLogger.warn("Unable to decide correctness. Please check the following counterexample manually.");
			break;
		default:
			throw new UnsupportedOperationException("Unknown overall result " + mOverallResult);
		}
		
		mRootOfNewModel = mArtifact;
	}
	
	private void logNumberOfWitnessInvariants(final Collection<ProgramPoint> errNodesOfAllProc) {
		int numberOfCheckedInvariants = 0;
		for (final ProgramPoint err : errNodesOfAllProc) {
			final BoogieASTNode boogieASTNode = err.getBoogieASTNode();
			final IAnnotations annot = boogieASTNode.getPayload().getAnnotations().get(Check.class.getName());
			if (annot != null) {
				final Check check = (Check) annot;
				if (check.getSpec() == Spec.WITNESS_INVARIANT) {
					numberOfCheckedInvariants++;
				}
			}
		}
		if (numberOfCheckedInvariants > 0) {
			mLogger.info("Automizer considered " + numberOfCheckedInvariants + " witness invariants");
			mLogger.info("WitnessConsidered=" + numberOfCheckedInvariants);
		}
	}
	
	private void iterate(final String name, final RootNode root, final TAPreferences taPrefs,
			final SmtManager smtManager, final TraceAbstractionBenchmarks taBenchmark,
			final Collection<ProgramPoint> errorLocs,
			final INestedWordAutomatonSimple<WitnessEdge, WitnessNode> witnessAutomaton) {
		final BasicCegarLoop basicCegarLoop =
				constructCegarLoop(name, root, taPrefs, smtManager, taBenchmark, errorLocs);
		basicCegarLoop.setWitnessAutomaton(witnessAutomaton);
		
		final Result result = basicCegarLoop.iterate();
		basicCegarLoop.finish();
		final CegarLoopStatisticsGenerator cegarLoopBenchmarkGenerator = basicCegarLoop.getCegarLoopBenchmark();
		cegarLoopBenchmarkGenerator.stop(CegarLoopStatisticsDefinitions.OverallTime.toString());
		// TODO: Stop AI clock
		taBenchmark.aggregateBenchmarkData(cegarLoopBenchmarkGenerator);
		
		mOverallResult = computeOverallResult(errorLocs, basicCegarLoop, result);
		
		if (taPrefs.computeHoareAnnotation() && mOverallResult == Result.SAFE) {
			mLogger.debug("Computing Hoare annotation of CFG");
			basicCegarLoop.computeCFGHoareAnnotation();
			writeHoareAnnotationToLogger(root);
		} else {
			mLogger.debug("Ommiting computation of Hoare annotation");
			
		}
		mArtifact = basicCegarLoop.getArtifact();
	}
	
	private BasicCegarLoop constructCegarLoop(final String name, final RootNode root, final TAPreferences taPrefs,
			final SmtManager smtManager, final TraceAbstractionBenchmarks taBenchmark,
			final Collection<ProgramPoint> errorLocs) {
		final LanguageOperation languageOperation = mServices.getPreferenceProvider(Activator.PLUGIN_ID)
				.getEnum(TraceAbstractionPreferenceInitializer.LABEL_LANGUAGE_OPERATION, LanguageOperation.class);
		if (languageOperation == LanguageOperation.DIFFERENCE) {
			if (taPrefs.interpolantAutomaton() == InterpolantAutomaton.TOTALINTERPOLATION) {
				return new CegarLoopSWBnonRecursive(name, root, smtManager, taBenchmark, taPrefs, errorLocs,
						taPrefs.interpolation(), taPrefs.computeHoareAnnotation(), mServices, mToolchainStorage);
			}
			return new BasicCegarLoop(name, root, smtManager, taPrefs, errorLocs, taPrefs.interpolation(),
					taPrefs.computeHoareAnnotation(), mServices, mToolchainStorage);
		}
		return new IncrementalInclusionCegarLoop(name, root, smtManager, taPrefs, errorLocs, taPrefs.interpolation(),
				taPrefs.computeHoareAnnotation(), mServices, mToolchainStorage, languageOperation);
	}
	
	private Result computeOverallResult(final Collection<ProgramPoint> errorLocs, final BasicCegarLoop basicCegarLoop,
			final Result result) {
		switch (result) {
		case SAFE:
			reportPositiveResults(errorLocs);
			return mOverallResult;
		case UNSAFE:
			reportCounterexampleResult(basicCegarLoop.getRcfgProgramExecution());
			return result;
		case TIMEOUT:
			reportTimeoutResult(errorLocs, basicCegarLoop.getRunningTaskStackProvider());
			return mOverallResult != Result.UNSAFE ? result : mOverallResult;
		case UNKNOWN:
			final RcfgProgramExecution pe = basicCegarLoop.getRcfgProgramExecution();
			reportUnproveableResult(pe, pe.getUnprovabilityReasons());
			return mOverallResult != Result.UNSAFE ? result : mOverallResult;
		default:
			throw new IllegalArgumentException();
		}
	}
	
	private void writeHoareAnnotationToLogger(final RootNode root) {
		for (final Entry<String, Map<String, ProgramPoint>> proc2label2pp : root.getRootAnnot().getProgramPoints()
				.entrySet()) {
			for (final ProgramPoint pp : proc2label2pp.getValue().values()) {
				final HoareAnnotation hoare = getHoareAnnotation(pp);
				if (hoare == null) {
					mLogger.info("For program point  " + prettyPrintProgramPoint(pp)
							+ "  no Hoare annotation was computed.");
				} else {
					mLogger.info("At program point  " + prettyPrintProgramPoint(pp) + "  the Hoare annotation is:  "
							+ hoare.getFormula());
				}
			}
		}
	}
	
	private static String prettyPrintProgramPoint(final ProgramPoint pp) {
		final int startLine = pp.getPayload().getLocation().getStartLine();
		final int endLine = pp.getPayload().getLocation().getStartLine();
		final StringBuilder sb = new StringBuilder();
		sb.append(pp);
		if (startLine == endLine) {
			sb.append("(line " + startLine + ")");
		} else {
			sb.append("(lines " + startLine + " " + endLine + ")");
		}
		return sb.toString();
	}
	
	private void reportPositiveResults(final Collection<ProgramPoint> errorLocs) {
		for (final ProgramPoint errorLoc : errorLocs) {
			final PositiveResult<RcfgElement> pResult =
					new PositiveResult<>(Activator.PLUGIN_NAME, errorLoc, mServices.getBacktranslationService());
			reportResult(pResult);
		}
	}
	
	private void reportCounterexampleResult(final RcfgProgramExecution pe) {
		if (!pe.getOverapproximations().isEmpty()) {
			reportUnproveableResult(pe, pe.getUnprovabilityReasons());
			return;
		}
		reportResult(new CounterExampleResult<RcfgElement, RCFGEdge, Term>(getErrorPP(pe), Activator.PLUGIN_NAME,
				mServices.getBacktranslationService(), pe));
	}
	
	private void reportTimeoutResult(final Collection<ProgramPoint> errorLocs, final IRunningTaskStackProvider rtsp) {
		for (final ProgramPoint errorIpp : errorLocs) {
			final ProgramPoint errorLoc = errorIpp;
			final ILocation origin = errorLoc.getBoogieASTNode().getLocation().getOrigin();
			String timeOutMessage = "Unable to prove that ";
			timeOutMessage += ResultUtil.getCheckedSpecification(errorLoc).getPositiveMessage();
			timeOutMessage += " (line " + origin.getStartLine() + ").";
			if (rtsp != null) {
				timeOutMessage += " Cancelled " + rtsp.printRunningTaskMessage();
			}
			final TimeoutResultAtElement<RcfgElement> timeOutRes = new TimeoutResultAtElement<>(errorLoc,
					Activator.PLUGIN_NAME, mServices.getBacktranslationService(), timeOutMessage);
			reportResult(timeOutRes);
		}
	}
	
	private void reportUnproveableResult(final RcfgProgramExecution pe,
			final List<UnprovabilityReason> unproabilityReasons) {
		final ProgramPoint errorPP = getErrorPP(pe);
		final UnprovableResult<RcfgElement, RCFGEdge, Term> uknRes = new UnprovableResult<>(Activator.PLUGIN_NAME,
				errorPP, mServices.getBacktranslationService(), pe, unproabilityReasons);
		reportResult(uknRes);
	}
	
	private <T> void reportBenchmark(final ICsvProviderProvider<T> benchmark) {
		final String shortDescription = "Ultimate Automizer benchmark data";
		final BenchmarkResult<T> res = new BenchmarkResult<>(Activator.PLUGIN_NAME, shortDescription, benchmark);
		reportResult(res);
	}
	
	private static boolean isAuxilliaryProcedure(final String proc) {
		return "ULTIMATE.init".equals(proc) || "ULTIMATE.start".equals(proc);
	}
	
	private void reportResult(final IResult res) {
		mServices.getResultService().reportResult(Activator.PLUGIN_ID, res);
	}
	
	/**
	 * @return the root of the CFG.
	 */
	public IElement getRootOfNewModel() {
		return mRootOfNewModel;
	}
	
	public static HoareAnnotation getHoareAnnotation(final ProgramPoint programPoint) {
		return HoareAnnotation.getAnnotation(programPoint);
	}
	
	public static ProgramPoint getErrorPP(final RcfgProgramExecution rcfgProgramExecution) {
		final int lastPosition = rcfgProgramExecution.getLength() - 1;
		final RCFGEdge last = rcfgProgramExecution.getTraceElement(lastPosition).getTraceElement();
		return (ProgramPoint) last.getTarget();
	}
	
	private boolean interpolationModeSwitchNeeded() {
		final SolverMode solver = mServices.getPreferenceProvider(Activator.PLUGIN_ID)
				.getEnum(RcfgPreferenceInitializer.LABEL_Solver, SolverMode.class);
		return solver == SolverMode.External_PrincessInterpolationMode;
	}
}
