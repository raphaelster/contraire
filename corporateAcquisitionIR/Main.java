package corporateAcquisitionIR;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
		List<List<String>> documentFilepathList;
		

		try {
			documentFilepathList = Utility.readFile(args[0], false);
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException:\n"+e.getMessage());
			return;
		}
		
		
		InformationExtractor model = new InformationExtractor();

		try {
			for (List<String> list : documentFilepathList) {
				for (String path : list) {
					System.out.println(model.extract(Utility.readFile(path, false), getFilename(path)));
				}
			}
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException while loading file in doclist:\n"+e.getMessage());
			return;
		}
	}
}
