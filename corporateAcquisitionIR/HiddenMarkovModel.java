package corporateAcquisitionIR;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

enum StateType {
	PREFIX,
	SUFFIX,
	TARGET,
	BACKGROUND
}
enum TargetEvent {
	ENTER,
	EXIT,
	NO_EVENT
}


/*
class HMMTransition {
	public LogProb prob;
	public HMMState next;
	
	HMMTransition(LogProb p, HMMState n) {
		prob = p; next = n;
	}
	
	public HMMTransition deepCopy() {
		return new HMMTransition(prob, next);
	}
}*/

class HMMState {
	private static final boolean IGNORE_CASE = false;
	private static int maxID = 0;
	
	private HashMap<HMMState, LogProb> transitions;
	private HashSet<HMMState> parents;
	private HashMap<String, LogProb> emissions;
	private StateType type;
	private int id;
	
	LogProb defaultEmissionProbability;
	
	public HMMState(StateType t) {
		transitions = new HashMap<HMMState, LogProb>();
		emissions = new HashMap<String, LogProb>();
		parents = new HashSet<HMMState>();
		defaultEmissionProbability = new LogProb(0);
		type = t;
		id = maxID++;
	}
	
	private String getToStringWithoutChildren() {
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
		}
		
		return out;
	}
	
	public String toString() {
		String out = getToStringWithoutChildren();
		out += " [";
		
		for (HMMState s : transitions.keySet()) {
			out += transitions.get(s) + " -> " + s.getToStringWithoutChildren() + ", ";
		}
		
		if (transitions.keySet().size() > 0) out = out.substring(0, out.length()-2);
		return out + "]";
	}
	
	public HMMState shallowCopy() {
		HMMState out = new HMMState(type);
		out.emissions.putAll(emissions);
		out.transitions.putAll(transitions);
		out.parents.addAll(parents);
		
		return out;
	}
	
	public LogProb getEmissionProbability(String word) {
		if (IGNORE_CASE) word = word.toLowerCase();
		
		if (emissions.containsKey(word)) return emissions.get(word);
		return defaultEmissionProbability;
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
	
	public List<HMMState> getOptimalSequence(List<List<String>> doc, List<List<TargetEvent>> events) {
		
		
		
		List<HMMState> out = null;
		//follow back pointers
		return out;
	}

	public ProbabilityTable getForwardsProbabilityTable(List<List<String>> doc, List<List<TargetEvent>> events) {
		List<Map<HMMState, LogProb>> sparseMap = new ArrayList<Map<HMMState, LogProb>>();
		
		//if (doc.size() == 0) return new LogProb(1);
		if (doc.size() == 0) return new ProbabilityTable(sparseMap);
		
		int totalWordsToProcess = 0;
		
		for (List<String> s : doc) totalWordsToProcess += s.size();
		
		
		Map<HMMState, LogProb> startNode = new HashMap<HMMState, LogProb>();
		startNode.put(this, this.getEmissionProbability(doc.get(0).get(0)));
		sparseMap.add(startNode);
	
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

				Map<HMMState, LogProb> nextMap = directionalProbabilityStep(top, transitions, curStr, curEvent);
				sparseMap.add(nextMap);
			}
		}

		return new ProbabilityTable(sparseMap);
		//return findTargetProbabilityOrSumIfTargetNull(sparseMap.get(sparseMap.size()-1), trg);
	}
	

	public ProbabilityTable getBackwardsProbabilityTable(Set<HMMState> allStates, List<List<String>> doc, List<List<TargetEvent>> events) {
		List<Map<HMMState, LogProb>> sparseMap = new ArrayList<Map<HMMState, LogProb>>();
		
		int stopAtIthWord = -1;
		
		if (doc.size() == 0) return new ProbabilityTable(sparseMap);
		
		
		Map<HMMState, LogProb> startNode = new HashMap<HMMState, LogProb>();
		for (HMMState s : allStates) startNode.put(s, new LogProb(1.0));
		sparseMap.add(startNode);
	
		int totalSize = 0;
		for (List<String> sentence : doc) totalSize += sentence.size();
		
		final int LAST_WORD = totalSize - stopAtIthWord;
		
		int wordsProcessed = 1;
		
		search_loop:
		for (int sIdx=doc.size()-1; sIdx >= 0; sIdx--) {
			List<String> sequence = doc.get(sIdx);
			
			for (int i=sequence.size()-1; i>=0; i--) {
				if (wordsProcessed == LAST_WORD) break search_loop;
				wordsProcessed++;
				
				String curStr = sequence.get(i);
				TargetEvent curEvent = events.get(sIdx).get(i);
				
				Map<HMMState, LogProb> top = sparseMap.get(sparseMap.size() - 1);
				
				Map<HMMState, Map<HMMState, LogProb>> transitions = new HashMap<HMMState, Map<HMMState, LogProb>>();
				
				for (HMMState s : top.keySet()) {
					transitions.put(s, new HashMap<HMMState, LogProb>());
					transitions.get(s).putAll(s.transitions);
				}

				Map<HMMState, LogProb> nextMap = directionalProbabilityStep(top, transitions, curStr, curEvent);
				sparseMap.add(nextMap);
			}
		}

		return new ProbabilityTable(sparseMap);
	}
	
	/*
	public LogProb getForwardsProbability(HMMState trg, int totalWordsToProcess, List<List<String>> doc, List<List<TargetEvent>> events) {
		List<Map<HMMState, LogProb>> sparseMap = new ArrayList<Map<HMMState, LogProb>>();
		
		if (doc.size() == 0) return new LogProb(1);
		
		
		Map<HMMState, LogProb> startNode = new HashMap<HMMState, LogProb>();
		startNode.put(this, this.getEmissionProbability(doc.get(0).get(0)));
		sparseMap.add(startNode);
	
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

				Map<HMMState, LogProb> nextMap = directionalProbabilityStep(top, transitions, curStr, curEvent);
				sparseMap.add(nextMap);
			}
		}

		return findTargetProbabilityOrSumIfTargetNull(sparseMap.get(sparseMap.size()-1), trg);
	}

	public LogProb getBackwardsProbability(HMMState trg, Set<HMMState> allStates, int stopAtIthWord, List<List<String>> doc, List<List<TargetEvent>> events) {
		List<Map<HMMState, LogProb>> sparseMap = new ArrayList<Map<HMMState, LogProb>>();
		
		if (doc.size() == 0) return new LogProb(1);
		
		
		Map<HMMState, LogProb> startNode = new HashMap<HMMState, LogProb>();
		for (HMMState s : allStates) startNode.put(s, new LogProb(1.0));
		sparseMap.add(startNode);
	
		int totalSize = 0;
		for (List<String> sentence : doc) totalSize += sentence.size();
		
		final int LAST_WORD = totalSize - stopAtIthWord;
		
		int wordsProcessed = 1;
		
		search_loop:
		for (int sIdx=doc.size()-1; sIdx >= 0; sIdx--) {
			List<String> sequence = doc.get(sIdx);
			
			for (int i=sequence.size()-1; i>=0; i--) {
				if (wordsProcessed == LAST_WORD) break search_loop;
				wordsProcessed++;
				
				String curStr = sequence.get(i);
				TargetEvent curEvent = events.get(sIdx).get(i);
				
				Map<HMMState, LogProb> top = sparseMap.get(sparseMap.size() - 1);
				
				Map<HMMState, Map<HMMState, LogProb>> transitions = new HashMap<HMMState, Map<HMMState, LogProb>>();
				
				for (HMMState s : top.keySet()) {
					transitions.put(s, new HashMap<HMMState, LogProb>());
					transitions.get(s).putAll(s.transitions);
				}

				Map<HMMState, LogProb> nextMap = directionalProbabilityStep(top, transitions, curStr, curEvent);
				sparseMap.add(nextMap);
			}
		}

		return findTargetProbabilityOrSumIfTargetNull(sparseMap.get(sparseMap.size()-1), trg);
	}*/
	
	private static Map<HMMState, LogProb> directionalProbabilityStep(Map<HMMState, LogProb> cur, Map<HMMState, Map<HMMState, LogProb>> allTransitions, 
																	 String curString, TargetEvent curEvent) {
		Map<HMMState, LogProb> outMap = new HashMap<HMMState, LogProb>();
		
		for (HMMState s : cur.keySet()) {
			Map<HMMState, LogProb> transitions = allTransitions.get(s);
			for (HMMState nextState : transitions.keySet()) {
				if (!isLegalTransition(s, nextState, curEvent)) continue;
				
				if (!outMap.containsKey(nextState)) outMap.put(nextState, new LogProb(1.0));
				
				outMap.put(nextState, nextState.getEmissionProbability(curString).add(transitions.get(nextState)).add(cur.get(s)));
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
		
		out.add(this);
		
		while (true) {
			HMMState top = out.get(out.size()-1);
			if (visited.contains(top)) throw new IllegalStateException("Detected same-type cycle (that isn't a self-cycle)");
			visited.add(top);
			
			List<HMMState> adjacentIgnoringSelfCycles = new ArrayList<HMMState>();
			for (HMMState s : top.transitions.keySet()) {
				if (s != top) adjacentIgnoringSelfCycles.add(s);
			}
			
			int sameTypeCount = 0;
			for (HMMState s : adjacentIgnoringSelfCycles) {
				if (s.type == top.type) sameTypeCount++;
			}
			
			if (sameTypeCount == 0) return out;
			if (sameTypeCount > 1) throw new IllegalStateException();
			
			for (HMMState s : adjacentIgnoringSelfCycles) {
				if (s.type == top.type) out.add(top);
				break;
			}
		}
	}
	
	public void replaceTransitionReferences(Map<HMMState, HMMState> oldToNew) {
		Set<HMMState> oldTransitions = transitions.keySet();
		
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
		emissions.put(word, p);
	}
	
	public void clearTransitions() {
		for (HMMState s : transitions.keySet()) {
			s.parents.remove(this);
		}
		transitions.clear();
	}
	
	public void normalizeProbability() {
		double sum = 0.0;
		for (LogProb p : transitions.values()) sum += p.getActualProbability();
		for (HMMState s : transitions.keySet()) {
			double newProb = transitions.get(s).getActualProbability() / sum;
			transitions.put(s, new LogProb(newProb));
		}
		
		sum = 0.0;
		for (LogProb p : emissions.values()) sum += p.getActualProbability();
		for (String s : emissions.keySet()) {
			double newProb = emissions.get(s).getActualProbability() / sum;
			emissions.put(s, new LogProb(newProb));
		}
	}
	public StateType getType() { return type; }
	
	public Map<HMMState, LogProb> getTransitions() { return transitions; }
	
	private static boolean isLegalTransition(HMMState start, HMMState end, TargetEvent event) {
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
	HMMState start;
	
	private List<HMMState> backgroundStates;
	private List<HMMState> prefixStateHeads;
	private List<HMMState> suffixStateHeads;
	private List<HMMState> targetStateHeads;
	
	public static final String PHI_TOKEN = ".~!PHI!~.";

	public HiddenMarkovModel() {
		states = new HashSet<HMMState>();

		backgroundStates = new ArrayList<HMMState>();
		prefixStateHeads = new ArrayList<HMMState>();
		suffixStateHeads = new ArrayList<HMMState>();
		targetStateHeads = new ArrayList<HMMState>();
		
		start = new HMMState(StateType.BACKGROUND);
		addState(start);
	}
	
	void lengthen(HMMState trg) {
		HMMState newState = new HMMState(trg.getType());
		
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
	
	
	public HiddenMarkovModel deepCopy() {
		HashMap<HMMState, HMMState> thisToCopy = new HashMap<HMMState, HMMState>();
		
		HiddenMarkovModel out = new HiddenMarkovModel();
		
		for (HMMState s : states) {
			HMMState s2 = s.shallowCopy();
			thisToCopy.put(s, s2);
			
			out.addState(s2);
		}
		
		for (HMMState s2 : out.states) {
			s2.replaceTransitionReferences(thisToCopy);
		}
		
		return out;
	}
	
	private void normalizeProbabilities(List<HMMTrainingDocument> trainingCorpus) {
		normalizeTransitions();
		
		normalizeEmissions(trainingCorpus);
	}
	
	private void normalizeTransitions() {
		for (HMMState s : states) {
			s.normalizeProbability();
		}
	}
	
	private void normalizeEmissions(List<HMMTrainingDocument> trainingCorpus) {
		Map<String, Double> vocabCount = new HashMap<String, Double>();
		
		for (HMMTrainingDocument doc : trainingCorpus) {
			for (List<String> sentence : doc.text) for (String word : sentence) {
				word = word.toLowerCase();
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
	
	/*public LogProb getForwardsProbability(List<List<String>> words, List<List<TargetEvent>> events) {
		return getForwardsProbabilityPartial(null, -1, words, events);
	}*/
	
	//public LogProb getBackwardsProbability(List<List<String>> words, List<List<TargetEvent>> events) {
	//	return new LogProb(1.0);
//	}
	
	public HMMState.ProbabilityTable getForwardsProbabilityTable(List<List<String>> words, List<List<TargetEvent>> events) {
		return start.getForwardsProbabilityTable(words, events);
	}
	

	public HMMState.ProbabilityTable getBackwardsProbabilityTable(List<List<String>> words, List<List<TargetEvent>> events) {
		return start.getBackwardsProbabilityTable(states, words, events);
	}
	
	
	/*public LogProb getForwardsProbabilityPartial(HMMState i, int t, List<List<String>> words, List<List<TargetEvent>> events) {
		return start.getForwardsProbability(i, t, words, events);
	}
	
	public LogProb getBackwardsProbabilityPartial(HMMState i, int t, List<List<String>> words, List<List<TargetEvent>> events) {
		return start.getBackwardsProbability(i, states, t, words, events);
	}*/
	
	/*
	private LogProb xiDenominator(int t, List<List<String>> words, List<List<TargetEvent>> events) {
		double sum = 0.0;
		for (HMMState s : states) {
			for (HMMState k : s.getTransitions().keySet()) {
				LogProb cur = xi(s, k, t, new LogProb(1.0), words, events);
				sum += cur.getActualProbability();
			}
		}
		
		return new LogProb(sum);
	}*/
	
	private LogProb xi( HMMState i, HMMState j, int t, LogProb denom, List<List<String>> words, List<List<TargetEvent>> events,
						HMMState.ProbabilityTable forwards, HMMState.ProbabilityTable backwards) {
		/*Map<Integer, String> flattenedIdxToString = new HashMap<Integer, String>();
		for (List<String> sentence : words) for (String s : sentence) {
			flattenedIdxToString.put(flattenedIdxToString.size(), s);
		}
		String wordAtJ = flattenedIdxToString.get(t+1);*/
		
		String wordAtJ = "";
		int totalIdx = 0;
		
		outer_loop:
		for (int a=0; a<words.size(); a++) for (int b=0; b<words.get(a).size(); b++) {
			if (totalIdx == t) {
				wordAtJ = words.get(a).get(b);
				break outer_loop;
			}
			
			totalIdx++;
		}
		
		LogProb numerator = forwards.find(i,  t);
		numerator = numerator.add(backwards.find(j, t+1));
		//LogProb numerator = getForwardsProbabilityPartial(i, t, words, events);
		//numerator = numerator.add(getBackwardsProbabilityPartial(j, t+1, words, events));
		numerator = numerator.add(i.getProbabilityTo(j)).add(j.getEmissionProbability(wordAtJ));
		
		return numerator.sub(denom);
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
	/*
	private LogProb gammaDenominator(HMMState i, int t, List<List<String>> words, List<List<TargetEvent>> events) {
		double sum = 0.0;
		
		for (HMMState s : states) {
			LogProb result = getForwardsProbabilityPartial(s, t, words, events);
			result = result.add(getBackwardsProbabilityPartial(s, t, words, events));
			
			sum += result.getActualProbability();
		}
		
		return new LogProb(sum);
		
	}*/
	private LogProb gamma(HMMState i, int t, LogProb den, List<List<String>> words, List<List<TargetEvent>> events,
						  HMMState.ProbabilityTable forwards, HMMState.ProbabilityTable backwards) {
		//return getForwardsProbabilityPartial(i, t, words, events).add(getBackwardsProbabilityPartial(i, t, words, events));
		double sum = 0.0;
		for (HMMState s : i.getTransitions().keySet()) {
			sum += xi(i, s, t, den, words, events, forwards, backwards).getActualProbability();
		}
		return new LogProb(sum);
	}
	
	private class XiInput {
		HMMState i;
		HMMState j;
		int t;
		List<List<String>> words;
		List<List<TargetEvent>> events;
	}
	
	private class XiGammaMemoizeContainer {
		//Map<>
	}
	
	/*
	private LogProb xiMemoize(XiGammaMemoizeContainer c, HMMState i, HMMState j, int t, List<List<String>> words, List<List<TargetEvent>> events) {
		return xi(i, j, t, new LogProb(1.0), words, events);
	}
	
	private LogProb gammaMemoize(XiGammaMemoizeContainer c, HMMState i, int t, List<List<String>> words, List<List<TargetEvent>> events) {
		double sum = 0.0;
		for (HMMState s : i.getTransitions().keySet()) {
			sum += xiMemoize(c, i, s, t, words, events).getActualProbability();
		}
		return new LogProb(sum);
	}*/
	
	
	
	private void baumWelchStep(List<HMMTrainingDocument> trainingDocs, List<HMMTrainingDocument> testingDocs) {
		normalizeTransitions();
		
		Map<StatePair, Double> transitionProbNumerator = new HashMap<StatePair, Double>();
		Map<HMMState, Map<String, Double>> emissionProbNumerator = new HashMap<HMMState, Map<String, Double>>();

		double commonDenominator = 0.0;

		
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
		
		for (HMMTrainingDocument doc : trainingDocs) {
			int totalSize = 0;
			List<List<TargetEvent>> targetEvents = new ArrayList<List<TargetEvent>>();
		
			parseTimer.start();
			
			//do any parsing necessary
			for (List<String> sentence : doc.text) {
				totalSize += sentence.size();
				
				targetEvents.add(new ArrayList<TargetEvent>());
				for (int k=0; k<sentence.size(); k++) {
					targetEvents.get(targetEvents.size() - 1).add(TargetEvent.NO_EVENT);
				}
				
				for (List<String> result : doc.tokenizedExpectedResults) {
					for (int k=0; k < sentence.size(); k++) {
						List<TargetEvent> curEvents = targetEvents.get(targetEvents.size() - 1);
						
						boolean incorrect = k + result.size() >= sentence.size();
						for (int j=0; j+k < result.size() && !incorrect; j++) {
							incorrect = !sentence.get(j+k).equals(result.get(j));
						}
						
						if (incorrect) continue;
	
						curEvents.set(k, TargetEvent.ENTER);
						curEvents.set(k+result.size(), TargetEvent.EXIT);
					}
				}
			}
			parseTimer.pause();
			
			tableTimer.start();
			HMMState.ProbabilityTable forwardsTable  = this.getForwardsProbabilityTable(doc.text, targetEvents);
			HMMState.ProbabilityTable backwardsTable = this.getBackwardsProbabilityTable(doc.text, targetEvents);
			tableTimer.pause();
			
			trainTimer.start();
			//train
			for (HMMState i : states) {
				transitionNumTimer.start();
				for (HMMState j : i.getTransitions().keySet()) {
				
					double aNum = 0.0;
					for (int t=1; t < totalSize - 1; t++) {
						xiTimer.start();
						aNum += xi(i, j, t, new LogProb(1.0), doc.text, targetEvents, forwardsTable, backwardsTable).getActualProbability();
						xiTimer.pause();
					}
					
					StatePair key = new StatePair(i, j);
					transitionProbNumerator.put(key, aNum + transitionProbNumerator.get(key));
				}
				transitionNumTimer.pause();

				emissionNumTimer.start();
				int idx = 0;
				///todo: this can be parallelized pretty easily, just a mapping
				for (List<String> sentence : doc.text) for (String str : sentence) {
					
					if (!emissionProbNumerator.containsKey(i)) emissionProbNumerator.put(i,  new HashMap<String, Double>());
					
					Map<String, Double> iWordProbs = emissionProbNumerator.get(i);
					if (!iWordProbs.containsKey(str)) iWordProbs.put(str, 0.0);
					
					iWordProbs.put(str, gamma(i, idx, new LogProb(1.0), doc.text, targetEvents, forwardsTable, backwardsTable).getActualProbability()
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
					commonDenominator += gamma(i, t, new LogProb(1.0), doc.text, targetEvents, forwardsTable, backwardsTable).getActualProbability();
					gammaTimer.pause();
				}
			}
			denTimer.pause();
			trainTimer.pause();
		}
		

		parseTimer.stopAndPrintFuncTiming("Baum-Welch parsing");
		trainTimer.stopAndPrintFuncTiming("Baum-Welch train");
		xiTimer.stopAndPrintFuncTiming("Baum-Welch xi step for num");
		gammaTimer.stopAndPrintFuncTiming("Baum-Welch gamma step for den");
		
		transitionNumTimer.stopAndPrintFuncTiming("BW transition numerator calc");
		emissionNumTimer.stopAndPrintFuncTiming("BW emission numerator calc");
		denTimer.stopAndPrintFuncTiming("BW denominator calc");
		
		tableTimer.stopAndPrintFuncTiming("Calculating tables for xi, gamma");
		
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
		
		updateTimer.stopAndPrintFuncTiming("Baum-Welch update");
		
		
	}
	
	public void baumWelchOptimize(int numSteps, List<HMMTrainingDocument> trainingDocs, List<HMMTrainingDocument> testingDocs) {
		normalizeProbabilities(trainingDocs);
		
		Timer t = new Timer();
		while (true) {
			System.out.println("Beginning training step");
			t.start();
			baumWelchStep(trainingDocs, testingDocs);
			t.stopAndPrintFuncTiming("Baum-Welch Step");
			normalizeTransitions();
		}
	}
	
	private void replaceConnection(HMMState head, boolean addSelfCycles, StateType[] endConnections) {
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
		for (StateType t : endConnections) {
			switch (t) {
			case PREFIX:
				tail.addAllChildren(prefixStateHeads, new LogProb(1.0));
				break;
				
			case SUFFIX:
				tail.addAllChildren(suffixStateHeads, new LogProb(1.0));
				break;
				
			case TARGET:
				tail.addAllChildren(targetStateHeads, new LogProb(1.0));
				break;
				
			case BACKGROUND:
				tail.addAllChildren(backgroundStates, new LogProb(1.0));
				break;
				
			default:
				throw new IllegalArgumentException();
			}
		}
	}
	
	public void replaceAllConnections() {
		replaceConnection(start, true, new StateType[]{StateType.TARGET, StateType.PREFIX});
		
		for (HMMState s : prefixStateHeads) replaceConnection(s, false, new StateType[] {StateType.TARGET});
		for (HMMState s : suffixStateHeads) replaceConnection(s, false, new StateType[] {StateType.BACKGROUND, StateType.PREFIX});
		for (HMMState s : backgroundStates) replaceConnection(s, true, new StateType[] {StateType.PREFIX});
		for (HMMState s : targetStateHeads) replaceConnection(s, true, new StateType[] {StateType.SUFFIX});
		
		this.normalizeTransitions();
	}
	
	public void addHead(HMMState s) {
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
			
		default:
			throw new IllegalArgumentException();
		}
	}
	
	public static HiddenMarkovModel generateBasicModel() {
		HiddenMarkovModel out = new HiddenMarkovModel();
		
		out.addHead(new HMMState(StateType.PREFIX));
		out.addHead(new HMMState(StateType.SUFFIX));
		out.addHead(new HMMState(StateType.BACKGROUND));
		out.addHead(new HMMState(StateType.TARGET));

		out.replaceAllConnections();
		
		return out;		
	}
	
	
	private void addState(HMMState s) {
		states.add(s);
	}
}
