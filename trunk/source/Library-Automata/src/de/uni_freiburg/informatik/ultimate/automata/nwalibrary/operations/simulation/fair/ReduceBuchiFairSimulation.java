/*
 * Copyright (C) 2015-2016 Daniel Tischner
 * Copyright (C) 2009-2016 University of Freiburg
 * 
 * This file is part of the ULTIMATE Automata Library.
 * 
 * The ULTIMATE Automata Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE Automata Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automata Library. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Automata Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP), 
 * containing parts covered by the terms of the Eclipse Public License, the 
 * licensors of the ULTIMATE Automata Library grant you additional permission 
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.simulation.fair;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.IOperation;
import de.uni_freiburg.informatik.ultimate.automata.LibraryIdentifiers;
import de.uni_freiburg.informatik.ultimate.automata.OperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.ResultChecker;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomatonOldApi;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.NestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.StateFactory;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.StringFactory;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.GetRandomDfa;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.GetRandomNwa;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.simulation.vertices.DuplicatorVertex;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.simulation.vertices.SpoilerVertex;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.simulation.vertices.Vertex;
import de.uni_freiburg.informatik.ultimate.core.services.ToolchainStorage;

/**
 * Operation that reduces a given buechi automaton by using
 * {@link FairSimulation}.<br/>
 * Once constructed the reduction automatically starts, the result can be get by
 * using {@link #getResult()}.
 * 
 * @author Daniel Tischner
 * 
 * @param <LETTER>
 *            Letter class of buechi automaton
 * @param <STATE>
 *            State class of buechi automaton
 */
public class ReduceBuchiFairSimulation<LETTER, STATE> implements IOperation<LETTER, STATE> {

	/**
	 * Demo usage of fair simulation in general. Also used for debugging
	 * purpose.
	 * 
	 * @param args
	 *            Not supported
	 * @throws OperationCanceledException
	 *             If the operation was canceled, for example from the Ultimate
	 *             framework.
	 */
	public static void main(final String[] args) throws OperationCanceledException {
		// Test correctness of operation using random automata or single given
		// instances

		ToolchainStorage services = new ToolchainStorage();
		StateFactory<String> snf = (StateFactory<String>) new StringFactory();

		// Buechi automaton
		Set<String> alphabet = new HashSet<>();
		alphabet.add("a");
		alphabet.add("b");
		NestedWordAutomaton<String, String> buechi = new NestedWordAutomaton<>(new AutomataLibraryServices(services),
				alphabet, null, null, snf);

		// Big example from Matthias cardboard
		// buechi.addState(true, false, "q0");
		// buechi.addState(false, false, "q1");
		// buechi.addState(false, true, "q2");
		// buechi.addState(false, false, "q3");
		// buechi.addState(false, true, "q4");
		// buechi.addInternalTransition("q0", "a", "q1");
		// buechi.addInternalTransition("q1", "a", "q1");
		// buechi.addInternalTransition("q1", "a", "q2");
		// buechi.addInternalTransition("q2", "a", "q2");
		// buechi.addInternalTransition("q2", "a", "q1");
		// buechi.addInternalTransition("q0", "a", "q3");
		// buechi.addInternalTransition("q3", "b", "q3");
		// buechi.addInternalTransition("q3", "a", "q4");
		// buechi.addInternalTransition("q4", "a", "q4");
		// buechi.addInternalTransition("q4", "b", "q3");

		// Small example from cav02 paper
		// buechi.addState(true, true, "q1");
		// buechi.addState(false, false, "q2");
		// buechi.addInternalTransition("q1", "a", "q1");
		// buechi.addInternalTransition("q1", "b", "q2");
		// buechi.addInternalTransition("q2", "b", "q2");
		// buechi.addInternalTransition("q2", "a", "q1");

		// Small example from cav02 paper extended so that nodes
		// share the same transitions
		// buechi.addState(true, true, "q1");
		// buechi.addState(false, false, "q2");
		// buechi.addInternalTransition("q1", "a", "q1");
		// buechi.addInternalTransition("q1", "b", "q1");
		// buechi.addInternalTransition("q1", "a", "q2");
		// buechi.addInternalTransition("q1", "b", "q2");
		// buechi.addInternalTransition("q2", "a", "q2");
		// buechi.addInternalTransition("q2", "b", "q2");
		// buechi.addInternalTransition("q2", "a", "q1");
		// buechi.addInternalTransition("q2", "b", "q1");

		// Small circle example from mind
		// buechi.addState(true, true, "q1");
		// buechi.addState(false, false, "q2");
		// buechi.addState(true, false, "q3");
		// buechi.addState(false, false, "q4");
		// buechi.addInternalTransition("q1", "a", "q2");
		// buechi.addInternalTransition("q2", "b", "q3");
		// buechi.addInternalTransition("q3", "a", "q4");
		// buechi.addInternalTransition("q4", "b", "q1");

		// Non merge-able example with a one-directed fair simulation
		// buechi.addState(true, true, "q0");
		// buechi.addState(false, false, "q1");
		// buechi.addInternalTransition("q0", "b", "q0");
		// buechi.addInternalTransition("q0", "a", "q1");
		// buechi.addInternalTransition("q1", "a", "q1");
		// buechi.addInternalTransition("q1", "b", "q1");

		// Big example from cav02
		// buechi.addState(true, false, "q1");
		// buechi.addState(false, false, "q2");
		// buechi.addState(false, true, "q3");
		// buechi.addState(false, true, "q4");
		// buechi.addState(false, false, "q5");
		// buechi.addState(false, true, "q6");
		// buechi.addState(false, false, "q7");
		// buechi.addState(false, false, "q8");
		// buechi.addState(false, false, "q9");
		// buechi.addState(false, true, "q10");
		// buechi.addInternalTransition("q1", "a", "q2");
		// buechi.addInternalTransition("q1", "a", "q3");
		// buechi.addInternalTransition("q2", "a", "q6");
		// buechi.addInternalTransition("q2", "b", "q4");
		// buechi.addInternalTransition("q2", "b", "q7");
		// buechi.addInternalTransition("q4", "a", "q2");
		// buechi.addInternalTransition("q6", "a", "q6");
		// buechi.addInternalTransition("q3", "b", "q5");
		// buechi.addInternalTransition("q3", "b", "q7");
		// buechi.addInternalTransition("q5", "a", "q3");
		// buechi.addInternalTransition("q7", "b", "q8");
		// buechi.addInternalTransition("q8", "a", "q9");
		// buechi.addInternalTransition("q8", "b", "q10");
		// buechi.addInternalTransition("q9", "a", "q9");
		// buechi.addInternalTransition("q9", "b", "q10");
		// buechi.addInternalTransition("q10", "b", "q10");

		// Comparing test
		boolean logNoErrorDebug = false;

		int n = 50;
		int k = 20;
		int f = 10;
		int totalityInPerc = 5;
		int debugPrintEvery = 10;
		int amount = 100;

		System.out.println("Start comparing test 'SCC vs. nonSCC' with " + amount + " random automata (n=" + n + ", k="
				+ k + ", f=" + f + ", totPerc=" + totalityInPerc + ")...");

		for (int i = 1; i <= amount; i++) {
			if (i % debugPrintEvery == 0) {
				System.out.println("\tWorked " + i + " automata");
			}

			if (logNoErrorDebug) {
				System.out.println("Start calculating random DFA (n=" + n + ", k=" + k + ", f=" + f + ", totPerc="
						+ totalityInPerc + ")...");
			}

			// Generate automaton
			boolean useNwaInsteadDfaMethod = false;
			if (useNwaInsteadDfaMethod) {
				buechi = new GetRandomNwa(new AutomataLibraryServices(services), k, n, 0.2, 0, 0,
						(totalityInPerc + 0.0f) / 100).getResult();
			} else {
				buechi = new GetRandomDfa(new AutomataLibraryServices(services), n, k, f, totalityInPerc, true, false,
						false, false).getResult();
			}

			if (logNoErrorDebug) {
				System.out.println("End calculating random DFA.");
				System.out.println();
			}

			// Check correctness
			ReduceBuchiFairSimulation<String, String> operation = new ReduceBuchiFairSimulation<>(
					new AutomataLibraryServices(services), snf, buechi);
			boolean errorOccurred = false;
			errorOccurred = checkOperationDeep(operation, logNoErrorDebug, false);
//			try {
//				errorOccurred = !operation.checkResult(operation.m_StateFactory);
//			} catch (AutomataLibraryException e) {
//				e.printStackTrace();
//			}
			if (errorOccurred) {
				break;
			}
		}

		System.out.println("Program terminated.");
	}

	/**
	 * Checks the operation deeply by using a given instance and comparing
	 * results of all stages to an instance with no SCC optimization if used SCC
	 * optimization and vice versa. Also checks the language equivalence of the
	 * results from both instances.
	 * 
	 * @param operation
	 *            Operation instance for reference
	 * @param logNoErrorDebug
	 *            If true some debugging information gets logged
	 * @param useLogger
	 *            True if internal logger should be used, false if
	 *            {@link System#out} should be used.
	 * @param <LETTER>
	 *            Letter class of the operation
	 * @param <STATE>
	 *            State class of the operation
	 * @return True if operation is correct, false if not.
	 * @throws OperationCanceledException
	 *             If the operation was canceled, for example from the Ultimate
	 *             framework.
	 */
	private static <LETTER, STATE> boolean checkOperationDeep(ReduceBuchiFairSimulation<LETTER, STATE> operation,
			final boolean logNoErrorDebug, final boolean useLogger) throws OperationCanceledException {
		ReduceBuchiFairSimulation<LETTER, STATE> operationSCC;
		FairSimulation<LETTER, STATE> simulationSCC;
		ReduceBuchiFairSimulation<LETTER, STATE> operationNoSCC;
		FairSimulation<LETTER, STATE> simulationNoSCC;
		Logger logger = null;
		if (useLogger) {
			logger = operation.m_Logger;
		}

		// Create instance of other version
		if (operation.m_UseSCCs) {
			operationSCC = operation;
			simulationSCC = operationSCC.m_Simulation;

			if (logNoErrorDebug)
				logMessage("Start Cross-Simulation without SCC...", logger);

			operationNoSCC = new ReduceBuchiFairSimulation<>(operation.m_Services, operation.m_StateFactory,
					operation.m_Operand, false);
			simulationNoSCC = operationNoSCC.m_Simulation;
			if (logNoErrorDebug)
				logMessage("", logger);
		} else {
			if (logNoErrorDebug)
				logMessage("Start Cross-Simulation with SCC...", logger);
			operationSCC = new ReduceBuchiFairSimulation<>(operation.m_Services, operation.m_StateFactory,
					operation.m_Operand, true);
			simulationSCC = operationSCC.m_Simulation;
			if (logNoErrorDebug)
				logMessage("", logger);

			operationNoSCC = operation;
			simulationNoSCC = operationNoSCC.m_Simulation;
		}

		// Start comparing results
		if (logNoErrorDebug)
			logMessage("Start comparing results...", logger);
		boolean errorOccurred = false;
		FairGameGraph<LETTER, STATE> simNoSCCGraph = (FairGameGraph<LETTER, STATE>) simulationNoSCC.getGameGraph();
		Set<Vertex<LETTER, STATE>> simSCCVertices = simulationSCC.getGameGraph().getVertices();
		Set<Vertex<LETTER, STATE>> simNoSCCVertices = simulationNoSCC.getGameGraph().getVertices();
		int globalInfinity = simNoSCCGraph.getGlobalInfinity();

		// Compare size
		if (simSCCVertices.size() != simSCCVertices.size()) {
			logMessage("SimSCC and SimNoSCC have different size: " + simSCCVertices.size() + " & "
					+ simNoSCCVertices.size(), logger);
			errorOccurred = true;
		}
		// Compare infinity
		if (globalInfinity != simulationSCC.getGameGraph().getGlobalInfinity()) {
			logMessage("SimSCC and SimNoSCC have different infinities: "
					+ simulationSCC.getGameGraph().getGlobalInfinity() + " & " + globalInfinity, logger);
			errorOccurred = true;
		}
		// Compare progress measure of vertices
		for (Vertex<LETTER, STATE> simSCCVertex : simSCCVertices) {
			if (simSCCVertex.isSpoilerVertex()) {
				SpoilerVertex<LETTER, STATE> asSV = (SpoilerVertex<LETTER, STATE>) simSCCVertex;
				SpoilerVertex<LETTER, STATE> simNoSCCVertex = simNoSCCGraph.getSpoilerVertex(asSV.getQ0(), asSV.getQ1(),
						false);
				if (simNoSCCVertex == null) {
					logMessage("SCCVertex unknown for nonSCC version: " + asSV, logger);
					errorOccurred = true;
				} else {
					int sccPM = asSV.getPM(null, globalInfinity);
					int nonSCCPM = simNoSCCVertex.getPM(null, globalInfinity);
					if (sccPM < globalInfinity && nonSCCPM >= globalInfinity) {
						logMessage(
								"SCCVertex is not infinity but nonSCC is (" + asSV + "): " + sccPM + " & " + nonSCCPM,
								logger);
						errorOccurred = true;
					} else if (sccPM >= globalInfinity && nonSCCPM < globalInfinity) {
						logMessage(
								"SCCVertex is infinity but nonSCC is not (" + asSV + "): " + sccPM + " & " + nonSCCPM,
								logger);
						errorOccurred = true;
					}
				}
			} else {
				DuplicatorVertex<LETTER, STATE> asDV = (DuplicatorVertex<LETTER, STATE>) simSCCVertex;
				DuplicatorVertex<LETTER, STATE> simNoSCCVertex = simNoSCCGraph.getDuplicatorVertex(asDV.getQ0(),
						asDV.getQ1(), asDV.getLetter(), false);
				if (simNoSCCVertex == null) {
					logMessage("SCCVertex unknown for nonSCC version: " + asDV, logger);
					errorOccurred = true;
				} else {
					int sccPM = asDV.getPM(null, globalInfinity);
					int nonSCCPM = simNoSCCVertex.getPM(null, globalInfinity);
					if (sccPM < globalInfinity && nonSCCPM >= globalInfinity) {
						logMessage(
								"SCCVertex is not infinity but nonSCC is (" + asDV + "): " + sccPM + " & " + nonSCCPM,
								logger);
						errorOccurred = true;
					} else if (sccPM >= globalInfinity && nonSCCPM < globalInfinity) {
						logMessage(
								"SCCVertex is infinity but nonSCC is not (" + asDV + "): " + sccPM + " & " + nonSCCPM,
								logger);
						errorOccurred = true;
					}
				}
			}
		}

		// Check operation correctness
		try {
			if (!operationSCC.checkResult(operation.m_StateFactory)) {
				logMessage("OperationSCC is not correct.", logger);
				errorOccurred = true;
			}
			if (!operationNoSCC.checkResult(operation.m_StateFactory)) {
				logMessage("OperationNoSCC is not correct.", logger);
				errorOccurred = true;
			}
		} catch (AutomataLibraryException e) {
			e.printStackTrace();
		}

		if (errorOccurred) {
			logMessage("End comparing results, a problem occurred. Logging buechi...", logger);
			logMessage(operation.m_Operand.toString(), logger);
		} else {
			if (logNoErrorDebug)
				logMessage("End comparing results, no problem occurred.", logger);
		}

		return errorOccurred;
	}

	/**
	 * Logs a given message to the debugging channel of a given logger or to
	 * {@link System#out} if logger is <tt>null</tt>.
	 * 
	 * @param message
	 *            Message to log
	 * @param logger
	 *            Logger to log to or <tt>null</tt> if logging to
	 *            {@link System#out} is desired
	 */
	private static void logMessage(final String message, final Logger logger) {
		if (logger != null) {
			logger.debug(message);
		} else {
			System.out.println(message);
		}
	}

	/**
	 * The logger used by the Ultimate framework.
	 */
	private final Logger m_Logger;
	/**
	 * The inputed buechi automaton.
	 */
	private final INestedWordAutomatonOldApi<LETTER, STATE> m_Operand;
	/**
	 * The resulting possible reduced buechi automaton.
	 */
	private final INestedWordAutomatonOldApi<LETTER, STATE> m_Result;
	/**
	 * Service provider of Ultimate framework.
	 */
	private final AutomataLibraryServices m_Services;
	/**
	 * Simulation used for operation.
	 */
	private final FairSimulation<LETTER, STATE> m_Simulation;
	/**
	 * State factory used for state creation.
	 */
	private final StateFactory<STATE> m_StateFactory;

	/**
	 * If the simulation calculation should be optimized using SCC, Strongly
	 * Connected Components.
	 */
	private final boolean m_UseSCCs;

	/**
	 * Creates a new buechi reduce object that starts reducing the given buechi
	 * automaton using SCCs as an optimization.<br/>
	 * Once finished the result can be get by using {@link #getResult()}.
	 * 
	 * @param services
	 *            Service provider of Ultimate framework
	 * @param stateFactory
	 *            The state factory used for creating states
	 * @param operand
	 *            The buechi automaton to reduce
	 * @throws OperationCanceledException
	 *             If the operation was canceled, for example from the Ultimate
	 *             framework.
	 */
	public ReduceBuchiFairSimulation(final AutomataLibraryServices services, final StateFactory<STATE> stateFactory,
			final INestedWordAutomatonOldApi<LETTER, STATE> operand) throws OperationCanceledException {
		this(services, stateFactory, operand, true, Collections.emptyList(), false);
	}

	/**
	 * Creates a new buechi reduce object that starts reducing the given buechi
	 * automaton.<br/>
	 * Once finished the result can be get by using {@link #getResult()}.
	 * 
	 * @param services
	 *            Service provider of Ultimate framework
	 * @param stateFactory
	 *            The state factory used for creating states
	 * @param operand
	 *            The buechi automaton to reduce
	 * @param useSCCs
	 *            If the simulation calculation should be optimized using SCC,
	 *            Strongly Connected Components.
	 * @throws OperationCanceledException
	 *             If the operation was canceled, for example from the Ultimate
	 *             framework.
	 */
	public ReduceBuchiFairSimulation(final AutomataLibraryServices services, final StateFactory<STATE> stateFactory,
			final INestedWordAutomatonOldApi<LETTER, STATE> operand, final boolean useSCCs)
					throws OperationCanceledException {
		this(services, stateFactory, operand, useSCCs, Collections.emptyList(), false);
	}

	/**
	 * Creates a new buechi reduce object that starts reducing the given buechi
	 * automaton.<br/>
	 * Once finished the result can be get by using {@link #getResult()}.
	 * 
	 * @param services
	 *            Service provider of Ultimate framework
	 * @param stateFactory
	 *            The state factory used for creating states
	 * @param operand
	 *            The buechi automaton to reduce
	 * @param useSCCs
	 *            If the simulation calculation should be optimized using SCC,
	 *            Strongly Connected Components.
	 * @param possibleEquivalentClasses
	 *            A collection of sets which contains states of the buechi
	 *            automaton that may be merge-able. States which are not in the
	 *            same set are definitely not merge-able which is used as an
	 *            optimization for the simulation
	 * @throws OperationCanceledException
	 *             If the operation was canceled, for example from the Ultimate
	 *             framework.
	 */
	public ReduceBuchiFairSimulation(final AutomataLibraryServices services, final StateFactory<STATE> stateFactory,
			final INestedWordAutomatonOldApi<LETTER, STATE> operand, final boolean useSCCs,
			final Collection<Set<STATE>> possibleEquivalentClasses) throws OperationCanceledException {
		this(services, stateFactory, operand, useSCCs, possibleEquivalentClasses, false);
	}

	/**
	 * Creates a new buechi reduce object that starts reducing the given buechi
	 * automaton.<br/>
	 * Once finished the result can be get by using {@link #getResult()}.
	 * 
	 * @param services
	 *            Service provider of Ultimate framework
	 * @param stateFactory
	 *            The state factory used for creating states
	 * @param operand
	 *            The buechi automaton to reduce
	 * @param useSCCs
	 *            If the simulation calculation should be optimized using SCC,
	 *            Strongly Connected Components.
	 * @param possibleEquivalentClasses
	 *            A collection of sets which contains states of the buechi
	 *            automaton that may be merge-able. States which are not in the
	 *            same set are definitely not merge-able which is used as an
	 *            optimization for the simulation
	 * @param checkOperationDeeply
	 *            If true the operation gets deeply checked for correctness,
	 *            false if that is not desired. This can take some time.
	 * @throws OperationCanceledException
	 *             If the operation was canceled, for example from the Ultimate
	 *             framework.
	 */
	public ReduceBuchiFairSimulation(final AutomataLibraryServices services, final StateFactory<STATE> stateFactory,
			final INestedWordAutomatonOldApi<LETTER, STATE> operand, final boolean useSCCs,
			final Collection<Set<STATE>> possibleEquivalentClasses, final boolean checkOperationDeeply)
					throws OperationCanceledException {
		this(services, stateFactory, operand, useSCCs, checkOperationDeeply,
				new FairSimulation<LETTER, STATE>(services.getProgressMonitorService(),
						services.getLoggingService().getLogger(LibraryIdentifiers.s_LibraryID), operand, useSCCs,
						stateFactory, possibleEquivalentClasses,
						new FairGameGraph<LETTER, STATE>(services, services.getProgressMonitorService(),
								services.getLoggingService().getLogger(LibraryIdentifiers.s_LibraryID), operand,
								stateFactory)));
	}

	/**
	 * Creates a new buechi reduce object that starts reducing the given buechi
	 * automaton using a given fair simulation.<br/>
	 * Once finished the result can be get by using {@link #getResult()}.
	 * 
	 * @param services
	 *            Service provider of Ultimate framework
	 * @param stateFactory
	 *            The state factory used for creating states
	 * @param operand
	 *            The buechi automaton to reduce
	 * @param useSCCs
	 *            If the simulation calculation should be optimized using SCC,
	 *            Strongly Connected Components.
	 * @param checkOperationDeeply
	 *            If true the operation gets deeply checked for correctness,
	 *            false if that is not desired. This can take some time.
	 * @param simulation
	 *            The simulation used by the operation
	 * @throws OperationCanceledException
	 *             If the operation was canceled, for example from the Ultimate
	 *             framework.
	 */
	protected ReduceBuchiFairSimulation(final AutomataLibraryServices services, final StateFactory<STATE> stateFactory,
			final INestedWordAutomatonOldApi<LETTER, STATE> operand, final boolean useSCCs,
			final boolean checkOperationDeeply, final FairSimulation<LETTER, STATE> simulation)
					throws OperationCanceledException {
		m_Services = services;
		m_StateFactory = stateFactory;
		m_Logger = m_Services.getLoggingService().getLogger(LibraryIdentifiers.s_LibraryID);
		m_Operand = operand;
		m_UseSCCs = useSCCs;
		m_Logger.info(startMessage());
		m_Logger.debug("Starting generation of Fair Game Graph...");
		simulation.getGameGraph().generateGameGraphFromBuechi();
		m_Simulation = simulation;
		simulation.doSimulation();
		m_Result = m_Simulation.getResult();

		// Debugging flag
		if (checkOperationDeeply) {
			m_Logger.info("Start testing correctness of operation deeply...");
			boolean operationIsNotCorrect = checkOperationDeep(this, false, true);
			if (operationIsNotCorrect) {
				m_Logger.info("End testing correctness of operation deeply, it is not correct.");
				// throw new AssertionError("The operation " + operationName() +
				// " returned a false result.");
			} else {
				m_Logger.info("End testing correctness of operation deeply, it is correct.");
			}
		}

		m_Logger.info(exitMessage());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.automata.IOperation#checkResult(
	 * de.uni_freiburg.informatik.ultimate.automata.nwalibrary.StateFactory)
	 */
	@SuppressWarnings("deprecation")
	@Override
	public boolean checkResult(final StateFactory<STATE> stateFactory) throws AutomataLibraryException {
		m_Logger.info("Start testing correctness of " + operationName());
		boolean correct = ResultChecker.reduceBuchi(m_Services, m_Operand, m_Result);
		m_Logger.info("Finished testing correctness of " + operationName());
		return correct;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.automata.IOperation#exitMessage()
	 */
	@Override
	public String exitMessage() {
		return "Finished " + operationName() + " Result " + m_Result.sizeInformation();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.automata.IOperation#getResult()
	 */
	@Override
	public INestedWordAutomatonOldApi<LETTER, STATE> getResult() throws AutomataLibraryException {
		return m_Result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.automata.IOperation#operationName()
	 */
	@Override
	public String operationName() {
		return "reduceBuchiFairSimulation";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.automata.IOperation#startMessage()
	 */
	@Override
	public String startMessage() {
		return "Start " + operationName() + ". Operand has " + m_Operand.sizeInformation();
	}
}