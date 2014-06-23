package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretation;

import java.util.Collections;
import java.util.List;

import de.uni_freiburg.informatik.ultimate.access.IObserver;
import de.uni_freiburg.informatik.ultimate.core.preferences.UltimatePreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.ep.interfaces.IAnalysis;
import de.uni_freiburg.informatik.ultimate.model.GraphType;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretation.Activator;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretation.AbstractInterpretationObserver;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretation.preferences.AbstractInterpretationPreferenceInitializer;

/**
 * Main class of Plug-In AbstractInterpretation
 * 
 * @author Christopher Dillo
 */
public class AbstractInterpretation implements IAnalysis {

	private static final String s_PLUGIN_NAME = Activator.s_PLUGIN_NAME;
	private static final String s_PLUGIN_ID = Activator.s_PLUGIN_ID;
	
	private AbstractInterpretationObserver m_Observer;
	private GraphType m_InputDefinition;

	@Override
	public GraphType getOutputDefinition() {
		return new GraphType(Activator.s_PLUGIN_ID,
				m_InputDefinition.getType(), m_InputDefinition.getFileNames());
	}

	@Override
	public boolean isGuiRequired() {
		return false;
	}

	@Override
	public QueryKeyword getQueryKeyword() {
		return QueryKeyword.LAST;
	}

	@Override
	public List<String> getDesiredToolID() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setInputDefinition(GraphType graphType) {
		this.m_InputDefinition = graphType;
	}

	@Override
	public List<IObserver> getObservers() {
		return Collections.singletonList((IObserver) m_Observer);
	}

	@Override
	public int init() {
		m_Observer = new AbstractInterpretationObserver();
		return 0;
	}

	@Override
	public String getName() {
		return s_PLUGIN_NAME;
	}

	@Override
	public String getPluginID() {
		return s_PLUGIN_ID;
	}

	@Override
	public UltimatePreferenceInitializer getPreferences() {
		return new AbstractInterpretationPreferenceInitializer();
	}

}
