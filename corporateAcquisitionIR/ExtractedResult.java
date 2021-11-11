package corporateAcquisitionIR;

import java.util.ArrayList;
import java.util.HashMap;
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
		if (str == null) return "---";
		return str;
	}
	
	private static String listToPrefixedLines(String prefix, List<String> list) {
		String out = "";
		
		if (list.size() == 0) out += makePrefixedLine(prefix, "");
		else for (String s : list) out += makePrefixedLine(prefix, s);
		 
		return out;
	}
	
	private static String makePrefixedLine(String prefix, String word) {
		return prefix + ": \"" + emptyToDashes(word) + "\"\n";
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
	
	public static ExtractedResult fromKey(List<List<String>> keyFile) {
		List<List<String>> keyProperties = new ArrayList<List<String>>();
		
		
		String properties[] = {"TEXT", "ACQUIRED", "ACQBUS", "ACQLOC", "DLRAMT", "PURCHASER", "SELLER", "STATUS"};
		HashMap<String, Integer> strToPropertyIdx = new HashMap<String, Integer>();

		for (int i=0; i<8; i++) {
			properties[i] += ":";
			
			keyProperties.add(new ArrayList<String>());
			strToPropertyIdx.put(properties[i], i);
		}
		
		for (List<String> line : keyFile) {
			int idx = strToPropertyIdx.get(line.get(0));
			String rhs = "";
			for (int i=1; i<line.size(); i++) {
				if (line.get(i).equals("/")) break;
				rhs += line.get(i)+" ";
			}
			
			rhs = rhs.replace("\"", "");
			
			if (rhs.equals("---")) rhs = null;
			else rhs = rhs.substring(0, rhs.length()-1);
			
			keyProperties.get(idx).add(rhs);
		}

		//public ExtractedResult( String _text, List<String> _acquired, String _acqBus, String _acqLoc, String _dlrAmt,
		//						List<String> _purchaser, List<String> _seller, String _status) 
			
		
		return new ExtractedResult(	keyProperties.get(0).get(0), keyProperties.get(1), keyProperties.get(2).get(0),
									keyProperties.get(3).get(0), keyProperties.get(4).get(0), keyProperties.get(5),
									keyProperties.get(6), keyProperties.get(7).get(0));
	}
}
