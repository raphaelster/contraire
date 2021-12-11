package corporateAcquisitionIR;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructure;

public class FileConverter {

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
	public static List<GrammaticalStructure> parseAugment(List<List<TaggedWord>> tagged, DependencyParser parser) {
		List<GrammaticalStructure> out = new ArrayList<GrammaticalStructure>();
		for (List<TaggedWord> sentence : tagged) out.add(parser.predict(sentence));
		return out;
	}
	public List<List<ConvertedWord>> tokenizeAugment(List<List<String>> s) {
		List<List<HasWord>> hwList = MaxentTagger.tokenizeText(new ListListReader(s));
		
		List<List<TaggedWord>> tagged = tagAugment(hwList, tagger);
		List<List<CoreLabel>> ner = nerAugment(tagged, classifier);
		//List<GrammaticalStructure> parsed = parseAugment(tagged, parser);
		
		return ConvertedWord.convertParsedFile(ner);
	}
	
	MaxentTagger tagger;
	AbstractSequenceClassifier<CoreLabel> classifier;
	public FileConverter(String filepath) {

		if (filepath == null) tagger = new MaxentTagger("lib/tagger/english-left3words-distsim.tagger");
		else tagger = new MaxentTagger(filepath+"/tagger/english-left3words-distsim.tagger");

		//DependencyParser parser;
		
		try {
			if (filepath == null) classifier = CRFClassifier.getClassifier("lib/classifier/english.muc.7class.distsim.crf.ser.gz");
			else classifier = CRFClassifier.getClassifier(filepath+"/classifier/english.muc.7class.distsim.crf.ser.gz");
			//parser = DependencyParser.loadFromModelFile("edu/stanford/nlp/models/parser/nndep/english_UD.gz"); 
			
		} catch (Exception e) {
			System.out.println("Failed to load NER classifier:\n"+e.getMessage());
			return;
		}
		
	}
	
	public List<List<ConvertedWord>> processFile(List<List<String>> train) {
		return tokenizeAugment(train);
	}
	
	public static List<List<String>> tokenize(List<List<String>> s) {
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
	
	public static List<String> tokenizeSingle(String s) {
		List<List<String>> list = new ArrayList<List<String>>();
		list.add(new ArrayList<String>());
		list.get(0).add(s);
		
		List<List<String>> full = tokenize(list);
		
		return Utility.flatten(full);
	};
	
	public static List<List<List<String>>> readAllInputFiles(String trainDocfilePath) {


		//keyFilepathList = Utility.readFile(keyPath, false);
		
		List<List<List<String>>> allTrainingFiles = new ArrayList<List<List<String>>>();
		
		try {
			List<List<String>> trainFilepathList = Utility.readFile(trainDocfilePath, false);
			for (int i=0; i<trainFilepathList.size(); i++) {
				List<String> trainList = trainFilepathList.get(i);
				//List<String> keyList = keyFilepathList.get(i);
				
				for (int j=0; j<trainList.size(); j++) {
					String trainPath = trainList.get(j);
					//String keyPath = keyList.get(j);
					
					List<List<String>> trainFile = Utility.readFile(trainPath, false);
					allTrainingFiles.add(trainFile);
					//List<List<String>> keyFile = Utility.readFile(keyPath, false);
					//allKeyFiles.add(ExtractedResult.fromKey(keyFile));
				}
			}
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException while loading file in doclist:\n"+e.getMessage());
			return null;
		}
		
		return allTrainingFiles;
		
	}
	

	public static List<ExtractedResult> readAllKeyFiles(String keyDocFilePath) {
		List<ExtractedResult> allKeyFiles = new ArrayList<ExtractedResult>();
		
		try {
			List<List<String>> keyFilepathList = Utility.readFile(keyDocFilePath, false);
			for (int i=0; i<keyFilepathList.size(); i++) {
				List<String> keyList = keyFilepathList.get(i);
				
				for (int j=0; j<keyList.size(); j++) {
					String keyPath = keyList.get(j);
					
					List<List<String>> keyFile = Utility.readFile(keyPath, false);
					allKeyFiles.add(ExtractedResult.fromKey(keyFile));
				}
			}
		}
		catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException while loading file in doclist:\n"+e.getMessage());
			return null;
		}
		
		return allKeyFiles;
	}
	

}
