/*
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2013-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE Core.
 * 
 * The ULTIMATE Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Core. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Core, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP), 
 * containing parts covered by the terms of the Eclipse Public License, the 
 * licensors of the ULTIMATE Core grant you additional permission 
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.result;

import de.uni_freiburg.informatik.ultimate.core.services.model.IBacktranslationService;
import de.uni_freiburg.informatik.ultimate.models.IElement;
import de.uni_freiburg.informatik.ultimate.result.model.IResultWithLocation;

/**
 * Report a procedure contract that holds at ELEM which is a node in an 
 * Ultimate model.
 * The contract is given as an expression of type E.
 * @author Matthias Heizmann
 */
public class ProcedureContractResult<ELEM extends IElement, E> 
		extends AbstractResultAtElement<ELEM> implements IResultWithLocation {
	
	private E m_Contract;
	private final String m_ProcedureName;
	
	/**
	 * Constructor.
	 * @param location the Location
	 */
	public ProcedureContractResult(String plugin, ELEM position, 
			IBacktranslationService translatorSequence,
			String procedureName, E contract) {
		super(position, plugin, translatorSequence);
		this.m_ProcedureName = procedureName;
		this.m_Contract = contract;
	}
	
	public E getContract() {
		return m_Contract;
	}

	@Override
	public String getShortDescription() {
		return "Procedure Contract for " + m_ProcedureName;
	}

	@SuppressWarnings("unchecked")
	@Override
	public String getLongDescription() {
		StringBuffer sb = new StringBuffer();
		sb.append("Derived contract for procedure ");
		sb.append(m_ProcedureName);
		sb.append(": ");
		sb.append(mTranslatorSequence.translateExpressionToString(m_Contract, (Class<E>)m_Contract.getClass()));
		return sb.toString();
	}
}