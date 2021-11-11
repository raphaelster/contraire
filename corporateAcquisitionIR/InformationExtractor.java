package corporateAcquisitionIR;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.TypesafeMap.Key;

import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;

import edu.stanford.nlp.parser.nndep.DependencyParser;

//todo:
// actual IE part
// coreference resolution

public class InformationExtractor {
	MaxentTagger tagger;
	AbstractSequenceClassifier<CoreLabel> classifier;
	//LexicalizedParser _parser;
	DependencyParser parser;
	
	
	private class ParseInfo {
		public List<List<TaggedWord>> taggedFile;
		public List<List<CoreLabel>> nerFile;
		public List<GrammaticalStructure> parsedFile;
		
		public ParseInfo(List<List<TaggedWord>> t, List<List<CoreLabel>> n, List<GrammaticalStructure> p) {
			taggedFile = t;
			nerFile = n;
			parsedFile = p;
		}
	}
	
	public InformationExtractor() {
		tagger = new MaxentTagger("lib/tagger/english-left3words-distsim.tagger");
		try {
			classifier = CRFClassifier.getClassifier("lib/classifier/english.muc.7class.distsim.crf.ser.gz");
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load classifier: \n"+e.getMessage());
		}
		//parser = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
		parser = DependencyParser.loadFromModelFile("edu/stanford/nlp/models/parser/nndep/english_UD.gz"); 
	}
	
	
	private static List<String> strToList(String s) {
		List<String> out = new ArrayList<String>();
		out.add(s);
		
		return out;
	}
	
	private List<List<TaggedWord>> posTag(List<List<String>> file) {
		ListListReader reader = new ListListReader(file);
		
		List<List<HasWord>> tokenizedFile =  MaxentTagger.tokenizeText(reader);
		List<List<TaggedWord>> out = new ArrayList<List<TaggedWord>>();
		
		for (List<HasWord> line : tokenizedFile) {
			out.add(tagger.tagSentence(line));
			//String tagged = tagger.tagString(token);
			//System.out.print(tagged+" ");
		}
		//System.out.println();
		
		return out;
	}
	

	private List<List<CoreLabel>> nerTag(List<List<TaggedWord>> taggedFile) {
		List<List<CoreLabel>> out = new ArrayList<List<CoreLabel>>();
		//classifier.featureFactories
		
		for (List<TaggedWord> sentence : taggedFile) {
			out.add(classifier.classifySentence(sentence));
		}
		
		return out;
	}
	
	private List<GrammaticalStructure> parseNERFile(List<List<TaggedWord>> file) {
		List<GrammaticalStructure> out = new ArrayList<GrammaticalStructure>();
		
		for (List<TaggedWord> sentence : file) {
			//out.add(parser.apply(sentence));
			out.add(parser.predict(sentence));
		}
		
		return out;
	}
	
	ParseInfo parseFile(List<List<String>> file) {

		List<List<TaggedWord>> taggedFile = posTag(file);
		
		List<List<CoreLabel>> nerFile = nerTag(taggedFile);
		parser.predict(taggedFile.get(0));

		List<GrammaticalStructure> parses = parseNERFile(taggedFile);
		
		
		return new ParseInfo(taggedFile, nerFile, parses);
	}
	
	public ExtractedResult extract(List<List<String>> file, String name) {
		//do_stuff();
		for (List<String> line : file) {
			for (String token : line) {
				System.out.print(token+" ");
			}
			System.out.println();
		}
		
		ParseInfo info = parseFile(file);

	    //TreePrint tp = new TreePrint("penn,typedDependenciesCollapsed");
	    
	    //for (Tree t : parses) tp.printTree(t);
	    
		
		
		
		/*for (List<CoreLabel> sentence : nerFile) {
			for (CoreLabel word : sentence) {
				System.out.print(word.get(CoreAnnotations.TextAnnotation.class)+"/");
				System.out.print(word.get(CoreAnnotations.AnswerAnnotation.class)+" ");
			}
			System.out.println();
		}*/
		
		for (int i=0; i<info.nerFile.size(); i++) {
			for (int j=0; j < info.nerFile.get(i).size(); j++) {
				CoreLabel cWord = info.nerFile.get(i).get(j);
				TaggedWord tWord = info.taggedFile.get(i).get(j);
				
				System.out.print(tWord.toString("/")+"/");
				System.out.print(cWord.get(CoreAnnotations.AnswerAnnotation.class)+" ");
			}
		}
		
		return new ExtractedResult(	name, strToList("acquired"), "acqbus", "acqloc", 
									"dlrAmt", strToList("purchasers"), strToList("sellers"), "status");
	}
	
	private CoreLabel getLabel(Tree leaf) {
		return (CoreLabel) ((LabeledScoredTreeNode) leaf).label();
	}
	
	void trainOn(List<List<String>> train, List<List<String>> key) {
		ParseInfo trainInfo = parseFile(train);
		
		ExtractedResult actual = ExtractedResult.fromKey(key);
		
		GrammaticalStructure _parse = trainInfo.parsedFile.get(0);
		Tree parse = _parse.root();
		
		
		
		//Tree commonParentOf()
		//Tree getCommonParent(Tree root, List<String> sequence) {
		//	List<Tree> leaves = root.getLeaves();
		//}
		
		Queue<Tree> path = new ArrayDeque<Tree>();
		path.add(parse);
		
		while (path.size() > 0) {
			Tree top = path.remove();
			
			if (!top.isLeaf()) for (Tree t : top.children()) path.add(t);
			
			
			Set<Constituent> scet = top.constituents();
			System.out.println(top);
			
			System.out.println(top.getClass());
			/*LabeledScoredTreeNode node = (LabeledScoredTreeNode) top;
			IntPair span = node.getSpan();
			CoreLabel label = (CoreLabel) node.label();
			String val = node.value();
			boolean leaf = top.isLeaf();
			
			String word = "";
			if (leaf) word = label.get(CoreAnnotations.TextAnnotation.class);
			String pos = "?";
			if (leaf) pos = label.get(CoreAnnotations.PositionAnnotation.class);
			//int start = label.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class)-1;
			//int end = label.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)-1;
			
			System.out.println(word+" @"+pos+"");
			for (Tree l : node.getLeaves()) {
				CoreLabel lbl = getLabel(l);
				
				System.out.print(lbl.get(CoreAnnotations.TextAnnotation.class)+" ");
			}
			System.out.println();*/
		}
		
		System.out.println(actual.toString());
	}
}
