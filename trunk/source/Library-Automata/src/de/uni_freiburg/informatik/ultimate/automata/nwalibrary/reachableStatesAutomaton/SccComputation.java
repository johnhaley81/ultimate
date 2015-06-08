package de.uni_freiburg.informatik.ultimate.automata.nwalibrary.reachableStatesAutomaton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Logger;

import petruchio.interfaces.algorithms.StructuralCongruence;
import de.uni_freiburg.informatik.ultimate.core.services.IUltimateServiceProvider;

/**
 * Offers a method to compute the strongly connected components (SCCs) of
 * the game graph. Implementation of Tarjan SCC algorithm. {@link http
 * ://en.wikipedia
 * .org/wiki/Tarjan%27s_strongly_connected_components_algorithm}
 * 
 * @author heizmann@informatik.uni-freiburg.de
 */
public class SccComputation<NODE, COMP extends StronglyConnectedComponent<NODE>> {

	private final IUltimateServiceProvider m_Services;
	private final Logger m_Logger;
	
	private final IStronglyConnectedComponentFactory<NODE, COMP> m_SccFactory;
	protected final ISuccessorProvider<NODE> m_SuccessorProvider;
	
	/**
	 * Number of all states to which the SCC computation is applied.
	 */
	private final int m_NumberOfAllStates;
	/**
	 * Number of vertices that have been processed so far.
	 */
	int m_Index = 0;
	/**
	 * Vertices that have not yet been assigned to any SCC.
	 */
	protected final StackHashSet<NODE> m_NoScc = new StackHashSet<NODE>();
	/**
	 * Assigns to each vertex v the number of vertices that have been
	 * processed before in this algorithm. This number is called the index
	 * of v.
	 */
	protected final Map<NODE, Integer> m_Indices = new HashMap<NODE, Integer>();
	protected final Map<NODE, Integer> m_LowLinks = new HashMap<NODE, Integer>();
	protected final Collection<COMP> m_Balls = new ArrayList<COMP>();
	private int m_NumberOfNonBallSCCs = 0;

	


	public SccComputation(IUltimateServiceProvider services, Logger logger,
			ISuccessorProvider<NODE> successorProvider,
			IStronglyConnectedComponentFactory<NODE, COMP> sccFac,
			int numberOfAllNodes,
			Set<NODE> startNodes) {
		super();
		m_Services = services;
		m_Logger = logger;
		m_SccFactory = sccFac;
		m_SuccessorProvider = successorProvider;
		m_NumberOfAllStates = numberOfAllNodes;
		
		for (NODE node : startNodes) {
			if (!m_Indices.containsKey(node)) {
				strongconnect(node);
			}
		}
		assert (automatonPartitionedBySCCs());
		m_Logger.info("Graph consists of " + getBalls().size() + 
				" InCaSumBalls and " + m_NumberOfNonBallSCCs
				+ " non ball SCCs. Number of states in SCCs "
				+ m_NumberOfAllStates + ".");
	}


	public interface IStronglyConnectedComponentFactory<NODE, C extends StronglyConnectedComponent<NODE>> {
			public C constructNewSCComponent();
	}
	
	public interface ISuccessorProvider<NODE> {
		public Iterator<NODE> getSuccessors(NODE node);
	}

	public Collection<COMP> getBalls() {
		return m_Balls;
	}

	protected void strongconnect(NODE v) {
		assert (!m_Indices.containsKey(v));
		assert (!m_LowLinks.containsKey(v));
		m_Indices.put(v, m_Index);
		m_LowLinks.put(v, m_Index);
		m_Index++;
		this.m_NoScc.push(v);
		
		Iterator<NODE> it = m_SuccessorProvider.getSuccessors(v);
		while(it.hasNext()) {
			NODE succCont = it.next();
			processSuccessor(v, succCont);
		}
	
		if (m_LowLinks.get(v).equals(m_Indices.get(v))) {
			establishNewComponent(v);
		}
	}

	protected void establishNewComponent(NODE v) {
		NODE w;
		COMP scc = m_SccFactory.constructNewSCComponent();
		do {
			w = m_NoScc.pop();
			scc.addNode(w);
		} while (v != w);
		scc.setRootNode(w);
		if (isBall(scc)) {
			m_Balls.add(scc);
		} else {
			m_NumberOfNonBallSCCs++;
		}
	}

	private void processSuccessor(NODE v, NODE w) {
		if (!m_Indices.containsKey(w)) {
			strongconnect(w);
			updateLowlink(v, m_LowLinks.get(w));
		} else if (m_NoScc.contains(w)) {
			updateLowlink(v, m_Indices.get(w));
		}
	}

	protected void updateLowlink(NODE node, int newValueCandidate) {
		int min = Math.min(m_LowLinks.get(node), newValueCandidate);
		m_LowLinks.put(node, min);
	}

	boolean isBall(StronglyConnectedComponent<NODE> scc) {
		if (scc.getNumberOfStates() == 1) {
			NODE cont = scc.getRootNode();
			Iterator<NODE> it = m_SuccessorProvider.getSuccessors(cont);
			while(it.hasNext()) {
				NODE succCont = it.next();
				if (cont.equals(succCont)) {
					return true;
				}
			}
			return false;
		} else {
			assert scc.getNumberOfStates() > 1;
			return true;
		}
	}

	/**
	 * @return true iff the SCCS form a partition of the automaton.
	 */
	protected boolean automatonPartitionedBySCCs() {
		int statesInAllBalls = 0;
		int max = 0;
		for (StronglyConnectedComponent<NODE> scc : m_Balls) {
			statesInAllBalls += scc.getNumberOfStates();
			max = Math.max(max, scc.getNumberOfStates());
		}
		m_Logger.debug("The biggest SCC has " + max + " vertices.");
		boolean sameNumberOfVertices = (statesInAllBalls + m_NumberOfNonBallSCCs == m_NumberOfAllStates);
		return sameNumberOfVertices;
	}


	/**
	 * Stack and Set in one object. Elements that are already contained 
	 * must not be added.
	 * @author Matthias Heizmann
	 *
	 */
	class StackHashSet<NODE> {
		private final Stack<NODE> m_Stack = new Stack<>();
		private final Set<NODE> m_Set = new HashSet<>();
		
		public NODE pop() {
			NODE node = m_Stack.pop();
			m_Set.remove(node);
			return node;
		}
		
		public void push(NODE node) {
			m_Stack.push(node);
			boolean modified = m_Set.add(node);
			if (!modified) {
				throw new IllegalArgumentException("Illegal to add element twice " + node);
			}
		}
		
		public boolean contains(NODE node) {
			return m_Set.contains(node);
		}
	}


}