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
	
	
	private class ParseInfo {
		public List<List<String>> rawFile;
		public List<List<TaggedWord>> taggedFile;
		public List<List<CoreLabel>> nerFile;
		public List<GrammaticalStructure> parsedFile;
		
		public ParseInfo(List<List<String>> r, List<List<TaggedWord>> t, List<List<CoreLabel>> n, List<GrammaticalStructure> p) {
			rawFile = r;
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
		
		
		return new ParseInfo(file, taggedFile, nerFile, parses);
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
		return (CoreLabel) ((TreeGraphNode) leaf).label();
	}
	
	/*
	List<IntPair> findInstancesOf(List<TaggedWord> sentence, String word) {
		List<IntPair> out = new ArrayList<IntPair>();
		List<String> tokenized = Utility.splitOnSpaces(word);
		
		for (int i=0; i<sentence.size() - tokenized.size(); i++) {
			int len = 0;
			while (len < tokenized.size() && sentence.get(i+len).value().equals(tokenized.get(len)) ) len++;
			if (len == tokenized.size()) out.add(new IntPair(i, len));
		}
		
		return out;
	}*/
	
	private class OutgoingEdge {
		GrammaticalRelation edge;
		IndexedWord word;
		String root;
		boolean rootParent;
		
		public OutgoingEdge(String r, GrammaticalRelation e, IndexedWord w, boolean rp) {
			edge = e;
			word = w;
			root = r;
			rootParent = rp;
		}
		
		public String toString() {
			if (rootParent) return root+" --["+edge.toString()+"]-> "+word.toString();
			else return root+" <-["+edge.toString()+"]-- "+word.toString();
		}
	}
	
	private class NamedEntity {//assumes organization
		List<String> tokens;

		//returns len of recognition; 0 == not recognized
		int recognizeStartsAt(List<TaggedWord> sentence, int idx) {
			if (idx < 0 || idx + tokens.size() >= sentence.size()) return 0;
			
			for (int i=0; i<tokens.size(); i++) {
				if (!sentence.get(i + idx).value().equals(tokens.get(i))) return i; 
			}
			
			return tokens.size();
		}
		
		//returns len of recognition; 0 == not recognized
		int recognizeContains(List<TaggedWord> sentence, int idx) {
			for (int offset = -tokens.size() + 1; offset <= 0; offset++) {
				//if (idx + offset < 0 || idx + offset + tokens.size() > sentence.size()) continue;
				int k = recognizeStartsAt(sentence, idx + offset);
				if (k > 0) return k;
			}
			
			return 0;
		}
		
		NamedEntity(List<TaggedWord> sentence, int start, int end) {
			tokens = new ArrayList<String>();
			
			for (int k=start; k<end; k++) {
				tokens.add(sentence.get(k).value());
			}
		}
		
		public String toString() {
			String out = "";
			
			for (String s : tokens) out += s + " ";
			
			return out.substring(0, out.length()-1);
		}
	}
	
	private class NamedEntityInstance {
		public NamedEntity type;
		public IntPair span;
		public int nthSentence;
		
		public NamedEntityInstance(NamedEntity t, int start, int len, int n) {
			type = t;
			span = new IntPair(start, start + len);
			nthSentence = n;
		}
		
		public String toString() {
			return "("+type.toString()+" ("+span.toString()+") in "+nthSentence+")";
		}
	}
	
	private class NamedEntityContext {
		List<NamedEntity> organizations;
		
		NamedEntityContext(ParseInfo info) {
			organizations = new ArrayList<NamedEntity>();
			Class answerClass = CoreAnnotations.AnswerAnnotation.class;
			
			//for (List<CoreLabel> sentence : info.nerFile) {
			for (int s = 0; s < info.nerFile.size(); s++) {
				List<CoreLabel> sentence = info.nerFile.get(s);
				List<TaggedWord> originalSentence = info.taggedFile.get(s);
				
				String previousCase = "";
				//List<IndexedWord> organizations;
				
				for (int i=0; i<sentence.size(); i++) {
					CoreLabel l = sentence.get(i);
					
					String type = l.get(CoreAnnotations.AnswerAnnotation.class);
					
					if (type.equalsIgnoreCase("O")) continue;
					
					int start = i;
					for (i = start; i < sentence.size() && sentence.get(i).get(answerClass).equals(type); i++);
					
					organizations.add(new NamedEntity(originalSentence, start, i));
				}
			}
		}
		
		NamedEntity withinOrganizationEntity(List<TaggedWord> sentence, int idx) {
			for (NamedEntity ne : organizations) {
				if (ne.recognizeContains(sentence, idx) > 0) return ne;
			}
			return null;
		}
		
		List<NamedEntityInstance> getOrganizationsInSentence(ParseInfo info, int sentenceIdx, NamedEntity subject) {
			List<NamedEntityInstance> out = new ArrayList<NamedEntityInstance>();
			
			GrammaticalStructure parse = info.parsedFile.get(sentenceIdx);
			List<TaggedWord> sentence = info.taggedFile.get(sentenceIdx);
			List<TypedDependency> list = parse.typedDependenciesCCprocessed();
			
			
			for (int i=0; i<sentence.size(); i++) {
				NamedEntity cur = null;
				int bestMatchLen = 0;
				
				for (NamedEntity e : organizations) {
					int len = e.recognizeStartsAt(sentence, i);
					if (len > bestMatchLen) {
						cur = e;
						bestMatchLen = len;
						break;
					}
				}
				
				//assume that personal pronouns are about the subject
				// many sentences start with "X company said [they/it] ...
				if (cur == null && subject != null && sentence.get(i).tag().equalsIgnoreCase("prp")) {
					cur = subject;
					bestMatchLen = 1;
				}
				
				if (cur != null) out.add(new NamedEntityInstance(cur, i, bestMatchLen, sentenceIdx));
			}
			
			return out;
		}
		
		List<NamedEntityInstance> getOrganizations(ParseInfo info) {
			NamedEntity subject = null;
			NamedEntity lastSubject = null;
			
			List<NamedEntityInstance> out = new ArrayList<NamedEntityInstance>();
			
			for (int i=0; i<info.taggedFile.size(); i++) {
				GrammaticalStructure parse = info.parsedFile.get(i);
				List<TaggedWord> sentence = info.taggedFile.get(i);
				List<TypedDependency> list = parse.typedDependenciesCCprocessed(Extras.NONE);
				
				for (TypedDependency d : list) {
					if (d.reln().getShortName().equals("nsubj")) {
						int subjIdx = d.dep().index()-1;
						NamedEntity ent = withinOrganizationEntity(sentence, subjIdx);
						if (ent == null) {
							ent = lastSubject;
						}
						
						subject = ent;
						break;
					}
				}

				List<NamedEntityInstance> cur = getOrganizationsInSentence(info, i, subject);
				out.addAll(cur);
				
				lastSubject = subject;
			}
			
			
			return out;
		}
	}
	
	List<OutgoingEdge> findOutgoingEdges(ParseInfo info, String entity) {
		if (entity == null) return new ArrayList<OutgoingEdge>();
		
		List<OutgoingEdge> out = new ArrayList<OutgoingEdge>();
		
		for (int i=0; i<info.parsedFile.size(); i++) {
			GrammaticalStructure parse = info.parsedFile.get(i);
			List<TaggedWord> raw = info.taggedFile.get(i);
			
			List<TypedDependency> list = parse.typedDependenciesCCprocessed(Extras.NONE);
			
			//List<IntPair> allInstances = findInstancesOf(raw, entity);
			NamedEntityContext context = new NamedEntityContext(info);
			
			List<NamedEntityInstance> allInstances = context.getOrganizations(info);
			
			for (NamedEntityInstance instance : allInstances) {
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
					
					if (containsDep) out.add(new OutgoingEdge(entity, relation, gov, false));
					else		 	 out.add(new OutgoingEdge(entity, relation, dep, true));
				}
			}
		}
		
		return out;
	}
	
	void trainOn(List<List<String>> train, List<List<String>> key) {
		ParseInfo trainInfo = parseFile(train);
		
		ExtractedResult actual = ExtractedResult.fromKey(key);
		
		GrammaticalStructure _parse = trainInfo.parsedFile.get(0);
		TreeGraphNode parse = _parse.root();
		
		for (List<String> sentence : train) {
			for (String word : sentence) System.out.print(word + " ");
			System.out.println();
		}
		
		//Tree commonParentOf()
		//Tree getCommonParent(Tree root, List<String> sequence) {
		//	List<Tree> leaves = root.getLeaves();
		//}
		
		Queue<TreeGraphNode> path = new ArrayDeque<TreeGraphNode>();
		path.add(parse);

		List<OutgoingEdge> acquiredBusinessEdges = findOutgoingEdges(trainInfo, actual.acquiredBusiness);
		List<OutgoingEdge> acquiredLocationEdges = findOutgoingEdges(trainInfo, actual.acquiredLocation);
		List<OutgoingEdge> dollarAmountEdges     = findOutgoingEdges(trainInfo, actual.dollarAmount);
		List<OutgoingEdge> statusEdges 			 = findOutgoingEdges(trainInfo, actual.status);
		List<List<OutgoingEdge>> purchaseEdges   = new ArrayList<List<OutgoingEdge>>();
		List<List<OutgoingEdge>> acquiredEdges   = new ArrayList<List<OutgoingEdge>>();
		List<List<OutgoingEdge>> sellerEdges     = new ArrayList<List<OutgoingEdge>>();
		
		for (String str : actual.purchasers) {
			purchaseEdges.add(findOutgoingEdges(trainInfo, str));
		}
		for (String str : actual.acquired) {
			acquiredEdges.add(findOutgoingEdges(trainInfo, str));
		}
		for (String str : actual.sellers) {
			sellerEdges.add(  findOutgoingEdges(trainInfo, str));
		}
		
		// = findOutgoingEdges(trainInfo, actual.purchasers.get(0));
		
		/*
		while (path.size() > 0) {
			TreeGraphNode top = path.remove();
			
			if (!top.isLeaf()) for (TreeGraphNode t : top.children()) path.add(t);
			
			
			Set<Constituent> scet = top.constituents();
			System.out.println(top);
			
			
			System.out.println(top.getClass());
			IntPair span = top.getSpan();
			CoreLabel label = (CoreLabel) top.label();
			String val = top.value();
			boolean leaf = top.isLeaf();
			
			String word = "";
			if (leaf) word = label.get(CoreAnnotations.TextAnnotation.class);
			String pos = "?";
			if (leaf) pos = label.get(CoreAnnotations.PositionAnnotation.class);
			//int start = label.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class)-1;
			//int end = label.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)-1;
			
			System.out.println(word+" @"+pos+"");
			for (Tree l : top.getLeaves()) {
				CoreLabel lbl = getLabel(l);
				
				System.out.print(lbl.get(CoreAnnotations.TextAnnotation.class)+" ");
			}
			System.out.println();
		}*/
		
		for (List<OutgoingEdge> pe : purchaseEdges) for (OutgoingEdge e : pe) System.out.println(e);
		
		System.out.println(actual.toString());
		System.out.println();
	}
}
