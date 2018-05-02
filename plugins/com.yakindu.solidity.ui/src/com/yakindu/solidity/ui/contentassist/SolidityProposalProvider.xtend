/**
 * Copyright (c) 2018 committers of YAKINDU and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * 	Andreas Muelder - Itemis AG - initial API and implementation
 * 	Karsten Thoms   - Itemis AG - initial API and implementation
 * 	Florian Antony  - Itemis AG - initial API and implementation
 * 	committers of YAKINDU 
 * 
 */
package com.yakindu.solidity.ui.contentassist

import com.google.inject.name.Named
import com.yakindu.solidity.SolidityRuntimeModule
import java.util.Collections
import java.util.Set
import javax.inject.Inject
import org.eclipse.emf.ecore.EObject
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.xtext.Keyword
import org.eclipse.xtext.RuleCall
import org.eclipse.xtext.XtextFactory
import org.eclipse.xtext.ui.editor.contentassist.ConfigurableCompletionProposal
import org.eclipse.xtext.ui.editor.contentassist.ContentAssistContext
import org.eclipse.xtext.ui.editor.contentassist.ICompletionProposalAcceptor
import org.eclipse.xtext.ui.editor.hover.IEObjectHover

/**
 * @author Andreas Muelder - Initial contribution and API
 * @author Karsten Thoms
 * @author Florian Antony
 */
class SolidityProposalProvider extends AbstractSolidityProposalProvider {
	private static final Set<String> IGNORED_KEYWORDS = Collections.unmodifiableSet(
		#{"+", "-", "*", "/", "%", "&", "++", "--", "(", ")", "[", "]", "{", "}", ";", ",", ".", ":", "?", "!",
			"^", "=", "==", "!=", "+=", "-=", "*=", "/=", "%=", "/=", "^=", "&&=", "||=", "&=", "|=", "|", "||", "|||",
			"or", "&", "&&", "and", "<", ">", "<=", ">=", "<<", "=>", "event"}
	);

	@Inject @Named(SolidityRuntimeModule.SOLIDITY_VERSION) String solcVersion

	override complete_VERSION(EObject model, RuleCall ruleCall, ContentAssistContext context,
		ICompletionProposalAcceptor acceptor) {
		acceptor.accept(createCompletionProposal("^"+solcVersion, solcVersion, null, context));
	}

	override completeKeyword(Keyword keyword, ContentAssistContext contentAssistContext,
		ICompletionProposalAcceptor acceptor) {
		if (IGNORED_KEYWORDS.contains(keyword.value)) {
			return
		}
		super.completeKeyword(keyword, contentAssistContext, acceptor)
	}

	static class AcceptorDelegate implements ICompletionProposalAcceptor {

		val ICompletionProposalAcceptor delegate
		val IEObjectHover hover

		new(ICompletionProposalAcceptor delegate, IEObjectHover hover) {
			this.delegate = delegate
			this.hover = hover
		}

		override accept(ICompletionProposal proposal) {
			if (proposal instanceof ConfigurableCompletionProposal) {
				var keyword = XtextFactory.eINSTANCE.createKeyword()
				keyword.value = proposal.displayString
				proposal.additionalProposalInfo = keyword
				proposal.hover = hover
			}
			delegate.accept(proposal)
		}

		override canAcceptMoreProposals() {
			delegate.canAcceptMoreProposals
		}
	}

}
