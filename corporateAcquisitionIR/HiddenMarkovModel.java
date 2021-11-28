package corporateAcquisitionIR;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	
	private HashMap<HMMState, LogProb> transitions;
	private HashSet<HMMState> parents;
	private HashMap<String, LogProb> emissions;
	private StateType type;
	
	public HMMState(StateType t) {
		transitions = new HashMap<HMMState, LogProb>();
		emissions = new HashMap<String, LogProb>();
		parents = new HashSet<HMMState>();
		type = t;
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
		return null;
	}
	
	public LogProb getProbabilityTo(HMMState next) {
		LogProb result = transitions.get(next);
		if (result == null) return new LogProb(0);
		return result;
	}
	
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
				Map<HMMState, LogProb> nextMap = new HashMap<HMMState, LogProb>();
				
				
				for (HMMState s : top.keySet()) {
					for (HMMState nextState : s.transitions.keySet()) {
						if (!isLegalTransition(s, nextState, curEvent)) continue;
						
						if (!nextMap.containsKey(nextState)) nextMap.put(s, new LogProb(1.0));
						
						nextMap.put(s, s.getEmissionProbability(curStr).add(nextMap.get(s)));
					}
				}
			}
		}
		
		LogProb out = new LogProb(1.0);

		return findTargetProbabilityOrSumIfTargetNull(sparseMap.get(sparseMap.size()-1), trg);
	}
	

	public LogProb getBackwardsProbability(HMMState trg, int totalWordsToProcess, List<List<String>> doc, List<List<TargetEvent>> events) {
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
				Map<HMMState, LogProb> nextMap = new HashMap<HMMState, LogProb>();
				
				
				for (HMMState s : top.keySet()) {
					for (HMMState nextState : s.transitions.keySet()) {
						if (!isLegalTransition(s, nextState, curEvent)) continue;
						
						if (!nextMap.containsKey(nextState)) nextMap.put(s, new LogProb(1.0));
						
						nextMap.put(s, s.getEmissionProbability(curStr).add(nextMap.get(s)));
					}
				}
			}
		}
		
		return findTargetProbabilityOrSumIfTargetNull(sparseMap.get(sparseMap.size()-1), trg);
	}
	private static Map<HMMState, LogProb> directionalProbabilityStep(Map<HMMState, LogProb> cur, Map<HMMState, Map<HMMState, LogProb>> allTransitions, 
																	 String curString, TargetEvent curEvent) {
		Map<HMMState, LogProb> outMap = new HashMap<HMMState, LogProb>();
		
		for (HMMState s : cur.keySet()) {
			Map<HMMState, LogProb> transitions = allTransitions.get(s);
			for (HMMState nextState : transitions.keySet()) {
				if (!isLegalTransition(s, nextState, curEvent)) continue;
				
				if (!outMap.containsKey(nextState)) outMap.put(s, new LogProb(1.0));
				
				outMap.put(s, s.getEmissionProbability(curString).add(outMap.get(s)));
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
}

public class HiddenMarkovModel {
	private HashMap<Integer, HMMState> states;
	private int maxID;
	HMMState start;
	
	private List<HMMState> backgroundStates;
	private List<HMMState> prefixStateHeads;
	private List<HMMState> suffixStateHeads;
	private List<HMMState> tagetStateHeads;
	
	public static final String PHI_TOKEN = ".~!PHI!~.";
	
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
	
	public HiddenMarkovModel() {
		states = new HashMap<Integer, HMMState>();
		maxID = 0;
	}
	
	public HiddenMarkovModel deepCopy() {
		HashMap<HMMState, HMMState> thisToCopy = new HashMap<HMMState, HMMState>();
		
		HiddenMarkovModel out = new HiddenMarkovModel();
		
		out.maxID = this.maxID;
		
		for (HMMState s : states.values()) {
			HMMState s2 = s.shallowCopy();
			thisToCopy.put(s, s2);
			
			out.addState(s2);
		}
		
		for (HMMState s2 : out.states.values()) {
			s2.replaceTransitionReferences(thisToCopy);
		}
		
		return out;
	}
	
	private void normalizeProbabilities() {
		for (HMMState s : states.values()) {
			s.normalizeProbability();
		}
	}
	
	public LogProb getForwardsProbability(List<List<String>> words, List<List<TargetEvent>> events) {
		return getForwardsProbabilityPartial(null, -1, words, events);
	}
	
	//public LogProb getBackwardsProbability(List<List<String>> words, List<List<TargetEvent>> events) {
	//	return new LogProb(1.0);
//	}
	
	//updates connections for leaves after each head node
	public void updateLeafConnections() {
		throw new IllegalStateException();
	}
	
	public LogProb getForwardsProbabilityPartial(HMMState i, int t, List<List<String>> words, List<List<TargetEvent>> events) {
		return start.getForwardsProbability(i, t, words, events);
	}
	
	public LogProb getBackwardsProbabilityPartial(HMMState i, int t, List<List<String>> words, List<List<TargetEvent>> events) {
		return start.getBackwardsProbability(i, t, words, events);
	}
	
	private LogProb xi(HMMState i, HMMState j, int t, LogProb observationProb, List<List<String>> words, List<List<TargetEvent>> events) {
		Map<Integer, String> flattenedIdxToString = new HashMap<Integer, String>();
		for (List<String> sentence : words) for (String s : sentence) {
			flattenedIdxToString.put(flattenedIdxToString.size(), s);
		}
		String tWord = flattenedIdxToString.get(t);
		
		LogProb numerator = getForwardsProbabilityPartial(i, t, words, events).add(getBackwardsProbabilityPartial(j, t+1, words, events));
		numerator = numerator.add(i.getProbabilityTo(j)).add(j.getEmissionProbability(tWord));
		
		LogProb denominator = observationProb;
		
		return numerator.sub(denominator);
	}
	
	public void trainBaumWelch(List<HMMTrainingDocument> trainingDocs, List<HMMTrainingDocument> testingDocs) {
		for (HMMTrainingDocument doc : trainingDocs) {
			List<List<TargetEvent>> targetEvents = new ArrayList<List<TargetEvent>>();
			
			//do any parsing necessary
			for (List<String> sentence : doc.text) {
				targetEvents.add(new ArrayList<TargetEvent>());
				for (int k=0; k<sentence.size(); k++) {
					targetEvents.get(targetEvents.size() - 1).add(TargetEvent.NO_EVENT);
				}
				
				for (int k=0; k < sentence.size(); k++) {
					List<TargetEvent> curEvents = targetEvents.get(targetEvents.size() - 1);
					
					boolean incorrect = k + doc.tokenizedExpectedResult.size() > sentence.size();
					for (int j=0; j+k < doc.tokenizedExpectedResult.size() && !incorrect; j++) {
						incorrect = !sentence.get(j+k).equals(doc.tokenizedExpectedResult.get(j));
					}
					
					if (incorrect) continue;

					curEvents.set(k, TargetEvent.ENTER);
					curEvents.set(k+doc.tokenizedExpectedResult.size(), TargetEvent.EXIT);
				}
			}
			
			//train
			
		}
	}
	
	private void addState(HMMState s) {
		states.put(maxID++, s);
	}
}
