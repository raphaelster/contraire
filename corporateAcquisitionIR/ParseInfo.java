package corporateAcquisitionIR;

import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.GrammaticalStructure;

class ParseInfo {
	public List<List<String>> rawFile;
	public List<List<TaggedWord>> taggedFile;
	public List<List<CoreLabel>> nerFile;
	public List<GrammaticalStructure> parsedFile;
	
	public ParseInfo(List<List<String>> r, List<List<TaggedWord>> t, List<List<CoreLabel>> n, List<GrammaticalStructure> p) {
		rawFile = r;
		taggedFile = t;
		nerFile = n;
		parsedFile = p;
	}
}