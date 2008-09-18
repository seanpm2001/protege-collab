package edu.stanford.smi.protege.collab.annotation.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.bmir.protegex.chao.annotation.api.AgreeDisagreeVote;
import edu.stanford.bmir.protegex.chao.annotation.api.AgreeDisagreeVoteProposal;
import edu.stanford.bmir.protegex.chao.annotation.api.AnnotatableThing;
import edu.stanford.bmir.protegex.chao.annotation.api.AnnotationFactory;
import edu.stanford.bmir.protegex.chao.annotation.api.FiveStarsVoteProposal;
import edu.stanford.smi.protege.code.generator.wrapping.OntologyJavaMappingUtil;
import edu.stanford.smi.protege.model.Cls;
import edu.stanford.smi.protege.model.KnowledgeBase;

/**
 * @author Tania Tudorache <tudorache@stanford.edu>
 *
 */
public class AnnotationsComboBoxUtil {
	private KnowledgeBase changesKb;
	private static AnnotationsComboBoxUtil annotationsComboBoxUtil;

	private static ArrayList<Cls> allAnnotationTypes = new ArrayList<Cls>();
	private static Set<Cls> filteredOutAnnotationTypes = new HashSet<Cls>();


	private AnnotationsComboBoxUtil(KnowledgeBase changesKb) {
		this.changesKb = changesKb;
		initializeAllAnnoatationTypes();
	}

	private void initializeAllAnnoatationTypes() {
		AnnotationFactory factory = new AnnotationFactory(changesKb);
		Cls annotCls = factory.getAnnotationClass();

		for (Object obj : annotCls.getSubclasses()) {
			Cls annotSubCls = (Cls) obj;
			if (!annotSubCls.isAbstract()) {
				allAnnotationTypes.add(annotSubCls);
			}
		}

		filterOutUnneededAnnotationTypes();
	}

	private void filterOutUnneededAnnotationTypes() {
		AnnotationFactory factory = new AnnotationFactory(changesKb);
		filteredOutAnnotationTypes.add(factory.getSimpleProposalClass());

		allAnnotationTypes.removeAll(filteredOutAnnotationTypes);
	}

	public static AnnotationsComboBoxUtil getAnnotationsComboBoxUtil(KnowledgeBase changesKb){
		if (annotationsComboBoxUtil == null) {
			annotationsComboBoxUtil = new AnnotationsComboBoxUtil(changesKb);
		}
		return annotationsComboBoxUtil;
	}


	//TT: probably we should cache this
	public Collection<Cls> getAllowableAnnotationTypes(AnnotatableThing thing) {
		if (thing == null) {
			return allAnnotationTypes;
		}

		ArrayList<Cls> allowableAnnotations = new ArrayList<Cls>(allAnnotationTypes);
		AnnotationFactory factory = new AnnotationFactory(changesKb);


		//TODO: these rules should not be hard-coded
		if (OntologyJavaMappingUtil.canAs(thing, FiveStarsVoteProposal.class)) {
			allowableAnnotations.remove(factory.getAgreeDisagreeVoteClass());
			allowableAnnotations.remove(factory.getAgreeDisagreeVoteProposalClass());
			allowableAnnotations.remove(factory.getFiveStarsVoteProposalClass());
		} else if (OntologyJavaMappingUtil.canAs(thing, AgreeDisagreeVoteProposal.class)) {
			allowableAnnotations.remove(factory.getFiveStarsVoteClass());
			allowableAnnotations.remove(factory.getAgreeDisagreeVoteProposalClass());
			allowableAnnotations.remove(factory.getFiveStarsVoteProposalClass());
		} else if (OntologyJavaMappingUtil.canAs(thing, AgreeDisagreeVote.class) ||
				OntologyJavaMappingUtil.canAs(thing, FiveStarsVoteProposal.class)) {
			allowableAnnotations.remove(factory.getAgreeDisagreeVoteClass());
			allowableAnnotations.remove(factory.getFiveStarsVoteClass());
			allowableAnnotations.remove(factory.getAgreeDisagreeVoteProposalClass());
			allowableAnnotations.remove(factory.getFiveStarsVoteProposalClass());
		}

		return allowableAnnotations;
	}

}
