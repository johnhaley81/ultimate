/*
 * Copyright (C) 2015-2016 Claus Schaetzle (schaetzc@informatik.uni-freiburg.de)
 * Copyright (C) 2015-2016 University of Freiburg
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

package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.relational.octagon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import de.uni_freiburg.informatik.ultimate.boogie.ast.CallStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Declaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Expression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IdentifierExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Procedure;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Statement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VarList;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableLHS;
import de.uni_freiburg.informatik.ultimate.boogie.symboltable.BoogieSymbolTable;
import de.uni_freiburg.informatik.ultimate.core.model.models.IBoogieType;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Boogie2SmtSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.BoogieVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.IBoogieVar;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.model.IAbstractPostOperator;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.util.BoogieUtil;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.util.CollectionUtil;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.util.TypeUtil;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Call;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Return;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Summary;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

public class OctPostOperator implements IAbstractPostOperator<OctDomainState, CodeBlock, IBoogieVar> {

	private final ILogger mLogger;
	private final BoogieSymbolTable mSymbolTable;
	private final int mMaxParallelStates;
	private final boolean mFallbackAssignIntervalProjection;

	private final HavocBundler mHavocBundler;
	private final ExpressionTransformer mExprTransformer;
	private final OctStatementProcessor mStatementProcessor;
	private final OctAssumeProcessor mAssumeProcessor;
	private final Boogie2SmtSymbolTable mBpl2SmtTable;

	public OctPostOperator(final ILogger logger, final BoogieSymbolTable symbolTable, final int maxParallelStates,
			final boolean fallbackAssignIntervalProjection, final Boogie2SmtSymbolTable bpl2smtTable) {

		if (maxParallelStates < 1) {
			throw new IllegalArgumentException("MaxParallelStates needs to be > 0, was " + maxParallelStates);
		}

		mLogger = logger;
		mSymbolTable = symbolTable;
		mBpl2SmtTable = bpl2smtTable;
		mMaxParallelStates = maxParallelStates;
		mFallbackAssignIntervalProjection = fallbackAssignIntervalProjection;

		mHavocBundler = new HavocBundler();
		mExprTransformer = new ExpressionTransformer(bpl2smtTable);
		mStatementProcessor = new OctStatementProcessor(this);
		mAssumeProcessor = new OctAssumeProcessor(this);
	}

	public static OctDomainState join(final List<OctDomainState> states) {
		OctDomainState joinedState = null;
		for (final OctDomainState result : states) {
			if (joinedState == null) {
				joinedState = result;
			} else {
				joinedState = joinedState.join(result);
			}
		}
		return joinedState;
	}

	public static List<OctDomainState> joinToSingleton(final List<OctDomainState> states) {
		return CollectionUtil.singeltonArrayList(join(states));
	}

	public static List<OctDomainState> deepCopy(final List<OctDomainState> states) {
		final List<OctDomainState> copy = new ArrayList<>(states.size());
		states.forEach(state -> copy.add(state.deepCopy()));
		return copy;
	}

	public List<OctDomainState> splitF(final List<OctDomainState> oldStates,
			final Function<List<OctDomainState>, List<OctDomainState>> op1,
			final Function<List<OctDomainState>, List<OctDomainState>> op2) {

		final List<OctDomainState> newStates = op1.apply(deepCopy(oldStates));
		newStates.addAll(op2.apply(oldStates));
		return joinDownToMax(newStates);
	}

	public List<OctDomainState> splitC(final List<OctDomainState> oldStates, final Consumer<OctDomainState> op1,
			final Consumer<OctDomainState> op2) {

		final List<OctDomainState> copiedOldStates = deepCopy(oldStates);
		oldStates.forEach(op1);
		copiedOldStates.forEach(op2);
		oldStates.addAll(copiedOldStates);
		return joinDownToMax(oldStates);
	}

	public static List<OctDomainState> removeBottomStates(final List<OctDomainState> states) {
		final List<OctDomainState> nonBottomStates = new ArrayList<>(states.size());
		for (final OctDomainState state : states) {
			if (!state.isBottom()) {
				nonBottomStates.add(state);
			}
		}
		return nonBottomStates;
	}

	public List<OctDomainState> joinDownToMax(List<OctDomainState> states) {
		if (states.size() <= mMaxParallelStates) {
			return states;
		}
		states = removeBottomStates(states);
		if (states.size() <= mMaxParallelStates) {
			return states;
		}
		return joinToSingleton(states);
	}

	public ILogger getLogger() {
		return mLogger;
	}

	public ExpressionTransformer getExprTransformer() {
		return mExprTransformer;
	}

	public OctAssumeProcessor getAssumeProcessor() {
		return mAssumeProcessor;
	}

	public Boogie2SmtSymbolTable getBoogie2SmtSymbolTable() {
		return mBpl2SmtTable;
	}

	public int getMaxParallelStates() {
		return mMaxParallelStates;
	}

	public boolean isFallbackAssignIntervalProjectionEnabled() {
		return mFallbackAssignIntervalProjection;
	}

	@Override
	public List<OctDomainState> apply(final OctDomainState oldState, final CodeBlock codeBlock) {
		List<OctDomainState> currentState = deepCopy(Collections.singletonList(oldState));
		final List<Statement> statements = mHavocBundler.bundleHavocsCached(codeBlock);
		for (final Statement statement : statements) {
			currentState = mStatementProcessor.processStatement(statement, currentState);
		}
		return currentState;
	}

	@Override
	public List<OctDomainState> apply(final OctDomainState stateBeforeTransition,
			final OctDomainState stateAfterTransition, final CodeBlock transition) {

		List<OctDomainState> result;
		if (transition instanceof Call) {
			result = applyCall(stateBeforeTransition, stateAfterTransition, (Call) transition);
		} else if (transition instanceof Return) {
			result = applyReturn(stateBeforeTransition, stateAfterTransition, ((Return) transition).getCallStatement());
		} else if (transition instanceof Summary) {
			result = applyReturn(stateBeforeTransition, stateAfterTransition,
					((Summary) transition).getCallStatement());
		} else {
			throw new UnsupportedOperationException("Unsupported transition: " + transition);
		}
		return result;
	}

	private List<OctDomainState> applyCall(final OctDomainState stateBeforeCall, final OctDomainState stateAfterCall,
			final Call callTransition) {

		if (stateAfterCall.isBottom()) {
			return new ArrayList<>();
		}

		final CallStatement call = callTransition.getCallStatement();
		final Procedure procedure = calledProcedure(call);

		final Map<String, IBoogieVar> tmpVars = new HashMap<>();
		final List<Pair<IBoogieVar, IBoogieVar>> mapInParamToTmpVar = new ArrayList<>();
		final List<Pair<IBoogieVar, Expression>> mapTmpVarToArg = new ArrayList<>();
		int paramNumber = 0;
		for (final VarList inParamList : procedure.getInParams()) {
			final IBoogieType type = inParamList.getType().getBoogieType();
			if (!TypeUtil.isBoolean(type) && !TypeUtil.isNumeric(type)) {
				paramNumber += inParamList.getIdentifiers().length;
				continue;
				// results in "var := \top" for these variables, which is always assumed for unsupported types
			}
			for (final String inParam : inParamList.getIdentifiers()) {
				// unique (inParams are all unique + brackets are forbidden)
				final String tmpVarName = "octTmp(" + inParam + ")";
				final BoogieVar realBoogieVar = mBpl2SmtTable.getBoogieVar(inParam, call.getMethodName(), true);
				final IBoogieVar tmpBoogieVar = BoogieUtil.createTemporaryIBoogieVar(tmpVarName, type);
				final Expression arg = call.getArguments()[paramNumber];
				++paramNumber;

				tmpVars.put(tmpVarName, tmpBoogieVar);
				mapInParamToTmpVar.add(new Pair<>(realBoogieVar, tmpBoogieVar));
				mapTmpVarToArg.add(new Pair<>(tmpBoogieVar, arg));
			}
		}
		// add temporary variables
		List<OctDomainState> tmpStates = new ArrayList<>();
		tmpStates.add(stateBeforeCall.addVariables(tmpVars.values()));

		// assign tmp := args
		tmpStates = deepCopy(tmpStates);
		for (final Pair<IBoogieVar, Expression> assign : mapTmpVarToArg) {
			tmpStates = mStatementProcessor.processSingleAssignment(assign.getFirst(), assign.getSecond(), tmpStates);
		}

		// inParam := tmp (copy to scope opened by call)
		// note: bottom-states are not overwritten (see top of this method)
		final List<OctDomainState> result = new ArrayList<>();
		tmpStates.forEach(s -> result.add(stateAfterCall.copyValuesOnScopeChange(s, mapInParamToTmpVar)));
		return result;
		// No need to remove the temporary variables.
		// The states with temporary variables are only local variables of this method.
	}

	private List<OctDomainState> applyReturn(final OctDomainState stateBeforeReturn, OctDomainState stateAfterReturn,
			final CallStatement correspondingCall) {

		final ArrayList<OctDomainState> result = new ArrayList<>();
		if (!stateAfterReturn.isBottom()) {
			final Procedure procedure = calledProcedure(correspondingCall);
			final List<Pair<IBoogieVar, IBoogieVar>> mapLhsToOut =
					generateMapCallLhsToOutParams(correspondingCall.getLhs(), procedure);
			stateAfterReturn = stateAfterReturn.copyValuesOnScopeChange(stateBeforeReturn, mapLhsToOut);
			result.add(stateAfterReturn);
		}
		return result;
	}

	private Procedure calledProcedure(final CallStatement call) {
		final List<Declaration> procedureDeclarations =
				mSymbolTable.getFunctionOrProcedureDeclaration(call.getMethodName());
		Procedure implementation = null;
		for (final Declaration d : procedureDeclarations) {
			assert d instanceof Procedure : "call/return of non-procedure " + call.getMethodName() + ": " + d;
			final Procedure p = (Procedure) d;
			if (p.getBody() != null) {
				if (implementation != null) {
					throw new UnsupportedOperationException("Multiple implementations of " + call.getMethodName());
				}
				implementation = p;
			}
		}
		if (implementation == null) {
			throw new UnsupportedOperationException("Missing implementation of " + call.getMethodName());
		}
		return implementation;
	}

	private List<Pair<IBoogieVar, IBoogieVar>> generateMapCallLhsToOutParams(final VariableLHS[] callLhs,
			final Procedure calledProcedure) {
		final List<Pair<IBoogieVar, IBoogieVar>> mapLhsToOut = new ArrayList<>(callLhs.length);
		int i = 0;
		for (final VarList outParamList : calledProcedure.getOutParams()) {
			for (final String outParam : outParamList.getIdentifiers()) {
				assert i < callLhs.length : "missing left hand side for out-parameter";
				final VariableLHS currentLhs = callLhs[i];
				final BoogieVar lhsBoogieVar = mBpl2SmtTable.getBoogieVar(currentLhs.getIdentifier(),
						currentLhs.getDeclarationInformation(), false);
				final BoogieVar outParamBoogieVar =
						mBpl2SmtTable.getBoogieVar(outParam, calledProcedure.getIdentifier(), false);
				mapLhsToOut.add(new Pair<>(lhsBoogieVar, outParamBoogieVar));
				++i;
			}
		}
		assert i == callLhs.length : "missing out-parameter for left hand side";
		return mapLhsToOut;
	}

	IBoogieVar getBoogieVar(final VariableLHS vLhs) {
		return getBoogie2SmtSymbolTable().getBoogieVar(vLhs.getIdentifier(), vLhs.getDeclarationInformation(), false);
	}

	IBoogieVar getBoogieVar(final IdentifierExpression ie) {
		return getBoogie2SmtSymbolTable().getBoogieVar(ie.getIdentifier(), ie.getDeclarationInformation(), false);
	}

}
