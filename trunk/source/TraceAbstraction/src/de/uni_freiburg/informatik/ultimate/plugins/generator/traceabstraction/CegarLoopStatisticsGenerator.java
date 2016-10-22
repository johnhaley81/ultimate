/*
 * Copyright (C) 2014-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
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

import java.util.Collection;

import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.CegarStatisticsType.SizeIterationPair;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.CoverageAnalysis.BackwardCoveringInformation;
import de.uni_freiburg.informatik.ultimate.util.statistics.IStatisticsDataProvider;
import de.uni_freiburg.informatik.ultimate.util.statistics.IStatisticsType;
import de.uni_freiburg.informatik.ultimate.util.statistics.StatisticsData;
import de.uni_freiburg.informatik.ultimate.util.statistics.StatisticsGeneratorWithStopwatches;

public class CegarLoopStatisticsGenerator extends StatisticsGeneratorWithStopwatches
		implements IStatisticsDataProvider {

	private Object mResult;
	private final StatisticsData mEcData = new StatisticsData();
	private final StatisticsData mPuData = new StatisticsData();
	private final StatisticsData mTcData = new StatisticsData();
	private final StatisticsData mTiData = new StatisticsData();
	private final StatisticsData mInterpolantConsolidationBenchmarks = new StatisticsData();
	private int mStatesRemovedByMinimization = 0;
	private int mMinimizationAttempts = 0;
	private int mIterations = 0;
	private int mAbsIntIterations = 0;
	private SizeIterationPair mBiggestAbstraction = new SizeIterationPair(-1, -1);
	private BackwardCoveringInformation mBCI = new BackwardCoveringInformation(0, 0);
	private int mAbsIntStrong = 0;
	private int mTraceHistogramMaximum = 0;

	@Override
	public Collection<String> getKeys() {
		return getBenchmarkType().getKeys();
	}

	public void setResult(final Object result) {
		mResult = result;
	}

	public void addEdgeCheckerData(final IStatisticsDataProvider ecbd) {
		mEcData.aggregateBenchmarkData(ecbd);
	}

	public void addPredicateUnifierData(final IStatisticsDataProvider pubd) {
		mPuData.aggregateBenchmarkData(pubd);
	}

	public void addTraceCheckerData(final IStatisticsDataProvider tcbd) {
		mTcData.aggregateBenchmarkData(tcbd);
	}

	public void addInterpolationConsolidationData(final IStatisticsDataProvider tcbd) {
		mInterpolantConsolidationBenchmarks.aggregateBenchmarkData(tcbd);
	}

	public void addTotalInterpolationData(final IStatisticsDataProvider tibd) {
		mTiData.aggregateBenchmarkData(tibd);
	}

	public void addBackwardCoveringInformation(final BackwardCoveringInformation bci) {
		mBCI = new BackwardCoveringInformation(mBCI, bci);
	}

	public void announceStatesRemovedByMinimization(final int statesRemoved) {
		mStatesRemovedByMinimization += statesRemoved;
	}

	public void announceNextIteration() {
		mIterations++;
	}

	public void announceNextAbsIntIteration() {
		mAbsIntIterations++;
	}

	public void announceStrongAbsInt() {
		mAbsIntStrong++;
	}

	/**
	 * @return true iff size is the new maximum
	 */
	public boolean reportAbstractionSize(final int size, final int iteration) {
		if (size > mBiggestAbstraction.getSize()) {
			mBiggestAbstraction = new SizeIterationPair(size, iteration);
			return true;
		}
		return false;
	}

	public void reportTraceHistogramMaximum(final int maxCurrentTrace) {
		if (maxCurrentTrace > mTraceHistogramMaximum) {
			mTraceHistogramMaximum = maxCurrentTrace;
		}
	}

	public void reportMinimizationAttempt() {
		mMinimizationAttempts++;
	}

	@Override
	public Object getValue(final String key) {
		final CegarLoopStatisticsDefinitions keyEnum = Enum.valueOf(CegarLoopStatisticsDefinitions.class, key);
		switch (keyEnum) {
		case Result:
			return mResult;
		case OverallTime:
		case AutomataDifference:
		case DeadEndRemovalTime:
		case AutomataMinimizationTime:
		case HoareAnnotationTime:
		case BasicInterpolantAutomatonTime:
		case AbstIntTime:
			try {
				return getElapsedTime(key);
			} catch (final StopwatchStillRunningException e) {
				throw new AssertionError("clock still running: " + key);
			}
		case HoareTripleCheckerStatistics:
			return mEcData;
		case PredicateUnifierStatistics:
			return mPuData;
		case TraceCheckerStatistics:
			return mTcData;
		case InterpolantConsolidationStatistics:
			return mInterpolantConsolidationBenchmarks;
		case TotalInterpolationStatistics:
			return mTiData;
		case StatesRemovedByMinimization:
			return mStatesRemovedByMinimization;
		case MinimizatonAttempts:
			return mMinimizationAttempts;
		case OverallIterations:
			return mIterations;
		case TraceHistogramMax:
			return mTraceHistogramMaximum;
		case AbstIntIterations:
			return mAbsIntIterations;
		case AbstIntStrong:
			return mAbsIntStrong;
		case BiggestAbstraction:
			return mBiggestAbstraction;
		case InterpolantCoveringCapability:
			return mBCI;
		default:
			throw new AssertionError("unknown data");
		}
	}

	@Override
	public IStatisticsType getBenchmarkType() {
		return CegarStatisticsType.getInstance();
	}

	@Override
	public String[] getStopwatches() {
		return new String[] { CegarLoopStatisticsDefinitions.OverallTime.toString(),
				CegarLoopStatisticsDefinitions.AbstIntTime.toString(),
				CegarLoopStatisticsDefinitions.AutomataDifference.toString(),
				CegarLoopStatisticsDefinitions.DeadEndRemovalTime.toString(),
				CegarLoopStatisticsDefinitions.AutomataMinimizationTime.toString(),
				CegarLoopStatisticsDefinitions.HoareAnnotationTime.toString(),
				CegarLoopStatisticsDefinitions.BasicInterpolantAutomatonTime.toString() };
	}

}
