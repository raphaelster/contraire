package corporateAcquisitionIR;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TypedDependency;

public class ConvertedWord {
	private String rawWord;
	private String convertedWord;
	
	private List<ConvertedWord> concatenated;
	private Map<ConvertedWord, TreeNode> context;
	
	public ConvertedWord(String raw, String convert) {
		rawWord = raw;
		convertedWord = convert;
		context = new HashMap<ConvertedWord, TreeNode>();
		concatenated = new ArrayList<ConvertedWord>();
		concatenated.add(this);
	}
	
	
	private static Pattern numberPattern = Pattern.compile("(\\d+,)*\\d+(\\.\\d+)?");

	private static class TreeNode {
		public TreeNode parent;
		public Set<TreeNode> children;
		ConvertedWord value;
		Integer idx;

		public Set<TreeNode> getAllChildren() {
			Stack<TreeNode> traversal = new Stack<TreeNode>();
			Set<TreeNode> visited = new HashSet<TreeNode>();
			
			traversal.add(this);
			
			while (!traversal.isEmpty()) {
				TreeNode top = traversal.pop();
				visited.add(top);
				for (TreeNode next : top.children) {
					if (visited.contains(next)) continue;
					traversal.add(next);
				}
			}

			return visited;
		}
		
		public TreeNode(TreeNode p, int i) {
			parent = p;
			children = new HashSet<TreeNode>();
			value = null;
			idx = i;
		}

		public boolean hasChild(TreeNode n) {
			Stack<TreeNode> traversal = new Stack<TreeNode>();
			Set<TreeNode> visited = new HashSet<TreeNode>();
			
			traversal.add(this);
			
			while (!traversal.isEmpty()) {
				TreeNode top = traversal.pop();
				if (top == n) return true;
				
				for (TreeNode next : top.children) {
					if (visited.contains(next)) continue;
					traversal.add(next);
				}
			}
			return false;
		}
		
		public static TreeNode getSubtreeHead(List<TreeNode> nodes) {
			Set<TreeNode> lookingFor = new HashSet<TreeNode>();
			for (TreeNode n : nodes) lookingFor.add(n);

			TreeNode highestParent = nodes.get(0);
			
			while (highestParent.parent != null) highestParent = highestParent.parent;

			for (TreeNode n : nodes) if (highestParent.hasChild(n) == false) return null;
			
			TreeNode head = highestParent;
			
			boolean changed = true;
			while (changed) {
				changed = false;
				for (TreeNode n : head.children) {
					boolean valid = true;
					
					for (TreeNode looking : nodes) {
						if (!n.hasChild(looking)) {
							valid = false;
							break;								
						}
					}
					
					if (valid) {
						changed = true;
						head = n;
						continue;
					}
				}
			}
			
			return head;
		}
		
	}
	
	public static List<List<ConvertedWord>> convertParsedFile(List<List<CoreLabel>> doc, List<GrammaticalStructure> parsed) {
		
		List<List<ConvertedWord>> out = new ArrayList<List<ConvertedWord>>();
		Set<String> businessSuffixes = new HashSet<String>();
		for (String s :  new String[] {"Co", "Corp", "Inc", "Ltd"} ) {
			businessSuffixes.add(s);
			businessSuffixes.add(s.toUpperCase());
		}
		
		
		for (List<CoreLabel> sentence : doc) {
			
			out.add(new ArrayList<ConvertedWord>());
			for (CoreLabel label : sentence) {
				String rawWord = label.getString(CoreAnnotations.TextAnnotation.class);
				String convertedWord = "LBL_"+label.getString(CoreAnnotations.AnswerAnnotation.class);
				
				if (convertedWord.equals("LBL_O")) convertedWord = rawWord;
				if (numberPattern.matcher(convertedWord).find()) {
					convertedWord = "LBL_NUM";
					if (rawWord.contains(",")) convertedWord += "_COMMA";
					if (rawWord.contains(".")) convertedWord += "_DEC";
				}
				if (convertedWord.equals("LBL_ORGANIZATION") && businessSuffixes.contains(rawWord)) {
					convertedWord += "_SUF";
				}
				out.get(out.size()-1).add(new ConvertedWord(rawWord, convertedWord));
			}
		}
	

		for (int i=0; i<doc.size(); i++) {
			GrammaticalStructure cur = parsed.get(i);
			List<ConvertedWord> line = out.get(i);
			
			Map<ConvertedWord, TreeNode> treeStructure = new HashMap<ConvertedWord, TreeNode>();
		
			for (TypedDependency d : cur.typedDependencies()) {
				if (d.gov().index() == 0) continue;
				
				ConvertedWord gov = line.get(d.gov().index()-1);
				ConvertedWord dep = line.get(d.dep().index()-1);
				
				treeStructure.putIfAbsent(gov, new TreeNode(null, d.gov().index()-1));
				treeStructure.putIfAbsent(dep, new TreeNode(null, d.dep().index()-1));
				
				treeStructure.get(gov).value = gov;
				treeStructure.get(dep).value = dep;
				
				treeStructure.get(gov).children.add(treeStructure.get(dep));
				treeStructure.get(dep).parent = treeStructure.get(gov);
			}

			for (ConvertedWord w : line) w.context = treeStructure;
		}
		return out;
	}
	
	public String getOriginal()  { return rawWord; }
	public String get() { return convertedWord; }

	
	public ConvertedWord tryPhraseComplete() {
		List<TreeNode> concat = new ArrayList<TreeNode>();
		for (ConvertedWord w : concatenated) concat.add(context.get(w));
		TreeNode head = TreeNode.getSubtreeHead(concat);
		
		if (head == null) return this;
		
		Map<Integer, ConvertedWord> last = new HashMap<Integer, ConvertedWord>();
		for (TreeNode leaf : head.getAllChildren()) {
			last.put(leaf.idx, leaf.value);
		}


		List<Integer> finalList = new ArrayList<Integer>();
		
		for (Integer i : last.keySet()) finalList.add(i);
		Collections.sort(finalList);
		
		List<ConvertedWord> out = new ArrayList<ConvertedWord>();
		for (Integer i : finalList) {
			out.add(last.get(i));
		}
		
		return concatenate(out);
		
	}
	
	//characters to sanitize whitespace for:
	// ' . , ( ) -
	//observations based on data:
	// always true: " - " -> "-"
	//              " , " -> ", "
	//				", \d" -> ",\d"
	//				" . " -> ". "
	// mostly true  ". [a-zA-Z]" -> ".[a-zA-Z]"
	
	public static Pattern removeAddedWhitespacePattern = Pattern.compile("\\s*([\\-'])\\s*");
	public static Pattern removeWhitespaceBeforePattern = Pattern.compile("\\s+([).,])");
	public static Pattern removeWhitespaceAfterPattern = Pattern.compile("([(])\\s+");
	public static Pattern combineDecimalPattern = Pattern.compile("(\\.)\\s+(\\d)");
	public static Pattern mergeAcronymPattern = Pattern.compile("([A-Z]\\.)\\s+([A-Z]\\.\\s)");
	
	
	public static ConvertedWord concatenate(List<ConvertedWord> list) {
		ConvertedWord out = new ConvertedWord("", "");
		
		for (ConvertedWord w : list) {
			out.rawWord += w.rawWord + " ";
			out.convertedWord += w.convertedWord + " ";
		}
		if (list.size() > 0) {
			out.rawWord = out.rawWord.substring(0, out.rawWord.length()-1);
			out.convertedWord = out.convertedWord.substring(0, out.convertedWord.length()-1);
		}
		
		out.rawWord = removeAddedWhitespacePattern.matcher(out.rawWord).replaceAll((m) -> {return m.group(1);});
		out.rawWord = removeWhitespaceBeforePattern.matcher(out.rawWord).replaceAll((m) -> {return m.group(1);});
		out.rawWord = removeWhitespaceAfterPattern.matcher(out.rawWord).replaceAll((m) -> {return m.group(1);});
		out.rawWord = combineDecimalPattern.matcher(out.rawWord).replaceAll((m) -> {return m.group(1) + m.group(2);});
		
		for (String newWord = mergeAcronymPattern.matcher(out.rawWord).replaceAll((m) -> {return m.group(1) + m.group(2);}); newWord != out.rawWord; out.rawWord = newWord);
	
		out.concatenated = list;
		out.context = list.get(0).context;
		return out;
	}
	
	public String toString() {
		if (rawWord.equals(convertedWord)) return convertedWord;
		
		return "("+convertedWord+"<-"+rawWord+")";
	}
}
