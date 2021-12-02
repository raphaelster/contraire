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
		
		double precision = totalCorrect / totalGuesses;
		if (Double.isNaN(precision)) precision = Math.pow(10, -50);
		double recall = totalCorrect / totalExpected;
		double fScore = 2*(precision * recall) / (precision + recall);
		if (Double.isNaN(fScore)) fScore = 0.0;
		
		if (verbose) System.out.println("Total F score: "+fScore+", precision is "+precision+" and recall is "+recall+"\n"+totalCorrect+" successful extractions");
		return fScore;
	}
	
	public static void main(String[] args) {

		
		FileConverter converter = new FileConverter("./");
		

		List<List<List<String>>> allTrainingFiles = converter.readAllInputFiles(args[0]);
		List<ExtractedResult> allKeyFiles = converter.readAllKeyFiles(args[1]);
		
		
		System.out.println("All training files loaded");
		List<List<List<ConvertedWord>>> convertedCorpus = new ArrayList<List<List<ConvertedWord>>>();

		Timer parseTimer = new Timer();
		parseTimer.start();
		for (List<List<String>> doc : allTrainingFiles) {
			convertedCorpus.add(converter.tokenizeAugment(doc));
		}
		parseTimer.stopAndPrintFuncTiming("parsing training data");
		
		boolean tried = false;
		Map<ResultField, Double> prevScores = new HashMap<ResultField, Double>();
		Map<ResultField, Double> postScores = new HashMap<ResultField, Double>();
		
		boolean first = true;
		
		
		for (ResultField f : ResultField.values()) {
			List<HMMTrainingDocument> allHMMTrainingFiles = HMMTrainingDocument.makeFromCorpusForField(convertedCorpus, allKeyFiles, f, (s) -> {return FileConverter.tokenizeSingle(s);});

			
			int size = allHMMTrainingFiles.size();
			int partSize = 3*size/4;

			HiddenMarkovModel basic = HiddenMarkovModel.generateBasicModel();
			List<HMMTrainingDocument> train = allHMMTrainingFiles.subList(0, partSize);
			List<HMMTrainingDocument> test = allHMMTrainingFiles.subList(partSize, size);

			List<List<List<ConvertedWord>>> test2 = new ArrayList<List<List<ConvertedWord>>>();
			for (HMMTrainingDocument d : test) {
				test2.add(d.text);
			}

			Function<HiddenMarkovModel, Double> scorer = (model) -> {
				return scoreAll(model, test2, allKeyFiles.subList(partSize, size), f, false);
			};

			if (first) {

				HiddenMarkovModel profileHMM = HiddenMarkovModel.generateBasicModel();
				profileHMM = HiddenMarkovModel.fromFile("./ACQBUS.ser");

				profileHMM.baumWelchOptimize(10, train, test, true, true, scorer);
				
				scoreAll(profileHMM, test2, allKeyFiles.subList(partSize, size), f, true);
				first = false;
			}
			
			boolean verbose = true;
			
			//if (!tried && f == ResultField.ACQUIRED) {
				//basic.extract(Utility.flatten(test.get(0).text), ExtractedResult.fieldIsSingular(f));
			double prev = scoreAll(basic, test2, allKeyFiles.subList(partSize, size), f, verbose);
			
			HiddenMarkovModel best = HiddenMarkovModelGenerator.evolveOptimal(train, test, scorer, f);
			//basic.baumWelchOptimize(10, train, test, true, false);
			double post  = scoreAll(basic, test2, allKeyFiles.subList(partSize, size), f, verbose);
			
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
