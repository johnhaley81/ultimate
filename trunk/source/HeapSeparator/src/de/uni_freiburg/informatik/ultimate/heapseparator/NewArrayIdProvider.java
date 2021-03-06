/*
 * Copyright (C) 2016 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE HeapSeparator plug-in.
 *
 * The ULTIMATE HeapSeparator plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE HeapSeparator plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE HeapSeparator plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE HeapSeparator plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE HeapSeparator plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.heapseparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.BoogieConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.BoogieNonOldVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.BoogieOldVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.LocalBoogieVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.DefaultIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transformations.IntraproceduralReplacementVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.elements.EqNode;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.NestedMap2;

public class NewArrayIdProvider {
	
	private final Map<Set<IProgramVarOrConst>, PartitionInformation> mArrayToPartitionInformation = new HashMap<>();
	private final DefaultIcfgSymbolTable mNewSymbolTable;
	
	private final Map<IProgramVarOrConst, Set<IProgramVarOrConst>> mArrayIdToArrayGroup = new HashMap<>();

	private final ManagedScript mManagedScript;

	public NewArrayIdProvider(final CfgSmtToolkit csToolkit) {
		mManagedScript = csToolkit.getManagedScript();
		mNewSymbolTable = new DefaultIcfgSymbolTable(csToolkit.getSymbolTable(), csToolkit.getProcedures());
	}

	/**
	 * Return the partition id of the partitioned array belonging to originalArrayId at position indexVector
	 * @param originalArrayId
	 * @param indexVector
	 * @return
	 */
	public IProgramVarOrConst getNewArrayId(final IProgramVarOrConst originalArrayId, final List<EqNode> indexVector) {
		return mArrayToPartitionInformation.get(mArrayIdToArrayGroup.get(originalArrayId)).getNewArray(originalArrayId, indexVector);
	}

	public void registerEquivalenceClass(
			final Set<IProgramVarOrConst> arrayIds, 
			final Set<EqNode> ec) {
		final IndexPartition indexPartition = new IndexPartition(arrayIds, ec);

		PartitionInformation partitionInfo = mArrayToPartitionInformation.get(arrayIds);
		if (partitionInfo == null) {
			partitionInfo = new PartitionInformation(arrayIds, mManagedScript, mNewSymbolTable);
			mArrayToPartitionInformation.put(arrayIds, partitionInfo);
		}
		partitionInfo.addPartition(indexPartition);
		
		
		for (IProgramVarOrConst arrayId : arrayIds) {
			mArrayIdToArrayGroup.put(arrayId, arrayIds);
		}
	}

	public List<IProgramVarOrConst> getAllNewArrayIds(IProgramVarOrConst oldLhs) {
		return mArrayToPartitionInformation.get(mArrayIdToArrayGroup.get(oldLhs)).getNewArrayIds().get(oldLhs);
	}
	
	@Override
	public String toString() {
		return "NewArrayIdProvider: \n" + mArrayToPartitionInformation.toString();
	}
}

/*
 * Represents a set of Array Indices which, with respect to a given array, may never alias with 
 * the indices in any other partition.
 */
class IndexPartition {
	final Set<IProgramVarOrConst> arrayIds;
	final Set<EqNode> indices;

	public IndexPartition(final Set<IProgramVarOrConst> arrayIds, final Set<EqNode> indices) {
		this.arrayIds = arrayIds;
		this.indices = Collections.unmodifiableSet(indices);
	}
	
	@Override
	public String toString() {
		return indices.toString();
	}
}

class PartitionInformation {
	private final Set<IProgramVarOrConst> arrayIds;
	int versionCounter = 0;
	private final DefaultIcfgSymbolTable mNewSymbolTable;
	private final Set<IndexPartition> indexPartitions;
	
	private final Map<IProgramVarOrConst, List<IProgramVarOrConst>> mOldArrayIdToNewArrayIds = new HashMap<>();
	
//	final Map<IndexPartition, IProgramVarOrConst> indexPartitionToArrayToNewArrayId = new HashMap<>();
	final NestedMap2<IndexPartition, IProgramVarOrConst, List<IProgramVarOrConst>> indexPartitionToArrayToNewArrayId = new NestedMap2<>();
	
	private final NestedMap2<List<IndexPartition>, IProgramVarOrConst, IProgramVarOrConst> partitionVectorToOldArrayIdToNewArrayId = new NestedMap2<>();
	
	private final Map<EqNode, IndexPartition> indexToIndexPartition = new HashMap<>();
	private final ManagedScript mManagedScript;
	
	public PartitionInformation(final Set<IProgramVarOrConst> arrayIds, final ManagedScript mScript, final DefaultIcfgSymbolTable newSymbolTable) {
		this.arrayIds = arrayIds;
		indexPartitions = new HashSet<>();
		mManagedScript = mScript;
		mNewSymbolTable = newSymbolTable;
	}
	
	IProgramVarOrConst getNewArray(final IProgramVarOrConst arrayId, final List<EqNode> indexVector) {
		assert arrayIds.contains(arrayId);
		List<IndexPartition> partitionVector = new ArrayList<>();
		for (EqNode eqNode : indexVector) {
			IndexPartition ip = indexToIndexPartition.get(eqNode);
			assert ip != null;
			partitionVector.add(ip);
		}
//		final List<IndexPartition> partitionVector = 
//				indexVector.stream().map(eqNode -> indexToIndexPartition.get(eqNode)).collect(Collectors.toList());
		return getNewArrayIdsForIndexPartitionVector(arrayId, partitionVector);
	}

	private IProgramVarOrConst getNewArrayIdsForIndexPartitionVector(IProgramVarOrConst oldArrayId, final List<IndexPartition> partitionVector) {
		IProgramVarOrConst result = partitionVectorToOldArrayIdToNewArrayId.get(partitionVector, oldArrayId);
		if (result == null) {
			Map<IProgramVarOrConst, IProgramVarOrConst> freshVars = obtainFreshProgramVar(oldArrayId, partitionVector);
			for (Entry<IProgramVarOrConst, IProgramVarOrConst> en : freshVars.entrySet()) {
				partitionVectorToOldArrayIdToNewArrayId.put(partitionVector, en.getKey(), en.getValue());
			}
			result = freshVars.get(oldArrayId);
			assert result != null;
		}
		return result;
	}

	void addPartition(final IndexPartition ip) {
		indexPartitions.add(ip);
		for (EqNode index : ip.indices) {
			indexToIndexPartition.put(index, ip);
		}
	}

	private int getFreshVersionIndex() {
		//TODO: a method seems overkill right now -- remove if nothing changes..
		return versionCounter++;
	}

	private Map<IProgramVarOrConst, IProgramVarOrConst> obtainFreshProgramVar(
			IProgramVarOrConst oldArrayId, List<IndexPartition> partitionVector) {
		// TODO: would it be a good idea to introduce something like ReplacementVar for this??
		
		
		Map<IProgramVarOrConst, IProgramVarOrConst> oldArrayIdToNewlyCreatedArrayId = new HashMap<>();
		mManagedScript.lock(this);
		
		for (IProgramVarOrConst arrayId : arrayIds) {
			IProgramVarOrConst freshVar = null;
			if (arrayId instanceof LocalBoogieVar) {
				final LocalBoogieVar lbv = (LocalBoogieVar) arrayId;
				final String newId = lbv.getIdentifier() + "_part_" + getFreshVersionIndex();
				final TermVariable newTv = mManagedScript.constructFreshCopy(lbv.getTermVariable());

				String constString = newId + "_const";
				mManagedScript.getScript().declareFun(constString, new Sort[0], newTv.getSort());
				final ApplicationTerm newConst = (ApplicationTerm) mManagedScript.term(this, constString);

				String constPrimedString = newId + "_const_primed";
				mManagedScript.getScript().declareFun(constPrimedString, new Sort[0], newTv.getSort());
				final ApplicationTerm newPrimedConst = (ApplicationTerm) mManagedScript.term(this, constPrimedString);

				freshVar = new LocalBoogieVar(
						newId, 
						lbv.getProcedure(), 
						null, 
						newTv, 
						newConst, 
						newPrimedConst);
				mNewSymbolTable.add(freshVar);
			} else if (arrayId instanceof BoogieNonOldVar) {
				BoogieNonOldVar bnovOld = (BoogieNonOldVar) arrayId;

				final String newId = bnovOld.getIdentifier() + "_part_" + getFreshVersionIndex();
				final TermVariable newTv = mManagedScript.constructFreshCopy(bnovOld.getTermVariable());
				final TermVariable newTvOld = mManagedScript.constructFreshCopy(bnovOld.getOldVar().getTermVariable());

				String constString = newId + "_const";
				mManagedScript.getScript().declareFun(constString, new Sort[0], newTv.getSort());
				final ApplicationTerm newConst = (ApplicationTerm) mManagedScript.term(this, constString);
				mManagedScript.getScript().declareFun(constString + "_old", new Sort[0], newTv.getSort());
				final ApplicationTerm newConstOld = (ApplicationTerm) mManagedScript.term(this, constString + "_old");

				String constPrimedString = newId + "_const_primed";
				mManagedScript.getScript().declareFun(constPrimedString, new Sort[0], newTv.getSort());
				final ApplicationTerm newPrimedConst = (ApplicationTerm) mManagedScript.term(this, constPrimedString);
				mManagedScript.getScript().declareFun(constPrimedString + "_old", new Sort[0], newTv.getSort());
				final ApplicationTerm newPrimedConstOld = (ApplicationTerm) mManagedScript.term(this, constPrimedString + "_old");

				final BoogieOldVar bovNew = new BoogieOldVar(newId + "_old", 
						null, 
						newTvOld, 
						newConstOld, 
						newPrimedConstOld);
				
				BoogieNonOldVar bnovNew = new BoogieNonOldVar(
							newId, 
							null, 
							newTv, 
							newConst, 
							newPrimedConst,
							bovNew);
				
//				if (arrayId instanceof BoogieNonOldVar) {
				freshVar = bnovNew;
				mNewSymbolTable.add(freshVar);
				partitionVectorToOldArrayIdToNewArrayId.put(partitionVector, ((BoogieNonOldVar) arrayId).getOldVar(), bovNew);
//				} else {
//					assert arrayId instanceof BoogieOldVar;
//					freshVar = bov;
//					partitionVectorToOldArrayIdToNewArrayId.put(partitionVector, arrayId.get
//					
//				}
			} else if (arrayId instanceof BoogieOldVar) {
				freshVar = partitionVectorToOldArrayIdToNewArrayId.get(partitionVector, arrayId);
				assert freshVar != null;
			} else if (arrayId instanceof IntraproceduralReplacementVar) {
				assert false : "TODO: implement";
			} else if (arrayId instanceof BoogieConst) {
				assert false : "TODO: implement";
			} else {
				assert false : "case missing --> add it?";
			}


			List<IProgramVarOrConst> newIdList = mOldArrayIdToNewArrayIds.get(arrayId);
			if (newIdList == null) {
				newIdList = new ArrayList<>();
				mOldArrayIdToNewArrayIds.put(arrayId, newIdList);
			}
			newIdList.add(freshVar);
			
			oldArrayIdToNewlyCreatedArrayId.put(arrayId, freshVar);
		}
		
		mManagedScript.unlock(this);
		
		return oldArrayIdToNewlyCreatedArrayId;
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("PartitionInformation:\n");
		
		sb.append(" array group: " + arrayIds);
		
		sb.append(" partitions: " + indexPartitions);
		sb.append("\n");
		
		return sb.toString();
	}
	
	Map<IProgramVarOrConst, List<IProgramVarOrConst>> getNewArrayIds() {
		return mOldArrayIdToNewArrayIds;
	}
}
