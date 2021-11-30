package corporateAcquisitionIR;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	
	//characters to sanitize whitespace for:
	// ' . , ( ) -
	//observations based on data:
	// always true: " - " -> "-"
	//              " , " -> ", "
	//				", \d" -> ",\d"
	//				" . " -> ". "
	// mostly true  ". [a-zA-Z]" -> ".[a-zA-Z]"
	
	public static Pattern removeAddedWhitespacePattern = Pattern.compile("\\s*([\\-'])\\s*");
	public static Pattern removeWhitespaceBeforePattern = Pattern.compile("\\s+([).,])");
	public static Pattern removeWhitespaceAfterPattern = Pattern.compile("([(])\\s+");
	public static Pattern combineDecimalPattern = Pattern.compile("(\\.)\\s+(\\d)");
	public static Pattern mergeAcronymPattern = Pattern.compile("([A-Z]\\.)\\s+([A-Z]\\.\\s)");
	
	
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
		
		if (list.size() > 1) {
			System.out.println("hmm");
		}   

		out.rawWord = removeAddedWhitespacePattern.matcher(out.rawWord).replaceAll((m) -> {return m.group(1);});
		out.rawWord = removeWhitespaceBeforePattern.matcher(out.rawWord).replaceAll((m) -> {return m.group(1);});
		out.rawWord = removeWhitespaceAfterPattern.matcher(out.rawWord).replaceAll((m) -> {return m.group(1);});
		out.rawWord = combineDecimalPattern.matcher(out.rawWord).replaceAll((m) -> {return m.group(1) + m.group(2);});
		
		for (String newWord = mergeAcronymPattern.matcher(out.rawWord).replaceAll((m) -> {return m.group(1) + m.group(2);}); newWord != out.rawWord; out.rawWord = newWord);
		
		return out;
	}
	
	public String toString() {
		if (rawWord.equals(convertedWord)) return convertedWord;
		
		return "("+convertedWord+"<-"+rawWord+")";
	}
}
