/**
 * 
 */
package de.uni_freiburg.informatik.ultimatetest.traceabstraction;

import java.util.Collection;

import de.uni_freiburg.informatik.ultimatetest.UltimateTestCase;

/**
 * @author heizmann@informatik.uni-freiburg.de
 *
 */
public class All_TreeInterpolants extends
		AbstractTraceAbstractionTestSuite {
	private static final String m_Path = "examples/programs/";
	
	// Time out for each test case in milliseconds
	private static int m_Timeout = 5000;

	private static final boolean s_Boogie_TreeInterpolants = true;
	private static final boolean s_C_TreeInterpolants = true;
	
	@Override
	public Collection<UltimateTestCase> createTestCases() {
		if (s_Boogie_TreeInterpolants) {
			addTestCases(
					"TraceAbstraction.xml",
					"TreeInterpolants.epf",
				    m_Path,
				    new String[] {".bpl"},
				    "TraceAbstraction via tree interpolation",
				    "Boogie",
				    m_Timeout);
		} 
		if (s_C_TreeInterpolants) {
			addTestCases(
					"TraceAbstractionC.xml",
					"TreeInterpolants.epf",
				    m_Path,
				    new String[] {".c", ".i"},
				    "TraceAbstraction via tree interpolation",
				    "C",
				    m_Timeout);
		}
		return super.createTestCases();
	}
}
