package corporateAcquisitionIR;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class HMMTrainingDocument {
	public List<List<ConvertedWord>> text;
	public List<List<String>> tokenizedExpectedResults;
	private List<List<String>> textIgnoringOriginal;
	
	public HMMTrainingDocument(List<List<ConvertedWord>> file, List<List<String>> extractedResultList) {
		text = new ArrayList<List<ConvertedWord>>();
		for (List<ConvertedWord> sentence : file) {
			text.add(new ArrayList<ConvertedWord>());
			text.get(text.size()-1).addAll(sentence);
		}
		tokenizedExpectedResults = extractedResultList;
		
		text.get(0).add(0, new ConvertedWord("[ERROR]", HiddenMarkovModel.PHI_TOKEN));
		
		textIgnoringOriginal = new ArrayList<List<String>>();
		for (List<ConvertedWord> sentence : text) {
			textIgnoringOriginal.add(new ArrayList<String>());
			for (ConvertedWord word : sentence) {
				textIgnoringOriginal.get(textIgnoringOriginal.size()-1).add(word.get());
			}
		}
	}
	
	public List<List<String>> getTextIgnoringOriginal() {
		return textIgnoringOriginal;
	}
	
	static public List<HMMTrainingDocument> makeFromCorpusForField(List<List<List<ConvertedWord>>> corpus, List<ExtractedResult> results, 
																	  ResultField field, Function<String, List<String>> resultTokenizer) {
		List<HMMTrainingDocument> out = new ArrayList<HMMTrainingDocument>();

		for (int i=0; i<corpus.size(); i++) {
			List<List<ConvertedWord>> doc = corpus.get(i);
			ExtractedResult r = results.get(i);
			
			List<List<String>> tokenizedResults = new ArrayList<List<String>>();
			
			for (String s : r.getFromField(field)) {
				tokenizedResults.add(resultTokenizer.apply(s));
			}
			
			HMMTrainingDocument cur = new HMMTrainingDocument(doc, tokenizedResults); 
			
			out.add(cur);
		}
		
		return out;
	}
}
