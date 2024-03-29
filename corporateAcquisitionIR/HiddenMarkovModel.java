package corporateAcquisitionIR;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import edu.stanford.nlp.parser.shiftreduce.CreateTransitionSequence;


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

class StateHierarchy implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7106329120634801425L;
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
	
	//this is the StateHierarchy one
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


class HMMState implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8912780681979259866L;
	static final boolean IGNORE_CASE = false;
	static final boolean IGNORE_HIERARCHY = true;
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
	
	public Set<HMMState> getParents() { return parents; }
	
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
	
	class StringProb {
		public String str;
		public LogProb prob;
		
		public StringProb(String s, LogProb p) {str = s; prob = p;}
		
	}
	
	List<StringProb> getNBestEmissions(int n) {
		List<String> best = new ArrayList<String>();
		
		for (String s : emissions.keySet()) {
			best.add(s);
			Collections.sort(best, (a, b) -> {return Double.compare(emissions.get(b).getValue(), emissions.get(a).getValue());});
			if (best.size() > n) best.remove(best.size()-1);
		}
		
		List<StringProb> out = new ArrayList<StringProb>();
		
		for (String s : best) out.add(new StringProb(s, emissions.get(s)));
		
		return out;
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
	

	public void pertube(Random r) {
		LogProb minEmission = new LogProb(0.0);
		for (LogProb l : emissions.values()) if (l.getValue() > minEmission.getValue()) minEmission = l;
		
		LogProb pertubationMagnitude = minEmission.sub(LogProb.makeFromExponent(-8));
		for (String s : emissions.keySet()) {
			if (r.nextBoolean()) emissions.put(s, emissions.get(s).add(pertubationMagnitude));
			else 				 emissions.put(s, emissions.get(s).sub(pertubationMagnitude));	
		}
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
		if (HMMState.IGNORE_HIERARCHY) ignoreHierarchy = true;
		if (HMMState.IGNORE_CASE) word = word.toLowerCase();
		

		if (!ignoreHierarchy) return hierarchyNode.getProbability(word, totalUniqueWords);
		
		if (word.equals(HiddenMarkovModel.PHI_TOKEN) && this.type != StateType.PHI) {
			return new LogProb(Math.pow(10, -300));
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
		private List<LogProb> sums;
		
		public ProbabilityTable(List<Map<HMMState, LogProb>> t) {
			table = t;
			sums = new ArrayList<LogProb>();
			
			for (int i=0; i<table.size(); i++) {
				LogProb lpSum = LogProb.safeSum(table.get(i).values());
				
				sums.add(lpSum);
			}
		}
		
		public LogProb find(HMMState trg, int t) {
			return HMMState.findTargetProbabilityOrSumIfTargetNull(table.get(t), trg);
		}
		
		public LogProb sum(int t) {
			return sums.get(t);
		}
	}
	
	void smoothTransitions() {
		LogProb addVal = LogProb.makeFromExponent(-200);
		double v = transitions.size();
		
		for (HMMState s : transitions.keySet()) {
			if (Double.isNaN(transitions.get(s).getValue())) {
				transitions.put(s, new LogProb(0));
			}
		}
		
		LogProb bestProb = new LogProb(0);
		for (LogProb p : transitions.values()) {
			if (p.getValue() > bestProb.getValue()) bestProb = p;	
		}
		
		LogProb totalProb = LogProb.safeSum(transitions.values());
		
		if (Double.isInfinite(bestProb.getValue())) {
			for (HMMState s : transitions.keySet()) transitions.put(s, new LogProb(1.0 / transitions.size()));
		}
			
		for (HMMState s : transitions.keySet()) {
			LogProb finalProb = LogProb.safeSum(transitions.get(s), addVal);
			finalProb.sub(LogProb.safeSum(totalProb, addVal.add(new LogProb(v))));
			//prob /= totalProb + addVal*v;
			transitions.put(s, finalProb);
		}
	}
	
	void smoothEmissions(double totalUniqueWords, double totalWords) {
		//(emission(s) + addedValue) / (totalProbability + addedValue*vocabSize)
		// addedValue*vocabSize = 1, when addedValue = 1/vocabSize
		hierarchyNode.invalidateCache();

		double MIN_EMISSION_VAL = -20000;
		
		totalUniqueWords++;	//+1 for all unknown words
		
		//double addKVal = 1.0 / totalUniqueWords / 2.0 / 500000.0;
		LogProb addKVal = LogProb.makeFromExponent(-500);
		
		Set<String> keysToRemove = new HashSet<String>();

		for (String s : emissions.keySet()) {
			if (emissions.get(s).getValue() < MIN_EMISSION_VAL || Double.isNaN(emissions.get(s).getValue())) {
				//emissions.put(s, LogProb.makeFromExponent(MIN_EMISSION_VAL));
				keysToRemove.add(s);
			}
		}
		
		
		for (String s : keysToRemove) emissions.remove(s);

		if (emissions.size() == 0) {
			defaultEmissionProbability = new LogProb(1.0 / totalUniqueWords);
			return;
		}
		
		LogProb emissionsSum = LogProb.safeSum(emissions.values());
		for (String s : emissions.keySet()) {
			LogProb prob = LogProb.safeSum(emissions.get(s), addKVal)
					.sub(LogProb.safeSum(emissionsSum, new LogProb(totalUniqueWords).add(addKVal)));
			emissions.put(s,  emissions.get(s).sub(emissionsSum));
		}
		
		//double totalProbability = 0.0;
		//double minProbability = Double.NEGATIVE_INFINITY;
		//double emissionSum = 0.0;
		//for (LogProb p : emissions.values()) emissionSum += p.getActualProbability();
		
		defaultEmissionProbability = addKVal.sub(emissionsSum);
		
		
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
		LogProb max = new LogProb(0);
		
		for (LogProb l : col.values()) {
			if (l.getValue() > max.getValue()) max = l;
		}
		/*
		for (LogProb l : col.values()) {
			double prob = l.sub(max).getActualProbability();
			
			sum += prob;
		}
		LogProb factor = new LogProb(sum).add(max); 
		*/
		LogProb factor = max;
		
		//hacky fix for underflow
		if (Double.isInfinite(factor.getValue())) {
			for (HMMState s : col.keySet()) col.put(s, new LogProb(1.0 / col.size()));
			return;
		}

		for (HMMState s : col.keySet()) {
			col.put(s, col.get(s).sub(factor));
		}
	}
	
	private static String dumpProbabilityTableState(List<Map<HMMState, LogProb>> sparseMap, List<String> doc, List<TargetEvent> events, double totalUniqueWords) {
		Set<HMMState> allSeenStates = new HashSet<HMMState>();
		
		String message = "IsNaN found after renormalization:\nsparseMap:\n";
		
		int count = 0;
		for (Map<HMMState, LogProb> col : sparseMap) {
			for (HMMState s : col.keySet()) {
				allSeenStates.add(s);
				LogProb result = col.get(s);
				message += count + "\t" + s + " " + result + " ";
			}
			message += "\n";
			count++;
		}
		message += "transitionProbs:\n";
		for (HMMState s : allSeenStates) {
			for (HMMState other : s.transitions.keySet()) {
				message += s + " -> " + other + " = " + s.transitions.get(other) + "\n";
			}
		}
		Set<String> allStrings = new HashSet<String>();
		
		message += "idx, document, target events\n";
		int idx = 0;
		
		for (int i=0; i<doc.size(); i++) {
			message += i + "\t" + events.get(i) + "\t" + doc.get(i) + "\n";
		}
		
		message += "\nemission probabilities:\n";
		for (HMMState s : allSeenStates) for (String word : allStrings) {
			message += s + " " + word + " " + s.getEmissionProbability(word, totalUniqueWords) + "\n";
		}
			
		return message;
	}
	
	public ProbabilityTable getForwardsProbabilityTable(List<String> doc, List<TargetEvent> events, double totalUniqueWords) {
		List<Map<HMMState, LogProb>> sparseMap = new ArrayList<Map<HMMState, LogProb>>();
		
		if (doc.size() == 0) return new ProbabilityTable(sparseMap);
		
		
		
		Map<HMMState, LogProb> startNode = new HashMap<HMMState, LogProb>();
		startNode.put(this, this.getEmissionProbability(doc.get(0), totalUniqueWords));
		sparseMap.add(startNode);
		
		if (HiddenMarkovModel.RENORMALIZE) renormalizeColumn(startNode);
	
		for (int i=1; i<doc.size(); i++) {
			
			String curStr = doc.get(i);
			TargetEvent curEvent = events.get(i);
			
			Map<HMMState, LogProb> top = sparseMap.get(sparseMap.size() - 1);
			
			Map<HMMState, Map<HMMState, LogProb>> transitions = new HashMap<HMMState, Map<HMMState, LogProb>>();
			
			for (HMMState s : top.keySet()) {
				transitions.put(s, new HashMap<HMMState, LogProb>());
				for (HMMState other : s.transitions.keySet()) {
					boolean ignore = false;
					if (other.getType() == StateType.PREFIX) {
						int expectedTargetIdx = i;
						for (HMMState curChild = other; curChild.getType() == StateType.PREFIX; curChild = curChild.transitions.keySet().iterator().next()) {
							expectedTargetIdx++;
						}
						ignore = expectedTargetIdx >= doc.size() || events.get(expectedTargetIdx) != TargetEvent.ENTER;
					}
					
					if (!ignore) transitions.get(s).put(other, s.transitions.get(other));
				}
				//transitions.get(s).putAll(s.transitions);
			}
			
			Map<HMMState, LogProb> nextMap = directionalProbabilityStep(top, transitions, curStr, curEvent, false, totalUniqueWords);
			
			if (HiddenMarkovModel.RENORMALIZE) renormalizeColumn(nextMap);
			 
			boolean hasNaN = false; 
			for (LogProb p  : nextMap.values()) if (Double.isNaN(p.getActualProbability())) hasNaN = true;
			
			if (hasNaN || nextMap.size() == 0) {
				String message = dumpProbabilityTableState(sparseMap, doc, events, totalUniqueWords);
				
				throw new IllegalStateException(message+"\n(forwards prob)");
			}
				
			sparseMap.add(nextMap);
		}

		return new ProbabilityTable(sparseMap);
	}
	

	public ProbabilityTable getBackwardsProbabilityTable(Set<HMMState> allStates, List<String> doc, List<TargetEvent> events,
														 double totalUniqueWords) {
		List<Map<HMMState, LogProb>> sparseMap = new ArrayList<Map<HMMState, LogProb>>();
		
		int stopAtIthWord = 0;
		
		if (doc.size() == 0) return new ProbabilityTable(sparseMap);
		
		int phiAvailableIdx = -1;
		for (int i=0; i<events.size(); i++) if (events.get(i) == TargetEvent.ENTER) {
			phiAvailableIdx = i;
			break;
		}
		
		Map<HMMState, LogProb> startNode = new HashMap<HMMState, LogProb>();
		for (HMMState s : allStates) {
			if (s.getType() != StateType.TARGET
			    && !(s.getType() == StateType.PHI && phiAvailableIdx != -1)) startNode.put(s, new LogProb(1.0));
		}
		sparseMap.add(startNode);

		if (HiddenMarkovModel.RENORMALIZE) renormalizeColumn(startNode);
	
		
		final int LAST_WORD = doc.size() - stopAtIthWord;
		
		int wordsProcessed = 0;
		
		search_loop:
		for (int i=doc.size()-2; i>=0; i--) {
			if (wordsProcessed == LAST_WORD) break search_loop;
			wordsProcessed++;
			
			String curStr = doc.get(i);
			
			TargetEvent curEvent = null;
			curEvent = events.get(i+1);
			
			Map<HMMState, LogProb> top = sparseMap.get(sparseMap.size() - 1);
			
			Map<HMMState, Map<HMMState, LogProb>> transitions = new HashMap<HMMState, Map<HMMState, LogProb>>();
			
			for (HMMState s : top.keySet()) {
				transitions.put(s, new HashMap<HMMState, LogProb>());
				for (HMMState parent : s.parents) {
					boolean ignore = false;
					if (parent.getType() == StateType.SUFFIX) {
						int expectedTargetIdx = i;
						for (HMMState curParent = parent; curParent.getType() == StateType.SUFFIX; curParent = curParent.parents.iterator().next()) {
							expectedTargetIdx--;
						}
						ignore = expectedTargetIdx < 0 || events.get(expectedTargetIdx+1) != TargetEvent.EXIT;
					}
					else if (parent.getType() == StateType.PHI) ignore = i > phiAvailableIdx;
					
					if (!ignore) transitions.get(s).put(parent, parent.transitions.get(s));
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
				
				if (Double.isNaN(outMap.get(nextState).getActualProbability())) {
					LogProb a = nextState.getEmissionProbability(curString, totalUniqueWords);
					LogProb b = transitions.get(nextState);
					LogProb c = cur.get(s);
					throw new IllegalArgumentException("Found NaN probability in directionalProbabilityStep\n"
														+ cur + " -> " + nextState + "\n"
														+ "emission = "+a+", P(b | a) = " + b + ", previous term = "+c);
				}
			}
		}
		
		return outMap;
	}
	
	private static LogProb findTargetProbabilityOrSumIfTargetNull(Map<HMMState, LogProb> lastMap, HMMState trg) {
		if (trg != null) {
			LogProb out = lastMap.get(trg);
			if (out == null) return new LogProb(0);
			return out;
		}
		
		LogProb out = new LogProb(1);
		
		
		for (HMMState s : lastMap.keySet()) {
			LogProb prob = lastMap.get(s);
			
			if (trg == s) return prob;
			out = out.add(prob);
		}
		
		return out;
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
		LogProb selfCycleProb =  other.transitions.remove(other);
		transitions = other.transitions;
		if (selfCycleProb != null) {
			other.parents.remove(other);
			this.parents.add(this);
			this.transitions.put(this, selfCycleProb);
		}
		
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
		if (!transitions.containsKey(s)) throw new IllegalStateException("Attempted to add logProb to child "+this+" doesn't have");
		
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
	
	/*
	public void normalizeProbabilities() {
		normalizeTransitions();
		
		
		double sum = 0.0;
		for (LogProb p : emissions.values()) sum += p.getActualProbability();
		for (String s : emissions.keySet()) {
			double newProb = emissions.get(s).getActualProbability() / sum;
			emissions.put(s, new LogProb(newProb));
		}
	}*/
	
	public void normalizeTransitions() {
		LogProb sum = LogProb.safeSum(transitions.values());
		
		for (HMMState s : transitions.keySet()) {
			LogProb newProb = transitions.get(s).sub(sum);
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
		else throw new IllegalStateException("Unexpected TargetEvent for isLegalTransition()");
		
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







public class HiddenMarkovModel  implements Serializable  {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5234309004000188518L;

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
	public static final boolean RENORMALIZE = false;

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
		if (headNode != null && stateHeadContexts.get(headNode) == null) throw new IllegalStateException("Couldn't find context for head "+headNode);
		
		
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
			throw new IllegalStateException("Unexpected StateType in getHierarchyParent");
		}
	}
	
	void lengthen(HMMState trg) {
		HMMState newState = new HMMState(trg.getType(), getHierarchyParent(trg, trg.getType()));
		
		newState.stealTransitions(trg);
		trg.addChild(newState, new LogProb(1.0));
		
		addState(newState);
	}
	
	void split(HMMState start) {
		List<HMMState> oldList = start.getLineOfSameType();
		 
		StateType type = start.getType();
		
		HMMState head = addHead(type);
		
		List<HMMState> newList = new ArrayList<HMMState>();
		
		newList.add(head);
		
		for (HMMState parent : start.getParents()) {
			if (parent == start) continue;
			
			LogProb chance = parent.getTransitions().get(start);
			parent.addChild(head, chance);
		}
		
		for (int i=1; i<oldList.size(); i++) {
			HMMState newChild = new HMMState(type, this.getHierarchyParent(head, type));
			newList.add(newChild);
			addState(newChild);
		} 
		
		for (int i=1; i<newList.size(); i++) {
			HMMState prev = newList.get(i-1);
			HMMState next = newList.get(i);
			
			HMMState prevOld = oldList.get(i-1);
			LogProb selfCycleChance = prevOld.getTransitions().get(prevOld);
			if (selfCycleChance != null) prev.addChild(prev, selfCycleChance);
			
			HMMState nextOld = oldList.get(i);
			LogProb forwardsChance = prevOld.getTransitions().get(nextOld);
			prev.addChild(next, forwardsChance);
		}
		
		HMMState last = newList.get(newList.size()-1);
		HMMState oldLast = oldList.get(oldList.size()-1);
		
		for (HMMState other : oldLast.getTransitions().keySet()) {
			LogProb chance = oldLast.getTransitions().get(other);
			if (other == oldLast) other = last;
			last.addChild(other, chance);
		}
		
		//kind of a hack, targets are the only type that connect to others of same type
		for (HMMState otherTrg : targetStateHeads) {
			if (otherTrg == last) continue;
			
			last.addChild(otherTrg, new LogProb(1.0 / targetStateHeads.size() / last.getTransitions().size()));
			otherTrg.addChild(last, new LogProb(1.0 / otherTrg.getTransitions().size()));
			
			otherTrg.normalizeTransitions();
		}
		last.normalizeTransitions();
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

		throw new IllegalStateException("Couldn't find head in getHead");
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
				throw new IllegalStateException("Unexpected StateType in deepCopy()");
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
	
	public HMMState.ProbabilityTable getForwardsProbabilityTable(List<String> words, List<TargetEvent> events, double totalUniqueWords) {
		return start.getForwardsProbabilityTable(words, events, totalUniqueWords);
	}
	

	public HMMState.ProbabilityTable getBackwardsProbabilityTable(List<String> words, List<TargetEvent> events, double totalUniqueWords) {
		return start.getBackwardsProbabilityTable(states, words, events, totalUniqueWords);
	}
	
	private class XiTable {
		//private Map<Integer, Map<StatePair, LogProb>> table;
		private Map<StatePair, LogProb> summedTable;
		
		public XiTable(Map<StatePair, LogProb> t) {
			summedTable = t;
			
		}
		
		LogProb find(HMMState i, HMMState j) {
			StatePair p = new StatePair(i, j);
			
			//implicitly storing 0s to make debugging easier
			//if (table.get(t) == null || table.get(t).get(p) == null) return new LogProb(0);
			if (summedTable.get(p) == null) return new LogProb(0);
			
			return summedTable.get(p);
		}
	}
	
	//technically, this can be parallelized
	private XiTable getXiTable(List<String> words, 
							   HMMState.ProbabilityTable forwards, HMMState.ProbabilityTable backwards, double totalUniqueWords) {
		

		//Map<Integer, Map<StatePair, LogProb>> results = new HashMap<Integer, Map<StatePair, LogProb>>();
		Map<StatePair, List<LogProb>> storedSums = new HashMap<StatePair, List<LogProb>>();
		LogProb currentSavedBest = new LogProb(0.0);
		LogProb lastSavedBest = new LogProb(0.0);
		Map<StatePair, LogProb> currentResults = new HashMap<StatePair, LogProb>();
		
		

		for (int t=0; t < words.size() - 1; t++) {
			lastSavedBest = currentSavedBest;
			currentSavedBest = new LogProb(0.0);
			
			LogProb max = new LogProb(0.0);
			
			for (HMMState i : states) {
				for (HMMState j : i.getTransitions().keySet()) {
					StatePair pair = new StatePair(i, j);
	
					String wordAtJ = words.get(t+1);
					
					LogProb numerator = forwards.find(i,  t);
					numerator = numerator.add(backwards.find(j, t+1));
					numerator = numerator.add(i.getProbabilityTo(j)).add(j.getEmissionProbability(wordAtJ, totalUniqueWords));
					
					if (Double.isInfinite(numerator.getValue())) continue;
					
					currentResults.put(pair, numerator);
					
					if (numerator.getValue() > max.getValue()) max = numerator;
				}
			}
			
			
			LogProb normalizeDenominator = LogProb.safeSum(currentResults.values());
			
			for (StatePair pair : currentResults.keySet()) {
				LogProb p = currentResults.get(pair).sub(normalizeDenominator);
				if (p.getValue() > currentSavedBest.getValue()) currentSavedBest = p;
				currentResults.put(pair, p);
			}
			
			//add to sums
			for (StatePair p : currentResults.keySet()) {
				storedSums.putIfAbsent(p, new ArrayList<LogProb>());
				storedSums.get(p).add(currentResults.get(p));
				
				/*double value = currentResults.get(p).sub(currentSavedBest).getActualProbability()
							   + storedSums.get(p) * Math.pow(2, lastSavedBest.getValue() - currentSavedBest.getValue());
				storedSums.put(p, value);*/
			}
			
			currentResults.clear();
		}
		
		Map<StatePair, LogProb> finalSums = new HashMap<StatePair, LogProb>();
		
		for (StatePair p : storedSums.keySet()) finalSums.put(p, LogProb.safeSum(storedSums.get(p)));
		//for (StatePair p : storedSums.keySet()) finalSums.put(p, new LogProb(storedSums.get(p)).add(currentSavedBest));
		
		
		return new XiTable(finalSums);
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
			return start == other.start && end.equals(other.end);
			
		}
	}
	
	/*private LogProb gamma(HMMState i, int t, List<List<String>> words, 
						  HMMState.ProbabilityTable forwards, HMMState.ProbabilityTable backwards, XiTable xiTable) {
		//return getForwardsProbabilityPartial(i, t, words, events).add(getBackwardsProbabilityPartial(i, t, words, events));
		/*double sum = 0.0;
		for (HMMState s : i.getTransitions().keySet()) {
			//sum += xiTable.find(i, s, t).getActualProbability();
			
			/*if (Double.isNaN(sum)) {
				throw new IllegalStateException("NaN gamma");
			}*/
			//sum += xi(i, s, t, den, words, events, forwards, backwards).getActualProbability();
		/*} 
		return new LogProb(sum);*/
		/*LogProb actualDen = forwards.sum(t).add(backwards.sum(t));
		
		return forwards.find(i, t).add(backwards.find(i, t)).sub(actualDen);
	}*/

	private List<LogProb> gammaVector(HMMState i, int maxT, HMMState.ProbabilityTable forwards, HMMState.ProbabilityTable backwards) {
		List<LogProb> gammas = new ArrayList<LogProb>();
		
		for (int t=0; t<maxT; t++) {
			LogProb actualDen = forwards.sum(t).add(backwards.sum(t));
			
			gammas.add(forwards.find(i, t).add(backwards.find(i, t)).sub(actualDen));
		}
		
		return gammas;
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
			throw new IllegalStateException("Unexpected StateType in getHeadListForType()");
		}
	}

	
	private static List<List<TargetEvent>> getTargetEvents(HMMTrainingDocument doc) {
		
		List<ConvertedWord> flattened = Utility.flatten(doc.text);
		List<TargetEvent> flatEvents = new ArrayList<TargetEvent>();
		
		for (int i=0; i<flattened.size(); i++) flatEvents.add(TargetEvent.NO_EVENT);
		
		for (List<String> result : doc.tokenizedExpectedResults) {
			for (int i=0; i < flattened.size(); i++) {
				boolean incorrect = i + result.size() >= flattened.size();
				
				for (int j=0; j < result.size() && !incorrect; j++) {
					incorrect = !flattened.get(j+i).getOriginal().equals(result.get(j));
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
		
		Map<StatePair, LogProb> xiSumOverT = new HashMap<StatePair, LogProb>();
		Map<HMMState, LogProb> gammaSumOverT = new HashMap<HMMState, LogProb>();
		Map<StateWord, LogProb> gammaDotObservationVectorSum = new HashMap<StateWord, LogProb>();
		
		//so, for emission probabilities:
		//  for each state i:
		//   num = sum gamma_i(t) * occurence vector
		//  
		
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

		

		Timer trainTimer = new Timer();
		Timer parseTimer = new Timer();
		
		Timer denTimer = new Timer();
		
		Timer tableTimer = new Timer();
		
		for (HMMTrainingDocument fullDoc : trainingDocs) {
			List<List<String>> doc = fullDoc.getTextIgnoringOriginal();
			
			
			parseTimer.start();
			List<String> flattenedDoc = Utility.flatten(doc);
			
			//do any parsing necessary
			List<List<TargetEvent>> targetEvents = getTargetEvents(fullDoc);
			List<TargetEvent> flattenedEvents = Utility.flatten(targetEvents);
			
			parseTimer.pause();
			
			tableTimer.start();
			HMMState.ProbabilityTable forwardsTable  = this.getForwardsProbabilityTable(flattenedDoc, flattenedEvents, totalUniqueWords);
			HMMState.ProbabilityTable backwardsTable = this.getBackwardsProbabilityTable(flattenedDoc, flattenedEvents, totalUniqueWords);
			XiTable xiTable = this.getXiTable(flattenedDoc, forwardsTable, backwardsTable, totalUniqueWords);
			tableTimer.pause();
			
			trainTimer.start();
			//train
			for (HMMState i : states) {
				for (HMMState j : i.getTransitions().keySet()) {
					StatePair p = new StatePair(i, j);

					xiSumOverT.putIfAbsent(p, new LogProb(0.0));
					xiSumOverT.put(p, LogProb.safeSum(xiSumOverT.get(p), xiTable.find(i, j)));
				}
				
				List<LogProb> gammas = gammaVector(i, flattenedDoc.size(), forwardsTable, backwardsTable);
				
				gammaSumOverT.putIfAbsent(i, new LogProb(0.0));
				gammaSumOverT.put(i, LogProb.safeSum(gammaSumOverT.get(i), LogProb.safeSum(gammas)));
				
				for (int k=0; k<gammas.size(); k++) {
					String word = flattenedDoc.get(k);
					LogProb gamma = gammas.get(k);
					
					StateWord cur = new StateWord(i, word);
					gammaDotObservationVectorSum.putIfAbsent(cur, new LogProb(0.0));
					
					LogProb newValue = LogProb.safeSum(gammaDotObservationVectorSum.get(cur), gamma);
					gammaDotObservationVectorSum.put(cur, newValue);
				}
			}
			trainTimer.pause();
		}
		

		if (printTiming) {
			parseTimer.stopAndPrintFuncTiming("Baum-Welch parsing");
			
			tableTimer.stopAndPrintFuncTiming("Calculating lookup tables (forwardsProb, backwardsProb, xi)");
		
			
			denTimer.stopAndPrintFuncTiming("BW denominator calc");
			trainTimer.stopAndPrintFuncTiming("Baum-Welch train");
		}
		
		Timer updateTimer = new Timer();
		
		updateTimer.start();

		LogProb addedTransitionProb = LogProb.makeFromExponent(-1000);
		LogProb totalXiProb = LogProb.safeSum(xiSumOverT.values());
		
		
		for (StatePair pair : xiSumOverT.keySet()) {
			HMMState first = pair.start;
		
			
			
			
			LogProb prob = xiSumOverT.get(pair).sub(gammaSumOverT.get(first));
			
			/*prob = LogProb.safeSum(prob, addedTransitionProb);
			prob = prob.sub(LogProb.safeSum(totalXiProb, new LogProbaddedTransitionProb * xiSumOverT.size())));
			*/
			first.updateChild(pair.end, prob);
		}
		
		for (StateWord sw : gammaDotObservationVectorSum.keySet()) {
			sw.start.setEmission(sw.end, gammaDotObservationVectorSum.get(sw));
		}
		
		this.smoothTransitions();
		this.normalizeTransitions();
		
		
		this.smoothAllEmissions(totalUniqueWords, totalWordsFound);
		 //this.test(trainingDocs);
		//this.trainEmissionLambdas(trainingDocs, totalUniqueWords);
		
		if (printTiming) updateTimer.stopAndPrintFuncTiming("Baum-Welch update");
		
		//return this.test(trainingDocs);
	}
	

	public String toGraphViz() {
		String out = "";
		HashMap<StateType, String> typeClusters = new HashMap<StateType, String>();
		for (HMMState s : states) {
			typeClusters.putIfAbsent(s.getType(), "");
			typeClusters.put(s.getType(), typeClusters.get(s.getType()) + s.toString() + "\n");
		}
		
		for (StateType t : StateType.values()) {
			out += "subgraph cluster_"+t.toString() + "{\nstyle = filled; color = lightgrey;\n";
			out += typeClusters.get(t);
			out += "}\n";
		}
		
		final double RED_PROBABILITY = 15.0;
		
		for (HMMState s : states) {
			//String name = s.toString() + "[label = \"\\n"+s.toString()+"\\n\\n";
			String name = s.toString() + "[label = \""+s.toString();
			for (HMMState.StringProb sp : s.getNBestEmissions(20)) name += "\\n P(" + sp.str.replaceAll("\"", "\\\"")+ ") = " + sp.prob;
			
			out += name + "\"]\n";
			
			for (HMMState other : s.getTransitions().keySet()) {
				double transitionExponent = Utility.clamp(-s.getTransitions().get(other).getValue(), 0.0, RED_PROBABILITY)/RED_PROBABILITY;
				double hue = Utility.lerp(transitionExponent, 0.3, 0.0);
				double saturation = Utility.lerp(transitionExponent, 0.9, 0.6);
				double value = (transitionExponent == 0.0 ? 0.6 : 0.4);
				
				out += s + " -> " + other + " [color = \"" + hue + " " + saturation + " "+value+"\"]\n";
				//out += s + " -> " + other + " [label = \""+s.getTransitions().get(other)+"\"]\n";
			}
		}
		return "digraph G {\n"+out+"\n}\n";
	}
	
	private void pertubeStates(Random rng) {
		for (HMMState s : states) s.pertube(rng);
	}
	
	public void baumWelchOptimize(int numSteps, List<HMMTrainingDocument> trainingDocs, List<HMMTrainingDocument> testingDocs,
								  boolean clear, boolean printTiming, Function<HiddenMarkovModel, Double> scorer,
								  Random pertubator) {
		if (clear) normalizeProbabilities(trainingDocs);
		
		Timer t = new Timer();
		//double prevScore = scorer.apply(this);
		double prevScore = -1;
		if (printTiming) prevScore = scorer.apply(this);
		
		for (int i=0; i < numSteps; i++) {
			this.pertubeStates(pertubator);
			if (printTiming) System.out.println("Beginning training step");
			t.start();
			baumWelchStep(trainingDocs, testingDocs, printTiming);
			if (printTiming) t.stopAndPrintFuncTiming("Full Baum-Welch Step");
			normalizeTransitions();
			
			
			//do_stuff()?
			if (printTiming) {
				double postScore = scorer.apply(this);
				
				System.out.println(prevScore+" -> "+postScore);
				prevScore = postScore;
			}
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
		for (HMMState s : targetStateHeads) replaceConnection(s, true, new StateType[] {StateType.SUFFIX, StateType.TARGET});
		
		this.normalizeTransitions();
	}
	
	public void smoothAllEmissions(double totalUniqueWords, double totalWords) {
		for (HMMState s : states) {
			s.smoothEmissions(totalUniqueWords, totalWords);
		}
	}
	
	public HMMState addHead(StateType t) {
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
		
		return s;
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
		double correctTarget = 0.0;
		double guessedTarget = 0.0;
		double totalTarget = 0.0;
		double expectedTarget = 0.0;
		
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
					if (state.getType() == StateType.TARGET) correctTarget++;
					expectedTarget++;
				}
				else {
					
				}
				
				if (state.getType() == StateType.TARGET) guessedTarget++;
				
			}
		}
		
		double targetPrecision = correctTarget / guessedTarget;
		double targetRecall    = correctTarget / expectedTarget;
		
		double fScore = 2*(targetPrecision * targetRecall) / (targetPrecision + targetRecall);
		
		if (correctTarget == 0.0) fScore = 0.0;
		
		return fScore;
		
	}
	
	public static HiddenMarkovModel fromFile(String filename) {
		try {
			ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename));
			HiddenMarkovModel model = (HiddenMarkovModel) in.readObject();
			in.close();
			
			return model;
		} catch (Exception e) {
			System.out.println("Couldn't load HMM model "+filename+"\n"+e.getMessage());
			return null;
		}
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
		
		//if (capturing && capturingTokens.size() > 0) allCapturedTokens.add(concatFunc.apply(capturingTokens));

		/*
		if (extractSingle) {
			Set<ConvertedWord> out = new HashSet<ConvertedWord>();
			for (ConvertedWord s : allCapturedTokens) {
				out.add(s);
				break;
			}
			
			return out;
		}*/
		
		return allCapturedTokens;
	}
	
	/*
	public String toString() {
		return toGraphViz();
	}*/
	
	public List<Set<ConvertedWord>> extractAll(List<List<List<ConvertedWord>>> corpus, boolean extractSingle, Function<List<ConvertedWord>, ConvertedWord> concatFunc) {
		List<Set<ConvertedWord>> out = new ArrayList<Set<ConvertedWord>>();
		for (List<List<ConvertedWord>> doc : corpus) {
			out.add(extract(Utility.flatten(doc), extractSingle, concatFunc));
		}
		return out;
	}
	
}
