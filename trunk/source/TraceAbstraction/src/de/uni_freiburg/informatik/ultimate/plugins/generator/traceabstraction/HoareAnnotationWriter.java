/*
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2014-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE TraceAbstraction plug-in.
 *
 * The ULTIMATE TraceAbstraction plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TraceAbstraction plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TraceAbstraction plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TraceAbstraction plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TraceAbstraction plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction;

import java.util.Map.Entry;

import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgCallTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.PredicateTransformer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.ISLPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.PredicateFactory;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;

/**
 * Write a Hoare annotation provided by HoareAnnotationFragments to the CFG.
 *
 * @author heizmann@informatik.uni-freiburg.de
 *
 */
public class HoareAnnotationWriter {

	private final IUltimateServiceProvider mServices;
	private final IIcfg<?> mIcfg;
	private final CfgSmtToolkit mCsToolkit;
	private final PredicateFactory mPredicateFactory;
	private final HoareAnnotationComposer mCegarLoopHoareAnnotation;

	/**
	 * What is the precondition for a context? Strongest postcondition or entry given by automaton?
	 */
	private final boolean mUseEntry;
	private final PredicateTransformer mPredicateTransformer;

	public HoareAnnotationWriter(final IIcfg<?> icfg, final CfgSmtToolkit csToolkit,
			final PredicateFactory predicateFactory, final HoareAnnotationComposer cegarLoopHoareAnnotation,
			final IUltimateServiceProvider services, final SimplificationTechnique simplicationTechnique,
			final XnfConversionTechnique xnfConversionTechnique) {
		mServices = services;
		mIcfg = icfg;
		mCsToolkit = csToolkit;
		mPredicateFactory = predicateFactory;
		mCegarLoopHoareAnnotation = cegarLoopHoareAnnotation;
		mUseEntry = true;
		mPredicateTransformer = new PredicateTransformer(services, csToolkit.getManagedScript(), simplicationTechnique,
				xnfConversionTechnique);
	}

	public void addHoareAnnotationToCFG() {
		for (final Entry<IcfgLocation, IPredicate> entry : mCegarLoopHoareAnnotation.getLoc2hoare().entrySet()) {
			final HoareAnnotation taAnnot = HoareAnnotation.getAnnotation(entry.getKey());
			final HoareAnnotation hoareAnnot;
			if (taAnnot == null) {
				hoareAnnot =
						mPredicateFactory.getNewHoareAnnotation(entry.getKey(), mCsToolkit.getModifiableGlobalsTable());
				hoareAnnot.annotate(entry.getKey());
			} else {
				hoareAnnot = taAnnot;
			}
			hoareAnnot.addInvariant(entry.getValue());
		}
		// for (final Triple<IcfgLocation, IPredicate, IPredicate> locPrecondInvariant :
		// mCegarLoopHoareAnnotation.getLoc2precond2invariant().entrySet()) {
		// addFormulasToLocNodes(locPrecondInvariant.getFirst(), locPrecondInvariant.getSecond(),
		// locPrecondInvariant.getThird());
		// }
	}

	/**
	 * @param csToolkit
	 * @param precondForContext
	 * @param pp2preds
	 */
	private void addHoareAnnotationForContext(final CfgSmtToolkit csToolkit, final IPredicate precondForContext,
			final HashRelation<IcfgLocation, IPredicate> pp2preds) {
		for (final IcfgLocation pp : pp2preds.getDomain()) {
			final IPredicate[] preds = pp2preds.getImage(pp).toArray(new IPredicate[0]);
			final Term tvp = mPredicateFactory.or(false, preds);
			final IPredicate formulaForPP = mPredicateFactory.newPredicate(tvp);
			addFormulasToLocNodes(pp, precondForContext, formulaForPP);
		}
	}

	private void addFormulasToLocNodes(final IcfgLocation pp, final IPredicate context, final IPredicate current) {
		final String procName = pp.getProcedure();
		final String locName = pp.getDebugIdentifier();
		final IcfgLocation locNode = mIcfg.getProgramPoints().get(procName).get(locName);
		HoareAnnotation hoareAnnot = null;

		final HoareAnnotation taAnnot = HoareAnnotation.getAnnotation(locNode);
		if (taAnnot == null) {
			hoareAnnot = mPredicateFactory.getNewHoareAnnotation(pp, mCsToolkit.getModifiableGlobalsTable());
			hoareAnnot.annotate(locNode);
		} else {
			hoareAnnot = taAnnot;
		}
		hoareAnnot.addInvariant(context, current);
	}

	private static IIcfgCallTransition<?> getCall(final ISLPredicate pred) {
		final IcfgLocation pp = pred.getProgramPoint();
		IIcfgCallTransition<?> result = null;
		for (final IcfgEdge edge : pp.getOutgoingEdges()) {
			if (edge instanceof IIcfgCallTransition<?>) {
				if (result == null) {
					result = (IIcfgCallTransition<?>) edge;
				} else {
					throw new UnsupportedOperationException("several outgoing calls");
				}
			}
		}
		if (result == null) {
			throw new AssertionError("no outgoing call");
		}
		return result;
	}

	private static boolean containsAnOldVar(final IPredicate p) {
		for (final IProgramVar bv : p.getVars()) {
			if (bv.isOldvar()) {
				return true;
			}
		}
		return false;
	}

}
