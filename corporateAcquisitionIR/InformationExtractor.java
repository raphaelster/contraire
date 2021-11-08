package corporateAcquisitionIR;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

public class InformationExtractor {
	MaxentTagger tagger;
	
	public InformationExtractor() {
		tagger = new MaxentTagger("lib/tagger/english-left3words-distsim.tagger");
	}
	
	
	private static List<String> strToList(String s) {
		List<String> out = new ArrayList<String>();
		out.add(s);
		
		return out;
	}
	
	private List<List<TaggedWord>> posTag(List<List<String>> file) {
		ListListReader reader = new ListListReader(file);
		
		List<List<HasWord>> tokenizedFile =  MaxentTagger.tokenizeText(reader);
		List<List<TaggedWord>> out = new ArrayList<List<TaggedWord>>();
		
		for (List<HasWord> line : tokenizedFile) {
			out.add(tagger.tagSentence(line));
			//String tagged = tagger.tagString(token);
			//System.out.print(tagged+" ");
		}
		//System.out.println();
		
		return out;
	}
	
	public ExtractedResult extract(List<List<String>> file, String name) {
		//do_stuff();
		for (List<String> line : file) {
			for (String token : line) {
				System.out.print(token+" ");
			}
			System.out.println();
		}
		
		List<List<TaggedWord>> taggedFile = posTag(file);
		
		for (List<TaggedWord> sentence : taggedFile) {
			for (TaggedWord word : sentence) {
				System.out.print(word.toString("/")+" ");
			}
			System.out.println();
		}
		
		return new ExtractedResult(	name, strToList("acquired"), "acqbus", "acqloc", 
									"dlrAmt", strToList("purchasers"), strToList("sellers"), "status");
	}
}
