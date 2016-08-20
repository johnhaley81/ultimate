/*
 * Copyright (C) 2013-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2009-2015 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.automata.nwalibrary.reachableStatesAutomaton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.LibraryIdentifiers;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.NestedRun;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.NestedWord;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.buchiNwa.BuchiAccepts;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.buchiNwa.NestedLassoRun;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.IncomingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.IncomingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.IncomingReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.OutgoingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.OutgoingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.OutgoingReturnTransition;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.util.HashUtils;

/**
 * Computes an accepting nested lasso run for a given Honda state. Expects
 * that the Honda state is contained in an accepting SCC. Nested Runs from
 * the Honda to an initial state (stem) and from the Honda to the Honda are
 * computed backwards using StacksOfFlaggedStates. The boolean flag is true
 * iff on the path from this stack to the honda an accepting state was
 * visited.
 * <p>
 * This is slow, old and superseded by the class LassoConstructor.
 * Problem: we do a backward search and store information about visited stacks,
 * this seems to be too costly.
 * <p>
 * This class does not give us the shortest lasso, because the construction of
 * the stem is not optimal.
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 */
class ShortestLassoExtractor<LETTER, STATE> {
	private final AutomataLibraryServices mServices;
	private final ILogger mLogger;
	private final NestedWordAutomatonReachableStates<LETTER, STATE> mNwars;
	
	private final List<Set<StackOfFlaggedStates>> mIterations = new ArrayList<Set<StackOfFlaggedStates>>();
	
	private final StateContainer<LETTER, STATE> mGoal;
	private StateContainer<LETTER, STATE> mFirstFoundInitialState;
	
	private int mGoalFoundIteration = -1;
	private int mInitFoundIteration = -1;
	
	private final NestedLassoRun<LETTER, STATE> mNlr;
	private NestedRun<LETTER, STATE> mStem;
	private NestedRun<LETTER, STATE> mLoop;
	private NestedRun<LETTER, STATE> mConstructedNestedRun;
	
	public ShortestLassoExtractor(final AutomataLibraryServices services,
			final NestedWordAutomatonReachableStates<LETTER, STATE> nwars,
			final StateContainer<LETTER, STATE> goal)
					throws AutomataOperationCanceledException {
		mServices = services;
		mLogger = mServices.getLoggingService().getLogger(LibraryIdentifiers.PLUGIN_ID);
		mNwars = nwars;
		mGoal = goal;
		addInitialStack(goal);
		findPath(1);
		mLogger.debug("Stem length: " + mInitFoundIteration);
		mLogger.debug("Loop length: " + mGoalFoundIteration);
		constructStem();
		constructLoop();
		mNlr = new NestedLassoRun<LETTER, STATE>(mStem, mLoop);
		mLogger.debug("Stem " + mStem);
		mLogger.debug("Loop " + mLoop);
		try {
			assert (new BuchiAccepts<LETTER, STATE>(mServices, nwars, mNlr.getNestedLassoWord())).getResult();
		} catch (final AutomataLibraryException e) {
			throw new AssertionError(e);
		}
	}
	
	private StackOfFlaggedStates addInitialStack(final StateContainer<LETTER, STATE> goal) {
		final StackOfFlaggedStates initialStack = new StackOfFlaggedStates(goal, false);
		final Set<StackOfFlaggedStates> initialStacks = new HashSet<StackOfFlaggedStates>();
		initialStacks.add(initialStack);
		mIterations.add(initialStacks);
		return initialStack;
	}
	
	public NestedLassoRun<LETTER, STATE> getNestedLassoRun() {
		return mNlr;
	}
	
	void findPath(final int startingIteration) {
		int i = startingIteration;
		while (mGoalFoundIteration == -1 || mInitFoundIteration == -1) {
			final Set<StackOfFlaggedStates> currentStacks = mIterations.get(i - 1);
			final Set<StackOfFlaggedStates> preceedingStacks = new HashSet<StackOfFlaggedStates>();
			mIterations.add(preceedingStacks);
			for (final StackOfFlaggedStates stack : currentStacks) {
				addPreceedingStacks(i, preceedingStacks, stack);
			}
			i++;
		}
	}
	
	private void addPreceedingStacks(final int i,
			final Set<StackOfFlaggedStates> preceedingStacks,
			final StackOfFlaggedStates stack) {
		final StateContainer<LETTER, STATE> cont = stack.getTopmostState();
		for (final IncomingInternalTransition<LETTER, STATE> inTrans : cont.internalPredecessors()) {
			final StateContainer<LETTER, STATE> predCont = mNwars.obtainSC(inTrans.getPred());
			final boolean finalOnPathToHonda = stack.getTopmostFlag() || mNwars.isFinal(inTrans.getPred());
			if (finalOnPathToHonda && stack.height() > 1 && !stack.getSecondTopmostFlag()) {
				continue;
			}
			final StackOfFlaggedStates predStack = new StackOfFlaggedStates(stack, inTrans, finalOnPathToHonda);
			preceedingStacks.add(predStack);
			checkIfGoalOrInitReached(i, predStack, predCont);
		}
		if (stack.height() == 1) {
			// call is pending
			for (final IncomingCallTransition<LETTER, STATE> inTrans : cont.callPredecessors()) {
				final StateContainer<LETTER, STATE> predCont = mNwars.obtainSC(inTrans.getPred());
				final boolean finalOnPathToHonda = stack.getTopmostFlag() || mNwars.isFinal(inTrans.getPred());
				final StackOfFlaggedStates predStack = new StackOfFlaggedStates(stack, inTrans, finalOnPathToHonda);
				preceedingStacks.add(predStack);
				checkIfGoalOrInitReached(i, predStack, predCont);
			}
		} else {
			for (final IncomingCallTransition<LETTER, STATE> inTrans : cont.callPredecessors()) {
				final StateContainer<LETTER, STATE> predCont = mNwars.obtainSC(inTrans.getPred());
				final boolean finalOnPathToHonda = stack.getTopmostFlag() || mNwars.isFinal(inTrans.getPred());
				if (!stack.getSecondTopmostState().getState().equals(inTrans.getPred())) {
					// call predecessor does not match state on stack
					continue;
				}
				if (finalOnPathToHonda != stack.getSecondTopmostFlag() && !mNwars.isFinal(inTrans.getPred())) {
					// information about finalOnPathToHonda does not match
					continue;
				}
				final StackOfFlaggedStates predStack = new StackOfFlaggedStates(stack, inTrans, finalOnPathToHonda);
				preceedingStacks.add(predStack);
				checkIfGoalOrInitReached(i, predStack, predCont);
			}
		}
		
		// TODO: Optimization (you can ignore stacks like (q0,false)  (q0,false)  (q1,true)
		for (final IncomingReturnTransition<LETTER, STATE> inTrans : cont.returnPredecessors()) {
			// note that goal or init can never be reached
			// (backwards) with empty stack directly after return.
			final int oldPreceedingStackSize = preceedingStacks.size();
			if (stack.getTopmostFlag()) {
				final StackOfFlaggedStates predStack = new StackOfFlaggedStates(stack, inTrans, true, true);
				preceedingStacks.add(predStack);
			} else {
				final boolean linPredIsFinal = mNwars.isFinal(inTrans.getLinPred());
				{
					final StackOfFlaggedStates predStack =
							new StackOfFlaggedStates(stack, inTrans, true, linPredIsFinal);
					preceedingStacks.add(predStack);
				}
				if (!mNwars.isFinal(inTrans.getHierPred()) && !linPredIsFinal) {
					final StackOfFlaggedStates predStack =
							new StackOfFlaggedStates(stack, inTrans, false, linPredIsFinal);
					preceedingStacks.add(predStack);
				}
			}
			assert oldPreceedingStackSize + 2 >= preceedingStacks.size();
		}
	}
	
	void constructStem() {
		assert mConstructedNestedRun == null;
		final Set<StackOfFlaggedStates> initIteration = mIterations.get(mInitFoundIteration);
		StackOfFlaggedStates stack = new StackOfFlaggedStates(mFirstFoundInitialState, true);
		if (!initIteration.contains(stack)) {
			stack = new StackOfFlaggedStates(mFirstFoundInitialState, false);
		}
		
		assert initIteration.contains(stack);
		final StateContainer<LETTER, STATE> cont = mFirstFoundInitialState;
		mConstructedNestedRun = new NestedRun<LETTER, STATE>(cont.getState());
		for (int i = mInitFoundIteration - 1; i >= 0; i--) {
			stack = getSuccessorStack(stack, mIterations.get(i));
		}
		mStem = mConstructedNestedRun;
		mConstructedNestedRun = null;
	}
	
	void constructLoop() {
		final Set<StackOfFlaggedStates> hondaIteration = mIterations.get(mGoalFoundIteration);
		StackOfFlaggedStates stack = new StackOfFlaggedStates(mGoal, true);
		if (!hondaIteration.contains(stack)) {
			stack = new StackOfFlaggedStates(mGoal, false);
		}
		assert hondaIteration.contains(stack);
		final StateContainer<LETTER, STATE> cont = mGoal;
		mConstructedNestedRun = new NestedRun<LETTER, STATE>(cont.getState());
		mLoop = new NestedRun<LETTER, STATE>(cont.getState());
		for (int i = mGoalFoundIteration - 1; i >= 0; i--) {
			stack = getSuccessorStack(stack, mIterations.get(i));
		}
		mLoop = mConstructedNestedRun;
		mConstructedNestedRun = null;
	}
	
	StackOfFlaggedStates getSuccessorStack(final StackOfFlaggedStates sofs,
			final Set<StackOfFlaggedStates> succCandidates) {
		final StateContainer<LETTER, STATE> cont = sofs.getTopmostState();
		if (sofs.getTopmostFlag()) {
			for (final OutgoingInternalTransition<LETTER, STATE> outTrans : cont.internalSuccessors()) {
				final StackOfFlaggedStates succStack = new StackOfFlaggedStates(sofs, outTrans, true);
				if (succCandidates.contains(succStack)) {
					final NestedRun<LETTER, STATE> runSegment = new NestedRun<LETTER, STATE>(
							cont.getState(), outTrans.getLetter(), NestedWord.INTERNAL_POSITION, outTrans.getSucc());
					mConstructedNestedRun = mConstructedNestedRun.concatenate(runSegment);
					return succStack;
				}
			}
			for (final OutgoingCallTransition<LETTER, STATE> outTrans : cont.callSuccessors()) {
				StackOfFlaggedStates succStack = new StackOfFlaggedStates(sofs, outTrans, true, false);
				if (succCandidates.contains(succStack)) {
					final NestedRun<LETTER, STATE> runSegment = new NestedRun<LETTER, STATE>(
							cont.getState(), outTrans.getLetter(), NestedWord.PLUS_INFINITY, outTrans.getSucc());
					mConstructedNestedRun = mConstructedNestedRun.concatenate(runSegment);
					return succStack;
				}
				if (sofs.height() == 1) {
					//check also for pending calls
					succStack = new StackOfFlaggedStates(sofs, outTrans, true, true);
					if (succCandidates.contains(succStack)) {
						final NestedRun<LETTER, STATE> runSegment = new NestedRun<LETTER, STATE>(
								cont.getState(), outTrans.getLetter(), NestedWord.PLUS_INFINITY, outTrans.getSucc());
						mConstructedNestedRun = mConstructedNestedRun.concatenate(runSegment);
						return succStack;
					}
					
				}
			}
			if (sofs.height() > 1) {
				for (final OutgoingReturnTransition<LETTER, STATE> outTrans : cont.returnSuccessors()) {
					if (sofs.getSecondTopmostState().getState().equals(outTrans.getHierPred())) {
						final StackOfFlaggedStates succStack = new StackOfFlaggedStates(sofs, outTrans, true);
						if (succCandidates.contains(succStack)) {
							final NestedRun<LETTER, STATE> runSegment = new NestedRun<LETTER, STATE>(
									cont.getState(), outTrans.getLetter(), NestedWord.MINUS_INFINITY,
									outTrans.getSucc());
							mConstructedNestedRun = mConstructedNestedRun.concatenate(runSegment);
							return succStack;
						}
					}
				}
			}
		}
		for (final OutgoingInternalTransition<LETTER, STATE> outTrans : cont.internalSuccessors()) {
			final StackOfFlaggedStates succStack = new StackOfFlaggedStates(sofs, outTrans, false);
			if (succCandidates.contains(succStack)) {
				final NestedRun<LETTER, STATE> runSegment = new NestedRun<LETTER, STATE>(
						cont.getState(), outTrans.getLetter(), NestedWord.INTERNAL_POSITION, outTrans.getSucc());
				mConstructedNestedRun = mConstructedNestedRun.concatenate(runSegment);
				return succStack;
			}
		}
		for (final OutgoingCallTransition<LETTER, STATE> outTrans : cont.callSuccessors()) {
			StackOfFlaggedStates succStack = new StackOfFlaggedStates(sofs, outTrans, false, false);
			if (succCandidates.contains(succStack)) {
				final NestedRun<LETTER, STATE> runSegment = new NestedRun<LETTER, STATE>(
						cont.getState(), outTrans.getLetter(), NestedWord.PLUS_INFINITY, outTrans.getSucc());
				mConstructedNestedRun = mConstructedNestedRun.concatenate(runSegment);
				return succStack;
			}
			if (sofs.height() == 1) {
				//check also for pending calls
				succStack = new StackOfFlaggedStates(sofs, outTrans, false, true);
				if (succCandidates.contains(succStack)) {
					final NestedRun<LETTER, STATE> runSegment = new NestedRun<LETTER, STATE>(
							cont.getState(), outTrans.getLetter(), NestedWord.PLUS_INFINITY, outTrans.getSucc());
					mConstructedNestedRun = mConstructedNestedRun.concatenate(runSegment);
					return succStack;
				}
				
			}
		}
		if (sofs.height() > 1) {
			for (final OutgoingReturnTransition<LETTER, STATE> outTrans : cont.returnSuccessors()) {
				if (sofs.getSecondTopmostState().getState().equals(outTrans.getHierPred())) {
					final StackOfFlaggedStates succStack = new StackOfFlaggedStates(sofs, outTrans, false);
					if (succCandidates.contains(succStack)) {
						final NestedRun<LETTER, STATE> runSegment = new NestedRun<LETTER, STATE>(
								cont.getState(), outTrans.getLetter(), NestedWord.MINUS_INFINITY, outTrans.getSucc());
						mConstructedNestedRun = mConstructedNestedRun.concatenate(runSegment);
						return succStack;
					}
				}
			}
		}
		throw new AssertionError("no corresponding state found");
	}
	
	private void checkIfGoalOrInitReached(final int i,
			final StackOfFlaggedStates stack,
			final StateContainer<LETTER, STATE> predCont) {
		if (predCont == mGoal && stack.hasOnlyTopmostElement()
				&& stack.getTopmostFlag()) {
			mGoalFoundIteration = i;
		}
		if (mFirstFoundInitialState == null
				&& mNwars.isInitial(predCont.getState())
				&& stack.hasOnlyTopmostElement()) {
			mInitFoundIteration = i;
			mFirstFoundInitialState = predCont;
		}
	}
	
	class StackOfFlaggedStates {
		private final StateContainer<LETTER, STATE> mTopmostState;
		private final boolean mTopmostFlag;
		private final StateContainer<LETTER, STATE>[] mStateStack;
		private final boolean[] mFlagStack;
		
		@SuppressWarnings("unchecked")
		public StackOfFlaggedStates(final StateContainer<LETTER, STATE> cont, final boolean flag) {
			mStateStack = new StateContainer[0];
			mFlagStack = new boolean[0];
			mTopmostState = cont;
			mTopmostFlag = flag;
		}
		
		public StackOfFlaggedStates(final StackOfFlaggedStates sofs,
				final IncomingInternalTransition<LETTER, STATE> inTrans, final boolean flag) {
			mStateStack = sofs.mStateStack;
			mFlagStack = sofs.mFlagStack;
			mTopmostState = mNwars.obtainSC(inTrans.getPred());
			mTopmostFlag = flag;
		}
		
		public StackOfFlaggedStates(final StackOfFlaggedStates sofs,
				final IncomingCallTransition<LETTER, STATE> inTrans, final boolean flag) {
			if (sofs.mStateStack.length == 0) {
				mStateStack = sofs.mStateStack;
				mFlagStack = sofs.mFlagStack;
				mTopmostState = mNwars.obtainSC(inTrans.getPred());
				mTopmostFlag = flag;
				
			} else {
				mStateStack = Arrays.copyOf(sofs.mStateStack, sofs.mStateStack.length - 1);
				mFlagStack = Arrays.copyOf(sofs.mFlagStack, sofs.mFlagStack.length - 1);
				mTopmostState = mNwars.obtainSC(inTrans.getPred());
				mTopmostFlag = flag;
			}
		}
		
		public StackOfFlaggedStates(final StackOfFlaggedStates sofs,
				final IncomingReturnTransition<LETTER, STATE> inTrans, final boolean hierFlag, final boolean linFlag) {
			mStateStack = Arrays.copyOf(sofs.mStateStack, sofs.mStateStack.length + 1);
			mFlagStack = Arrays.copyOf(sofs.mFlagStack, sofs.mFlagStack.length + 1);
			mStateStack[mStateStack.length - 1] = mNwars.obtainSC(inTrans.getHierPred());
			mFlagStack[mStateStack.length - 1] = hierFlag;
			mTopmostState = mNwars.obtainSC(inTrans.getLinPred());
			mTopmostFlag = linFlag;
		}
		
		public StackOfFlaggedStates(final StackOfFlaggedStates sofs,
				final OutgoingInternalTransition<LETTER, STATE> outTrans, final boolean flag) {
			mStateStack = sofs.mStateStack;
			mFlagStack = sofs.mFlagStack;
			mTopmostState = mNwars.obtainSC(outTrans.getSucc());
			mTopmostFlag = flag;
		}
		
		public StackOfFlaggedStates(final StackOfFlaggedStates sofs,
				final OutgoingCallTransition<LETTER, STATE> outTrans, final boolean flag, final boolean isPending) {
			if (isPending) {
				mStateStack = sofs.mStateStack;
				mFlagStack = sofs.mFlagStack;
				mTopmostState = mNwars.obtainSC(outTrans.getSucc());
				mTopmostFlag = flag;
			} else {
				mStateStack = Arrays.copyOf(sofs.mStateStack, sofs.mStateStack.length + 1);
				mFlagStack = Arrays.copyOf(sofs.mFlagStack, sofs.mFlagStack.length + 1);
				mStateStack[mStateStack.length - 1] = sofs.mTopmostState;
				mFlagStack[mStateStack.length - 1] = sofs.mTopmostFlag;
				mTopmostState = mNwars.obtainSC(outTrans.getSucc());
				mTopmostFlag = flag;
			}
		}
		
		public StackOfFlaggedStates(final StackOfFlaggedStates sofs,
				final OutgoingReturnTransition<LETTER, STATE> outTrans, final boolean flag) {
			mStateStack = Arrays.copyOf(sofs.mStateStack, sofs.mStateStack.length - 1);
			mFlagStack = Arrays.copyOf(sofs.mFlagStack, sofs.mFlagStack.length - 1);
			mTopmostState = mNwars.obtainSC(outTrans.getSucc());
			mTopmostFlag = flag;
		}
		
		public int height() {
			return mStateStack.length + 1;
		}
		
		/**
		 * @return true if there is only one element on the stack, i.e., if
		 *         the topmost element is the only element on the stack.
		 */
		public boolean hasOnlyTopmostElement() {
			return mStateStack.length == 0;
		}
		
		public StateContainer<LETTER, STATE> getSecondTopmostState() {
			return mStateStack[mStateStack.length - 1];
		}
		
		public StateContainer<LETTER, STATE> getTopmostState() {
			return mTopmostState;
		}
		
		public boolean getSecondTopmostFlag() {
			return mFlagStack[mFlagStack.length - 1];
		}
		
		public boolean getTopmostFlag() {
			return mTopmostFlag;
		}
		
		@Override
		public int hashCode() {
			int result = HashUtils.hashJenkins(((Boolean) mTopmostFlag).hashCode(), mTopmostState);
//			result = HashUtils.hashJenkins(result, mFlagStack);
			result = HashUtils.hashJenkins(result, mStateStack);
			return result;
		}
		
		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final StackOfFlaggedStates other = (StackOfFlaggedStates) obj;
			if (!getOuterType().equals(other.getOuterType())) {
				return false;
			}
			if (!Arrays.equals(mFlagStack, other.mFlagStack)) {
				return false;
			}
			if (!Arrays.equals(mStateStack, other.mStateStack)) {
				return false;
			}
			if (mTopmostFlag != other.mTopmostFlag) {
				return false;
			}
			if (mTopmostState == null) {
				if (other.mTopmostState != null) {
					return false;
				}
			} else if (!mTopmostState.equals(other.mTopmostState)) {
				return false;
			}
			return true;
		}
		
		private ShortestLassoExtractor getOuterType() {
			return ShortestLassoExtractor.this;
		}
		
		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < mStateStack.length; i++) {
				sb.append("(" + mStateStack[i].getState() + "," + mFlagStack[i] + ")  ");
			}
			sb.append("(" + mTopmostState.getState() + "," + mTopmostFlag + ")");
			return sb.toString();
		}
		
	}
}
