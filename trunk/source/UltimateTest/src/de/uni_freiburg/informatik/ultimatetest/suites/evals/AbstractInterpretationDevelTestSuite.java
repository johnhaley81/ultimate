/*
 * Copyright (C) 2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE Test Library.
 *
 * The ULTIMATE Test Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE Test Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Test Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Test Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Test Library grant you additional permission
 * to convey the resulting work.
 */

package de.uni_freiburg.informatik.ultimatetest.suites.evals;

import java.util.Collection;

import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.CegarLoopStatisticsDefinitions;
import de.uni_freiburg.informatik.ultimate.test.DirectoryFileEndingsPair;
import de.uni_freiburg.informatik.ultimate.test.UltimateRunDefinition;
import de.uni_freiburg.informatik.ultimate.test.UltimateTestCase;
import de.uni_freiburg.informatik.ultimate.test.decider.ITestResultDecider;
import de.uni_freiburg.informatik.ultimate.test.decider.OverapproximatingSafetyCheckTestResultDecider;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Triple;
import de.uni_freiburg.informatik.ultimatetest.suites.AbstractEvalTestSuite;
import de.uni_freiburg.informatik.ultimatetest.summaries.ColumnDefinition;
import de.uni_freiburg.informatik.ultimatetest.summaries.ColumnDefinition.Aggregate;
import de.uni_freiburg.informatik.ultimatetest.summaries.ConversionContext;

/**
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 */
public class AbstractInterpretationDevelTestSuite extends AbstractEvalTestSuite {

	private static final String[] C = new String[] { ".i", ".c" };
	private static final String[] BPL = new String[] { ".bpl" };
	private static final int DEFAULT_LIMIT = Integer.MAX_VALUE;

	// @formatter:off
	@SuppressWarnings("unchecked")
	private static final Triple<String, String[], String>[] TOOLCHAINS = new Triple[] {
			//### BPL
			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default.epf"),
//			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT.epf"),
//			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_P1.epf"),
//			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_total.epf"),
			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_Debug.epf"),
//			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_P1_Debug.epf"),
			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_Debug_noPredAbs.epf"),
//			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_Debug_refineAlways.epf"),
//			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_OCT.epf"),
			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_OCT_Debug.epf"),
//			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_CON.epf"),
//			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_CON_Debug.epf"),
//			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_COMP.epf"),
//			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_COMP_Debug.epf"),
//			new Triple<>("AutomizerBpl.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_COMP_Simple.epf"),

//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT.epf"),
//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_P1.epf"),
//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_Debug.epf"),
//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_P1_Debug.epf"),
//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_OCT.epf"),
//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_OCT_Debug.epf"),
//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_CON.epf"),
//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_CON_Debug.epf"),
//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_COMP.epf"),
//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_COMP_Debug.epf"),
//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_COMP_Simple.epf"),
//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_COMP_Simple_Debug.epf"),
//			new Triple<>("AbstractInterpretation.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_COMP_WO_CON_Debug.epf"),


			//### BPL Inline
//			new Triple<>("AutomizerBplInline.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default.epf"),
//			new Triple<>("AutomizerBplInline.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_Debug.epf"),
//			new Triple<>("AbstractInterpretationInline.xml", BPL, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_Debug.epf"),


			//### C
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default_SMTInterpol.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT.epf"),
			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_Debug.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_total.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_total_Debug.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_SMTInterpol.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_COMP_SMTInterpol.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_OCT.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_OCT_total.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_OCT_Debug.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_CON.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_COMP_Simple.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_Debug_noPredAbs.epf"),
//			new Triple<>("AutomizerC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_COMP_Simple_total.epf"),

//			new Triple<>("AbstractInterpretationC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT.epf"),
//			new Triple<>("AbstractInterpretationC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT_Debug.epf"),
//			new Triple<>("AbstractInterpretationC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_OCT_Debug.epf"),
//			new Triple<>("AbstractInterpretationC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_CON_Debug.epf"),
//			new Triple<>("AbstractInterpretationC.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_OCT_Debug.epf"),


			//### C Inline
//			new Triple<>("AutomizerCInline.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default.epf"),
//			new Triple<>("AutomizerCInline.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_INT.epf"),
//			new Triple<>("AutomizerCInline.xml", C, "ai/svcomp-Reach-32bit-Automizer_Default+AIv2_OCT_Debug.epf"),
	};

	private static final String[] INPUT = new String[] {
//			"examples/programs/abstractInterpretation/regression",
//			"examples/svcomp/ssh-simplified/s3_srvr_13_false-unreach-call.cil.c",
//			"examples/rers2016/Problem10.c",
			"examples/programs/abstractInterpretation/CountTillBound-Loop-2.bpl",
//			"examples/programs/toy/tooDifficultLoopInvariant/Luxembourg.bpl",

			"examples/svcomp/loop-lit/mcmillan2006_true-unreach-call_true-termination.c.i",
//			"examples/svcomp/bitvector-loops/verisec_sendmail__tTflag_arr_one_loop_false-unreach-call.i",
//			"examples/svcomp/ssh-simplified/s3_clnt_1_true-unreach-call.cil.c",
//			"examples/programs/abstractInterpretation/regression",

//			"examples/svcomp/loops/n.c11_true-unreach-call.i",
//			"examples/svcomp/recursive-simple/fibo_10_true-unreach-call.c",
//			"examples/svcomp/recursive-simple/id2_i5_o5_false-unreach-call.c"

//			"examples/programs/abstractInterpretation/regression/proc-summary-usage.bpl",
//			"examples/programs/abstractInterpretation/regression/proc-summary-usage-2.bpl",
//			"examples/programs/abstractInterpretation/regression/recursive-CallABAB_simple_incorrect.bpl",
//			"examples/programs/abstractInterpretation/regression/recursive-easy-4.bpl",
//			"examples/programs/abstractInterpretation/regression/recursive-Collatz.bpl",
//			"examples/programs/abstractInterpretation/regression/proc-bool-ret-value.bpl",

//			"examples/programs/abstractInterpretation/regression/inequalityTest_reals.bpl",
//			"examples/programs/abstractInterpretation/regression/all/logicimplies-test.bpl",
//			"examples/programs/abstractInterpretation/regression/all/logiciff.bpl",
//			"examples/programs/abstractInterpretation/regression/all/procedure-statesplit-logiciff.bpl",
//			"examples/programs/abstractInterpretation/regression/all/recursive-easy-3.bpl",
//			"examples/programs/abstractInterpretation/regression/recursive-CallABAB_count.bpl",
//			"examples/programs/abstractInterpretation/regression/recursive-CallABAB_count_incorrect.bpl",

//			"examples/programs/abstractInterpretation/regression/all/bool-assume.bpl",
//			"examples/programs/abstractInterpretation/regression/varupdate-multiplication.bpl",
//			"examples/programs/abstractInterpretation/regression/modulo-assume-bug.bpl",
//			"examples/programs/abstractInterpretation/regression/modulo-assume-bug-npe.bpl",
//			"examples/programs/abstractInterpretation/regression/loop-CountTillBound-2.bpl",
//			"examples/programs/abstractInterpretation/regression/recursive-wrong-prestate.bpl",

//			"examples/programs/abstractInterpretation/regression/all/recursive-easy-2.bpl",
//			"examples/programs/abstractInterpretation/regression/all/recursive-easy-1.bpl",
//			"examples/programs/abstractInterpretation/regression/all/recursive-CallABAB_incorrect.bpl",
//			"examples/programs/abstractInterpretation/regression/recursive-CallABAB.bpl",
//			"examples/svcomp/loops/invert_string_false-unreach-call.i",
//			"examples/svcomp/loops/bubble_sort_false-unreach-call.i"
	};
	// @formatter:on

	@Override
	protected long getTimeout() {
		// return 90 * 1000 * 1000;
		return 90 * 1000;
		// return 30 * 1000;
	}

	@Override
	protected ColumnDefinition[] getColumnDefinitions() {
		return new ColumnDefinition[] {
				new ColumnDefinition("Runtime (ns)", "Avg. runtime", ConversionContext.Divide(1000000000, 2, " s"),
						Aggregate.Sum, Aggregate.Average),
				new ColumnDefinition("Allocated memory end (bytes)", "Memory",
						ConversionContext.Divide(1048576, 2, " MB"), Aggregate.Max, Aggregate.Average),
				new ColumnDefinition(CegarLoopStatisticsDefinitions.OverallIterations.toString(), "Iter{-}ations",
						ConversionContext.BestFitNumber(), Aggregate.Ignore, Aggregate.Average),
				new ColumnDefinition(CegarLoopStatisticsDefinitions.AbstIntIterations.toString(), "AI Iter{-}ations",
						ConversionContext.BestFitNumber(), Aggregate.Ignore, Aggregate.Average),
				new ColumnDefinition(CegarLoopStatisticsDefinitions.AbstIntStrong.toString(), "AI Strong",
						ConversionContext.BestFitNumber(), Aggregate.Ignore, Aggregate.Average),
				new ColumnDefinition(CegarLoopStatisticsDefinitions.AbstIntTime.toString(), "AI Avg. Time",
						ConversionContext.Divide(1000000000, 2, " s"), Aggregate.Sum, Aggregate.Average),
				new ColumnDefinition(CegarLoopStatisticsDefinitions.OverallTime.toString(), "Trace Abstraction Time",
						ConversionContext.Divide(1000000000, 2, " s"), Aggregate.Sum, Aggregate.Average),
				new ColumnDefinition("TraceCheckerStatistics_NumberOfCodeBlocks", null,
						ConversionContext.BestFitNumber(), Aggregate.Ignore, Aggregate.Average),
				new ColumnDefinition("TraceCheckerStatistics_SizeOfPredicatesFP", null,
						ConversionContext.BestFitNumber(), Aggregate.Ignore, Aggregate.Average),
				new ColumnDefinition("TraceCheckerStatistics_SizeOfPredicatesBP", null,
						ConversionContext.BestFitNumber(), Aggregate.Ignore, Aggregate.Average),
				new ColumnDefinition("TraceCheckerStatistics_Conjuncts in SSA", null, ConversionContext.BestFitNumber(),
						Aggregate.Ignore, Aggregate.Average),
				new ColumnDefinition("TraceCheckerStatistics_Conjuncts in UnsatCore", null,
						ConversionContext.BestFitNumber(), Aggregate.Ignore, Aggregate.Average),
				new ColumnDefinition("InterpolantCoveringCapability", "ICC", ConversionContext.Percent(true, 2),
						Aggregate.Ignore, Aggregate.Average), };
	}

	@Override
	public ITestResultDecider constructITestResultDecider(final UltimateRunDefinition urd) {
		return new OverapproximatingSafetyCheckTestResultDecider(urd, false);
	}

	@Override
	public Collection<UltimateTestCase> createTestCases() {
		for (final Triple<String, String[], String> triple : TOOLCHAINS) {
			final DirectoryFileEndingsPair[] pairs = new DirectoryFileEndingsPair[INPUT.length];
			for (int i = 0; i < INPUT.length; ++i) {
				pairs[i] = new DirectoryFileEndingsPair(INPUT[i], triple.getSecond(), DEFAULT_LIMIT);
			}
			addTestCase(triple.getFirst(), triple.getThird(), pairs);
		}
		return super.createTestCases();
	}
}
