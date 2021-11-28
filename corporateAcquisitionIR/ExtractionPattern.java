package corporateAcquisitionIR;

import java.io.Serializable;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.TypedDependency;

class ExtractionPattern implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6737189865080826455L;
	GrammaticalRelation edge;
	IndexedWord word;
	//String root;
	Entity root;
	boolean rootParent;
	
	public double correctCount;
	public double totalCount;
	
	public ExtractionPattern(Entity r, GrammaticalRelation e, IndexedWord w, boolean rp) {
		edge = e;
		word = w;
		root = r;
		rootParent = rp;
		correctCount = totalCount = 0.0;
	}
	
	public String toString() {
		if (rootParent) return root+" --["+edge.toString()+"]-> "+word.toString();
		else return root+" <-["+edge.toString()+"]-- "+word.toString();
	}
	
	//returns null if doesn't apply
	//returns other IndexedWord if does
	public IndexedWord checkApplies(TypedDependency r) {
		if (!edge.getShortName().equalsIgnoreCase(r.reln().getShortName())) return null;
		
		if (word.value().equalsIgnoreCase(r.gov().value())) {
			return r.dep();
		}
		if (word.value().equalsIgnoreCase(r.dep().value())) {
			return r.gov();
		}
		return null;
	}
	
	public String getIdentifier() {
		return edge.getShortName() + " "+word.value();
	}
}