/*
 * Copyright (C) 2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE ModelCheckerUtils Library.
 *
 * The ULTIMATE ModelCheckerUtils Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE ModelCheckerUtils Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ModelCheckerUtils Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ModelCheckerUtils Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE ModelCheckerUtils Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple;

import java.util.function.Function;

import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.ICallAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IInternalAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IReturnAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript.ILockHolderWithVoluntaryLockRelease;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.util.statistics.AStatisticsType;
import de.uni_freiburg.informatik.ultimate.util.statistics.IStatisticsElement;

/**
 * Object that implement this interface check if Hoare Triples are valid. Hoare triples that we check are of the form {
 * P } act { Q } where P and Q are given by IPredicates, act has to be a single action. Note that for return statements
 * we have to check a quadruple
 *
 * @author Matthias Heizmann
 *
 */
public interface IHoareTripleChecker extends ILockHolderWithVoluntaryLockRelease {

	/**
	 * Hoare Triple Truth Value. This is the result of a Hoare triple check.
	 */
	public enum Validity {
		VALID, INVALID, UNKNOWN, NOT_CHECKED
	}

	/**
	 * Check if the Hoare triple {pre} act {succ} is valid for an internal action act. Internal transition means that
	 * the program is in the same procedure before and after the action act was executed.
	 */
	public Validity checkInternal(IPredicate pre, IInternalAction act, IPredicate succ);

	/**
	 * Check if the Hoare triple {pre} call {succ} is valid for a call action.
	 */
	public Validity checkCall(IPredicate pre, ICallAction act, IPredicate succ);

	/**
	 * Check if the Hoare quadruple {preLin} {preHier} return {succ} is valid for a return transition. Here, the action
	 * has to be a return, preLin is the IPredicate that describes a set of states of the called procedure before the
	 * return, preHier is the IPredicate that describes a set of states of the calling procedure before the call, and
	 * succ is the IPredicate that describes a set of states of the called procedure.
	 */
	public Validity checkReturn(IPredicate preLin, IPredicate preHier, IReturnAction act, IPredicate succ);

	public abstract HoareTripleCheckerStatisticsGenerator getEdgeCheckerBenchmark();

	public enum HoareTripleCheckerStatisticsDefinitions implements IStatisticsElement {

		SDtfs(Integer.class, AStatisticsType.s_IncareAddition, AStatisticsType.s_DataBeforeKey),

		SDslu(Integer.class, AStatisticsType.s_IncareAddition, AStatisticsType.s_DataBeforeKey),

		SDs(Integer.class, AStatisticsType.s_IncareAddition, AStatisticsType.s_DataBeforeKey),

		SdLazy(Integer.class, AStatisticsType.s_IncareAddition, AStatisticsType.s_DataBeforeKey),

		SolverSat(Integer.class, AStatisticsType.s_IncareAddition, AStatisticsType.s_DataBeforeKey),

		SolverUnsat(Integer.class, AStatisticsType.s_IncareAddition, AStatisticsType.s_DataBeforeKey),

		SolverUnknown(Integer.class, AStatisticsType.s_IncareAddition, AStatisticsType.s_DataBeforeKey),

		SolverNotchecked(Integer.class, AStatisticsType.s_IncareAddition, AStatisticsType.s_DataBeforeKey),

		Time(Integer.class, AStatisticsType.s_LongAddition, AStatisticsType.s_TimeBeforeKey),;

		private final Class<?> mClazz;
		private final Function<Object, Function<Object, Object>> mAggr;
		private final Function<String, Function<Object, String>> mPrettyprinter;

		HoareTripleCheckerStatisticsDefinitions(final Class<?> clazz,
				final Function<Object, Function<Object, Object>> aggr,
				final Function<String, Function<Object, String>> prettyprinter) {
			mClazz = clazz;
			mAggr = aggr;
			mPrettyprinter = prettyprinter;
		}

		@Override
		public Object aggregate(final Object o1, final Object o2) {
			return mAggr.apply(o1).apply(o2);
		}

		@Override
		public String prettyprint(final Object o) {
			return mPrettyprinter.apply(name()).apply(o);
		}

		@Override
		public Class<?> getDataType() {
			return mClazz;
		}
	}

	public static Validity lbool2validity(final LBool lbool) {
		switch (lbool) {
		case SAT:
			return Validity.INVALID;
		case UNKNOWN:
			return Validity.UNKNOWN;
		case UNSAT:
			return Validity.VALID;
		default:
			throw new AssertionError();
		}
	}

}
