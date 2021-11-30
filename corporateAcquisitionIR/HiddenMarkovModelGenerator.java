package corporateAcquisitionIR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HiddenMarkovModelGenerator extends HiddenMarkovModel {


	static BiFunction<HiddenMarkovModel, MutatorInput, HiddenMarkovModel> mutateLengthen = (model, input) -> {
		List<HMMState> list = model.getHeadListForType(input.type);
		model.lengthen(list.get(input.idx));
		return model;
	};
	static BiFunction<HiddenMarkovModel, MutatorInput, HiddenMarkovModel> mutateSplit = (model, input) -> {
		List<HMMState> list = model.getHeadListForType(input.type);
		model.split(list.get(input.idx));
		return model;
	};
	
	

	public static HiddenMarkovModel evolveOptimal(List<HMMTrainingDocument> trainData, List<HMMTrainingDocument> testData,
												  Function<HiddenMarkovModel, Double> evaluator) {
		final int TOTAL_STEPS = 5;
		final int MAX_NUM_CANDIDATES = 4;
		
		final int OPT_STEPS = 3;
		
		Comparator<Candidate> candidateComparator = (a, b) -> {
			if (a.score < b.score) return 1;
			if (a.score > b.score) return -1;
			return 0;
		};
		
		Function<List<List<Mutator>>, List<Candidate>> applyMutatorsParallel = (mutatorList) -> {
			List<Candidate> out;
			
			Timer mutationTimer = new Timer();
			out = mutatorList.parallelStream()
			//out = mutatorList.stream()
				.map((evolution) -> {return new Candidate(evolution, OPT_STEPS, trainData, testData, evaluator);})
				.collect(Collectors.toList());
			
			return out;
		};
		
		Candidate initialCandidate = new Candidate(new ArrayList<Mutator>(), OPT_STEPS, trainData, testData, evaluator);
		
		List<Candidate> out = new ArrayList<Candidate>();
		out.add(initialCandidate);
		
		List<Candidate> keptCandidates = new ArrayList<Candidate>();
		
		for (int i=0; i<TOTAL_STEPS; i++) {
			List<Candidate> toMutate = new ArrayList<Candidate>();
			
			toMutate.addAll(out);
			keptCandidates.addAll(out);
			out.clear();
			
			for (Candidate parent : toMutate) {
				List<List<Mutator>> mutations = parent.mutate();
				
				Timer mutationTimer = new Timer();
				mutationTimer.start();
				List<Candidate> evolutions = applyMutatorsParallel.apply(mutations);
				mutationTimer.stopAndPrintFuncTiming("Batch of mutations for "+parent);
				
				/*for (List<Mutator> evolutionPath : mutations) {
					out.add(new Candidate(evolutionPath, OPT_STEPS, trainData, testData, evaluator));
				}*/
				for (Candidate child : evolutions) out.add(child);
				
			}
			
			Collections.sort(out, candidateComparator);
			List<Candidate> goodCandidates = new ArrayList<Candidate>();
			for (int k=0; k<MAX_NUM_CANDIDATES; k++) goodCandidates.add(out.get(k));
			out = goodCandidates;
			keptCandidates.add(out.get(0));
		}
		
		Collections.sort(out, candidateComparator);
		
		return out.get(0).model;
	}
	
	
	

	static class Candidate {
		HiddenMarkovModel model;
		double score;
		List<Mutator> priorMutations;
		
		public Candidate(List<Mutator> mutations, int optSteps, List<HMMTrainingDocument> trainData, List<HMMTrainingDocument> testData,
				 Function<HiddenMarkovModel, Double> evaluator) {
			priorMutations = mutations;
			
			model = HiddenMarkovModel.generateBasicModel();
			for (Mutator m : mutations) model = m.apply(model);
			model.baumWelchOptimize(optSteps, trainData, testData, true, false);
			score = evaluator.apply(model);
		}
		
		private List<Mutator> getPossibleMutations(List<HMMState> category) {
			List<Mutator> out = new ArrayList<Mutator>();
			StateType t = category.get(0).getType();

			for (int i=0; i<category.size(); i++) {
				MutatorInput in = new MutatorInput(i, t);
				
				out.add(new Mutator(mutateSplit, in));
				if (t != StateType.BACKGROUND && t != StateType.TARGET) out.add(new Mutator(mutateLengthen, in));
			}

			return out;
		}
		
		private List<List<Mutator>> combinePossibilitiesWithPrior(List<Mutator> possibilities, List<Mutator> prior) {
			List<List<Mutator>> out = new ArrayList<List<Mutator>>();
			
			for (Mutator choice : possibilities) {
				List<Mutator> route = new ArrayList<Mutator>();
				route.addAll(prior);
				route.add(choice);
				
				out.add(route);
			}
			
			return out;
		}
		
		public List<List<Mutator>> mutate() {
			List<List<Mutator>> out = new ArrayList<List<Mutator>>();
			
			StateType[] mutableTypes = {StateType.BACKGROUND, StateType.PREFIX, StateType.SUFFIX, StateType.TARGET};
			
			for (StateType t : mutableTypes) {
				List<HMMState> category = model.getHeadListForType(t);
				List<Mutator> possibilities = getPossibleMutations(category);
				out.addAll(combinePossibilitiesWithPrior(possibilities, priorMutations));
			}
			
			return out;
		}
		
		public String toString() {
			return score + " " + model;
		}
	}
}


class MutatorInput {
	int idx;
	StateType type;
	
	public MutatorInput(int i, StateType t) {
		idx = i;
		type = t;
	}
}


class Mutator implements Function<HiddenMarkovModel, HiddenMarkovModel> {
	BiFunction<HiddenMarkovModel, MutatorInput, HiddenMarkovModel> mutationFunc;
	MutatorInput input;
	
	public Mutator(BiFunction<HiddenMarkovModel, MutatorInput, HiddenMarkovModel> func, MutatorInput in) {
		input = in;
		mutationFunc = func;
	}

	@Override
	public HiddenMarkovModel apply(HiddenMarkovModel model) {
		return mutationFunc.apply(model, input);
	}
}


