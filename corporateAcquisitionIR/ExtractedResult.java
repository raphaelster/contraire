package corporateAcquisitionIR;

import java.util.List;

public class ExtractedResult {
	private String text, acquiredBusiness, acquiredLocation, dollarAmount, status;
	private List<String> acquired, purchasers, sellers;
	
	public ExtractedResult( String _text, List<String> _acquired, String _acqBus, String _acqLoc, String _dlrAmt,
							List<String> _purchaser, List<String> _seller, String _status) {
		text = _text;
		acquiredBusiness = _acqBus;
		acquiredLocation = _acqLoc;
		dollarAmount = _dlrAmt;
		status = _status;
		acquired = _acquired;
		purchasers = _purchaser;
		sellers = _seller;
		status = _status;
	}
	
	private static String emptyToDashes(String str) {
		if (str.equals("")) return "---";
		return str;
	}
	
	private static String listToPrefixedLines(String prefix, List<String> list) {
		String out = "";
		
		if (list.size() == 0) out += makePrefixedLine(prefix, "");
		else for (String s : list) out += makePrefixedLine(prefix, s);
		 
		return out;
	}
	
	private static String makePrefixedLine(String prefix, String word) {
		return prefix + ": " + emptyToDashes(word) + "\n";
	}
	
	public String toString() {
		String out = "";
		out += makePrefixedLine(	"TEXT", 		text);
		out += listToPrefixedLines(	"ACQUIRED", 	acquired);
		out += makePrefixedLine(	"ACQBUS", 		acquiredBusiness);
		out += makePrefixedLine(	"ACQLOC", 		acquiredLocation);
		out += makePrefixedLine(	"DLRAMT", 		dollarAmount);
		out += listToPrefixedLines(	"PURCHASER", 	purchasers);
		out += listToPrefixedLines(	"SELLER", 		sellers);
		out += makePrefixedLine(	"STATUS", 		status);
		
		
		return out;
	}
}
