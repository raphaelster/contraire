package corporateAcquisitionIR;

import java.io.FileNotFoundException;
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
		
		
		InformationExtractor model = new InformationExtractor();

		try {
			for (int i=0; i<trainFilepathList.size(); i++) {
				List<String> trainList = trainFilepathList.get(i);
				List<String> keyList = keyFilepathList.get(i);
				
				for (int j=0; j<trainList.size(); j++) {
					String trainPath = trainList.get(j);
					String keyPath = keyList.get(j);
					
					List<List<String>> trainFile = Utility.readFile(trainPath, false);
					List<List<String>> keyFile = Utility.readFile(keyPath, false);
					
					model.trainOn(trainFile, keyFile);
				}
			}
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException while loading file in doclist:\n"+e.getMessage());
			return;
		}
	}
}
