/*
 * Copyright (C) 2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
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
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.model.IAbstractState;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 * A worklist item is used by {@link FixpointEngine} to store intermediate results from the effect calculation for one
 * transition.
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 */
final class WorklistItem<STATE extends IAbstractState<STATE, ACTION, VARDECL>, ACTION, VARDECL, LOCATION> {

	private final AbstractMultiState<STATE, ACTION, VARDECL> mPreState;
	private final ACTION mAction;
	private final Deque<IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>> mScopedStorages;
	private final Deque<LOCATION> mActiveLoops;
	private final Map<LOCATION, Pair<Integer, AbstractMultiState<STATE, ACTION, VARDECL>>> mLoopPairs;
	private final WorklistItem<STATE, ACTION, VARDECL, LOCATION> mPredecessor;
	private final SummaryMap<STATE, ACTION, VARDECL, LOCATION> mSummaryMap;

	private AbstractMultiState<STATE, ACTION, VARDECL> mHierachicalPreState;
	private Deque<ScopeStackItem> mScopes;

	private WorklistItem(final AbstractMultiState<STATE, ACTION, VARDECL> pre,
			final AbstractMultiState<STATE, ACTION, VARDECL> hierpre, final ACTION action,
			final Deque<IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>> scopedStorages,
			final Deque<LOCATION> activeLoops,
			final Map<LOCATION, Pair<Integer, AbstractMultiState<STATE, ACTION, VARDECL>>> loopPairs,
			final WorklistItem<STATE, ACTION, VARDECL, LOCATION> predecessorItem,
			final SummaryMap<STATE, ACTION, VARDECL, LOCATION> summaryMap, final Deque<ScopeStackItem> scopes) {
		assert action != null;
		assert pre != null : "Prestate may not be null";
		assert summaryMap != null;
		mHierachicalPreState = hierpre;
		mPreState = pre;
		mAction = action;
		mScopedStorages = scopedStorages;
		mActiveLoops = activeLoops;
		mLoopPairs = loopPairs;
		mPredecessor = predecessorItem;
		mSummaryMap = summaryMap;
		mScopes = scopes;
	}

	WorklistItem(final AbstractMultiState<STATE, ACTION, VARDECL> pre, final ACTION action,
			final IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION> globalStorage,
			final SummaryMap<STATE, ACTION, VARDECL, LOCATION> summaryMap) {
		this(pre, pre, action, new ArrayDeque<>(), new ArrayDeque<>(), new HashMap<>(), null, summaryMap, null);
		assert globalStorage != null;
		mScopedStorages.addFirst(globalStorage);
	}

	WorklistItem(final AbstractMultiState<STATE, ACTION, VARDECL> pre, final ACTION action,
			final WorklistItem<STATE, ACTION, VARDECL, LOCATION> oldItem) {
		// TODO: Replace eager initialization with lazy initialization
		this(pre, oldItem.getHierachicalPreState(), action, oldItem.getStoragesCopy(),
				new ArrayDeque<>(oldItem.mActiveLoops), new HashMap<>(oldItem.mLoopPairs), oldItem, oldItem.mSummaryMap,
				oldItem.getScopesCopy());
	}

	ACTION getAction() {
		return mAction;
	}

	AbstractMultiState<STATE, ACTION, VARDECL> getPreState() {
		return mPreState;
	}

	AbstractMultiState<STATE, ACTION, VARDECL> getHierachicalPreState() {
		return mHierachicalPreState;
	}

	ACTION getCurrentScope() {
		if (mScopes == null || mScopes.isEmpty()) {
			return null;
		}
		return mScopes.peek().getAction();
	}

	/**
	 * Has to be called whenever {@link FixpointEngine} enters a scope.
	 *
	 * @param scope
	 * @param postState
	 */
	void addScope(final ACTION scope, final AbstractMultiState<STATE, ACTION, VARDECL> postCallState) {
		assert scope != null;
		if (mScopes == null) {
			mScopes = new ArrayDeque<>();
		}
		mScopes.addFirst(new ScopeStackItem(scope, mHierachicalPreState, postCallState));
		mHierachicalPreState = mPreState;
		mScopedStorages.addFirst(getCurrentStorage().createStorage(scope));
	}

	/**
	 * Has to be called whenever {@link FixpointEngine} leaves a scope. Ensures that the state storage is in the correct
	 * state and that the summary map is updated.
	 *
	 * @param preReturnState
	 *            The post state after leaving the current scope.
	 * @return The scope that left.
	 */
	ACTION removeCurrentScope(final AbstractMultiState<STATE, ACTION, VARDECL> preReturnState) {
		final ScopeStackItem currentScopeItem = removeCurrentScopeWithoutSummary();
		if (currentScopeItem == null) {
			// happens when we leave the global scope
			return null;
		}
		// called when ACTION is a return; but before the scope is changed
		// meaning that the scope is the corresponding call, and one of its predecessors is the matching summary
		mSummaryMap.addSummary(currentScopeItem.getScopeFirstState(), preReturnState, currentScopeItem.getAction());
		return currentScopeItem.getAction();
	}

	AbstractMultiState<STATE, ACTION, VARDECL> getSummaryPostState(final ACTION summary,
			final AbstractMultiState<STATE, ACTION, VARDECL> preState) {
		return mSummaryMap.getSummaryPostState(summary, preState);
	}

	WorklistItem<STATE, ACTION, VARDECL, LOCATION> createSummarySubstitution(
			final AbstractMultiState<STATE, ACTION, VARDECL> summaryPostState, final ACTION summaryAction) {
		// facts about the state when this method is called:
		// - a summary replaces a call statement after the new post state of the call statement is already calculated
		// - this instance is the call item
		// - the summary will be treated as a normal statement for purposes of scoping, but as an return for purposes of
		// post calculation
		// - the summary should have as hierprestate and prestate both the prestate of the call (?)
		// - loops, scopestorage, scopes should be as if the call never happened
		// TODO: Cleanup
		getCurrentStorage().saveSummarySubstituion(getAction(), summaryPostState, summaryAction);
		final AbstractMultiState<STATE, ACTION, VARDECL> hierPreState = mHierachicalPreState;
		removeCurrentScopeWithoutSummary();
		final WorklistItem<STATE, ACTION, VARDECL, LOCATION> rtr =
				new WorklistItem<>(summaryPostState, summaryAction, this);
		rtr.mHierachicalPreState = hierPreState;
		return rtr;
	}

	private ScopeStackItem removeCurrentScopeWithoutSummary() {
		if (mScopes == null || mScopes.isEmpty()) {
			// happens when we leave the global scope
			return null;
		}
		mScopedStorages.removeFirst();
		final ScopeStackItem rtr = mScopes.removeFirst();
		mHierachicalPreState = rtr.getScopeHierPreState();
		return rtr;
	}

	IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION> getCurrentStorage() {
		assert !mScopedStorages.isEmpty();
		return mScopedStorages.peek();
	}

	int getScopeStackDepth() {
		if (mScopes == null || mScopes.isEmpty()) {
			return 0;
		}
		return mScopes.size();
	}

	/**
	 *
	 * @return A {@link Deque} that contains pairs of scopes and the corresponding state storage.
	 */
	Deque<Pair<ACTION, IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>>> getScopeStack() {
		final ArrayDeque<Pair<ACTION, IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>>> rtr =
				new ArrayDeque<>();
		final Iterator<IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>> storageIter =
				mScopedStorages.descendingIterator();
		// first, add the global storage
		rtr.add(new Pair<ACTION, IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>>(null, storageIter.next()));
		if (mScopes == null || mScopes.isEmpty()) {
			return rtr;
		}

		final Iterator<ScopeStackItem> scopeIter = mScopes.descendingIterator();

		while (scopeIter.hasNext() && storageIter.hasNext()) {
			rtr.add(new Pair<>(scopeIter.next().getAction(), storageIter.next()));
		}
		assert !scopeIter.hasNext();
		assert !storageIter.hasNext();

		return rtr;
	}

	boolean isActiveLoopHead(final LOCATION currentLoopHead) {
		return !mActiveLoops.isEmpty() && mActiveLoops.peek() == currentLoopHead;
	}

	int leaveCurrentLoop() {
		assert !mActiveLoops.isEmpty() : "Active loops is empty";
		final LOCATION lastLoopHead = mActiveLoops.pop();
		final Pair<Integer, AbstractMultiState<STATE, ACTION, VARDECL>> loopPair = mLoopPairs.get(lastLoopHead);
		assert loopPair != null;
		return loopPair.getFirst();
	}

	int enterLoop(final LOCATION loopHead) {
		if (mActiveLoops.isEmpty() || !mActiveLoops.peek().equals(loopHead)) {
			mActiveLoops.push(loopHead);
		}
		final AbstractMultiState<STATE, ACTION, VARDECL> prestate = getPreState();
		final Pair<Integer, AbstractMultiState<STATE, ACTION, VARDECL>> loopPair = mLoopPairs.get(loopHead);
		final Pair<Integer, AbstractMultiState<STATE, ACTION, VARDECL>> newLoopPair;
		if (loopPair == null) {
			newLoopPair = new Pair<>(0, prestate);
		} else {
			newLoopPair = new Pair<>(loopPair.getFirst() + 1, prestate);
		}
		mLoopPairs.put(loopHead, newLoopPair);
		return newLoopPair.getFirst();
	}

	Pair<Integer, AbstractMultiState<STATE, ACTION, VARDECL>> getLoopPair(final LOCATION loopHead) {
		return mLoopPairs.get(loopHead);
	}

	WorklistItem<STATE, ACTION, VARDECL, LOCATION> getPredecessor() {
		return mPredecessor;
	}

	private Deque<ScopeStackItem> getScopesCopy() {
		if (mScopes == null || mScopes.isEmpty()) {
			return null;
		}
		return new ArrayDeque<>(mScopes);
	}

	private Deque<IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>> getStoragesCopy() {
		assert !mScopedStorages.isEmpty();
		return new ArrayDeque<>(mScopedStorages);
	}

	@Override
	public String toString() {
		final String preStateHashCode = mPreState == null ? "?" : String.valueOf(mPreState.hashCode());
		final StringBuilder builder = new StringBuilder().append('[').append(preStateHashCode).append("]--[")
				.append(mAction.hashCode()).append("]--> ? (Scope={[G]");
		if (mScopes != null) {
			final Iterator<ScopeStackItem> iter = mScopes.descendingIterator();
			while (iter.hasNext()) {
				builder.append("[").append(iter.next().getAction().hashCode()).append("]");
			}
		}
		builder.append("})");
		return builder.toString();
	}

	String toExtendedString() {
		return toString() + " Pre: " + LoggingHelper.getHashCodeString(mPreState) + " "
				+ Optional.ofNullable(mPreState).map(a -> a.toLogString()).orElse("?") + " HierPre: "
				+ LoggingHelper.getHashCodeString(mHierachicalPreState) + " "
				+ Optional.ofNullable(mHierachicalPreState).map(a -> a.toLogString()).orElse("?");
	}

	/**
	 * Container for scope stack items.
	 *
	 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
	 */
	private final class ScopeStackItem {
		private final ACTION mScope;
		private final AbstractMultiState<STATE, ACTION, VARDECL> mScopeHierachicalPreState;
		private final AbstractMultiState<STATE, ACTION, VARDECL> mScopeFirstState;

		private ScopeStackItem(final ACTION action, final AbstractMultiState<STATE, ACTION, VARDECL> hierPre,
				final AbstractMultiState<STATE, ACTION, VARDECL> scopeFirst) {
			mScope = action;
			mScopeHierachicalPreState = hierPre;
			mScopeFirstState = scopeFirst;
		}

		ACTION getAction() {
			return mScope;
		}

		AbstractMultiState<STATE, ACTION, VARDECL> getScopeHierPreState() {
			return mScopeHierachicalPreState;
		}

		AbstractMultiState<STATE, ACTION, VARDECL> getScopeFirstState() {
			return mScopeFirstState;
		}
	}
}
