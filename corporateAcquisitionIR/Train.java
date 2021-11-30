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


public class Train {
	public static List<List<CoreLabel>> nerAugment(List<List<TaggedWord>> posFile, AbstractSequenceClassifier<CoreLabel> classifier) {
		List<List<CoreLabel>> out = new ArrayList<List<CoreLabel>>();

		for (List<TaggedWord> sentence : posFile) {
			out.add(classifier.classifySentence(sentence));
		}
		
		return out;
	}
	public static List<List<TaggedWord>> tagAugment(List<List<HasWord>> tokenizedFile, MaxentTagger tagger) {
		List<List<TaggedWord>> out = new ArrayList<List<TaggedWord>>();

		for (List<HasWord> sentence : tokenizedFile) {
			out.add(tagger.tagSentence(sentence));
		}
		
		return out;
	}
	
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

		List<List<String>> trainFilepathList;
		MaxentTagger tagger = new MaxentTagger("lib/tagger/english-left3words-distsim.tagger");
		AbstractSequenceClassifier<CoreLabel> classifier;
		
		try {
			classifier = CRFClassifier.getClassifier("lib/classifier/english.muc.7class.distsim.crf.ser.gz");
			
		} catch (Exception e) {
			System.out.println("Failed to load NER classifier:\n"+e.getMessage());
			return;
		}
		
		
		
		List<List<String>> keyFilepathList;
		
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
		
		
		
		Function<List<List<String>>, List<List<ConvertedWord>>> tokenizeAugment = (s) -> {
			List<List<HasWord>> hwList = MaxentTagger.tokenizeText(new ListListReader(s));
			
			List<List<TaggedWord>> tagged = tagAugment(hwList, tagger);
			List<List<CoreLabel>> ner = nerAugment(tagged, classifier);
			
			return ConvertedWord.convertParsedFile(ner);
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
		List<List<List<ConvertedWord>>> convertedCorpus = new ArrayList<List<List<ConvertedWord>>>();

		Timer parseTimer = new Timer();
		parseTimer.start();
		for (List<List<String>> doc : allTrainingFiles) {
			convertedCorpus.add(tokenizeAugment.apply(doc));
		}
		parseTimer.stopAndPrintFuncTiming("parsing training data");
		
		boolean tried = false;
		Map<ResultField, Double> prevScores = new HashMap<ResultField, Double>();
		Map<ResultField, Double> postScores = new HashMap<ResultField, Double>();
		
		HiddenMarkovModel profileHMM = HiddenMarkovModel.generateBasicModel();

		{
			List<HMMTrainingDocument> profileTraining = HMMTrainingDocument.makeFromCorpusForField(convertedCorpus, allKeyFiles, ResultField.ACQUIRED, tokenizeSingle);

			profileHMM.baumWelchOptimize(100, profileTraining, profileTraining, true, true);
		}
		
		for (ResultField f : ResultField.values()) {
			List<HMMTrainingDocument> allHMMTrainingFiles = HMMTrainingDocument.makeFromCorpusForField(convertedCorpus, allKeyFiles, f, tokenizeSingle);

			
			int size = allHMMTrainingFiles.size();
			int halfSize = size/2;

			HiddenMarkovModel basic = HiddenMarkovModel.generateBasicModel();
			List<HMMTrainingDocument> train = allHMMTrainingFiles.subList(0, halfSize);
			List<HMMTrainingDocument> test = allHMMTrainingFiles.subList(halfSize, size);
			
			List<List<List<ConvertedWord>>> test2 = new ArrayList<List<List<ConvertedWord>>>();
			for (HMMTrainingDocument d : test) {
				test2.add(d.text);
			}

			Function<HiddenMarkovModel, Double> scorer = (model) -> {
				return scoreAll(model, test2, allKeyFiles.subList(halfSize, size), f, false);
			};
			
			boolean verbose = true;
			
			//if (!tried && f == ResultField.ACQUIRED) {
				//basic.extract(Utility.flatten(test.get(0).text), ExtractedResult.fieldIsSingular(f));
			double prev = scoreAll(basic, test2, allKeyFiles.subList(halfSize, size), f, verbose);
			
			HiddenMarkovModel best = HiddenMarkovModelGenerator.evolveOptimal(train, test, scorer);
			//basic.baumWelchOptimize(10, train, test, true, false);
			double post  = scoreAll(basic, test2, allKeyFiles.subList(halfSize, size), f, verbose);
			
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
