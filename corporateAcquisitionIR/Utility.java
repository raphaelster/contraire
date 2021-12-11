package corporateAcquisitionIR;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Utility {
	static public List<List<String>> readFile(String filepath, boolean allowEmptyLines) throws FileNotFoundException {
		return readFile(filepath, "\\s+", allowEmptyLines);
	}
	
	static public List<List<String>> readFile(String filepath, String tokenRegexDelim, boolean allowEmptyLines) throws FileNotFoundException {
		Scanner s = new Scanner(new File(filepath));
		
		List<List<String>> out = new ArrayList<List<String>>();
		
		while (s.hasNextLine()) {
			String line = s.nextLine();
			
			List<String> splitLine = new ArrayList<String>();
			
			for (String token : line.split(tokenRegexDelim)) {
				if (token.length() == 0) continue;
				splitLine.add(token);
			}
			
			if (splitLine.size() == 0 && !allowEmptyLines) continue;
			out.add(splitLine);
		}
		
		return out;
	}
	
	static public Set<String> getTokensFromFile(String filepath, String regexDelim) throws FileNotFoundException {
		List<List<String>> file = readFile(filepath, regexDelim, false);
		
		HashSet<String> tokens = new HashSet<String>();
		
		for (List<String> line : file) for (String str : line) {
			tokens.add(str);
		}
		
		return tokens;
	}
	
	static public List<String> splitOnSpaces(String str) {
		List<String> out = new ArrayList<String>();
		String split[] = str.split("\\s+");
		
		for (String token : split) {
			if (token.length() > 0) out.add(token);
		}
		
		
		return out;
	}
	
	static public <T> List<T> flatten(List<List<T>> list) {
		List<T> out = new ArrayList<T>();
		
		for (List<T> line : list) out.addAll(line);
		
		return out;
	}
	

	static public double lerp(double t, double a, double b) {
		return (b-a)*t + a;
	}
	
	static public double clamp(double t, double a, double b) {
		if (b < a) {
			double tmp = a;
			a = b;
			b = tmp;
		}
		return Math.max(Math.min(t, b), a);
	}
}
