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
package de.uni_freiburg.informatik.ultimate.deltadebugger.core.parser.pst.interfaces;

import org.eclipse.cdt.core.dom.ast.ASTVisitor;

public interface IPSTVisitor {
	int PROCESS_SKIP = ASTVisitor.PROCESS_SKIP;
	int PROCESS_ABORT = ASTVisitor.PROCESS_ABORT;
	int PROCESS_CONTINUE = ASTVisitor.PROCESS_CONTINUE;

	default int defaultLeave(final IPSTNode node) {
		return PROCESS_CONTINUE;
	}

	default int defaultVisit(final IPSTNode node) {
		return PROCESS_CONTINUE;
	}

	default int leave(final IPSTComment comment) {
		return defaultLeave(comment);
	}

	default int leave(final IPSTACSLComment acslComment) {
		return defaultLeave(acslComment);
	}
	
	default int leave(final IPSTACSLNode acslNode) {
		return defaultLeave(acslNode);
	}
	
	default int leave(final IPSTConditionalBlock conditionalBlock) {
		return defaultLeave(conditionalBlock);
	}

	default int leave(final IPSTDirective directive) {
		return defaultLeave(directive);
	}

	default int leave(final IPSTIncludeDirective include) {
		return defaultLeave(include);
	}

	default int leave(final IPSTLiteralRegion literalRegion) {
		return PROCESS_SKIP;
	}

	default int leave(final IPSTMacroExpansion expansion) {
		return defaultLeave(expansion);
	}

	default int leave(final IPSTRegularNode node) {
		return defaultLeave(node);
	}

	default int leave(final IPSTTranslationUnit tu) {
		return defaultLeave(tu);
	}

	default int visit(final IPSTComment comment) {
		return defaultVisit(comment);
	}

	default int visit(final IPSTACSLComment acslComment) {
		return defaultVisit(acslComment);
	}
		
	default int visit(final IPSTACSLNode acslNode) {
		return defaultVisit(acslNode);
	}
	
	default int visit(final IPSTConditionalBlock conditionalBlock) {
		return defaultVisit(conditionalBlock);
	}

	default int visit(final IPSTDirective directive) {
		return defaultVisit(directive);
	}

	default int visit(final IPSTIncludeDirective include) {
		return defaultVisit(include);
	}

	default int visit(final IPSTLiteralRegion literalRegion) {
		return PROCESS_SKIP;
	}

	default int visit(final IPSTMacroExpansion expansion) {
		return defaultVisit(expansion);
	}

	default int visit(final IPSTRegularNode node) {
		return defaultVisit(node);
	}

	default int visit(final IPSTTranslationUnit tu) {
		return defaultVisit(tu);
	}

}
