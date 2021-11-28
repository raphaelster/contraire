package corporateAcquisitionIR;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

public class Train {
	public static void main(String[] args) {

		List<List<String>> trainFilepathList;
		List<List<String>> keyFilepathList;
		

		try {
			trainFilepathList = Utility.readFile(args[0], false);
			keyFilepathList = Utility.readFile(args[1], false);
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException:\n"+e.getMessage());
			return;
		}
		
		/*List<List<String>> shortTrainFilepath = new ArrayList<List<String>>();
		List<List<String>> shortKeyFilepath = new ArrayList<List<String>>();
		
		for (int i=0; i<35; i++) {
			shortTrainFilepath.add(trainFilepathList.get(i));
			shortKeyFilepath.add(keyFilepathList.get(i));
		}
		
		trainFilepathList = shortTrainFilepath;
		keyFilepathList = shortKeyFilepath;*/
		
		InformationExtractor model = new InformationExtractor();

		TrainingData accumulated = new TrainingData();
		
		try {
			for (int i=0; i<trainFilepathList.size(); i++) {
				List<String> trainList = trainFilepathList.get(i);
				List<String> keyList = keyFilepathList.get(i);
				
				for (int j=0; j<trainList.size(); j++) {
					String trainPath = trainList.get(j);
					String keyPath = keyList.get(j);
					
					List<List<String>> trainFile = Utility.readFile(trainPath, false);
					List<List<String>> keyFile = Utility.readFile(keyPath, false);
					
					TrainingData d = model.trainOn(trainFile, keyFile);
					System.out.println("done with "+trainPath);
					accumulated.add(d);
				}
			}
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException while loading file in doclist:\n"+e.getMessage());
			return;
		}
		
		System.out.println("done with first read");
		
		try {
			for (int i=0; i<trainFilepathList.size(); i++) {
				List<String> trainList = trainFilepathList.get(i);
				List<String> keyList = keyFilepathList.get(i);

				for (int j=0; j<trainList.size(); j++) {
					String trainPath = trainList.get(j);
					String keyPath = keyList.get(j);
					
					List<List<String>> trainFile = Utility.readFile(trainPath, false);
					List<List<String>> keyFile = Utility.readFile(keyPath, false);
					
					model.testPatterns(trainFile, keyFile, accumulated);
					System.out.println("done testing "+trainPath);
				}
			}
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException while loading file in doclist:\n"+e.getMessage());
			return;
		}
		
		final double MIN = 3.1;
		final double REQ_PROB = -1;
		
		System.out.println("removing bad rules; at least "+MIN+" occurences, and precision >="+REQ_PROB);
		System.out.println("Started with "+accumulated.size()+" rules");
		accumulated.eraseBadRules(REQ_PROB, MIN);
		System.out.println("Ended with "+accumulated.size()+" rules");
		
		try {
			ObjectOutputStream objOut = new ObjectOutputStream(new FileOutputStream("./rules.ser"));
			
			objOut.writeObject(accumulated);
			
			objOut.close();
		}
		catch (Exception e) {
			System.out.println("Failed to serialize test data:\n"+e.getMessage());
			return;
		}
	}
}
