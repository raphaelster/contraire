package corporateAcquisitionIR;

import edu.stanford.nlp.util.IntPair;

class EntityInstance {
	public Entity type;
	public IntPair span;
	public int nthSentence;
	
	public EntityInstance(Entity t, int start, int len, int n) {
		type = t;
		span = new IntPair(start, start + len);
		nthSentence = n;
	}
	
	public String toString() {
		return "("+type.toString()+" ("+span.toString()+") in "+nthSentence+")";
	}
}
