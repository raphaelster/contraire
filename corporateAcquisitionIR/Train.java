package corporateAcquisitionIR;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure;


public class Train {
	
	static class Score {
		double correct;
		double totalGuessed;
		double totalExpected;
		
		public Score(double c, double tG, double tE) {
			correct = c;
			totalGuessed = tG;
			totalExpected = tE;
			
			if (Double.isNaN(c) || Double.isNaN(tG) || Double.isNaN(tE)) {
				throw new IllegalStateException("NaN score");
			}
		}
	}
	
	public static Score score(HiddenMarkovModel model, List<ConvertedWord> tokenizedDoc, ExtractedResult expected, ResultField f, boolean verbose) {
		Set<ConvertedWord> convertedResults = model.extract(tokenizedDoc, ExtractedResult.fieldIsSingular(f), (s) -> {return ConvertedWord.concatenate(s);});
		
		Set<String> results = new HashSet<String>();
		
		for (ConvertedWord w : convertedResults) results.add(w.getOriginal());
		
		Set<String> expectedResults = new HashSet<String>();
		
		
		for (String s : expected.getFromField(f)) {
			expectedResults.add(s);
		}
		
		double totalExpected = expectedResults.size();
		for (String s : expectedResults) if (s == null) totalExpected--;
		double totalActual   = results.size();
		
		
		double totalGuesses = results.size();
		double totalCorrect = 0.0;
		for (String guess : results) if (expectedResults.contains(guess)) totalCorrect++;

		if (verbose) {
			System.out.println(" Testing model on doc:\n");
			for (ConvertedWord s : tokenizedDoc) System.out.print(s.toString()+" ");
			System.out.println();
			System.out.println(" Expected "+totalExpected+" results, got "+totalActual+" results. ");
		}
		
		return new Score(totalCorrect, totalActual, totalExpected);
		
	}
	
	public static double scoreAll(HiddenMarkovModel model, List<List<List<ConvertedWord>>> trainingCorpus, List<ExtractedResult> expectedResults, ResultField f, boolean verbose) {
		
		double totalCorrect = 0.0;
		double totalGuesses = 0.0;
		double totalExpected = 0.0;
		
		for (int i=0; i<trainingCorpus.size(); i++) {
			List<ConvertedWord> trainingInstance = Utility.flatten(trainingCorpus.get(i));
			ExtractedResult expected = expectedResults.get(i);
			
			Score score = score(model, trainingInstance, expected, f, verbose);

			totalCorrect += score.correct;
			totalExpected += score.totalExpected;
			totalGuesses += score.totalGuessed;
					
		}
		
		String gv = model.toGraphViz();
		double precision = totalCorrect / totalGuesses;
		if (Double.isNaN(precision)) precision = Math.pow(10, -50);
		double recall = totalCorrect / totalExpected;
		double fScore = 2*(precision * recall) / (precision + recall);
		if (Double.isNaN(fScore)) fScore = 0.0;
		
		if (verbose) System.out.println("Total F score: "+fScore+", precision is "+precision+" and recall is "+recall+"\n"+totalCorrect+" successful extractions");
		return fScore;
	}
	
	public static void main(String[] args) {

		
		FileConverter converter = new FileConverter(null);
		

		List<List<List<String>>> allTrainingFiles = converter.readAllInputFiles(args[0]);
		List<ExtractedResult> allTrainingKeyFiles = converter.readAllKeyFiles(args[1]);
		List<List<List<String>>> allTestFiles = converter.readAllInputFiles(args[2]);
		List<ExtractedResult> allTestKeyFiles = converter.readAllKeyFiles(args[3]);
		
		
		System.out.println("All training files loaded");
		List<List<List<ConvertedWord>>> convertedTrainCorpus = new ArrayList<List<List<ConvertedWord>>>();
		List<List<List<ConvertedWord>>> convertedTestCorpus = new ArrayList<List<List<ConvertedWord>>>();
		
		Timer parseTimer = new Timer();
		parseTimer.start();
		for (List<List<String>> doc : allTrainingFiles) {
			convertedTrainCorpus.add(converter.tokenizeAugment(doc));
		}
		for (List<List<String>> doc : allTestFiles) {
			convertedTestCorpus.add(converter.tokenizeAugment(doc));
		}
		parseTimer.stopAndPrintFuncTiming("parsing training data");
		
		boolean tried = false;
		Map<ResultField, Double> prevScores = new HashMap<ResultField, Double>();
		Map<ResultField, Double> postScores = new HashMap<ResultField, Double>();
		
		boolean first = true;
		
		Random pertubator = new Random();
		
		
		for (ResultField f : ResultField.values()) {
			List<HMMTrainingDocument> allHMMTrainingFiles = HMMTrainingDocument.makeFromCorpusForField(convertedTrainCorpus, allTrainingKeyFiles, f, (s) -> {return FileConverter.tokenizeSingle(s);});
			List<HMMTrainingDocument> allHMMTestFiles = HMMTrainingDocument.makeFromCorpusForField(convertedTestCorpus, allTestKeyFiles, f, (s) -> {return FileConverter.tokenizeSingle(s);});
			
			int size = allHMMTrainingFiles.size();
			int partSize = 3*size/4;

			HiddenMarkovModel basic = HiddenMarkovModel.generateBasicModel();
			List<HMMTrainingDocument> train = allHMMTrainingFiles;
			List<HMMTrainingDocument> test = allHMMTestFiles;


			Function<HiddenMarkovModel, Double> scorer = (model) -> {
				return scoreAll(model, convertedTestCorpus, allTestKeyFiles, f, false);
			};

			
			if (first) {

				HiddenMarkovModel profileHMM = HiddenMarkovModel.generateBasicModel();
				//profileHMM = HiddenMarkovModel.fromFile("./ACQBUS.ser");
				profileHMM = HiddenMarkovModel.generateBasicModel();

				profileHMM.baumWelchOptimize(20, train, test, true, true, scorer, pertubator);
				String gv = profileHMM.toGraphViz();
				scoreAll(profileHMM, convertedTestCorpus, allTestKeyFiles, f, true);
				first = false;
			}
			
			boolean verbose = true;
			
			//if (!tried && f == ResultField.ACQUIRED) {
				//basic.extract(Utility.flatten(test.get(0).text), ExtractedResult.fieldIsSingular(f));
			double prev = scoreAll(basic, convertedTestCorpus, allTestKeyFiles, f, verbose);
			
			HiddenMarkovModel best = HiddenMarkovModelGenerator.evolveOptimal(train, test, scorer, f, pertubator);
			//basic.baumWelchOptimize(10, train, test, true, false);
			double post  = scoreAll(basic, convertedTestCorpus, allTestKeyFiles, f, verbose);
			
			prevScores.put(f, prev);
			postScores.put(f, post);
				//basic.extract(Utility.flatten(test.get(0).text), ExtractedResult.fieldIsSingular(f));
			//}
			
		}
		
		System.out.println("Final scores:\n");
		for (ResultField f : ResultField.values()) {
			System.out.println(f.toString()+"\t"+prevScores.get(f) + "\t-> "+postScores.get(f));
		}
	}
}
