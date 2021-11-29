package corporateAcquisitionIR;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class HMMTrainingDocument {
	public List<List<String>> text;
	public List<List<String>> tokenizedExpectedResults;
	
	public HMMTrainingDocument(List<List<String>> file, List<List<String>> extractedResultList,  Function<List<List<String>>, List<List<String>>> tokenizer) {
		text = tokenizer.apply(file);
		
		
		tokenizedExpectedResults = extractedResultList;
		
		text.get(0).add(0, HiddenMarkovModel.PHI_TOKEN);
		
	}
	
	static public List<HMMTrainingDocument> makeFromCorpusForField(List<List<List<String>>> corpus, List<ExtractedResult> results, 
																	  ResultField field, Function<String, List<String>> resultTokenizer,
																	  Function<List<List<String>>, List<List<String>>> docTokenizer) {
		List<HMMTrainingDocument> out = new ArrayList<HMMTrainingDocument>();

		for (int i=0; i<corpus.size(); i++) {
			List<List<String>> doc = corpus.get(i);
			ExtractedResult r = results.get(i);
			
			List<List<String>> tokenizedResults = new ArrayList<List<String>>();
			
			for (String s : r.getFromField(field)) {
				tokenizedResults.add(resultTokenizer.apply(s));
			}
			
			HMMTrainingDocument cur = new HMMTrainingDocument(doc, tokenizedResults, docTokenizer); 
			
			out.add(cur);
		}
		
		return out;
	}
}
