package corporateAcquisitionIR;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.IndexedWord;
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
import edu.stanford.nlp.trees.GrammaticalStructure.Extras;
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
		
		
		return new ParseInfo(file, taggedFile, nerFile, parses);
	}
	
	public ExtractedResult extract(List<List<String>> file, String name, TrainingData data) {
		
		ParseInfo info = parseFile(file);

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
		return (CoreLabel) ((TreeGraphNode) leaf).label();
	}
	
	
	List<ExtractionPattern> findOutgoingEdges(ParseInfo info, String entity) {
		if (entity == null) return new ArrayList<ExtractionPattern>();
		
		List<ExtractionPattern> out = new ArrayList<ExtractionPattern>();
		
		for (int i=0; i<info.parsedFile.size(); i++) {
			GrammaticalStructure parse = info.parsedFile.get(i);
			List<TaggedWord> raw = info.taggedFile.get(i);
			
			List<TypedDependency> list = parse.typedDependenciesCCprocessed(Extras.NONE);
			
			//List<IntPair> allInstances = findInstancesOf(raw, entity);
			EntityContext context = new EntityContext(info);
			
			List<EntityInstance> allInstances = context.getOrganizations(info);
			
			for (EntityInstance instance : allInstances) {
				Set<Integer> relevantIndices = new HashSet<Integer>();
				
				for (int k=0; k < instance.span.get(1); k++) {
					relevantIndices.add(instance.span.get(0) + k);
				}
				
				for (TypedDependency d : list) {
					IndexedWord dep = d.dep();
					IndexedWord gov = d.gov();
					GrammaticalRelation relation = d.reln();
					
					boolean containsDep = instance.span.get(0) <= dep.index()-1 && dep.index()-1 < instance.span.get(1);
					boolean containsGov = instance.span.get(0) <= gov.index()-1 && gov.index()-1 < instance.span.get(1);
					
					int di = dep.index();
					int gi = gov.index();
					
					if (containsDep == containsGov) continue;
					
					if (containsDep) out.add(new ExtractionPattern(instance.type, relation, gov, false));
					else		 	 out.add(new ExtractionPattern(instance.type, relation, dep, true));
				}
			}
		}
		
		return out;
	}
	
	private static List<ExtractionPattern> condense(List<List<ExtractionPattern>> in) {
		List<ExtractionPattern> out = new ArrayList<ExtractionPattern>();
		
		for (List<ExtractionPattern> line : in) out.addAll(line);
		
		return out;
	}
	
	TrainingData trainOn(List<List<String>> train, List<List<String>> key) {
		ParseInfo trainInfo = parseFile(train);
		
		ExtractedResult actual = ExtractedResult.fromKey(key);
		
		for (List<String> sentence : train) {
			//for (String word : sentence) System.out.print(word + " ");
			//System.out.println();
		}
		
		//Tree commonParentOf()
		//Tree getCommonParent(Tree root, List<String> sequence) {
		//	List<Tree> leaves = root.getLeaves();
		//}

		List<ExtractionPattern> acquiredBusinessEdges = findOutgoingEdges(trainInfo, actual.acquiredBusiness);
		List<ExtractionPattern> acquiredLocationEdges = findOutgoingEdges(trainInfo, actual.acquiredLocation);
		List<ExtractionPattern> dollarAmountEdges     = findOutgoingEdges(trainInfo, actual.dollarAmount);
		List<ExtractionPattern> statusEdges 			 = findOutgoingEdges(trainInfo, actual.status);
		List<List<ExtractionPattern>> purchaseEdges   = new ArrayList<List<ExtractionPattern>>();
		List<List<ExtractionPattern>> acquiredEdges   = new ArrayList<List<ExtractionPattern>>();
		List<List<ExtractionPattern>> sellerEdges     = new ArrayList<List<ExtractionPattern>>();
		
		for (String str : actual.purchasers) {
			purchaseEdges.add(findOutgoingEdges(trainInfo, str));
		}
		for (String str : actual.acquired) {
			acquiredEdges.add(findOutgoingEdges(trainInfo, str));
		}
		for (String str : actual.sellers) {
			sellerEdges.add(  findOutgoingEdges(trainInfo, str));
		}
		
		TrainingData out = new TrainingData(acquiredBusinessEdges, acquiredLocationEdges, dollarAmountEdges, statusEdges,
											condense(purchaseEdges), condense(acquiredEdges), condense(sellerEdges));
		
		//for (List<ExtractionPattern> pe : purchaseEdges) for (ExtractionPattern e : pe) System.out.println(e);
		
		//System.out.println(actual.toString());
		//System.out.println();
		
		return out;
	}
	
	void testPatterns(List<List<String>> train, List<List<String>> key, TrainingData data) {
		ParseInfo trainInfo = parseFile(train);
		EntityContext context = new EntityContext(trainInfo);
		
		ExtractedResult actual = ExtractedResult.fromKey(key);
		
		for (int i=0; i<trainInfo.parsedFile.size(); i++) {
			GrammaticalStructure sentence = trainInfo.parsedFile.get(i);
			List<TaggedWord> taggedWords = trainInfo.taggedFile.get(i);
			
			List<TypedDependency> list = sentence.typedDependenciesCCprocessed();
			
			for (TypedDependency d : list) {
				for (ExtractionPattern e : data.acquiredBusinessEdges.values()) {
					IndexedWord word = e.checkApplies(d);
					if (word == null) continue;
					
					e.totalCount++;
					Entity ent = context.withinOrganizationEntity(taggedWords, word.index());
					
					if (ent != null && ent.recognizeContainsString(actual.acquired, 0) > 0) e.correctCount++; 
				}
			}
		}
	}
}
