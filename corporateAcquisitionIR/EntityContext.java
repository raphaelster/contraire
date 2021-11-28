package corporateAcquisitionIR;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.GrammaticalStructure.Extras;

class EntityContext {
	List<Entity> organizations;
	
	EntityContext(ParseInfo info) {
		organizations = new ArrayList<Entity>();
		Class answerClass = CoreAnnotations.AnswerAnnotation.class;
		
		//for (List<CoreLabel> sentence : info.nerFile) {
		for (int s = 0; s < info.nerFile.size(); s++) {
			List<CoreLabel> sentence = info.nerFile.get(s);
			List<TaggedWord> originalSentence = info.taggedFile.get(s);
			
			String previousCase = "";
			//List<IndexedWord> organizations;
			
			for (int i=0; i<sentence.size(); i++) {
				CoreLabel l = sentence.get(i);
				
				String type = l.get(CoreAnnotations.AnswerAnnotation.class);
				
				if (type.equalsIgnoreCase("O")) continue;
				
				int start = i;
				for (i = start; i < sentence.size() && sentence.get(i).get(answerClass).equals(type); i++);
				
				organizations.add(new Entity(originalSentence, start, i, EntityType.ORGANIZATION));
			}
		}
	}
	
	public Entity withinOrganizationEntity(List<TaggedWord> sentence, int idx) {
		for (Entity ne : organizations) {
			if (ne.recognizeContains(sentence, idx) > 0) return ne;
		}
		return null;
	}
	
	List<EntityInstance> getOrganizationsInSentence(ParseInfo info, int sentenceIdx, Entity subject) {
		List<EntityInstance> out = new ArrayList<EntityInstance>();
		
		GrammaticalStructure parse = info.parsedFile.get(sentenceIdx);
		List<TaggedWord> sentence = info.taggedFile.get(sentenceIdx);
		List<TypedDependency> list = parse.typedDependenciesCCprocessed();
		
		
		for (int i=0; i<sentence.size(); i++) {
			Entity cur = null;
			int bestMatchLen = 0;
			
			for (Entity e : organizations) {
				int len = e.recognizeStartsAt(sentence, i);
				if (len > bestMatchLen) {
					cur = e;
					bestMatchLen = len;
					break;
				}
			}
			
			//assume that personal pronouns are about the subject
			// many sentences start with "X company said [they/it] ...
			if (cur == null && subject != null && sentence.get(i).tag().equalsIgnoreCase("prp")) {
				cur = subject;
				bestMatchLen = 1;
			}
			
			if (cur != null) out.add(new EntityInstance(cur, i, bestMatchLen, sentenceIdx));
		}
		
		return out;
	}
	
	List<EntityInstance> getOrganizations(ParseInfo info) {
		Entity subject = null;
		Entity lastSubject = null;
		
		List<EntityInstance> out = new ArrayList<EntityInstance>();
		
		for (int i=0; i<info.taggedFile.size(); i++) {
			GrammaticalStructure parse = info.parsedFile.get(i);
			List<TaggedWord> sentence = info.taggedFile.get(i);
			List<TypedDependency> list = parse.typedDependenciesCCprocessed(Extras.NONE);
			
			for (TypedDependency d : list) {
				if (d.reln().getShortName().equals("nsubj")) {
					int subjIdx = d.dep().index()-1;
					Entity ent = withinOrganizationEntity(sentence, subjIdx);
					if (ent == null) {
						ent = lastSubject;
					}
					
					subject = ent;
					break;
				}
			}

			List<EntityInstance> cur = getOrganizationsInSentence(info, i, subject);
			out.addAll(cur);
			
			lastSubject = subject;
		}
		
		
		return out;
	}
}