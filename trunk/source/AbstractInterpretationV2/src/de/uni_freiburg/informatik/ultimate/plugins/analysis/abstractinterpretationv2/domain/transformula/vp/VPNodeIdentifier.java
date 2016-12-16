package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.ConstantTerm;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.arrays.MultiDimensionalSelect;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;

public class VPNodeIdentifier {
	
	private final EqNode mEqNode;
	private final Term mIdentifyingTerm;
	private final boolean mIsFunction;
	private final boolean mIsLiteral;
	private final VPArrayIdentifier mFunction;

	public VPNodeIdentifier(EqNode eqNode) {
		this.mEqNode = eqNode;
		this.mIsFunction = eqNode instanceof EqFunctionNode;
		this.mIdentifyingTerm = null;
		this.mIsLiteral = eqNode.isLiteral();
		if (mIsFunction) {
			mFunction = new VPArrayIdentifier(((EqFunctionNode) eqNode).getFunction());
		} else {
			mFunction = null;
		}
	}

	public VPNodeIdentifier(Term term) {
		this.mEqNode = null;
		this.mIsFunction = term instanceof ApplicationTerm 
				&& ((ApplicationTerm) term).getFunction().getName().equals("select");
		assert !(term instanceof ApplicationTerm) || 
			!((ApplicationTerm) term).getFunction().getName().equals("store") : "right?";
		this.mIdentifyingTerm = term;
		this.mIsLiteral = term instanceof ConstantTerm;
		if (mIsFunction) {
			ApplicationTerm at = (ApplicationTerm) term;
			assert at.getFunction().getName().equals("select");
			MultiDimensionalSelect mds = new MultiDimensionalSelect(at);
			mFunction = new VPArrayIdentifier(mds.getArray());
		} else {
			mFunction = null;
		}
	}

	public EqNode getEqNode() {
		return mEqNode;
	}

	public Term getTerm(ManagedScript script) {
		if (mIdentifyingTerm != null) {
			return mIdentifyingTerm;
		}
		return mEqNode.getTerm(script);
	}

	public Term getIdTerm() {
		return mIdentifyingTerm;
	}

	public boolean isFunction() {
		return mIsFunction;
	}
	
	public VPArrayIdentifier getFunction() {
		assert mIsFunction : "check isFunction() before";
		return mFunction;
	}
	
	public boolean isLiteral() {
		return mIsLiteral;
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof VPNodeIdentifier)) {
			return false;
		}
		VPNodeIdentifier otherNodeId = (VPNodeIdentifier) other;
		return this.mEqNode == otherNodeId.mEqNode 
				&& this.mIdentifyingTerm == otherNodeId.mIdentifyingTerm;
	}
}
