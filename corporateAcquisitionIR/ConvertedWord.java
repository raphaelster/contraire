package corporateAcquisitionIR;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;

public class ConvertedWord {
	private String rawWord;
	private String convertedWord;
	
	public ConvertedWord(String raw, String convert) {
		rawWord = raw;
		convertedWord = convert;
	}
	
	
	public static List<List<ConvertedWord>> convertParsedFile(List<List<CoreLabel>> doc) {
		List<List<ConvertedWord>> out = new ArrayList<List<ConvertedWord>>();
		
		for (List<CoreLabel> sentence : doc) {
			out.add(new ArrayList<ConvertedWord>());
			for (CoreLabel label : sentence) {
				String rawWord = label.getString(CoreAnnotations.TextAnnotation.class);
				String convertedWord = label.getString(CoreAnnotations.AnswerAnnotation.class);
				
				if (convertedWord.equals("O")) convertedWord = rawWord;
				out.get(out.size()-1).add(new ConvertedWord(rawWord, convertedWord));
			}
		}
		
		return out;
		
	}
	
	public String getOriginal()  { return rawWord; }
	public String get() { return convertedWord; }
	
	public static ConvertedWord concatenate(List<ConvertedWord> list) {
		ConvertedWord out = new ConvertedWord("", "");
		
		for (ConvertedWord w : list) {
			out.rawWord += w.rawWord + " ";
			out.convertedWord += w.convertedWord + " ";
		}
		if (list.size() > 0) {
			out.rawWord = out.rawWord.substring(0, out.rawWord.length()-1);
			out.convertedWord = out.convertedWord.substring(0, out.convertedWord.length()-1);
		}
		
		return out;
	}
	
	public String toString() {
		if (rawWord.equals(convertedWord)) return convertedWord;
		
		return "("+convertedWord+"<-"+rawWord+")";
	}
}
