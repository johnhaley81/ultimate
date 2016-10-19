/*
 * Copyright (C) 2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE AbstractInterpretationV2 plug-in.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE AbstractInterpretationV2 plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE AbstractInterpretationV2 plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE AbstractInterpretationV2 plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IProgressAwareTimer;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Boogie2SMT;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.model.IAbstractDomain;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.model.IAbstractPostOperator;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.model.IAbstractState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.model.IAbstractState.SubsetResult;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.model.IAbstractStateBinaryOperator;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.preferences.AbsIntPrefInitializer;
import de.uni_freiburg.informatik.ultimate.util.CoreUtil;
import de.uni_freiburg.informatik.ultimate.util.ToolchainCanceledException;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * @author Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 *
 */
public class FixpointEngine<STATE extends IAbstractState<STATE, ACTION, VARDECL>, ACTION, VARDECL, LOCATION, EXPRESSION> {

	private final int mMaxUnwindings;
	private final int mMaxParallelStates;

	private final ITransitionProvider<ACTION, LOCATION> mTransitionProvider;
	private final IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION> mStateStorage;
	private final IAbstractDomain<STATE, ACTION, VARDECL> mDomain;
	private final IVariableProvider<STATE, ACTION, VARDECL> mVarProvider;
	private final ILoopDetector<ACTION> mLoopDetector;
	private final IDebugHelper<STATE, ACTION, VARDECL, LOCATION> mDebugHelper;
	private final IProgressAwareTimer mTimer;
	private final ILogger mLogger;

	private AbstractInterpretationBenchmark<ACTION, LOCATION> mBenchmark;
	private AbstractInterpretationResult<STATE, ACTION, VARDECL, LOCATION> mResult;
	private final SummaryMap<STATE, ACTION, VARDECL, LOCATION> mSummaryMap;

	public FixpointEngine(final FixpointEngineParameters<STATE, ACTION, VARDECL, LOCATION, EXPRESSION> params) {
		if (params == null || !params.isValid()) {
			throw new IllegalArgumentException("invalid params");
		}
		mTimer = params.getTimer();
		mLogger = params.getLogger();
		mTransitionProvider = params.getTransitionProvider();
		mStateStorage = params.getStorage();
		mDomain = params.getAbstractDomain();
		mVarProvider = params.getVariableProvider();
		mLoopDetector = params.getLoopDetector();
		mDebugHelper = params.getDebugHelper();
		mMaxUnwindings = params.getMaxUnwindings();
		mMaxParallelStates = params.getMaxParallelStates();
		mSummaryMap = new SummaryMap<>(mDomain.getMergeOperator(), mTransitionProvider, mLogger);
	}

	public AbstractInterpretationResult<STATE, ACTION, VARDECL, LOCATION> run(final ACTION start, final Script script,
			final Boogie2SMT bpl2smt,
			final AbstractInterpretationResult<STATE, ACTION, VARDECL, LOCATION> intermediateResult) {
		mLogger.info("Starting fixpoint engine with domain " + mDomain.getClass().getSimpleName() + " (maxUnwinding="
				+ mMaxUnwindings + ", maxParallelStates=" + mMaxParallelStates + ")");
		mResult = intermediateResult == null ? new AbstractInterpretationResult<>(mDomain, mTransitionProvider)
				: intermediateResult;
		mBenchmark = mResult.getBenchmark();
		calculateFixpoint(start);
		mResult.saveRootStorage(mStateStorage, start, script, bpl2smt);
		mResult.saveSummaryStorage(mSummaryMap);
		return mResult;
	}

	public AbstractInterpretationResult<STATE, ACTION, VARDECL, LOCATION> run(final ACTION start, final Script script,
			final Boogie2SMT bpl2smt) {
		return run(start, script, bpl2smt, new AbstractInterpretationResult<>(mDomain, mTransitionProvider));
	}

	private void calculateFixpoint(final ACTION start) {
		final Deque<WorklistItem<STATE, ACTION, VARDECL, LOCATION>> worklist = new ArrayDeque<>();
		final IAbstractPostOperator<STATE, ACTION, VARDECL> postOp = mDomain.getPostOperator();
		final IAbstractStateBinaryOperator<STATE> wideningOp = mDomain.getWideningOperator();
		final Set<ACTION> reachedErrors = new HashSet<>();

		worklist.add(createInitialWorklistItem(start));

		while (!worklist.isEmpty()) {
			checkTimeout();

			final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem = worklist.removeFirst();
			mBenchmark.addIteration(currentItem);

			if (mLogger.isDebugEnabled()) {
				mLogger.debug(getLogMessageCurrentTransition(currentItem));
			}

			final AbstractMultiState<STATE, ACTION, VARDECL> postState = calculateAbstractPost(currentItem, postOp);

			if (isUnnecessaryPostState(currentItem, postState)) {
				continue;
			}

			// if (useSummaryInstead(currentItem, postState, worklist)) {
			// continue;
			// }

			setActiveLoopState(currentItem);
			checkReachedError(currentItem, postState, reachedErrors);

			final AbstractMultiState<STATE, ACTION, VARDECL> postStateAfterWidening =
					widenIfNecessary(currentItem, postState, wideningOp);
			if (postStateAfterWidening == null) {
				// we have reached a fixpoint
				if (mLogger.isDebugEnabled()) {
					mLogger.debug(AbsIntPrefInitializer.INDENT
							+ " Skipping successors because post state is already contained");
				}
				continue;
			}
			logDebugPostChanged(postState, postStateAfterWidening, "Widening");
			final AbstractMultiState<STATE, ACTION, VARDECL> postStatesAfterSave =
					savePostState(currentItem, postStateAfterWidening);
			assert postStatesAfterSave != null : "Saving a state is not allowed to return null";
			logDebugPostChanged(postStateAfterWidening, postStatesAfterSave, "Merge");

			final List<WorklistItem<STATE, ACTION, VARDECL, LOCATION>> newItems =
					createSuccessorItems(currentItem, postStatesAfterSave);
			worklist.addAll(newItems);
		}
	}

	/**
	 * This method checks whether the current item can be ignored by a summary. This is the case if this item is a call
	 * to a procedure for which a summary is already calculated. In this case, the item will count as processed and
	 * instead a new item will be added to the worklist.
	 *
	 * @param currentItem
	 * @param postState
	 * @param worklist
	 * @return true if a summary item was added to the worklist and this item should be ignored.
	 */
	private boolean useSummaryInstead(final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem,
			final AbstractMultiState<STATE, ACTION, VARDECL> postState,
			final Deque<WorklistItem<STATE, ACTION, VARDECL, LOCATION>> worklist) {
		final ACTION callAction = currentItem.getAction();
		if (!mTransitionProvider.isEnteringScope(callAction)) {
			// can only use summary if entering a scope
			return false;
		}
		final ACTION summaryAction = mTransitionProvider.getSummaryForCall(callAction);

		final AbstractMultiState<STATE, ACTION, VARDECL> summaryPostState =
				currentItem.getSummaryPostState(summaryAction, postState);
		if (summaryPostState == null) {
			// we do not have a usable summary for this call, we have to use it as-it
			if (mLogger.isDebugEnabled()) {
				mLogger.debug(AbsIntPrefInitializer.INDENT + " No summary available for "
						+ LoggingHelper.getTransitionString(callAction, mTransitionProvider));
			}
			return false;
		}

		if (summaryPostState.isBottom()) {
			// skip this item without adding a new one if the summary is already bottom
			if (mLogger.isDebugEnabled()) {
				mLogger.debug(AbsIntPrefInitializer.INDENT + " The summary for "
						+ LoggingHelper.getTransitionString(callAction, mTransitionProvider) + " is bottom");
			}
			return true;
		}

		// we have a usable summary for this call
		if (mLogger.isDebugEnabled()) {
			mLogger.debug(AbsIntPrefInitializer.INDENT + " Using summary for "
					+ LoggingHelper.getTransitionString(callAction, mTransitionProvider));
			mLogger.debug(AbsIntPrefInitializer.DINDENT + " Using " + LoggingHelper.getStateString(summaryPostState));
			mLogger.debug(AbsIntPrefInitializer.DINDENT + " Instead of " + LoggingHelper.getStateString(postState));
		}
		final WorklistItem<STATE, ACTION, VARDECL, LOCATION> newItem =
				currentItem.createSummarySubstitution(summaryPostState, summaryAction);
		worklist.add(newItem);
		return true;
	}

	private AbstractMultiState<STATE, ACTION, VARDECL> calculateAbstractPost(
			final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem,
			final IAbstractPostOperator<STATE, ACTION, VARDECL> postOp) {

		final AbstractMultiState<STATE, ACTION, VARDECL> preState = currentItem.getPreState();
		final AbstractMultiState<STATE, ACTION, VARDECL> hierachicalPreState = currentItem.getHierachicalPreState();
		final ACTION currentAction = currentItem.getAction();

		// calculate the (abstract) effect of the current action by first
		// declaring variables in the prestate, and then calculating their
		// values
		final AbstractMultiState<STATE, ACTION, VARDECL> preStateWithFreshVariables =
				preState.defineVariablesAfter(mVarProvider, currentAction, hierachicalPreState);

		AbstractMultiState<STATE, ACTION, VARDECL> postState;
		if (preState == preStateWithFreshVariables) {
			postState = preStateWithFreshVariables.apply(postOp, currentAction);
		} else {
			// a context switch happened
			postState = preStateWithFreshVariables.apply(postOp, preState, currentAction);
			// TODO: it is ok if this gets bottom, but there is a bug somewhere...
			isHierachicalPostResultBottom(postState, currentItem);
		}

		assert mTransitionProvider.isSummaryWithImplementation(currentAction) || mDebugHelper.isPostSound(preState,
				preStateWithFreshVariables, postState, currentAction) : getLogMessageUnsoundPost(preState,
						preStateWithFreshVariables, postState, currentAction);

		// check if we enter or leave a scope and act accordingly (saving summaries, creating new scope storages, etc.)
		postState = prepareScope(currentItem, postState);

		return postState;
	}

	/**
	 * Check whether a pending post state is bottom or already subsumed by the current fixpoint.
	 *
	 * @param currentItem
	 *            The worklist item for which we calculate a post state.
	 * @param pendingPostState
	 *            The post state as computed by the abstract post.
	 * @return true if the pendingPostState is either false or already subsumed by an existing state (i.e., a fixpoint),
	 *         and false otherwise.
	 */
	private boolean isUnnecessaryPostState(final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem,
			final AbstractMultiState<STATE, ACTION, VARDECL> pendingPostState) {
		if (pendingPostState.isBottom()) {
			// if the new abstract state is bottom, we do not enter loops and we do not add
			// new actions to the worklist
			if (mLogger.isDebugEnabled()) {
				mLogger.debug(getLogMessagePostIsBottom(pendingPostState));
			}
			return true;
		}

		final IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION> currentStateStorage =
				currentItem.getCurrentStorage();

		// check if the pending post state is already subsumed by a pre-existing state and if this is not a return
		if (checkSubset(currentStateStorage, currentItem.getAction(), pendingPostState)) {
			// it is subsumed, we can skip all successors safely
			return true;
		}

		return false;
	}

	private boolean isHierachicalPostResultBottom(final AbstractMultiState<STATE, ACTION, VARDECL> postState,
			final WorklistItem<?, ?, ?, ?> currentItem) {
		if (postState.isBottom()) {
			if (mLogger.isDebugEnabled()) {
				mLogger.debug("Predecessor sequence of current worklist item: " + CoreUtil.getPlatformLineSeparator()
						+ LoggingHelper.getPrecedessorSequence(currentItem));
			}
			return false;
		}
		return true;
	}

	private void setActiveLoopState(final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem) {
		final ACTION currentAction = currentItem.getAction();
		// check if we are entering a loop
		if (mLoopDetector.isEnteringLoop(currentAction)) {
			loopEnter(currentItem);
		}

		// check if we are leaving a loop
		if (currentItem.isActiveLoopHead(mTransitionProvider.getTarget(currentAction))) {
			loopLeave(currentItem);
		}
	}

	private AbstractMultiState<STATE, ACTION, VARDECL> savePostState(
			final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem,
			final AbstractMultiState<STATE, ACTION, VARDECL> postState) {
		final IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION> currentStorage = currentItem.getCurrentStorage();
		final ACTION currentAction = currentItem.getAction();

		if (mLogger.isDebugEnabled()) {
			mLogger.debug(getLogMessageNewPostState(postState));
		}
		return currentStorage.addAbstractPostState(currentAction, postState);
	}

	private void checkReachedError(final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem,
			final AbstractMultiState<STATE, ACTION, VARDECL> postState, final Set<ACTION> reachedErrors) {
		final ACTION currentAction = currentItem.getAction();
		if (!mTransitionProvider.isPostErrorLocation(currentAction, currentItem.getCurrentScope())
				|| postState.isBottom() || !reachedErrors.add(currentAction)) {
			// no new error reached
			return;
		}
		if (mLogger.isDebugEnabled()) {
			mLogger.debug(new StringBuilder().append(AbsIntPrefInitializer.INDENT).append(" Error state reached"));
		}

		mResult.reachedError(mTransitionProvider, currentItem, postState);
	}

	private void loopLeave(final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem) {
		final int loopCounterValue = currentItem.leaveCurrentLoop();
		if (mLogger.isDebugEnabled()) {
			final ACTION current = currentItem.getAction();
			final LOCATION loopHead = mTransitionProvider.getTarget(current);
			mLogger.debug(getLogMessageLeaveLoop(loopCounterValue, loopHead));
		}
	}

	private void loopEnter(final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem) {
		final LOCATION currentLoopHead = mTransitionProvider.getSource(currentItem.getAction());
		final int loopCounterValue = currentItem.enterLoop(currentLoopHead);
		if (mLogger.isDebugEnabled()) {
			mLogger.debug(getLogMessageEnterLoop(loopCounterValue, currentLoopHead, currentItem.getPreState()));
		}
	}

	private WorklistItem<STATE, ACTION, VARDECL, LOCATION> createInitialWorklistItem(final ACTION elem) {
		final STATE preState = mVarProvider.defineInitialVariables(elem, mDomain.createFreshState());
		final AbstractMultiState<STATE, ACTION, VARDECL> preMultiState =
				new AbstractMultiState<>(mMaxParallelStates, preState);
		return new WorklistItem<>(preMultiState, elem, mStateStorage, mSummaryMap);
	}

	private List<WorklistItem<STATE, ACTION, VARDECL, LOCATION>> createSuccessorItems(
			final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem,
			final AbstractMultiState<STATE, ACTION, VARDECL> postState) {
		final ACTION current = currentItem.getAction();
		final Collection<ACTION> successors = mTransitionProvider.getSuccessors(current, currentItem.getCurrentScope());

		if (successors.isEmpty()) {
			if (mLogger.isDebugEnabled()) {
				mLogger.debug(new StringBuilder().append(AbsIntPrefInitializer.INDENT).append(" No successors"));
			}
			return Collections.emptyList();
		}

		final List<WorklistItem<STATE, ACTION, VARDECL, LOCATION>> successorItems =
				successors.stream().filter(a -> !mTransitionProvider.isSummaryWithImplementation(a))
						.map(succ -> new WorklistItem<>(postState, succ, currentItem)).collect(Collectors.toList());

		if (mLogger.isDebugEnabled()) {
			successorItems.stream().map(this::getLogMessageAddTransition).forEach(mLogger::debug);
		}

		return successorItems;
	}

	private AbstractMultiState<STATE, ACTION, VARDECL> widenIfNecessary(
			final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem,
			final AbstractMultiState<STATE, ACTION, VARDECL> postState,
			final IAbstractStateBinaryOperator<STATE> wideningOp) {

		final ACTION currentAction = currentItem.getAction();

		// check if we should widen at this location before adding new successors
		// we should widen if the current item is a transition to a loop head
		// or if a successor transition enters a scope
		final LOCATION target = mTransitionProvider.getTarget(currentAction);
		final Pair<Integer, AbstractMultiState<STATE, ACTION, VARDECL>> loopPair = currentItem.getLoopPair(target);
		final AbstractMultiState<STATE, ACTION, VARDECL> oldState;
		boolean scopeWidening = false;
		if (loopPair != null && loopPair.getFirst() > mMaxUnwindings) {
			oldState = loopPair.getSecond();
		} else if (mTransitionProvider.isEnteringScope(currentAction)) {
			oldState = getWidenStateAtScopeEntry(currentItem);
			scopeWidening = true;
		} else {
			oldState = null;
		}

		if (oldState == null) {
			// no widening necessary
			return postState;
		}

		// we widen with the oldState and all postStates and keep the states that are not fixpoints
		if (mLogger.isDebugEnabled()) {
			mLogger.debug(AbsIntPrefInitializer.DINDENT + "Applying widening op:");
			mLogger.debug(AbsIntPrefInitializer.DINDENT + "Op1: " + LoggingHelper.getStateString(oldState));
			mLogger.debug(AbsIntPrefInitializer.DINDENT + "Op2: " + LoggingHelper.getStateString(postState));
		}
		final AbstractMultiState<STATE, ACTION, VARDECL> postStateAfterWidening = oldState.apply(wideningOp, postState);
		if (isFixpoint(oldState, postStateAfterWidening)) {
			if (scopeWidening) {
				// if we found a fixpoint during scope widening, it means that we will not continue into this scope but
				// rather subsume all calls to this scope by the current one
				currentItem.getCurrentStorage().scopeFixpointReached();
			}
			return null;
		}
		return postStateAfterWidening;
	}

	/**
	 * Check if we are entering or leaving a scope and if so, create or delete it.
	 *
	 * @param postState
	 */
	private AbstractMultiState<STATE, ACTION, VARDECL> prepareScope(
			final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem,
			final AbstractMultiState<STATE, ACTION, VARDECL> postState) {
		final ACTION action = currentItem.getAction();
		if (mTransitionProvider.isEnteringScope(action)) {
			currentItem.addScope(action, postState);
			if (mLogger.isDebugEnabled()) {
				mLogger.debug(getLogMessageEnterScope(currentItem));
			}
			return postState;
		} else if (isLeavingScope(currentItem)) {
			final ACTION oldScope = currentItem.removeCurrentScope(currentItem.getPreState());
			if (mLogger.isDebugEnabled()) {
				mLogger.debug(getLogMessageLeaveScope(oldScope, currentItem));
			}
			return postState;
		} else {
			return postState;
		}
	}

	private boolean isLeavingScope(final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem) {
		return mTransitionProvider.isLeavingScope(currentItem.getAction(), currentItem.getCurrentScope());
	}

	private AbstractMultiState<STATE, ACTION, VARDECL>
			getWidenStateAtScopeEntry(final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem) {
		final ACTION currentAction = currentItem.getAction();

		final Deque<Pair<ACTION, IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>>> stackAtCallLocation =
				currentItem.getScopeStack();

		// count all stack items that are there more than once and the current item
		final Optional<Long> count = stackAtCallLocation.stream().map(a -> a.getFirst()).filter(a -> a != null)
				.collect(Collectors.groupingBy(a -> a, Collectors.counting())).entrySet().stream()
				.filter(e -> e.getValue() > 1 || e.getKey() == currentAction).map(e -> e.getValue())
				.reduce((a, b) -> a + b);
		if (!count.isPresent() || count.get() <= mMaxUnwindings) {
			// if the stack is too small, we do not need to widen
			return null;
		}

		if (mLogger.isDebugEnabled()) {
			mLogger.debug(AbsIntPrefInitializer.DINDENT + " Scope widening sequence for "
					+ LoggingHelper.getTransitionString(currentAction, mTransitionProvider) + " (MaxUnwindings="
					+ mMaxUnwindings + ")");
			mLogger.debug(AbsIntPrefInitializer.DINDENT + " Stack");
			stackAtCallLocation.stream().sequential().map(a1 -> a1.getFirst())
					.map(a2 -> a2 == null ? "[G]" : LoggingHelper.getTransitionString(a2, mTransitionProvider))
					.map(a3 -> AbsIntPrefInitializer.TINDENT + a3).forEach(mLogger::debug);
		}

		// get all stack items in the correct order that contain only calls to the current scope
		final List<Pair<ACTION, IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>>> relevantStackItems =
				stackAtCallLocation.stream().sequential()
						.filter(a -> a.getFirst() == currentAction || a.getFirst() == null)
						.collect(Collectors.toList());
		if (relevantStackItems.isEmpty()) {
			// there is no relevant sequence
			return null;
		}

		if (mLogger.isDebugEnabled()) {
			mLogger.debug(AbsIntPrefInitializer.DINDENT + "Relevant stack states");
			relevantStackItems.stream().sequential()
					.map(a -> AbsIntPrefInitializer.TINDENT
							+ (a.getFirst() == null ? "[G]" : LoggingHelper.getHashCodeString(a.getFirst())) + " "
							+ a.getSecond().toString())
					.forEach(mLogger::debug);
		}

		final List<AbstractMultiState<STATE, ACTION, VARDECL>> orderedStates = relevantStackItems.stream().sequential()
				.map(a -> a.getSecond().getAbstractPostStates(currentAction)).collect(Collectors.toList());
		if (orderedStates.isEmpty()) {
			// this is the first occurrence of this action, so we cannot widen
			return null;
		}

		if (mLogger.isDebugEnabled()) {
			mLogger.debug(
					AbsIntPrefInitializer.DINDENT + "Ordered states " + LoggingHelper.getHashCodeString(currentAction));
			orderedStates.stream().sequential()
					.forEach(a -> mLogger.debug(AbsIntPrefInitializer.TINDENT + LoggingHelper.getStateString(a)));
		}

		// select the last state
		final int idx = orderedStates.size() - 2;
		if (idx < 0) {
			// not enough states to widen
			return null;
		}
		final AbstractMultiState<STATE, ACTION, VARDECL> lastState = orderedStates.get(orderedStates.size() - 2);

		if (mLogger.isDebugEnabled()) {
			mLogger.debug(AbsIntPrefInitializer.DINDENT + "Selected " + LoggingHelper.getHashCodeString(lastState));
		}
		return lastState;
	}

	private boolean isFixpoint(final AbstractMultiState<STATE, ACTION, VARDECL> oldState,
			final AbstractMultiState<STATE, ACTION, VARDECL> newState) {
		if (oldState.isEqualTo(newState)) {
			if (mLogger.isDebugEnabled()) {
				mLogger.debug(getLogMessageFixpointFound(oldState, newState));
			}
			mBenchmark.addFixpoint();
			return true;
		}
		return false;
	}

	private boolean checkSubset(final IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION> currentStateStorage,
			final ACTION currentAction, final AbstractMultiState<STATE, ACTION, VARDECL> pendingPostState) {
		final AbstractMultiState<STATE, ACTION, VARDECL> oldPostState =
				currentStateStorage.getAbstractPostStates(currentAction);
		if (pendingPostState == oldPostState || pendingPostState.isSubsetOf(oldPostState) != SubsetResult.NONE) {
			if (mLogger.isDebugEnabled()) {
				mLogger.debug(getLogMessagePostIsSubsumed(pendingPostState, oldPostState));
			}
			return true;
		}
		return false;
	}

	private void checkTimeout() {
		if (!mTimer.continueProcessing()) {
			mLogger.warn("Received timeout, aborting fixpoint engine");
			throw new ToolchainCanceledException(getClass(), "executing abstract interpretation");
		}
	}

	private void logDebugPostChanged(final AbstractMultiState<STATE, ACTION, VARDECL> postState,
			final AbstractMultiState<STATE, ACTION, VARDECL> postStateAfterChange, final String reason) {
		if (!mLogger.isDebugEnabled()) {
			return;
		}
		if (postState == postStateAfterChange) {
			return;
		}
		final String prefix = AbsIntPrefInitializer.INDENT + AbsIntPrefInitializer.INDENT;
		mLogger.debug(prefix + reason);
		mLogger.debug(prefix + "Before: " + LoggingHelper.getStateString(postState));
		mLogger.debug(prefix + "After: " + LoggingHelper.getStateString(postStateAfterChange));
	}

	private String getLogMessageUnsoundPost(final AbstractMultiState<STATE, ACTION, VARDECL> preState,
			final AbstractMultiState<STATE, ACTION, VARDECL> preStateWithFreshVariables,
			final AbstractMultiState<STATE, ACTION, VARDECL> postState, final ACTION currentAction) {
		final StringBuilder sb = new StringBuilder();
		sb.append("Post is unsound because the term-transformation of the following triple is not valid: {");
		sb.append(preState.toLogString());
		sb.append("} ");
		if (preState != preStateWithFreshVariables) {
			sb.append("{");
			sb.append(preStateWithFreshVariables.toLogString());
			sb.append("} ");
		}
		sb.append(mTransitionProvider.toLogString(currentAction));
		sb.append(" {");
		if (postState != null) {
			sb.append(postState.toLogString());
		}
		sb.append("}");
		return sb.toString();
	}

	private StringBuilder
			getLogMessagePostIsBottom(final AbstractMultiState<STATE, ACTION, VARDECL> pendingNewPostState) {
		return new StringBuilder().append(AbsIntPrefInitializer.INDENT)
				.append(" Skipping all successors because post state [").append(pendingNewPostState.hashCode())
				.append("] is bottom");
	}

	private StringBuilder getLogMessagePostIsSubsumed(final AbstractMultiState<STATE, ACTION, VARDECL> subState,
			final AbstractMultiState<STATE, ACTION, VARDECL> superState) {
		return new StringBuilder().append(AbsIntPrefInitializer.INDENT)
				.append(" Skipping all successors because post state ").append(LoggingHelper.getStateString(subState))
				.append(" is subsumed by pre-existing state ").append(LoggingHelper.getStateString(superState));
	}

	private StringBuilder getLogMessageLeaveScope(final ACTION oldScope,
			final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem) {
		return new StringBuilder().append(AbsIntPrefInitializer.INDENT).append(" Transition [")
				.append(currentItem.getAction().hashCode()).append("] leaves scope ")
				.append(LoggingHelper.getHashCodeString(oldScope)).append(" (new depth=")
				.append(currentItem.getScopeStackDepth()).append("): ")
				.append(LoggingHelper.getScopeStackString(currentItem.getScopeStack()));
	}

	private StringBuilder getLogMessageEnterScope(final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem) {
		return new StringBuilder().append(AbsIntPrefInitializer.INDENT).append(" Transition [")
				.append(currentItem.getAction().hashCode()).append("] enters scope (new depth=")
				.append(currentItem.getScopeStackDepth()).append("): ")
				.append(LoggingHelper.getScopeStackString(currentItem.getScopeStack()));

	}

	private StringBuilder getLogMessageFixpointFound(final AbstractMultiState<STATE, ACTION, VARDECL> oldPostState,
			final AbstractMultiState<STATE, ACTION, VARDECL> newPostState) {
		return new StringBuilder().append(AbsIntPrefInitializer.INDENT).append(" State [")
				.append(oldPostState.hashCode()).append("] ").append(oldPostState.toLogString())
				.append(" is equal to [").append(newPostState.hashCode()).append("]");
	}

	private StringBuilder getLogMessageNewPostState(final AbstractMultiState<STATE, ACTION, VARDECL> newPostState) {
		return new StringBuilder().append(AbsIntPrefInitializer.INDENT).append(" Adding post state [")
				.append(newPostState.hashCode()).append("] ").append(newPostState.toLogString());
	}

	private StringBuilder getLogMessageEnterLoop(final int loopCounterValue, final LOCATION loopHead,
			final AbstractMultiState<STATE, ACTION, VARDECL> state) {
		return new StringBuilder().append(AbsIntPrefInitializer.INDENT).append(" Entering loop ").append(loopHead)
				.append(" (").append(loopCounterValue).append("), saving ").append(LoggingHelper.getStateString(state));
	}

	private StringBuilder getLogMessageLeaveLoop(final int loopCounterValue, final LOCATION loopHead) {
		return new StringBuilder().append(AbsIntPrefInitializer.INDENT).append(" Leaving loop ").append(loopHead)
				.append(" (").append(loopCounterValue).append(")");
	}

	private StringBuilder
			getLogMessageCurrentTransition(final WorklistItem<STATE, ACTION, VARDECL, LOCATION> currentItem) {
		final AbstractMultiState<STATE, ACTION, VARDECL> preState = currentItem.getPreState();
		final ACTION current = currentItem.getAction();
		final int depth = currentItem.getScopeStackDepth();
		final String preStateString = preState == null ? "NULL" : LoggingHelper.getStateString(preState).toString();
		return LoggingHelper.getTransitionString(current, mTransitionProvider).append(" processing for pre state ")
				.append(preStateString).append(" (depth=").append(depth).append(")");
	}

	private StringBuilder getLogMessageAddTransition(final WorklistItem<STATE, ACTION, VARDECL, LOCATION> item) {
		return new StringBuilder().append(AbsIntPrefInitializer.INDENT).append(" Adding [")
				.append(item.getPreState().hashCode()).append("]").append(" --[").append(item.getAction().hashCode())
				.append("]->");
	}
}
