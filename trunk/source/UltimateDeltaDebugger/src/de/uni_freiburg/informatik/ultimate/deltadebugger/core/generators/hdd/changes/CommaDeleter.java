/*
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the Ultimate Delta Debugger plug-in.
 *
 * The Ultimate Delta Debugger plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Ultimate Delta Debugger plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the Ultimate Delta Debugger plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the Ultimate Delta Debugger plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the Ultimate Delta Debugger plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.deltadebugger.core.generators.hdd.changes;

import java.util.Arrays;
import java.util.List;

import de.uni_freiburg.informatik.ultimate.deltadebugger.core.exceptions.ChangeConflictException;
import de.uni_freiburg.informatik.ultimate.deltadebugger.core.parser.pst.interfaces.IPSTNode;
import de.uni_freiburg.informatik.ultimate.deltadebugger.core.parser.util.CommaSeparatedChild;
import de.uni_freiburg.informatik.ultimate.deltadebugger.core.parser.util.CommaSeparatedChildDeleter;
import de.uni_freiburg.informatik.ultimate.deltadebugger.core.text.ISourceRange;
import de.uni_freiburg.informatik.ultimate.deltadebugger.core.text.SourceRewriter;

/**
 * Change by comma deletion.
 */
final class CommaDeleter extends CommaSeparatedChildDeleter {
	private final SourceRewriter mRewriter;
	
	private CommaDeleter(final List<IPSTNode> childrenToDelete, final List<CommaSeparatedChild> allChildren,
			final SourceRewriter rewriter) {
		super(childrenToDelete, allChildren);
		mRewriter = rewriter;
	}
	
	@Override
	protected void deleteComma(final ISourceRange location) {
		if (mRewriter != null) {
			Change.replaceByWhitespace(mRewriter, location);
		}
	}
	
	@Override
	protected void deleteNode(final IPSTNode node) {
		if (mRewriter != null) {
			Change.deleteNodeText(mRewriter, node);
		}
	}
	
	static void deleteNodesWithComma(final SourceRewriter rewriter, final List<IPSTNode> nodesToDelete,
			final List<CommaSeparatedChild> commaPositions) {
		try {
			new CommaDeleter(nodesToDelete, commaPositions, rewriter).deleteChildren();
		} catch (final MissingCommaLocationException e) {
			throw new ChangeConflictException(e);
		}
	}
	
	static boolean isDeletionWithCommaPossible(final IPSTNode node, final List<CommaSeparatedChild> commaPositions) {
		try {
			new CommaDeleter(Arrays.asList(node), commaPositions, null).deleteChildren();
		} catch (final MissingCommaLocationException e) {
			return false;
		}
		return true;
	}
}
