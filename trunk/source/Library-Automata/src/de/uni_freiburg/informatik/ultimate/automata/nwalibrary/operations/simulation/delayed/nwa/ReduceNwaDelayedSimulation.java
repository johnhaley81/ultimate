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
package de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.simulation.delayed.nwa;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.IOperation;
import de.uni_freiburg.informatik.ultimate.automata.LibraryIdentifiers;
import de.uni_freiburg.informatik.ultimate.automata.OperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomatonOldApi;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.StateFactory;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.IsIncluded;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.simulation.delayed.BuchiReduce;

/**
 * Operation that reduces a given nwa automaton by using
 * {@link DelayedNwaSimulation}.<br/>
 * Once constructed the reduction automatically starts, the result can be get by
 * using {@link #getResult()}.<br/>
 * <br/>
 * 
 * For correctness its important that the inputed automaton has <b>no dead
 * ends</b> nor <b>duplicate transitions</b>.
 * 
 * @author Daniel Tischner
 * 
 * @param <LETTER>
 *            Letter class of nwa automaton
 * @param <STATE>
 *            State class of nwa automaton
 */
public final class ReduceNwaDelayedSimulation<LETTER, STATE> extends BuchiReduce<LETTER, STATE>
		implements IOperation<LETTER, STATE> {

	/**
	 * Creates a new nwa reduce object that starts reducing the given nwa
	 * automaton using SCCs as an optimization.<br/>
	 * Once finished the result can be get by using {@link #getResult()}.
	 * 
	 * @param services
	 *            Service provider of Ultimate framework
	 * @param stateFactory
	 *            The state factory used for creating states
	 * @param operand
	 *            The nwa automaton to reduce
	 * @throws OperationCanceledException
	 *             If the operation was canceled, for example from the Ultimate
	 *             framework.
	 */
	public ReduceNwaDelayedSimulation(AutomataLibraryServices services, StateFactory<STATE> stateFactory,
			INestedWordAutomatonOldApi<LETTER, STATE> operand) throws OperationCanceledException {
		this(services, stateFactory, operand, true);
	}

	/**
	 * Creates a new nwa reduce object that starts reducing the given nwa
	 * automaton.<br/>
	 * Once finished the result can be get by using {@link #getResult()}.
	 * 
	 * @param services
	 *            Service provider of Ultimate framework
	 * @param stateFactory
	 *            The state factory used for creating states
	 * @param operand
	 *            The nwa automaton to reduce
	 * @param useSCCs
	 *            If the simulation calculation should be optimized using SCC,
	 *            Strongly Connected Components.
	 * @throws OperationCanceledException
	 *             If the operation was canceled, for example from the Ultimate
	 *             framework.
	 */
	public ReduceNwaDelayedSimulation(AutomataLibraryServices services, StateFactory<STATE> stateFactory,
			INestedWordAutomatonOldApi<LETTER, STATE> operand, final boolean useSCCs)
					throws OperationCanceledException {
		super(services, stateFactory, operand,
				new DelayedNwaSimulation<LETTER, STATE>(services.getProgressMonitorService(),
						services.getLoggingService().getLogger(LibraryIdentifiers.s_LibraryID), useSCCs, stateFactory,
						new DelayedNwaGameGraph<LETTER, STATE>(services, services.getProgressMonitorService(),
								services.getLoggingService().getLogger(LibraryIdentifiers.s_LibraryID), operand,
								stateFactory)));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.
	 * simulation.delayed.BuchiReduce#checkResult(de.uni_freiburg.informatik.
	 * ultimate.automata.nwalibrary.StateFactory)
	 */
	@Override
	public boolean checkResult(final StateFactory<STATE> stateFactory) throws AutomataLibraryException {
		getLogger().info("Start testing correctness of " + operationName());
		boolean correct = true;
		correct &= (new IsIncluded<LETTER, STATE>(getServices(), stateFactory, getOperand(), getResult())).getResult();
		correct &= (new IsIncluded<LETTER, STATE>(getServices(), stateFactory, getResult(), getOperand())).getResult();
		getLogger().info("Finished testing correctness of " + operationName());
		return correct;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.
	 * simulation.delayed.BuchiReduce#operationName()
	 */
	@Override
	public String operationName() {
		return "reduceNwaDelayedSimulation";
	}
}