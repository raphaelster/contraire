package corporateAcquisitionIR;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
	static Pattern filenamePattern = Pattern.compile("(?:.*\\/)?([^.\\n]+)(?:\\..*)?");
	
	private static String getFilename(String path) {
		Matcher m = filenamePattern.matcher(path);
		m.find();
		
		return m.group(1);
	}
	
	public static void main(String[] args) {
		Map<ResultField, HiddenMarkovModel> extractors = new HashMap<ResultField, HiddenMarkovModel>();
		
		for (ResultField f : ResultField.values()) extractors.put(f,  HiddenMarkovModel.fromFile(args[1] + "/testModels/" + f.toString() + ".ser"));
		List<List<String>> documentFilepathList;

		for (ResultField f : ResultField.values()) {
			System.out.println(f);
			System.out.println(extractors.get(f).toGraphViz());
		}
		
		try {
			documentFilepathList = Utility.readFile(args[0], false);
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException:\n"+e.getMessage());
			return;
		}
		catch (Exception e) {
			System.out.println("Exception:\n"+e.getMessage());
			return;
		}

		try {
			FileConverter converter = new FileConverter(args[1]);
			
			for (List<String> list : documentFilepathList) {
				for (String path : list) {
					ExtractedResult out = new ExtractedResult(getFilename(path));;
					
					for (ResultField f : ResultField.values()) {
						List<List<String>> file = Utility.readFile(path, false);
					
						List<ConvertedWord> convFile = Utility.flatten(converter.processFile(file));
						
						Set<ConvertedWord> result = extractors.get(f).extract(convFile, ExtractedResult.fieldIsSingular(f),  (s) -> {return ConvertedWord.concatenate(s);});
						
						Set<String> actual = new HashSet<String>();
						for (ConvertedWord c : result) actual.add(c.getOriginal());
						
						out.setFromField(f, actual);;
						
					}
					System.out.println(out);
				}
			}
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException while loading file in doclist:\n"+e.getMessage());
			return;
		}
	}
}
