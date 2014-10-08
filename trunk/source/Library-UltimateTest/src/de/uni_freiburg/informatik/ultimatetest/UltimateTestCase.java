package de.uni_freiburg.informatik.ultimatetest;

import static org.junit.Assert.fail;

import java.util.List;

import org.apache.log4j.Logger;

import de.uni_freiburg.informatik.junit_helper.testfactory.FactoryTestMethod;
import de.uni_freiburg.informatik.ultimate.util.ExceptionUtils;
import de.uni_freiburg.informatik.ultimatetest.decider.ITestResultDecider;
import de.uni_freiburg.informatik.ultimatetest.decider.ITestResultDecider.TestResult;
import de.uni_freiburg.informatik.ultimatetest.summary.IIncrementalLog;
import de.uni_freiburg.informatik.ultimatetest.summary.ITestSummary;

/**
 * @author dietsch
 * 
 */
public class UltimateTestCase {

	private final String mName;
	private final UltimateRunDefinition mUltimateRunDefinition;
	private final UltimateStarter mStarter;
	private final ITestResultDecider mDecider;
	private final List<ITestSummary> mSummaries;
	private final List<IIncrementalLog> mLogs;
	private final Logger mLogger;

	public UltimateTestCase(String name, ITestResultDecider decider, UltimateStarter starter,
			UltimateRunDefinition ultimateRunDefinition, List<ITestSummary> summaries,
			List<IIncrementalLog> incrementalLogs) {
		mLogger = Logger.getLogger(UltimateStarter.class);
		mStarter = starter;
		mName = name;
		mDecider = decider;
		mSummaries = summaries;
		mUltimateRunDefinition = ultimateRunDefinition;
		mLogs = incrementalLogs;
	}

	@FactoryTestMethod
	public void test() {

		Throwable th = null;

		TestResult result = TestResult.FAIL;

		try {
			updateLogsPreStart();
			mStarter.runUltimate();
			result = mDecider.getTestResult(mStarter.getServices().getResultService());
		} catch (Throwable e) {
			th = e;
			result = mDecider.getTestResult(mStarter.getServices().getResultService(), e);
			mLogger.fatal(String.format("There was an exception during the execution of Ultimate: %s%n%s", e,
					ExceptionUtils.getStackTrace(e)));
		} finally {

			boolean success = mDecider.getJUnitSuccess(result);
			updateSummaries(result);
			updateLogsPostCompletion(result);
			mStarter.complete();

			if (!success) {
				String message = mDecider.getResultMessage();
				if (message == null) {
					message = "ITestResultDecider provided no message";
				}
				if (th != null) {
					message += " (Ultimate threw an Exception: " + th.getMessage() + ")";
				}
				fail(message);
			}
		}
	}

	private void updateLogsPreStart() {
		if (mLogs != null) {
			for (IIncrementalLog log : mLogs) {
				log.addEntryPreStart(mUltimateRunDefinition);
			}
		}
	}

	private void updateLogsPostCompletion(TestResult result) {
		if (mLogs != null) {
			for (IIncrementalLog log : mLogs) {
				log.addEntryPostCompletion(mUltimateRunDefinition, result, mDecider.getResultCategory(),
						mDecider.getResultMessage(), mStarter.getServices());
			}
		}
	}

	private void updateSummaries(TestResult result) {
		if (mSummaries != null) {
			for (ITestSummary summary : mSummaries) {
				summary.addResult(mUltimateRunDefinition, result, mDecider.getResultCategory(),
						mDecider.getResultMessage(), mName, mStarter.getServices().getResultService());
			}
		}
	}

	@Override
	public String toString() {
		return mName;
	}
}