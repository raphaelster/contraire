package corporateAcquisitionIR;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
		
		TrainingData data = null;

		try {
			documentFilepathList = Utility.readFile(args[0], false);
			ObjectInputStream objIn = new ObjectInputStream(new FileInputStream("./rules.ser"));
			
			data = (TrainingData) objIn.readObject();
			
			objIn.close();
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException:\n"+e.getMessage());
			return;
		}
		catch (Exception e) {
			System.out.println("Exception:\n"+e.getMessage());
			return;
		}
		
		data.eraseBadRules(-1, 12.0);
		
		InformationExtractor model = new InformationExtractor();

		try {
			for (List<String> list : documentFilepathList) {
				for (String path : list) {
					System.out.println(model.extract(Utility.readFile(path, false), getFilename(path), data));
				}
			}
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException while loading file in doclist:\n"+e.getMessage());
			return;
		}
	}
}
