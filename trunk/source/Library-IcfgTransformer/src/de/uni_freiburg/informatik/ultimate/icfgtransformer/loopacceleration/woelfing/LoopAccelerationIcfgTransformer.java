/*
 * Copyright (C) 2016 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2017 Dennis Wölfing
 * Copyright (C) 2016-2017 University of Freiburg
 *
 * This file is part of the ULTIMATE IcfgTransformer library.
 *
 * The ULTIMATE IcfgTransformer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE IcfgTransformer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE IcfgTransformer library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE IcfgTransformer library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE IcfgTransformer grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.icfgtransformer.loopacceleration.woelfing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.IPayload;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.icfgtransformer.ITransformulaTransformer;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.BasicIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgCallTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgReturnTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgCallTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgReturnTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;

/**
 * An IcfgTransformer that accelerates loops.
 *
 * @param <INLOC>
 *            The type of the locations of the old IIcfg.
 * @param <OUTLOC>
 *            The type of the locations of the transformed IIcfg.
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * @author Dennis Wölfing
 *
 */
public class LoopAccelerationIcfgTransformer<INLOC extends IcfgLocation, OUTLOC extends IcfgLocation> {

	private final ILogger mLogger;
	private final IIcfg<OUTLOC> mResultIcfg;
	private final Map<INLOC, OUTLOC> mOldLoc2NewLoc;
	private final Map<IIcfgCallTransition<INLOC>, IcfgCallTransition> mOldCalls2NewCalls;
	private final ILocationFactory<INLOC, OUTLOC> mLocationFactory;
	private final IBacktranslationTracker mBacktranslationTracker;
	private final ITransformulaTransformer mTransformer;

	private final Set<IcfgEdge> mLoopEntryTransitions;
	private final Map<INLOC, List<Backbone>> mBackbones;

	/**
	 * Constructs a LoopAccelerationIcfgTransformer.
	 *
	 * @param logger
	 *            A {@link ILogger} instance that is used for debug logging.
	 * @param originalIcfg
	 * @param funLocFac
	 * @param backtranslationTracker
	 * @param outLocationClass
	 * @param newIcfgIdentifier
	 * @param transformer
	 */
	public LoopAccelerationIcfgTransformer(final ILogger logger, final IIcfg<INLOC> originalIcfg,
			final ILocationFactory<INLOC, OUTLOC> funLocFac, final IBacktranslationTracker backtranslationTracker,
			final Class<OUTLOC> outLocationClass, final String newIcfgIdentifier,
			final ITransformulaTransformer transformer) {
		final IIcfg<INLOC> origIcfg = Objects.requireNonNull(originalIcfg);
		mLogger = Objects.requireNonNull(logger);
		mLocationFactory = Objects.requireNonNull(funLocFac);
		mOldLoc2NewLoc = new HashMap<>();
		mOldCalls2NewCalls = new HashMap<>();
		mBacktranslationTracker = backtranslationTracker;
		mTransformer = Objects.requireNonNull(transformer);

		mLoopEntryTransitions = new HashSet<>();
		mBackbones = new HashMap<>();

		// perform transformation last
		mResultIcfg = transform(origIcfg, Objects.requireNonNull(newIcfgIdentifier),
				Objects.requireNonNull(outLocationClass));
	}

	/**
	 * Transforms the Icfg.
	 */
	@SuppressWarnings("unchecked")
	private IIcfg<OUTLOC> transform(final IIcfg<INLOC> originalIcfg, final String newIcfgIdentifier,
			final Class<OUTLOC> outLocationClass) {

		// Find all backbones for initial nodes.
		for (final INLOC initialNode : originalIcfg.getInitialNodes()) {
			final List<Backbone> backbones = new ArrayList<>();
			for (final IcfgEdge edge : initialNode.getOutgoingEdges()) {
				backbones.addAll(findBackbones(edge));
			}
			mBackbones.put(initialNode, backbones);
		}

		// Find backbones for loop locations.
		for (final IcfgEdge entryTransition : mLoopEntryTransitions) {
			final List<Backbone> backbones = findBackbones(entryTransition);
			mBackbones.put((INLOC) entryTransition.getSource(), backbones);
		}

		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Found the following backbones:");
			mLogger.debug(mBackbones);
		}

		// Create a new Icfg.
		final BasicIcfg<OUTLOC> resultIcfg =
				new BasicIcfg<>(newIcfgIdentifier, originalIcfg.getCfgSmtToolkit(), outLocationClass);

		final Set<INLOC> init = originalIcfg.getInitialNodes();
		final Deque<INLOC> open = new ArrayDeque<>(init);
		final Set<INLOC> closed = new HashSet<>();

		while (!open.isEmpty()) {
			final INLOC oldSource = open.removeFirst();
			if (!closed.add(oldSource)) {
				continue;
			}

			final OUTLOC newSource = createNewLocation(originalIcfg, resultIcfg, oldSource);
			for (final IcfgEdge oldTransition : oldSource.getOutgoingEdges()) {
				final INLOC oldTarget = (INLOC) oldTransition.getTarget();
				open.add(oldTarget);
				final OUTLOC newTarget = createNewLocation(originalIcfg, resultIcfg, oldTarget);
				final IcfgEdge newTransition;
				if (oldTransition instanceof IIcfgInternalTransition) {
					newTransition = createNewLocalTransition(newSource, newTarget,
							(IIcfgInternalTransition<INLOC>) oldTransition);
				} else if (oldTransition instanceof IIcfgCallTransition) {
					newTransition =
							createNewCallTransition(newSource, newTarget, (IIcfgCallTransition<INLOC>) oldTransition);
				} else if (oldTransition instanceof IIcfgReturnTransition) {
					newTransition = createNewReturnTransition(newSource, newTarget,
							(IIcfgReturnTransition<INLOC, ?>) oldTransition);
				} else {
					throw new IllegalArgumentException("Unknown edge type " + oldTransition.getClass().getSimpleName());
				}
				newSource.addOutgoing(newTransition);
				newTarget.addIncoming(newTransition);
				mBacktranslationTracker.rememberRelation(oldTransition, newTransition);
			}
		}
		return resultIcfg;
	}

	/**
	 * Finds backbones.
	 * @param entryTransition The entry transition of the backbones.
	 * @return A list of backbones.
	 */
	private List<Backbone> findBackbones(final IcfgEdge entryTransition) {
		// TODO: This also gives backbones that end in assertions inside nested loops.

		final List<Backbone> incompleteBackbones = new ArrayList<>();
		final List<Backbone> completeBackbones = new ArrayList<>();
		incompleteBackbones.add(new Backbone(entryTransition));

		while (!incompleteBackbones.isEmpty()) {
			for (int i = incompleteBackbones.size() - 1; i >= 0; i--) {
				final Backbone backbone = incompleteBackbones.get(i);
				final INLOC lastLocation = (INLOC) backbone.getLastLocation();

				if (lastLocation != entryTransition.getSource() && backbone.endsInLoop()) {
					incompleteBackbones.remove(i);
					mLoopEntryTransitions.add(backbone.getLoopEntryTransition());
				} else if (lastLocation == entryTransition.getSource() || lastLocation.getOutgoingEdges().isEmpty()) {
					incompleteBackbones.remove(i);
					completeBackbones.add(backbone);
				}
			}

			final int backboneSize = incompleteBackbones.size();
			for (int i = 0; i < backboneSize; i++) {
				final Backbone backbone = incompleteBackbones.get(i);
				final INLOC location = (INLOC) backbone.getLastLocation();

				final List<IcfgEdge> outgoingEdges = location.getOutgoingEdges();
				for (int j = 0; j < outgoingEdges.size(); j++) {
					final IcfgEdge edge = outgoingEdges.get(j);
					if (j == outgoingEdges.size() - 1) {
						backbone.addTransition(edge);
					} else {
						final Backbone newBackbone = new Backbone(backbone);
						newBackbone.addTransition(edge);
						incompleteBackbones.add(newBackbone);
					}
				}
			}
		}

		return completeBackbones;
	}

	private IcfgReturnTransition createNewReturnTransition(final IcfgLocation source, final IcfgLocation target,
			final IIcfgReturnTransition<INLOC, ?> oldTransition) {
		final IIcfgCallTransition<INLOC> oldCorrespondingCall = oldTransition.getCorrespondingCall();
		final IcfgCallTransition newCorrespondingCall = mOldCalls2NewCalls.get(oldCorrespondingCall);
		assert newCorrespondingCall != null : "The Icfg has been traversed out of order "
				+ "(found return before having found the corresponding call)";
		final UnmodifiableTransFormula retAssign = mTransformer.transform(oldTransition.getAssignmentOfReturn());
		final UnmodifiableTransFormula localVarAssign =
				mTransformer.transform(oldTransition.getLocalVarsAssignmentOfCall());
		return new IcfgReturnTransition(source, target, newCorrespondingCall, getPayloadIfAvailable(oldTransition),
				retAssign, localVarAssign);
	}

	private IcfgCallTransition createNewCallTransition(final IcfgLocation source, final IcfgLocation target,
			final IIcfgCallTransition<INLOC> oldTransition) {
		final UnmodifiableTransFormula unmodTf = mTransformer.transform(oldTransition.getLocalVarsAssignment());
		final IcfgCallTransition rtr =
				new IcfgCallTransition(source, target, getPayloadIfAvailable(oldTransition), unmodTf);
		// cache the created call for usage during return creation
		mOldCalls2NewCalls.put(oldTransition, rtr);
		return rtr;
	}

	private IcfgInternalTransition createNewLocalTransition(final IcfgLocation source, final IcfgLocation target,
			final IIcfgInternalTransition<INLOC> oldTransition) {
		final UnmodifiableTransFormula unmodTf = mTransformer.transform(oldTransition.getTransformula());
		return new IcfgInternalTransition(source, target, getPayloadIfAvailable(oldTransition), unmodTf);
	}

	private static IPayload getPayloadIfAvailable(final IElement elem) {
		if (elem == null) {
			return null;
		}
		if (elem.hasPayload()) {
			return elem.getPayload();
		}
		return null;
	}

	private OUTLOC createNewLocation(final IIcfg<INLOC> originalIcfg, final BasicIcfg<OUTLOC> resultIcfg,
			final INLOC oldLoc) {
		final OUTLOC alreadyCreated = mOldLoc2NewLoc.get(oldLoc);
		if (alreadyCreated != null) {
			// was already created, no need to re-add to the result icfg
			return alreadyCreated;
		}

		final String proc = oldLoc.getProcedure();
		final OUTLOC freshLoc = mLocationFactory.createLocation(oldLoc, oldLoc.getDebugIdentifier(), proc);

		// determine attributes of location
		final boolean isInitial = originalIcfg.getInitialNodes().contains(oldLoc);
		final Set<INLOC> errorLocs = originalIcfg.getProcedureErrorNodes().get(proc);
		final boolean isError = errorLocs != null && errorLocs.contains(oldLoc);
		final boolean isProcEntry = oldLoc.equals(originalIcfg.getProcedureEntryNodes().get(proc));
		final boolean isProcExit = oldLoc.equals(originalIcfg.getProcedureExitNodes().get(proc));
		final boolean isLoopLocation = originalIcfg.getLoopLocations().contains(oldLoc);

		// add fresh location to resultIcfg
		resultIcfg.addLocation(freshLoc, isInitial, isError, isProcEntry, isProcExit, isLoopLocation);

		// cache created IcfgLocation
		mOldLoc2NewLoc.put(oldLoc, freshLoc);

		return freshLoc;
	}

	public IIcfg<OUTLOC> getResult() {
		return mResultIcfg;
	}

	/**
	 * Interface that describes a factory which creates locations for an {@link IIcfg} based on an old location.
	 *
	 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
	 *
	 * @param <INLOC>
	 *            The type of the old locations.
	 * @param <OUTLOC>
	 *            The type of locations that should be produced.
	 */
	@FunctionalInterface
	public static interface ILocationFactory<INLOC extends IcfgLocation, OUTLOC extends IcfgLocation> {
		OUTLOC createLocation(final INLOC oldLocation, final String debugIdentifier, final String procedure);
	}

	@FunctionalInterface
	public static interface IBacktranslationTracker {
		void rememberRelation(final IIcfgTransition<?> oldTransition, final IIcfgTransition<?> newTransition);
	}

}
