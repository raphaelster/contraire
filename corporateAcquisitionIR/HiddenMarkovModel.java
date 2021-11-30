package corporateAcquisitionIR;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

enum StateType {
	PREFIX,
	SUFFIX,
	TARGET,
	BACKGROUND,
	PHI,
	RETARGET
}
enum TargetEvent {
	ENTER,
	EXIT,
	NO_EVENT
}

class StateHierarchy {
	public StateHierarchy parent;
	public Set<StateHierarchy> children;
	public HMMState value;
	private boolean probabilityIsUniform;
	private Map<String, List<Double>> memoizedEmissions;
	private String name;
	private double vocabularySize;
	
	List<Double> lambdas;

	public StateHierarchy(StateHierarchy _parent, String _name) {
		children = new HashSet<StateHierarchy>();
		value = null;
		lambdas = null;
		probabilityIsUniform = false;
		memoizedEmissions = new HashMap<String, List<Double>>();
		name = _name;
		
		setParent(_parent);
	}
	
	
	public StateHierarchy(StateHierarchy _parent, HMMState _value) {
		children = null;
		value = _value;
		lambdas = new ArrayList<Double>();
		probabilityIsUniform = false;
		memoizedEmissions = new HashMap<String, List<Double>>();
		
		name = "(PARENTOF "+_value.toString()+")";

		setParent(_parent);
	}
	
	
	public void setParent(StateHierarchy p) {
		parent = p;
		if (parent == null) return;
		
		
		parent.children.add(this);
		lambdas = new ArrayList<Double>();
		for (StateHierarchy top = this; top != null; top = top.parent) {
			lambdas.add(1.0);
		}
		
		normalizeLambdas();
	}
	public void normalizeLambdas() {
		double sum = 0.0;
		for (Double d : lambdas) sum += d;
		
		for (int i=0; i<lambdas.size(); i++)  lambdas.set(i, lambdas.get(i) / sum);
		
	}
	
	void optimizeLambdaStep(HMMState child, Set<String> emissionsSeenForThisState, double totalUniqueWords) {
		normalizeLambdas();
		
		List<Double> priorLambdas = new ArrayList<Double>();
		

		while (true) {
			priorLambdas.clear();
			priorLambdas.addAll(lambdas);
			
			List<Double> betas = new ArrayList<Double>();
			for (int i=0; i<lambdas.size(); i++) betas.add(0.0);
			
			for (String word : emissionsSeenForThisState) {
				List<Double> emissions = getEmissionVector(word, totalUniqueWords);
				
				double total = 0.0;
				for (int nth = 0; nth < lambdas.size(); nth++) total += lambdas.get(nth) * emissions.get(nth); 
				
				for (int nth = 0; nth < lambdas.size(); nth++) {
					double preSumValue = lambdas.get(nth) * emissions.get(nth) / total; 
					
					betas.set(nth, betas.get(nth) + preSumValue);
				}
			}
			
			for (int i=0; i<lambdas.size(); i++) lambdas.set(i, betas.get(i));
			
			normalizeLambdas();
			
			double changeInLambdas = 0.0;
			
			for (int i=0; i < lambdas.size(); i++) {
				changeInLambdas += Math.pow(lambdas.get(i) - priorLambdas.get(i), 2); 
			}
			
			final double CHANGE_THRESHOLD = Math.pow(10, -5);
			if (changeInLambdas <= CHANGE_THRESHOLD) break;
		}
	}
	
	public String toString() {
		return "SHNode_"+name;
	}
	
	StateHierarchy getRoot() {
		StateHierarchy top = this;
		
		while (top.parent != null) top = top.parent;
		
		return top;
	}
	
	Set<StateHierarchy> getTree() {
		StateHierarchy p = getRoot();
		
		Set<StateHierarchy> visited = new HashSet<StateHierarchy>();
		Stack<StateHierarchy> traversal = new Stack<StateHierarchy>();
		traversal.add(p);

		while (traversal.size() > 0) {
			StateHierarchy top = traversal.pop();
			visited.add(top);
			if (top.children != null) for (StateHierarchy h : top.children) {
				if (visited.contains(h)) throw new IllegalStateException("found cycle in assumed acyclic StateHierarchy structure");
				traversal.add(h);
			}
		}
		
		return visited;
	}
	
	public void invalidateCache() {
		for (StateHierarchy h : getTree()) h.memoizedEmissions.clear();
	}
	
	Set<StateHierarchy> getLeaves() {
		Set<StateHierarchy> visited = new HashSet<StateHierarchy>();
		Set<StateHierarchy> leaves = new HashSet<StateHierarchy>();
		
		Stack<StateHierarchy> traversal = new Stack<StateHierarchy>();
		traversal.add(this);
		
		while (traversal.size() > 0) {
			StateHierarchy top = traversal.pop();
			visited.add(top);
			if (top.children != null) for (StateHierarchy h : top.children) {
				if (visited.contains(h)) throw new IllegalStateException("found cycle in assumed acyclic StateHierarchy structure");
				traversal.add(h);
			}
			else leaves.add(top);
		}
		
		return leaves;
	}
	
	public void setUsingUniformDistribution(boolean b) {
		this.probabilityIsUniform = b;
	}
	
	private List<Double> getEmissionVector(String word, double totalUniqueWords) {
		if (memoizedEmissions.containsKey(word)) return memoizedEmissions.get(word);
		
		List<Double> out = new ArrayList<Double>();
		
		for (StateHierarchy top = this; top != null; top = top.parent) {
			if (top.probabilityIsUniform) {
				out.add(1.0 / totalUniqueWords);
				continue;
			}
			
			Set<StateHierarchy> leaves = top.getLeaves();
			
			
			double totalLeafProbability = 1.0 * leaves.size();
			double sum = 0.0;
			for (StateHierarchy leaf : leaves) {
				LogProb cur = leaf.value.getEmissionProbability(word, totalUniqueWords, true);
				sum += cur.getActualProbability();
			}
			sum /= totalLeafProbability;
			out.add(sum); 
		}
		
		memoizedEmissions.put(word, out);
		return out;
	}
	
	LogProb getProbability(String word, double totalUniqueWords) {
		if (this.probabilityIsUniform) return new LogProb(1.0);
		
		List<Double> emissionVec = getEmissionVector(word, totalUniqueWords);
		
		if (emissionVec.size() != lambdas.size()) throw new IllegalStateException("StateHierarchy: |lambdas| != |emissionVector|");
		
		double finalProb = 0.0;
		
		for (int i=0; i<emissionVec.size(); i++) {
			finalProb += emissionVec.get(i) * lambdas.get(i);
		}
		
		
		
		return new LogProb(finalProb);
	}
	
}


class HMMState {
	static final boolean IGNORE_CASE = false;
	private static int maxID = 0;
	
	private HashMap<HMMState, LogProb> transitions;
	private HashSet<HMMState> parents;
	private HashMap<String, LogProb> emissions;
	private StateType type;
	private int id;
	
	StateHierarchy hierarchyNode;
	
	LogProb defaultEmissionProbability;
	
	public HMMState(StateType t, StateHierarchy parent) {
		transitions = new HashMap<HMMState, LogProb>();
		emissions = new HashMap<String, LogProb>();
		parents = new HashSet<HMMState>();
		defaultEmissionProbability = new LogProb(1.0);
		type = t;
		hierarchyNode = new StateHierarchy(parent, this);
		id = maxID++;
	}
	
	public Set<String> getEmissionWords() {
		return emissions.keySet();
	}
	
	public String getToStringWithoutChildren() {
		String out = "State"+id+"_";
		
		switch (type) {
		case PREFIX:
			out += "PREF";
			break;			
		case SUFFIX:
			out += "SUFF";
			break;			
		case TARGET:
			out += "TARG";
			break;
		case BACKGROUND:
			out += "BACK";
			break;
		case PHI:
			out += "PHI";
			break;
		case RETARGET:
			out += "RTRG";
			break;
		}
		
		return out;
	}
	
	class Extraction {
		public List<HMMState> path;
		public LogProb chance;
		
		public Extraction() {
			path = new ArrayList<HMMState>();
			chance = new LogProb(0);
		}
		
		public Extraction(List<HMMState> p, LogProb c) {
			path = p; chance = c;
		}
	}
	
	public String toString() {
		return getToStringWithoutChildren();
		
		/*
		String out = getToStringWithoutChildren();
		out += " [";
		
		for (HMMState s : transitions.keySet()) {
			out += transitions.get(s) + " -> " + s.getToStringWithoutChildren() + ", ";
		}
		
		if (transitions.keySet().size() > 0) out = out.substring(0, out.length()-2);
		return out + "]";*/
	}
	
	//need to recreate hierarchy
	public HMMState shallowCopy() {
		HMMState out = new HMMState(type, null);
		out.emissions.putAll(emissions);
		out.transitions.putAll(transitions);
		out.parents.addAll(parents);
		
		return out;
	}
	
	public LogProb getEmissionProbability(String word, double totalUniqueWords, boolean ignoreHierarchy) {
		if (IGNORE_CASE) word = word.toLowerCase();
		

		if (!ignoreHierarchy) return hierarchyNode.getProbability(word, totalUniqueWords);
		
		if (word.equals(HiddenMarkovModel.PHI_TOKEN) && this.type != StateType.PHI) {
			return new LogProb(0);
		}
		
		LogProb baseEmissionProb = null;
		
		if (emissions.containsKey(word)) baseEmissionProb = emissions.get(word);
		else baseEmissionProb = defaultEmissionProbability;
		
		return baseEmissionProb;
	}
	
	public LogProb getEmissionProbability(String word, double totalUniqueWords) {
		return getEmissionProbability(word, totalUniqueWords, false);
	}
	
	public LogProb getProbabilityTo(HMMState next) {
		LogProb result = transitions.get(next);
		if (result == null) return new LogProb(0);
		return result;
	}
	
	public class ProbabilityTable {
		private List<Map<HMMState, LogProb>> table;
		
		public ProbabilityTable(List<Map<HMMState, LogProb>> t) {
			table = t;
		}
		
		public LogProb find(HMMState trg, int t) {
			return HMMState.findTargetProbabilityOrSumIfTargetNull(table.get(t), trg);
		}
	}
	
	void smoothTransitions() {
		double addVal = Math.pow(10, -50);
		double v = transitions.size();
		
		
		double totalProb = 0.0;
		for (LogProb p : transitions.values()) totalProb += p.getActualProbability();
			
		for (HMMState s : transitions.keySet()) {
			double prob = transitions.get(s).getActualProbability() + addVal;
			prob /= totalProb + addVal*v;
			transitions.put(s, new LogProb(prob));
		}
	}
	
	void smoothEmissions(double totalUniqueWords, double totalWords) {
		//(emission(s) + addedValue) / (totalProbability + addedValue*vocabSize)
		// addedValue*vocabSize = 1, when addedValue = 1/vocabSize
		hierarchyNode.invalidateCache();
		
		Set<String> keysToRemove = new HashSet<String>();

		for (String s : emissions.keySet()) {
			if (emissions.get(s).getValue() < -300) {
				LogProb p = emissions.get(s);
				keysToRemove.add(s);
			}
		}
		
		for (String s : keysToRemove) emissions.remove(s);
		
		double totalProbability = 0.0;
		double minProbability = 1.0;
		
		double zeroWords = totalUniqueWords - emissions.size();
		
		
		for (LogProb p : emissions.values()) {
			
			totalProbability += p.getActualProbability();
		}
		
		LogProb totalProbLog = new LogProb(totalProbability);
		for (String s : emissions.keySet()) {
			emissions.put(s, emissions.get(s).sub(totalProbLog));
			minProbability = Math.min(minProbability, emissions.get(s).getActualProbability());
		}
		totalProbability = 1.0;

		if (zeroWords < 0.001) {
			defaultEmissionProbability = new LogProb(minProbability/2.0);
		}
		
		double discount = minProbability / 4.0;
		
		for (String s : emissions.keySet()) {
			double newProb = (emissions.get(s).getActualProbability() - discount) / totalProbability;
			emissions.put(s, new LogProb(newProb));
		}
		double defaultProb = (discount * (totalUniqueWords - zeroWords)) / zeroWords;
		defaultEmissionProbability = new LogProb(defaultProb);
		
		if (!Double.isFinite(defaultEmissionProbability.getValue())) {
			System.out.println("Error; default probability = 0");
			defaultEmissionProbability = new LogProb(Math.pow(10, -200));
			//throw new IllegalArgumentException("Default probability is meant to be nonzero");
		}
		/*
		//double addedValue = totalProbability / totalWords / 10.0 / (totalUniqueWords);
		
		//LogProb den = new LogProb(totalProbability + addedValue * totalUniqueWords);

		//defaultEmissionProbability = new LogProb(addedValue).sub(den);
		
		//for (String s : emissions.keySet()) {
			LogProb num = new LogProb(emissions.get(s).getActualProbability() + addedValue);
			emissions.put(s,  num.sub(den));
		}*/
		
	}
	
	void updateLambdas(Set<String> testEmissions, double totalUniqueWords) {
		hierarchyNode.optimizeLambdaStep(this, testEmissions, totalUniqueWords);
	}
	
	public Extraction getOptimalSequence(List<String> doc, double totalUniqueWords) {
		if (doc.size() == 0) return new Extraction();
		
		class ProbBackPointer {
			public LogProb prob;
			public List<HMMState> backPointers;
			
			ProbBackPointer(LogProb p, HMMState prev) {
				prob = p;
				backPointers = new ArrayList<HMMState>();
				if (prev != null) backPointers.add(prev);
			}
			
			ProbBackPointer(LogProb p, List<HMMState> l, HMMState newer) {
				prob = p;
				backPointers = new ArrayList<HMMState>();
				backPointers.addAll(l);
				backPointers.add(newer);
				
			}
		}
		
		Map<HMMState, ProbBackPointer> prior = new HashMap<HMMState, ProbBackPointer>();
		
		prior.put(this, new ProbBackPointer(this.getEmissionProbability(doc.get(0), totalUniqueWords), this));
		
		
		for (int i=1; i<doc.size(); i++) {
			String word = doc.get(i);
			
			Map<HMMState, ProbBackPointer> next = new HashMap<HMMState, ProbBackPointer>();
			
			for (HMMState before : prior.keySet()) {
				
				for (HMMState after : before.getTransitions().keySet()) {
					next.putIfAbsent(after, new ProbBackPointer(new LogProb(0), null));
					ProbBackPointer best = next.get(after);
				
					LogProb prob = prior.get(before).prob;
					
					LogProb transProb = before.getProbabilityTo(after);
					LogProb emissionProb = after.getEmissionProbability(word, totalUniqueWords);
					prob = prob.add(transProb).add(emissionProb);
					int bestID = Integer.MAX_VALUE;	//use ID as tiebreaker, since the hash map ordering is actually random
					if (best.backPointers.size() > 0) bestID = best.backPointers.get(best.backPointers.size()-1).id;
					
					if (prob.getValue() >= best.prob.getValue() && (prob.getValue() != best.prob.getValue() || after.id < bestID)) {
						best = new ProbBackPointer(prob, prior.get(before).backPointers, after);
					}
					
					next.put(after, best);
				}
				
			}
			
			prior = next;
			next = null;
		}
		
		ProbBackPointer best = new ProbBackPointer(new LogProb(0), null);
		
		for (ProbBackPointer candidate : prior.values()) {
			if (candidate.prob.getValue() > best.prob.getValue()) {
				best = candidate;
			}
		}
		
		return new Extraction(best.backPointers, best.prob);
	}

	private static void renormalizeColumn(Map<HMMState, LogProb> col) {
		double sum = 0.0;
		for (LogProb l : col.values()) {
			sum += l.getActualProbability();
		}
		LogProb factor = new LogProb(sum);

		for (HMMState s : col.keySet()) {
			col.put(s, col.get(s).sub(factor));
		}
	}
	
	private static String dumpProbabilityTableState(List<Map<HMMState, LogProb>> sparseMap, List<List<String>> doc, List<List<TargetEvent>> events, double totalUniqueWords) {
		Set<HMMState> allSeenStates = new HashSet<HMMState>();
		
		String message = "IsNaN found after renormalization:\nsparseMap:\n";
		for (Map<HMMState, LogProb> col : sparseMap) {
			for (HMMState s : col.keySet()) {
				allSeenStates.add(s);
				LogProb result = col.get(s);
				message += s + " " + result + " ";
			}
			message += "\n";
		}
		message += "transitionProbs:\n";
		for (HMMState s : allSeenStates) {
			for (HMMState other : s.transitions.keySet()) {
				message += s + " -> " + other + " = " + s.transitions.get(other) + "\n";
			}
		}
		Set<String> allStrings = new HashSet<String>();
		
		message += "document:\n";
		for (List<String> line : doc) for (String s : line) {
			message += s + " ";
			allStrings.add(s);
		}
		message += "\ntarget events:\n";
		for (List<TargetEvent> line : events) for (TargetEvent e : line) message += e + " ";
		
		message += "\nemission probabilities:\n";
		for (HMMState s : allSeenStates) for (String word : allStrings) {
			message += s + " " + s.getEmissionProbability(word, totalUniqueWords) + "\n";
		}
			
		return message;
	}
	
	public ProbabilityTable getForwardsProbabilityTable(List<List<String>> doc, List<List<TargetEvent>> events, double totalUniqueWords) {
		List<Map<HMMState, LogProb>> sparseMap = new ArrayList<Map<HMMState, LogProb>>();
		
		if (doc.size() == 0) return new ProbabilityTable(sparseMap);
		
		int totalWordsToProcess = 0;
		
		for (List<String> s : doc) totalWordsToProcess += s.size();
		
		
		Map<HMMState, LogProb> startNode = new HashMap<HMMState, LogProb>();
		startNode.put(this, this.getEmissionProbability(doc.get(0).get(0), totalUniqueWords));
		sparseMap.add(startNode);
		
		if (HiddenMarkovModel.RENORMALIZE) renormalizeColumn(startNode);
	
		int wordsProcessed = 1;
		
		search_loop:
		for (int sIdx=0; sIdx < doc.size(); sIdx++) {
			List<String> sequence = doc.get(sIdx);
			
			int firstIdx = (sIdx == 0 ? 1 : 0);
			
			for (int i=firstIdx; i<sequence.size(); i++) {
				if (wordsProcessed == totalWordsToProcess) break search_loop;
				wordsProcessed++;
				
				String curStr = sequence.get(i);
				TargetEvent curEvent = events.get(sIdx).get(i);
				
				Map<HMMState, LogProb> top = sparseMap.get(sparseMap.size() - 1);
				
				Map<HMMState, Map<HMMState, LogProb>> transitions = new HashMap<HMMState, Map<HMMState, LogProb>>();
				
				for (HMMState s : top.keySet()) {
					transitions.put(s, new HashMap<HMMState, LogProb>());
					transitions.get(s).putAll(s.transitions);
				}
				
				Map<HMMState, LogProb> nextMap = directionalProbabilityStep(top, transitions, curStr, curEvent, false, totalUniqueWords);
				
				if (HiddenMarkovModel.RENORMALIZE) renormalizeColumn(nextMap);
				for (LogProb p  : nextMap.values()) if (Double.isNaN(p.getActualProbability())) {
					String message = dumpProbabilityTableState(sparseMap, doc, events, totalUniqueWords);
					
					throw new IllegalStateException(message+"\n(forwards prob)");
				}
					
				sparseMap.add(nextMap);
				

				
			}
		}

		return new ProbabilityTable(sparseMap);
	}
	

	public ProbabilityTable getBackwardsProbabilityTable(Set<HMMState> allStates, List<List<String>> doc, List<List<TargetEvent>> events,
														 double totalUniqueWords) {
		List<Map<HMMState, LogProb>> sparseMap = new ArrayList<Map<HMMState, LogProb>>();
		
		int stopAtIthWord = 0;
		
		if (doc.size() == 0) return new ProbabilityTable(sparseMap);
		
		
		Map<HMMState, LogProb> startNode = new HashMap<HMMState, LogProb>();
		for (HMMState s : allStates) startNode.put(s, new LogProb(1.0));
		sparseMap.add(startNode);

		if (HiddenMarkovModel.RENORMALIZE) renormalizeColumn(startNode);
	
		int totalSize = 0;
		for (List<String> sentence : doc) totalSize += sentence.size();
		
		final int LAST_WORD = totalSize - stopAtIthWord;
		
		int wordsProcessed = 0;
		
		search_loop:
		for (int sIdx=doc.size()-1; sIdx >= 0; sIdx--) {
			List<String> sequence = doc.get(sIdx);
			
			int firstIdx = sequence.size()-1;
			//if (sIdx == doc.size()-1) firstIdx--;
			for (int i=firstIdx; i>=0; i--) {
				if (wordsProcessed == LAST_WORD) break search_loop;
				wordsProcessed++;
				
				String curStr = sequence.get(i);
				
				TargetEvent curEvent = null;
				if (events.get(sIdx).size() <= i+1) {
					if (sIdx+1 >= events.size()) curEvent = TargetEvent.NO_EVENT;
					else curEvent = events.get(sIdx+1).get(0);
				}
				else curEvent = events.get(sIdx).get(i+1);
				
				Map<HMMState, LogProb> top = sparseMap.get(sparseMap.size() - 1);
				
				Map<HMMState, Map<HMMState, LogProb>> transitions = new HashMap<HMMState, Map<HMMState, LogProb>>();
				
				for (HMMState s : top.keySet()) {
					transitions.put(s, new HashMap<HMMState, LogProb>());
					for (HMMState parent : s.parents) {
						transitions.get(s).put(parent, parent.transitions.get(s));
					}
				}

				Map<HMMState, LogProb> nextMap = directionalProbabilityStep(top, transitions, curStr, curEvent, true, totalUniqueWords);

				if (HiddenMarkovModel.RENORMALIZE) renormalizeColumn(nextMap);

				for (LogProb p  : nextMap.values()) if (Double.isNaN(p.getActualProbability())) {
					String message = dumpProbabilityTableState(sparseMap, doc, events, totalUniqueWords);
					
					throw new IllegalStateException(message+"\n(backwards prob)");
				}
					
				
				sparseMap.add(nextMap);
			}
		}
		
		Collections.reverse(sparseMap);

		return new ProbabilityTable(sparseMap);
	}
	
	private static Map<HMMState, LogProb> directionalProbabilityStep(Map<HMMState, LogProb> cur, Map<HMMState, Map<HMMState, LogProb>> allTransitions, 
																	 String curString, TargetEvent curEvent, boolean flipTransitionLegality,
																	 double totalUniqueWords) {
		Map<HMMState, LogProb> outMap = new HashMap<HMMState, LogProb>();
		
		for (HMMState s : cur.keySet()) {
			Map<HMMState, LogProb> transitions = allTransitions.get(s);
			for (HMMState nextState : transitions.keySet()) {
				boolean illegal = !isLegalTransition(s, nextState, curEvent);
				if (flipTransitionLegality) illegal = !isLegalTransition(nextState, s, curEvent);
				
				if (illegal) continue;
				
				if (!outMap.containsKey(nextState)) outMap.put(nextState, new LogProb(1.0));
				
				outMap.put(nextState, nextState.getEmissionProbability(curString, totalUniqueWords).add(transitions.get(nextState)).add(cur.get(s)));
				
				LogProb a = nextState.getEmissionProbability(curString, totalUniqueWords);
				LogProb b = transitions.get(nextState);
				LogProb c = cur.get(s);
				if (Double.isNaN(outMap.get(nextState).getActualProbability())) {
					throw new IllegalArgumentException("Found NaN probability in directionalProbabilityStep\n"
														+ cur + " -> " + nextState + "\n"
														+ "emission = "+a+", P(b | a) = " + b + ", previous term = "+c);
				}
			}
		}
		
		return outMap;
	}
	
	private static LogProb findTargetProbabilityOrSumIfTargetNull(Map<HMMState, LogProb> lastMap, HMMState trg) {
		LogProb out = new LogProb(1);
		
		for (HMMState s : lastMap.keySet()) {
			LogProb prob = lastMap.get(s);
			
			if (trg == s) return prob;
			out = out.add(prob);
		}
		
		if  (trg == null) return out;
		return new LogProb(0);
	}
	
	public List<HMMState> getLineOfSameType() {
		List<HMMState> out = new ArrayList<HMMState>();
		Set<HMMState> visited = new HashSet<HMMState>();
		
		Stack<HMMState> traversal = new Stack<HMMState>();
		traversal.add(this);
		 
		while (traversal.size() > 0) {
			HMMState top = traversal.pop();
			
			if (visited.contains(top)) throw new IllegalStateException("Detected same-type cycle (that isn't a self-cycle)");
			visited.add(top);
			out.add(top);
			
			Set<HMMState> adjacent = top.transitions.keySet();
			List<HMMState> adjacentSameType = new ArrayList<HMMState>();
			
			for (HMMState s : adjacent) {
				if (s.getType() != top.getType()) continue;
				if (s == top) continue;
				adjacentSameType.add(s);
			}
			
			if (adjacentSameType.size() > 1) throw new IllegalStateException("Found branch of same type off state");
			
			if (adjacentSameType.size() > 0) traversal.add(adjacentSameType.get(0));
		}
		
		return out;
	}
	
	public void replaceInvalidReferencesFromShallowCopy(Map<HMMState, HMMState> oldToNew, StateHierarchy newParent) {
		Set<HMMState> oldTransitions = new HashSet<HMMState>();
		oldTransitions.addAll(transitions.keySet());
		hierarchyNode.setParent(newParent);
		
		for (HMMState oldState : oldTransitions) {
			HMMState newState = oldToNew.get(oldState);
			LogProb prob = transitions.get(oldState);

			transitions.remove(oldState);
			transitions.put(newState, prob);
		}
		
		@SuppressWarnings("unchecked")
		Set<HMMState> oldParents = (Set<HMMState>) parents.clone();
		for (HMMState old : oldParents) {
			parents.remove(old);
			parents.add(oldToNew.get(old));
		}
	}
	
	
	public void addChild(HMMState other, LogProb p) {
		//HMMTransition t = new HMMTransition(p, other);
		transitions.put(other, p);
		other.parents.add(this);
	}
	
	public void addEmission(String word, LogProb p) {
		if (IGNORE_CASE) word = word.toLowerCase();
		emissions.put(word, p);
	}
	
	public void stealTransitions(HMMState other) {
		transitions = other.transitions;
		other.transitions = new HashMap<HMMState, LogProb>();
		for (HMMState child : transitions.keySet()) {
			child.parents.remove(other);
			child.parents.add(this);
		}
	}
	
	
	//ignores self cycles
	HMMState getSingleOutgoingEdge() {
		if (transitions.size() > 2 || transitions.size() == 0) return null;
		if (transitions.size()  == 1) {
			HMMState actualNext = transitions.keySet().iterator().next();
			
			if (actualNext == this) return null;
			return actualNext;
		}
		
		int selfCycleCount = 0;
		int outgoingCount = 0;
		
		HMMState nonThisNext = null;
		
		for (HMMState s : transitions.keySet()) {
			if (s == this) selfCycleCount++;
			else {
				outgoingCount++;
				nonThisNext = s;
			}
		}
		
		if (selfCycleCount == 1 && outgoingCount == 1) return nonThisNext;
		return null;
	}
	
	public void updateChild(HMMState s, LogProb p) {
		if (!transitions.containsKey(s)) throw new IllegalStateException();
		
		transitions.put(s, p);
	}
	
	public void clearEmissions() {
		emissions.clear();
	}
	
	public void setEmission(String word, LogProb p) {
		if (IGNORE_CASE) word = word.toLowerCase();
		emissions.put(word, p);
	}
	
	public void clearTransitions() {
		for (HMMState s : transitions.keySet()) {
			s.parents.remove(this);
		}
		transitions.clear();
	}
	
	public void normalizeProbabilities() {
		normalizeTransitions();
		
		double sum = 0.0;
		for (LogProb p : emissions.values()) sum += p.getActualProbability();
		for (String s : emissions.keySet()) {
			double newProb = emissions.get(s).getActualProbability() / sum;
			emissions.put(s, new LogProb(newProb));
		}
	}
	
	public void normalizeTransitions() {
		double sum = 0.0;
		for (LogProb p : transitions.values()) sum += p.getActualProbability();
		for (HMMState s : transitions.keySet()) {
			LogProb newProb = transitions.get(s).sub(new LogProb(sum));
			transitions.put(s, newProb);
		}
		
	}
	public StateType getType() { return type; }
	
	public Map<HMMState, LogProb> getTransitions() { return transitions; }
	
	public static boolean isLegalTransition(HMMState start, HMMState end, TargetEvent event) {
		if (event == TargetEvent.NO_EVENT) return (start.getType() == StateType.TARGET) == (end.getType() == StateType.TARGET);
		
		Boolean matching = null;

		if (event == TargetEvent.ENTER) {
			matching = start.getType() != StateType.TARGET && end.getType() == StateType.TARGET; 
		}
		else if (event == TargetEvent.EXIT) {
			matching = start.getType() == StateType.TARGET && end.getType() != StateType.TARGET;
		}
		else throw new IllegalStateException();
		
		return matching;
	}
	
	public void addAllChildren(List<HMMState> list, LogProb p) {
		for (HMMState s : list) addChild(s, p);
	}

	
	public void normalizeEmissions(Map<String, Double> vocabCount, double totalWords) {
		defaultEmissionProbability = (new LogProb(1.0)).sub(new LogProb(totalWords));
		
		//for (String s : emissions.keySet()) {
		//	emissions.put(s, (new LogProb(1)).add(emissions.get(s)));
		//}
	}
}







public class HiddenMarkovModel {
	private HashSet<HMMState> states;
	
	private HMMState start;
	private HMMState retargetSuffix;
	
	private List<HMMState> backgroundStates;
	private List<HMMState> prefixStateHeads;
	private List<HMMState> suffixStateHeads;
	private List<HMMState> targetStateHeads;
	
	private StateHierarchy uniform, global, context;
	private StateHierarchy backgroundGlobal, prefixGlobal, suffixGlobal, targetGlobal; 
	
	private Map<HMMState, StateHierarchy> stateHeadContexts;
	
	public static final String PHI_TOKEN = ".~!phi!~.";
	public static final boolean RENORMALIZE = true;

	private void initHierarchy() {

		uniform = new StateHierarchy(null, "UNIFORM");
		global = new StateHierarchy(uniform, "GLOBAL");
		context = new StateHierarchy(global, "CONTEXT");
		
		backgroundGlobal = new StateHierarchy(context, "BACK_GLOBAL");
		prefixGlobal = new StateHierarchy(context, "PREF_GLOBAL");
		suffixGlobal = new StateHierarchy(context, "SUFF_GLOBAL");
		targetGlobal = new StateHierarchy(global, "TARG_GLOBAL");
	

		uniform.setUsingUniformDistribution(true);
	}
	
	public HiddenMarkovModel() {
		states = new HashSet<HMMState>();

		backgroundStates = new ArrayList<HMMState>();
		prefixStateHeads = new ArrayList<HMMState>();
		suffixStateHeads = new ArrayList<HMMState>();
		targetStateHeads = new ArrayList<HMMState>();
		stateHeadContexts = new HashMap<HMMState, StateHierarchy>();
		
		initHierarchy();
		
		start = new HMMState(StateType.PHI, getHierarchyParent(null, StateType.PHI));
		retargetSuffix = new HMMState(StateType.RETARGET, getHierarchyParent(null, StateType.RETARGET));
		addState(start);
		addState(retargetSuffix);
	}
	
	private StateHierarchy getHierarchyParent(HMMState headNode, StateType thisType) {
		if (headNode != null && stateHeadContexts.get(headNode) != null) {
			return stateHeadContexts.get(headNode);
		}
		if (headNode != null && stateHeadContexts.get(headNode) == null) throw new IllegalStateException();
		
		
		//if headNode == null, then assuming the new node will be a head node
		switch (thisType) {
		case PREFIX:
			return prefixGlobal;
		case SUFFIX:
			return suffixGlobal;
		case TARGET:
			return targetGlobal;
		case BACKGROUND:
			return backgroundGlobal;
		case PHI:
		case RETARGET:
			return global;
		default:
			throw new IllegalStateException();
		}
	}
	
	void lengthen(HMMState trg) {
		HMMState newState = new HMMState(trg.getType(), getHierarchyParent(trg, trg.getType()));
		
		newState.stealTransitions(trg);
		trg.addChild(newState, new LogProb(1.0));
		
		addState(newState);
	}
	
	void split(HMMState start) {
		List<HMMState> list = new ArrayList<HMMState>();
		
		list.add(start);
		
		while (list.get(list.size()-1).getType() == start.getType()) {
			HMMState top = list.get(list.size()-1);
			
			HMMState next = top.getSingleOutgoingEdge();
			
			if (next == null) break;
			
			list.add(next);
		}
	}
	//void split()
	
	protected List<HMMState> getAllHeads() {
		List<HMMState> out = new ArrayList<HMMState>();
		
		out.addAll(prefixStateHeads);
		out.addAll(suffixStateHeads);
		out.addAll(targetStateHeads);
		out.addAll(backgroundStates);
		
		return out;
	}
	
	private HMMState getHead(HMMState s) {
		//this is slow, but since the number of states will at most be in the double digits, O(N^2) is fine)
		if (prefixStateHeads.contains(s) || suffixStateHeads.contains(s)
		  || targetStateHeads.contains(s) || backgroundStates.contains(s)
		  || s.equals(this.start) || s.equals(this.retargetSuffix)) return null;
		
		for (HMMState head : getAllHeads()) {
			List<HMMState> line = head.getLineOfSameType();
			
			if (line.contains(s)) return head;
		}
		
		if (s == start || s == retargetSuffix) return null;

		throw new IllegalStateException();
	}
	
	
	public HiddenMarkovModel deepCopy() {
		HashMap<HMMState, HMMState> thisToCopy = new HashMap<HMMState, HMMState>();
		
		HiddenMarkovModel out = new HiddenMarkovModel();
		out.states.clear();
		
		List<HMMState> thisHeads = this.getAllHeads();
		for (HMMState s : states) {
			HMMState s2 = s.shallowCopy();
			thisToCopy.put(s, s2);
			
			if (s.getType() == StateType.PHI) out.start = s2;
			if (s.getType() == StateType.RETARGET) out.retargetSuffix = s2;
			
			out.addState(s2);
			if (thisHeads.contains(s)) switch (s2.getType()) {
			case PREFIX:
				out.prefixStateHeads.add(s2);
				break;
				
			case SUFFIX:
				out.suffixStateHeads.add(s2);
				break;
				
			case TARGET:
				out.targetStateHeads.add(s2);
				break;
				
			case BACKGROUND:
				out.backgroundStates.add(s2);
				break;
				
			default:
				throw new IllegalStateException();
			}
			
		}
		
		out.stateHeadContexts.clear();
		for (HMMState head : out.getAllHeads()) {
			out.stateHeadContexts.put(head, head.hierarchyNode);
		}
		
		for (HMMState s2 : out.states) {
			HMMState head = out.getHead(s2);
			if (out.getAllHeads().contains(s2)) head = null;
			s2.replaceInvalidReferencesFromShallowCopy(thisToCopy, out.getHierarchyParent(head, s2.getType()));
		}
		
		return out;
	}
	
	private void normalizeProbabilities(List<HMMTrainingDocument> trainingCorpus) {
		normalizeTransitions();
		
		normalizeEmissions(trainingCorpus);
	}
	
	private void normalizeTransitions() {
		for (HMMState s : states) {
			s.normalizeTransitions();
		}
	}
	
	private void normalizeEmissions(List<HMMTrainingDocument> trainingCorpus) {
		Map<String, Double> vocabCount = new HashMap<String, Double>();
		
		for (HMMTrainingDocument doc : trainingCorpus) {
			for (List<String> sentence : doc.getTextIgnoringOriginal()) for (String word : sentence) {
				if (HMMState.IGNORE_CASE) word = word.toLowerCase();
				if (!vocabCount.containsKey(word)) vocabCount.put(word, 0.0);
				
				vocabCount.put(word, vocabCount.get(word)+1);
			}
		}
		double totalWords = 0;
		for (Double d : vocabCount.values()) totalWords += d;
		
		for (HMMState s : states) {
			s.normalizeEmissions(vocabCount, totalWords);
		}
	}
	
	public HMMState.ProbabilityTable getForwardsProbabilityTable(List<List<String>> words, List<List<TargetEvent>> events, double totalUniqueWords) {
		return start.getForwardsProbabilityTable(words, events, totalUniqueWords);
	}
	

	public HMMState.ProbabilityTable getBackwardsProbabilityTable(List<List<String>> words, List<List<TargetEvent>> events, double totalUniqueWords) {
		return start.getBackwardsProbabilityTable(states, words, events, totalUniqueWords);
	}
	
	private class XiTable {
		private Map<StatePair, Map<Integer, LogProb>> table;
		
		public XiTable(Map<StatePair, Map<Integer, LogProb>> t) {
			table = t;
			
		}
		
		LogProb find(HMMState i, HMMState j, int t) {
			StatePair p = new StatePair(i, j);
			
			//implicitly storing 0s to make debugging easier
			if (table.get(p) == null || table.get(p).get(t) == null) return new LogProb(0);
			
			return table.get(p).get(t);
		}
	}
	
	//technically, this can be parallelized
	private XiTable getXiTable(List<String> words, 
							   HMMState.ProbabilityTable forwards, HMMState.ProbabilityTable backwards, double totalUniqueWords) {
		

		Map<StatePair, Map<Integer, LogProb>> results = new HashMap<StatePair, Map<Integer, LogProb>>();
		
		
		
		
		for (HMMState i : states) {
			for (HMMState j : states) {
				StatePair pair = new StatePair(i, j);
				for (int t=0; t < words.size(); t++) {

					String wordAtJ = words.get(t);
					
					LogProb numerator = forwards.find(i,  t);
					numerator = numerator.add(backwards.find(j, t+1));
					numerator = numerator.add(i.getProbabilityTo(j)).add(j.getEmissionProbability(wordAtJ, totalUniqueWords));
					
					if (numerator.getActualProbability() == 0.0) continue;
					
					results.putIfAbsent(pair, new HashMap<Integer, LogProb>());
					results.get(pair).put(t, numerator);
				}
			}
		}
		
		
		
		return new XiTable(results);
	}
	
	private class StatePair {
		HMMState start;
		HMMState end;
		
		public StatePair(HMMState a, HMMState b) {
			start = a; end = b;
		}
		
		public int hashCode() {
			return start.hashCode() * 1024 + end.hashCode();
		}
		
		public boolean equals(Object o) {
			if (o instanceof StatePair == false) return false;
			
			StatePair other = (StatePair) o;
			return start == other.start && end == other.end;
			
		}
		
		public String toString() {
			return "("+start.getToStringWithoutChildren() +", "+end.getToStringWithoutChildren()+")";
		}
	}
	private class StateWord {
		HMMState start;
		String end;
		
		public StateWord(HMMState a, String b) {
			start = a; end = b;
		}
		
		public int hashCode() {
			return start.hashCode() * 1024 + end.hashCode();
		}
		
		public boolean equals(Object o) {
			if (o instanceof StateWord == false) return false;
			
			StateWord other = (StateWord) o;
			return start == other.start && end == other.end;
			
		}
	}
	
	private LogProb gamma(HMMState i, int t, LogProb den, List<List<String>> words, 
						  HMMState.ProbabilityTable forwards, HMMState.ProbabilityTable backwards, XiTable xiTable) {
		//return getForwardsProbabilityPartial(i, t, words, events).add(getBackwardsProbabilityPartial(i, t, words, events));
		double sum = 0.0;
		for (HMMState s : i.getTransitions().keySet()) {
			sum += xiTable.find(i, s, t).getActualProbability();
			//sum += xi(i, s, t, den, words, events, forwards, backwards).getActualProbability();
		}
		return new LogProb(sum);
	}

	protected List<HMMState> getHeadListForType(StateType t) {
		switch (t) {
		case PREFIX:
			return prefixStateHeads;
		case SUFFIX:
			return suffixStateHeads;
		case BACKGROUND:
			return backgroundStates;
		case TARGET:
			return targetStateHeads;
		default:
			throw new IllegalStateException();
		}
	}

	
	private static List<List<TargetEvent>> getTargetEvents(HMMTrainingDocument doc) {
		
		List<String> flattened = Utility.flatten(doc.getTextIgnoringOriginal());
		List<TargetEvent> flatEvents = new ArrayList<TargetEvent>();
		
		for (int i=0; i<flattened.size(); i++) flatEvents.add(TargetEvent.NO_EVENT);
		
		for (List<String> result : doc.tokenizedExpectedResults) {
			for (int i=0; i < flattened.size(); i++) {
				boolean incorrect = i + result.size() >= flattened.size();
				
				for (int j=0; j < result.size() && !incorrect; j++) {
					incorrect = !flattened.get(j+i).equals(result.get(j));
				}
				

				if (incorrect) continue;

				flatEvents.set(i, TargetEvent.ENTER);
				flatEvents.set(i+result.size(), TargetEvent.EXIT);
			}
		}
		
		List<List<TargetEvent>> out = new ArrayList<List<TargetEvent>>();
		int flatIdx = 0;
		for (int i=0; i<doc.text.size(); i++) {
			out.add(new ArrayList<TargetEvent>());
			for (int j=0; j < doc.text.get(i).size(); j++) {
				out.get(out.size()-1).add(flatEvents.get(flatIdx));
				flatIdx++;
			}
		}
		

		
		return out;
	}
	
	private void baumWelchStep(List<HMMTrainingDocument> trainingDocs, List<HMMTrainingDocument> testingDocs, boolean printTiming) {
		normalizeTransitions();
		
		Map<StatePair, Double> transitionProbNumerator = new HashMap<StatePair, Double>();
		Map<HMMState, Map<String, Double>> emissionProbNumerator = new HashMap<HMMState, Map<String, Double>>();

		double commonDenominator = 0.0;
		

		double totalUniqueWords;
		double totalWordsFound = 0.0;
		
		Set<String> uniqueWordSet = new HashSet<String>();
		
		for (HMMTrainingDocument d : trainingDocs) {
			for (List<String> sentence : d.getTextIgnoringOriginal()) for (String s : sentence) {
				if (HMMState.IGNORE_CASE) s = s.toLowerCase();
				uniqueWordSet.add(s);
				totalWordsFound++;
			}
		}
		totalUniqueWords = uniqueWordSet.size();

		
		for (HMMState s : states)  {
			for (HMMState k : s.getTransitions().keySet()) {
				transitionProbNumerator.put(new StatePair(s, k), 0.0);
			}
		}

		Timer trainTimer = new Timer();
		Timer parseTimer = new Timer();
		Timer xiTimer = new Timer();
		Timer gammaTimer = new Timer();
		
		Timer transitionNumTimer = new Timer();
		Timer emissionNumTimer = new Timer();
		Timer denTimer = new Timer();
		
		Timer tableTimer = new Timer();
		
		for (HMMTrainingDocument fullDoc : trainingDocs) {
			List<List<String>> doc = fullDoc.getTextIgnoringOriginal();
			
			int totalSize = 0;
			for (List<String> sentence : doc) totalSize += sentence.size();
			
			parseTimer.start();
			List<String> flattenedDoc = Utility.flatten(doc);
			
			//do any parsing necessary
			List<List<TargetEvent>> targetEvents = getTargetEvents(fullDoc);
			
			parseTimer.pause();
			
			tableTimer.start();
			HMMState.ProbabilityTable forwardsTable  = this.getForwardsProbabilityTable(doc, targetEvents, totalUniqueWords);
			HMMState.ProbabilityTable backwardsTable = this.getBackwardsProbabilityTable(doc, targetEvents, totalUniqueWords);
			XiTable xiTable = this.getXiTable(flattenedDoc, forwardsTable, backwardsTable, totalUniqueWords);
			tableTimer.pause();
			
			trainTimer.start();
			//train
			for (HMMState i : states) {
				transitionNumTimer.start();
				for (HMMState j : i.getTransitions().keySet()) {
				
					double aNum = 0.0;
					for (int t=1; t < totalSize - 1; t++) {
						xiTimer.start();
						aNum += xiTable.find(i, j, t).getActualProbability();
						//aNum += xi(i, j, t, new LogProb(1.0), doc.text, targetEvents, forwardsTable, backwardsTable).getActualProbability();
						xiTimer.pause();
					}
					
					StatePair key = new StatePair(i, j);
					transitionProbNumerator.put(key, aNum + transitionProbNumerator.get(key));
				}
				transitionNumTimer.pause();

				emissionNumTimer.start();
				int idx = 0;

				for (List<String> sentence : doc) for (String str : sentence) {
					
					if (!emissionProbNumerator.containsKey(i)) emissionProbNumerator.put(i,  new HashMap<String, Double>());
					
					Map<String, Double> iWordProbs = emissionProbNumerator.get(i);
					if (!iWordProbs.containsKey(str)) iWordProbs.put(str, 0.0);
					
					iWordProbs.put(str, gamma(i, idx, new LogProb(1.0), doc, forwardsTable, backwardsTable, xiTable).getActualProbability()
									    + iWordProbs.get(str));
					
					idx++;
				}
				emissionNumTimer.pause();
			}
			
			//train denominator
			denTimer.start();
			for (HMMState i : states) {
				for (int t=1; t < totalSize; t++) {
					gammaTimer.start();
					commonDenominator += gamma(i, t, new LogProb(1.0), doc, forwardsTable, backwardsTable, xiTable).getActualProbability();
					gammaTimer.pause();
				}
			}
			denTimer.pause();
			trainTimer.pause();
			
			if (Double.isNaN(commonDenominator)) {
				throw new IllegalStateException();
			}
		}
		

		if (printTiming) {
			parseTimer.stopAndPrintFuncTiming("Baum-Welch parsing");
			
			tableTimer.stopAndPrintFuncTiming("Calculating lookup tables (forwardsProb, backwardsProb, xi)");
			
			xiTimer.stopAndPrintFuncTiming("Baum-Welch xi step for num");
			gammaTimer.stopAndPrintFuncTiming("Baum-Welch gamma step for den");
			
			transitionNumTimer.stopAndPrintFuncTiming("BW transition numerator calc");
			emissionNumTimer.stopAndPrintFuncTiming("BW emission numerator calc");
			
			denTimer.stopAndPrintFuncTiming("BW denominator calc");
			trainTimer.stopAndPrintFuncTiming("Baum-Welch train");
		}
		
		Timer updateTimer = new Timer();
		
		updateTimer.start();
		
		//update
		for (StatePair s : transitionProbNumerator.keySet()) {
			LogProb prob = new LogProb(transitionProbNumerator.get(s));
			
			s.start.updateChild(s.end, prob.sub(new LogProb(commonDenominator)));
		}
		for (HMMState state : emissionProbNumerator.keySet()) {
			for (String word : emissionProbNumerator.get(state).keySet()) {
				LogProb prob = new LogProb(emissionProbNumerator.get(state).get(word));
				state.setEmission(word, prob.sub(new LogProb(commonDenominator)));	
			}
		}
		
		this.normalizeTransitions();
		
		
		this.smoothAllEmissions(totalUniqueWords, totalWordsFound);
		 //this.test(trainingDocs);
		//this.trainEmissionLambdas(trainingDocs, totalUniqueWords);
		
		if (printTiming) updateTimer.stopAndPrintFuncTiming("Baum-Welch update");
		
		//return this.test(trainingDocs);
	}
	
	public void baumWelchOptimize(int numSteps, List<HMMTrainingDocument> trainingDocs, List<HMMTrainingDocument> testingDocs, boolean clear, boolean printTiming) {
		if (clear) normalizeProbabilities(trainingDocs);
		
		Timer t = new Timer();
		for (int i=0; i < numSteps; i++) {
			if (printTiming) System.out.println("Beginning training step");
			t.start();
			baumWelchStep(trainingDocs, testingDocs, printTiming);
			if (printTiming) t.stopAndPrintFuncTiming("Full Baum-Welch Step");
			smoothTransitions();
			normalizeTransitions();
		}
	}
	
	private void smoothTransitions() {
		for (HMMState s : states) s.smoothTransitions();
	}
	
	private void trainEmissionLambdas(List<HMMTrainingDocument> testingDocs, double totalUniqueWords) {
		Map<HMMState, Set<String>> emittedStrings = new HashMap<HMMState, Set<String>>();
		
		for (HMMTrainingDocument doc : testingDocs) {
			List<String> flattened = Utility.flatten(doc.getTextIgnoringOriginal());
			
			HMMState.Extraction result = start.getOptimalSequence(flattened, totalUniqueWords);
			List<HMMState> path = result.path;
			
			for (int i=0; i<flattened.size(); i++) {
				HMMState s = path.get(i);
				String   w = flattened.get(i);
				
				emittedStrings.putIfAbsent(s, new HashSet<String>());
				
				emittedStrings.get(s).add(w);
			}
		}
		
		for (HMMState h : emittedStrings.keySet()) h.updateLambdas(emittedStrings.get(h), totalUniqueWords);
	}

	private void replaceConnection(HMMState head, boolean addSelfCycles, StateType[] typesThatTailConnectsTo) {
		List<HMMState> path = head.getLineOfSameType();

		for (int i=1; i<path.size(); i++) {
			HMMState prev = path.get(i-1);
			prev.clearTransitions();
			prev.addChild(path.get(i), new LogProb(1.0));
			if (addSelfCycles) prev.addChild(prev, new LogProb(1.0));
		}
		
		HMMState tail = path.get(path.size()-1);
		
		tail.clearTransitions();
		if (addSelfCycles) tail.addChild(tail, new LogProb(1.0));
		for (StateType t : typesThatTailConnectsTo) {
			switch (t) {
			case PREFIX:
				tail.addAllChildren(prefixStateHeads, new LogProb(1.0));
				break;
				
			case SUFFIX:
				tail.addChild(retargetSuffix, new LogProb(1.0));
				tail.addAllChildren(suffixStateHeads, new LogProb(1.0));
				break;
				
			case TARGET:
				tail.addAllChildren(targetStateHeads, new LogProb(1.0));
				break;
				
			case BACKGROUND:
				tail.addAllChildren(backgroundStates, new LogProb(1.0));
				break;
				
			case RETARGET:
			case PHI:
				break;
				
			default:
				throw new IllegalArgumentException("Unexpected StateType in replaceConnections");
			}
		}
	}
	
	public void replaceAllConnections() {
		replaceConnection(start, true, new StateType[]{StateType.TARGET, StateType.PREFIX, StateType.BACKGROUND});
		replaceConnection(retargetSuffix, false, new StateType[] {StateType.TARGET, StateType.PREFIX});
		
		for (HMMState s : prefixStateHeads) replaceConnection(s, false, new StateType[] {StateType.TARGET});
		for (HMMState s : suffixStateHeads) replaceConnection(s, false, new StateType[] {StateType.BACKGROUND, StateType.PREFIX});
		for (HMMState s : backgroundStates) replaceConnection(s, true, new StateType[] {StateType.PREFIX});
		for (HMMState s : targetStateHeads) replaceConnection(s, true, new StateType[] {StateType.SUFFIX});
		
		this.normalizeTransitions();
	}
	
	public void smoothAllEmissions(double totalUniqueWords, double totalWords) {
		for (HMMState s : states) {
			s.smoothEmissions(totalUniqueWords, totalWords);
		}
	}
	
	public void addHead(StateType t) {
		HMMState s = new HMMState(t, new StateHierarchy(getHierarchyParent(null, t), t.toString()+"_HEAD"));
		stateHeadContexts.put(s, s.hierarchyNode.parent);
		
		Set<HMMState> visited = new HashSet<HMMState>();
		
		Stack<HMMState> next = new Stack<HMMState>();
		next.add(s);
		
		while (!next.isEmpty()) {
			HMMState top = next.pop();
			visited.add(top);
			
			for (HMMState n : top.getTransitions().keySet()) {
				if (!visited.contains(n)) next.push(n);
			}
		}
		
		for (HMMState found : visited) addState(found);
		
		switch (s.getType()) {
		case PREFIX:
			prefixStateHeads.add(s);
			break;
			
		case SUFFIX:
			suffixStateHeads.add(s);
			break;
			
		case TARGET:
			targetStateHeads.add(s);
			break;
			
		case BACKGROUND:
			backgroundStates.add(s);
			break;
			
		case PHI:
		case RETARGET:
			break;
			
		default:
			throw new IllegalArgumentException("Unexpected StateType in addHead");
		}
	}
	
	public static HiddenMarkovModel generateBasicModel() {
		HiddenMarkovModel out = new HiddenMarkovModel();
		
		out.addHead(StateType.PREFIX);
		out.addHead(StateType.SUFFIX);
		out.addHead(StateType.BACKGROUND);
		out.addHead(StateType.TARGET);

		out.replaceAllConnections();
		
		return out;		
	}
	
	
	private void addState(HMMState s) {
		states.add(s);
	}
	
	private double estimateVocabularySize(List<HMMTrainingDocument> docs, Object ignoredValueToDisambiguate) {
		Set<String> uniqueWordsSeen = new HashSet<String>();
		
		for (HMMTrainingDocument doc : docs) {
			for (List<ConvertedWord> sentence : doc.text) for (ConvertedWord w : sentence) uniqueWordsSeen.add(w.get());
		}

		for (HMMState s : states) for (String str : s.getEmissionWords()) {
			uniqueWordsSeen.add(str);
		}
		
		return uniqueWordsSeen.size();
		
	}
	
	private double estimateVocabularySize(List<ConvertedWord> doc) {
		Set<String> uniqueWordsSeen = new HashSet<String>();
		
		for (ConvertedWord w : doc) uniqueWordsSeen.add(w.get());
		
		for (HMMState s : states) for (String str : s.getEmissionWords()) {
			uniqueWordsSeen.add(str);
		}
		
		return uniqueWordsSeen.size();
	}
	
	private double test(List<HMMTrainingDocument> trainingFiles) {
		double correctNonTargets = 0.001;
		double correctTargets = 0.001;
		double guessedTargets = 0.001;
		double guessedNonTargets = 0.001;
		double expectedNonTargets = 0.001;
		double expectedTargets = 0.001;
		
		double vocabSize = estimateVocabularySize(trainingFiles, null);
		
		
		for (HMMTrainingDocument doc : trainingFiles) {
			List<String> flattened = Utility.flatten(doc.getTextIgnoringOriginal());
			HMMState.Extraction actual = start.getOptimalSequence(flattened, vocabSize);
			List<TargetEvent> expected = Utility.flatten(getTargetEvents(doc));
			
			boolean extracting = false;
			for (int i=0; i < flattened.size(); i++) {
				HMMState 	state   = actual.path.get(i);
				TargetEvent event   = expected.get(i);
				
				if (event == TargetEvent.ENTER) extracting = true;
				if (event == TargetEvent.EXIT)  extracting = false;
				
				if (extracting) {
					if (state.getType() == StateType.TARGET) correctTargets++;
					expectedTargets++;
				}
				else {
					if (state.getType() != StateType.TARGET) correctNonTargets++;
					expectedNonTargets++;
				}
				
				if (state.getType() == StateType.TARGET) guessedTargets++;
				else guessedNonTargets++;
				
			}
		}
		
		double targetPrecision = correctTargets / guessedTargets;
		double targetRecall    = correctTargets / expectedTargets;
		double ntPrecision = correctNonTargets / guessedNonTargets;
		double ntRecall    = correctNonTargets / expectedNonTargets;
		
		double targetF1 = 2*(targetPrecision * targetRecall) / (targetPrecision + targetRecall);
		double ntF1     = 2*(ntPrecision * ntRecall) / (ntPrecision + ntRecall);
		
		if (Double.isNaN(targetF1) && Double.isNaN(ntF1)) throw new IllegalStateException("NaN found for both targetF1 and nonTargetF1 in test()");
		if (Double.isNaN(targetF1)) return 2*ntF1;
		else if (Double.isNaN(ntF1)) return 2*targetF1;
		
		else return targetF1 + ntF1;
		
	}
	
	public Set<ConvertedWord> extract(List<ConvertedWord> doc, boolean extractSingle, Function<List<ConvertedWord>, ConvertedWord> concatFunc) {
		List<String> convertedDoc = new ArrayList<String>();
		for (ConvertedWord w : doc) convertedDoc.add(w.get());
		double totalUniqueWords = estimateVocabularySize(doc);
		
		HMMState.Extraction result = start.getOptimalSequence(convertedDoc, totalUniqueWords);
		List<HMMState> path = result.path;
		
		Set<ConvertedWord> allCapturedTokens = new HashSet<ConvertedWord>();
		
		boolean capturing = false;
		List<ConvertedWord> capturingTokens = new ArrayList<ConvertedWord>();
		
		for (int i=0; i<path.size(); i++) {
			capturing = path.get(i).getType() == StateType.TARGET;
			
			if (capturing) {
				capturingTokens.add(doc.get(i));
			}
			else {
				if (capturingTokens.size() > 0) allCapturedTokens.add(concatFunc.apply(capturingTokens));
				capturingTokens.clear();
			}
				
		}
		
		if (capturing && capturingTokens.size() > 0) allCapturedTokens.add(concatFunc.apply(capturingTokens));

		
		if (extractSingle) {
			Set<ConvertedWord> out = new HashSet<ConvertedWord>();
			for (ConvertedWord s : allCapturedTokens) {
				out.add(s);
				break;
			}
			
			return out;
		}
		
		return allCapturedTokens;
	}
	
	public List<Set<ConvertedWord>> extractAll(List<List<List<ConvertedWord>>> corpus, boolean extractSingle, Function<List<ConvertedWord>, ConvertedWord> concatFunc) {
		List<Set<ConvertedWord>> out = new ArrayList<Set<ConvertedWord>>();
		for (List<List<ConvertedWord>> doc : corpus) {
			out.add(extract(Utility.flatten(doc), extractSingle, concatFunc));
		}
		return out;
	}
	
}
