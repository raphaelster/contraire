package corporateAcquisitionIR;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class ExtractedResult {
	//public String text, acquiredBusiness, acquiredLocation, dollarAmount, status;
	//public List<String> acquired, purchasers, sellers;
	public String text;
	public List<String> acquired, purchasers, sellers, acquiredBusiness, acquiredLocation, dollarAmount, status;
	
	public ExtractedResult( String _text, List<String> _acquired, List<String> _acqBus, List<String> _acqLoc, List<String> _dlrAmt,
							List<String> _purchaser, List<String> _seller, List<String> _status) {
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
		
		if (list.size() == 0) out += prefix + ": ---\n";
		else for (String s : list) out += makePrefixedLine(prefix, s);
		 
		return out;
	}
	
	private static String makePrefixedLine(String prefix, String word) {
		return prefix + ": \"" + emptyToDashes(word) + "\"\n";
	}
	
	public ExtractedResult(String txt) {
		text = txt;
		acquiredBusiness = acquiredLocation = dollarAmount = status = acquired
				         = purchasers = sellers = status = null;
	}
	
	private void revalidate() {
		List<List<String>> parameters = new ArrayList<List<String>>();

		parameters.add(acquired);
		parameters.add(purchasers);
		parameters.add(sellers);
		parameters.add(acquiredBusiness);
		parameters.add(acquiredLocation);
		parameters.add(dollarAmount);
		parameters.add(status);
		
		
		for (List<String> s : parameters) {
			s.remove("");
		}
	}
	
	public String toString() {
		String out = "";
		out += "TEXT: "+text+"\n";
		//out += makePrefixedLine(	"TEXT", 		text);
		out += listToPrefixedLines(	"ACQUIRED", 	acquired);
		out += listToPrefixedLines(	"ACQBUS", 		acquiredBusiness);
		out += listToPrefixedLines(	"ACQLOC", 		acquiredLocation);
		out += listToPrefixedLines(	"DLRAMT", 		dollarAmount);
		out += listToPrefixedLines(	"PURCHASER", 	purchasers);
		out += listToPrefixedLines(	"SELLER", 		sellers);
		out += listToPrefixedLines(	"STATUS", 		status);
		out += "\n";
		
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
		
		Pattern minuses = Pattern.compile("[-\\s]+");
		
		for (List<String> line : keyFile) {
			int idx = strToPropertyIdx.get(line.get(0));
			String rhs = "";
			for (int i=1; i<line.size(); i++) {
				if (line.get(i).equals("/")) break;
				rhs += line.get(i)+" ";
			}
			
			rhs = rhs.replace("\"", "");
			
			if (minuses.matcher(rhs).matches()) {
				rhs = null;
			}
			else rhs = rhs.substring(0, rhs.length()-1);
			
			keyProperties.get(idx).add(rhs);
		}

		//public ExtractedResult( String _text, List<String> _acquired, String _acqBus, String _acqLoc, String _dlrAmt,
		//						List<String> _purchaser, List<String> _seller, String _status) 
			
		
		return new ExtractedResult(	keyProperties.get(0).get(0), keyProperties.get(1), keyProperties.get(2),
									keyProperties.get(3), keyProperties.get(4), keyProperties.get(5),
									keyProperties.get(6), keyProperties.get(7));
	}
	
	private List<String> toList(String s) {
		List<String> out = new ArrayList<String>();
		
		if (s != null) out.add(s);
		
		return out;
	}
	
	public List<String> getFromField(ResultField f) {
		switch (f) {
		//case TEXT:
		//	return toList(text);
		case ACQUIRED_BUSINESS:
			return acquiredBusiness;
		case ACQUIRED_LOCATION:
			return acquiredLocation;
		case DOLLAR_AMOUNT:
			return dollarAmount;
		case STATUS:
			return status;
		case ACQUIRED:
			return acquired;
		case PURCHASERS:
			return purchasers;
		case SELLERS:
			return sellers;
		default:
			throw new IllegalArgumentException();
		}
	}
	
	public void setFromField(ResultField f, Set<String> values) {
		List<String> conversion = new ArrayList<String>();
		for (String s : values) conversion.add(s);
		switch (f) {
		case ACQUIRED_BUSINESS:
			acquiredBusiness = conversion;
			return;
		case ACQUIRED_LOCATION:
			acquiredLocation = conversion;
			return;
		case DOLLAR_AMOUNT:
			dollarAmount = conversion;
			return;
		case STATUS:
			status = conversion;
			return;
		case ACQUIRED:
			acquired = conversion;
			return;
		case PURCHASERS:
			purchasers = conversion;
			return;
		case SELLERS:
			sellers = conversion;
			return;
		}
	}
	
	public void setText(String str) { text = str; }
	
	public static boolean fieldIsSingular(ResultField f) {
		return !(f == ResultField.ACQUIRED || f == ResultField.PURCHASERS || f == ResultField.SELLERS);
	}
}
