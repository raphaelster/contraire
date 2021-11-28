package corporateAcquisitionIR;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.TaggedWord;

class Entity implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3553622820311311120L;
	List<String> tokens;
	EntityType type;

	//returns len of recognition; 0 == not recognized
	int recognizeStartsAt(List<TaggedWord> sentence, int idx) {
		if (idx < 0 || idx + tokens.size() >= sentence.size()) return 0;
		
		for (int i=0; i<tokens.size(); i++) {
			if (!sentence.get(i + idx).value().equals(tokens.get(i))) return i; 
		}
		
		return tokens.size();
	}
	
	//returns len of recognition; 0 == not recognized
	int recognizeContains(List<TaggedWord> sentence, int idx) {
		for (int offset = -tokens.size() + 1; offset <= 0; offset++) {
			//if (idx + offset < 0 || idx + offset + tokens.size() > sentence.size()) continue;
			int k = recognizeStartsAt(sentence, idx + offset);
			if (k > 0) return k;
		}
		
		return 0;
	}
	

	//returns len of recognition; 0 == not recognized
	int recognizeStartsAtString(List<String> sentence, int idx) {
		if (idx < 0 || idx + tokens.size() >= sentence.size()) return 0;
		
		for (int i=0; i<tokens.size(); i++) {
			if (!sentence.get(i + idx).equals(tokens.get(i))) return i; 
		}
		
		return tokens.size();
	}
	
	//returns len of recognition; 0 == not recognized
	int recognizeContainsString(List<String> sentence, int idx) {
		for (int offset = -tokens.size() + 1; offset <= 0; offset++) {
			//if (idx + offset < 0 || idx + offset + tokens.size() > sentence.size()) continue;
			int k = recognizeStartsAtString(sentence, idx + offset);
			if (k > 0) return k;
		}
		
		return 0;
	}
	
	Entity(List<TaggedWord> sentence, int start, int end, EntityType _type) {
		tokens = new ArrayList<String>();
		type = _type;
		
		for (int k=start; k<end; k++) {
			tokens.add(sentence.get(k).value());
		}
	}
	
	public String toString() {
		String out = "";
		
		for (String s : tokens) out += s + " ";
		
		return out.substring(0, out.length()-1);
	}
}