package corporateAcquisitionIR;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;


public class Train {
	public static double score(HiddenMarkovModel model, List<String> tokenizedDoc, ExtractedResult expected, ResultField f, boolean verbose) {
		Set<String> results = model.extract(tokenizedDoc, ExtractedResult.fieldIsSingular(f));
		
		Set<String> expectedResults = new HashSet<String>();
		
		
		for (String s : expected.getFromField(f)) {
			expectedResults.add(s);
		}
		
		double totalExpected = expectedResults.size();
		double totalActual   = results.size();
		
		
		double totalGuesses = results.size();
		double totalCorrect = 0.0;
		for (String guess : results) if (expectedResults.contains(guess)) totalCorrect++;

		if (verbose) {
			System.out.println(" Testing model on doc:\n");
			for (String s : tokenizedDoc) System.out.print(s+" ");
			System.out.println();
			System.out.println(" Expected "+totalExpected+" results, got "+totalActual+" results. ");
		}
		
		
		if (totalExpected == 0 && totalActual == 0) {
			if (verbose) System.out.println(" F score is 1.0 (correctly didn't identify)");
			return 1.0;
		}
		if (totalActual == 0 ^ totalExpected == 0) {
			if (verbose) System.out.println(" F score is 0.0 (one set is empty, other isn't)");
			return 0.0;
		}
		
		double precision = totalCorrect / totalGuesses;
		double recall    = totalCorrect / expectedResults.size();
		
		double fScore = 2.0 * (precision * recall) / (precision + recall);
		
		if (Double.isNaN(fScore)) fScore = 0;
		
		if (verbose) System.out.println(" F score is "+fScore+", precision = "+precision+" and recall = "+recall);
		return fScore;
		
	}
	
	public static double scoreAll(HiddenMarkovModel model, List<List<List<String>>> trainingCorpus, List<ExtractedResult> expectedResults, ResultField f, boolean verbose) {
		
		double totalFScore = 0.0;
		for (int i=0; i<trainingCorpus.size(); i++) {
			List<String> trainingInstance = Utility.flatten(trainingCorpus.get(i));
			ExtractedResult expected = expectedResults.get(i);
			
			double fScore = score(model, trainingInstance, expected, f, verbose);
			
			totalFScore += fScore;
			
		}
		
		if (verbose) System.out.println("Total F score: "+totalFScore);
		return totalFScore;
	}
	
	public static void main(String[] args) {

		List<List<String>> trainFilepathList;
		MaxentTagger tagger;
		
		
		
		List<List<String>> keyFilepathList;
		
		HiddenMarkovModel basic = HiddenMarkovModel.generateBasicModel();
		//AbstractSequenceClassifier<CoreLabel> classifier;

		try {
			trainFilepathList = Utility.readFile(args[0], false);
			keyFilepathList = Utility.readFile(args[1], false);
			//classifier = CRFClassifier.getClassifier("lib/classifier/english.muc.7class.distsim.crf.ser.gz");
			//tagger = new MaxentTagger("lib/tagger/english-left3words-distsim.tagger");
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException:\n"+e.getMessage());
			return;
		}
		catch (Exception e) {
			System.out.println(e.getMessage()); return;
		}
		

		List<List<List<String>>> allTrainingFiles = new ArrayList<List<List<String>>>();
		List<ExtractedResult> allKeyFiles = new ArrayList<ExtractedResult>();
		
		Function<List<List<String>>, List<List<String>>> tokenize = (s) -> {
			List<List<HasWord>> hwList = MaxentTagger.tokenizeText(new ListListReader(s));
			
			List<List<String>> out = new ArrayList<List<String>>();
			
			for (List<HasWord> l : hwList) {
				out.add(new ArrayList<String>());
				for (HasWord h : l) {
					out.get(out.size()-1).add(h.toString());
				}
			}
			
			return out;
		};
		
		Function<String, List<String>> tokenizeSingle = (s) -> {
			List<List<String>> list = new ArrayList<List<String>>();
			list.add(new ArrayList<String>());
			list.get(0).add(s);
			
			List<List<String>> full = tokenize.apply(list);
			
			return Utility.flatten(full);
		};
		
		try {
			for (int i=0; i<trainFilepathList.size(); i++) {
				List<String> trainList = trainFilepathList.get(i);
				List<String> keyList = keyFilepathList.get(i);
				
				for (int j=0; j<trainList.size(); j++) {
					String trainPath = trainList.get(j);
					String keyPath = keyList.get(j);
					
					List<List<String>> trainFile = Utility.readFile(trainPath, false);
					allTrainingFiles.add(trainFile);
					List<List<String>> keyFile = Utility.readFile(keyPath, false);
					allKeyFiles.add(ExtractedResult.fromKey(keyFile));
				}
			}
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException while loading file in doclist:\n"+e.getMessage());
			return;
		}
		
		System.out.println("All training files loaded");

		
		boolean tried = false;
		for (ResultField f : ResultField.values()) {
			List<HMMTrainingDocument> allHMMTrainingFiles = HMMTrainingDocument.makeFromCorpusForField(allTrainingFiles, allKeyFiles, f, tokenizeSingle, tokenize);
			
			int size = allHMMTrainingFiles.size();
			int halfSize = size/2;
			
			List<HMMTrainingDocument> train = allHMMTrainingFiles.subList(0, halfSize);
			List<HMMTrainingDocument> test = allHMMTrainingFiles.subList(halfSize, size);
			if (!tried && f == ResultField.ACQUIRED) {
				//basic.extract(Utility.flatten(test.get(0).text), ExtractedResult.fieldIsSingular(f));
				scoreAll(basic, allTrainingFiles.subList(halfSize, size), allKeyFiles.subList(halfSize, size), f, true);
				basic.baumWelchOptimize(10, train, test, true, true);
				scoreAll(basic, allTrainingFiles.subList(halfSize, size), allKeyFiles.subList(halfSize, size), f, true);
				//basic.extract(Utility.flatten(test.get(0).text), ExtractedResult.fieldIsSingular(f));
			}
		}
		
		
		/*List<List<String>> shortTrainFilepath = new ArrayList<List<String>>();
		List<List<String>> shortKeyFilepath = new ArrayList<List<String>>();
		
		for (int i=0; i<35; i++) {
			shortTrainFilepath.add(trainFilepathList.get(i));
			shortKeyFilepath.add(keyFilepathList.get(i));
		}
		
		trainFilepathList = shortTrainFilepath;
		keyFilepathList = shortKeyFilepath;*/
		
		
		/*
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
		*/
	}
}
