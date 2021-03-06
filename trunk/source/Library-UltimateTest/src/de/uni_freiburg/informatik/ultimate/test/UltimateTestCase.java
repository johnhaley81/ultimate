/*
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE UnitTest Library.
 *
 * The ULTIMATE UnitTest Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE UnitTest Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE UnitTest Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE UnitTest Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE UnitTest Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.test;

import java.util.List;

import org.eclipse.core.runtime.IStatus;

import de.uni_freiburg.informatik.ultimate.core.coreplugin.exceptions.LivecycleException;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IResultService;
import de.uni_freiburg.informatik.ultimate.test.decider.ITestResultDecider;
import de.uni_freiburg.informatik.ultimate.test.decider.ITestResultDecider.TestResult;
import de.uni_freiburg.informatik.ultimate.test.junitextension.testfactory.FactoryTestMethod;
import de.uni_freiburg.informatik.ultimate.test.reporting.IIncrementalLog;
import de.uni_freiburg.informatik.ultimate.test.reporting.ITestSummary;
import de.uni_freiburg.informatik.ultimate.util.ExceptionUtils;

/**
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public class UltimateTestCase implements Comparable<UltimateTestCase> {

	private final String mName;
	private final UltimateRunDefinition mUltimateRunDefinition;
	private final UltimateStarter mStarter;
	private final ITestResultDecider mDecider;
	private final List<ITestSummary> mSummaries;
	private final List<IIncrementalLog> mLogs;
	private final ConsoleLogger mTestLogger;

	public UltimateTestCase(final String name, final ITestResultDecider decider, final UltimateStarter starter,
			final UltimateRunDefinition ultimateRunDefinition, final List<ITestSummary> summaries,
			final List<IIncrementalLog> incrementalLogs) {
		if (ultimateRunDefinition == null) {
			throw new IllegalArgumentException("ultimateRunDefinition");
		}
		mStarter = starter;
		mName = name;
		mDecider = decider;
		mSummaries = summaries;
		mUltimateRunDefinition = ultimateRunDefinition;
		mLogs = incrementalLogs;
		mTestLogger = new ConsoleLogger();
	}

	@FactoryTestMethod
	public void test() {
		// call the garbage collector before starting a new test
		System.gc();
		System.runFinalization();
		System.gc();
		Runtime.getRuntime().gc();

		// start debug code: use this only in controlled situations!
		// try {
		// Thread.sleep(500);
		// } catch (InterruptedException e1) {
		// }
		// HeapDumper.dumpHeap("F:\\tmp\\ultimate benchmarks\\heapdump", false);
		// end debug ode

		Throwable th = null;
		TestResult result = TestResult.FAIL;
		boolean livecycleFailure = false;

		try {
			updateLogsPreStart();
			final String deciderName = mDecider.getClass().getSimpleName();
			final IStatus returnCode = mStarter.runUltimate();
			// logging service is only available after runUltimate() has been called
			final ILogger logger = mStarter.getServices().getLoggingService().getLogger(UltimateStarter.class);
			logger.info("Deciding this test: " + deciderName);
			result = mDecider.getTestResult(mStarter.getServices());
			if (!returnCode.isOK() && result != TestResult.FAIL) {
				logger.fatal("#################### Overwriting decision of " + deciderName
						+ " and setting test status to FAIL ####################");
				logger.fatal("Ultimate returned an unexpected status:");
				logger.fatal("Code      " + returnCode.getCode());
				logger.fatal("Severity  " + returnCode.getSeverity());
				logger.fatal("Message   " + returnCode.getMessage());
				logger.fatal("Plugin ID " + returnCode.getPlugin());
				if (returnCode.getException() != null) {
					logger.fatal("Exception:", returnCode.getException());
				}
				result = TestResult.FAIL;
			}

		} catch (final LivecycleException lex) {
			// if this happens, mStarter, mLogger, etc. are not initialized
			th = lex;
			result = mDecider.getTestResult(null, lex);
			lex.printStackTrace();
			livecycleFailure = true;
		} catch (final Throwable e) {
			th = e;
			result = mDecider.getTestResult(mStarter.getServices(), e);
			final ILogger logger = mStarter.getServices().getLoggingService().getLogger(UltimateStarter.class);
			logger.fatal(String.format("There was an exception during the execution of Ultimate: %s%n%s", e,
					ExceptionUtils.getStackTrace(e)));
		} finally {
			boolean success = false;

			if (!livecycleFailure) {
				success = mDecider.getJUnitSuccess(result);
			}

			updateSummaries(result);
			updateLogsPostCompletion(result);
			mStarter.complete();

			if (!success) {
				String message = null;

				if (!livecycleFailure) {
					message = mDecider.getResultMessage();
				}

				if (message == null) {
					message = "ITestResultDecider provided no message";
				}
				if (th != null) {
					message += " (Ultimate threw an Exception: " + th.getMessage() + ")";
					failTest(message, th);
				} else {
					failTest(message);
				}
			}
		}
	}

	private void updateLogsPreStart() {
		if (mLogs == null) {
			return;
		}
		for (final IIncrementalLog log : mLogs) {
			log.addEntryPreStart(mUltimateRunDefinition, mTestLogger);
		}

	}

	private void updateLogsPostCompletion(final TestResult result) {
		if (mLogs == null) {
			return;
		}
		for (final IIncrementalLog log : mLogs) {
			log.addEntryPostCompletion(mUltimateRunDefinition, result, mDecider.getResultCategory(),
					mDecider.getResultMessage(), mStarter.getServices(), mTestLogger);
		}
	}

	private void updateSummaries(final TestResult result) {
		if (mSummaries == null) {
			return;
		}

		IResultService rservice = null;
		if (mStarter.getServices() != null) {
			rservice = mStarter.getServices().getResultService();
			assert rservice != null : "Could not retrieve ResultService";
		}

		for (final ITestSummary summary : mSummaries) {
			summary.addResult(mUltimateRunDefinition, result, mDecider.getResultCategory(), mDecider.getResultMessage(),
					mName, rservice);
		}
	}

	public UltimateRunDefinition getUltimateRunDefinition() {
		return mUltimateRunDefinition;
	}

	@Override
	public String toString() {
		return mName;
	}

	private static void failTest(final String message) throws UltimateTestFailureException {
		final UltimateTestFailureException exception = new UltimateTestFailureException(message);
		final StackTraceElement elem = new StackTraceElement(UltimateTestCase.class.getPackage().getName(), "test",
				"UltimateTestCase.java", -1);
		exception.setStackTrace(new StackTraceElement[] { elem });
		throw exception;
	}

	private static void failTest(final String message, final Throwable th) throws UltimateTestFailureException {
		final UltimateTestFailureException exception = new UltimateTestFailureException(message, th);
		final StackTraceElement elem = new StackTraceElement(UltimateTestCase.class.getPackage().getName(), "test",
				"UltimateTestCase.java", -1);
		exception.setStackTrace(new StackTraceElement[] { elem });
		throw exception;
	}

	@Override
	public int compareTo(final UltimateTestCase o) {
		return mUltimateRunDefinition.compareTo(o.mUltimateRunDefinition);
	}
}
