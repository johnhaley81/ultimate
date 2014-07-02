package de.uni_freiburg.informatik.ultimate.ep.interfaces;

import java.util.List;

import de.uni_freiburg.informatik.ultimate.access.IObserver;
import de.uni_freiburg.informatik.ultimate.model.GraphType;

public interface ITool extends IToolchainPlugin{
	
	/**
	 * Provides Keywords for model selection. Depending on a Keyword and the 
	 * params in {@link ITool#getDesiredToolID()} core will select the 
	 * appropriate model for your tool.
	 * <ul>  
	 * <li>ALL - Core calls this Plugin for all roots of all present models</li> 
	 * <li>USER - Core presents a selection of all possible models before the
	 * toolchain starts </li> 
	 * <li>LAST - Core gives you the last modified model</li>  
	 * <li>SOURCE - Core gives you the model generated by the ISource Plugins
	 * </li> 
	 * <li>TOOL - Core calls {@link ITool#getDesiredToolID()} and expects the PLUGIN_ID 
	 * (or an array of PLUGIN_IDs) of the model-generating tools</li></ul> 
	 * @author  dietsch
	 */
	enum QueryKeyword{
		/**
		 * Give all models to the observers of a plugin.
		 */
		ALL,
		/**
		 * Let user choose model to operate on.
		 */
		USER,
		/**
		 * Operate on the last modified model.
		 */
		LAST,
		/**
		 * Request the original source model (usually a parse tree).
		 */
		SOURCE,
		/**
		 * Try automatic tool selection based on
		 * {@link ITool#getDesiredToolID()}.
		 */
		TOOL
	}
	
	/**
	 * Does this tool require a GUI to run?
	 * 
	 * @return yes / no
	 */
	boolean isGuiRequired(); 
	
	
	/**
	 * The core calls this method to determine which model he has to provide (AST, transformed AST, generated graphs like CG etc) 
	 * 
	 * @return A keyword as defined in QueryKeyword
	 */
	QueryKeyword getQueryKeyword();

	/**
	 * If getQuery() returns TOOL as keyword, the core calls getTool() and
	 * expects 1 or more PLUGIN_IDs of ITools. The models generated or modified
	 * by them are given to you. If you insert SOURCE in this array the core
	 * will give you additionally the base AST generated by the ISource Plugin.
	 * If you insert the PLUGIN_ID of an ISource Plugin the core will give you
	 * only models generated or transformed from the AST of this plugin,
	 * ignoring everything contradicting to this condition
	 * 
	 * @return one or more PLUGIN_IDs
	 */
	List<String> getDesiredToolID();
	
	/**
	 * Before calling a plugins processing method the core calls
	 * setInputDefinition to tell the plugin what kind of model has to be
	 * processed
	 * 
	 * @param graphType
	 *            The Description of the model. Contains informations about the
	 *            incoming data structure (like, Graph, Tree, cyclic, acyclic,
	 *            etc.)
	 */
	void setInputDefinition(GraphType graphType);
	
	/**
	 * 
	 * This method asks all tools for their implementations of IObserver. If you
	 * do not want to execute anything for the current model (as seen by
	 * {@link #setInputDefinition(GraphType)}, just return an empty list.
	 * 
	 * @return All observers which should be run for this tool. You may not
	 *         return null.
	 */
	List<IObserver> getObservers();	
}
