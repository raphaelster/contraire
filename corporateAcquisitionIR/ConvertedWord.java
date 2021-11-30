package corporateAcquisitionIR;

public class ConvertedWord {
	private String rawWord;
	private String convertedWord;
	
	public ConvertedWord(String raw, String convert) {
		rawWord = raw;
		convertedWord = convert;
	}
	
	public String getRaw()  { return rawWord; }
	public String get() { return convertedWord; }
}
