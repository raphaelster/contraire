package corporateAcquisitionIR;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
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
												  Function<HiddenMarkovModel, Double> evaluator, ResultField f, Random pertubator) {
		final int TOTAL_STEPS = 10;
		final int MAX_NUM_CANDIDATES = 4;
		
		final int OPT_STEPS = 1;
		
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
				.map((evolution) -> {return Candidate.makeCandidate(evolution, OPT_STEPS, trainData, testData, evaluator, pertubator);})
				.collect(Collectors.toList());
			
			out.removeIf((s) -> {return s == null;});
			
			return out;
		};
		
		Candidate initialCandidate = new Candidate(new ArrayList<Mutator>(), OPT_STEPS, trainData, testData, evaluator, pertubator);
		
		List<Candidate> out = new ArrayList<Candidate>();
		out.add(initialCandidate);
		
		List<Candidate> keptCandidates = new ArrayList<Candidate>();

		
		for (int i=0; i<TOTAL_STEPS; i++) {
			
			List<Candidate> toMutate = new ArrayList<Candidate>();
			
			toMutate.addAll(out);
			keptCandidates.addAll(out);
			out.clear();
			

			for (Candidate c : keptCandidates) {
				c.save(f+"_"+c.score);
			}
			keptCandidates.clear();
			
			for (Candidate parent : toMutate) {
				List<List<Mutator>> mutations = parent.mutate();
				
				Timer mutationTimer = new Timer();
				mutationTimer.start();
				List<Candidate> evolutions = applyMutatorsParallel.apply(mutations);
				mutationTimer.stopAndPrintFuncTiming("Batch of mutations for "+parent);
				
				String gv = parent.model.toGraphViz();
				
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
			String gv = out.get(0).model.toGraphViz();
			System.out.println("Finished generation "+i+"/"+TOTAL_STEPS);
		}
		
		Collections.sort(keptCandidates, candidateComparator);
		
		String gv = keptCandidates.get(0).model.toGraphViz();
		keptCandidates.get(0).save(f+"_FINAL_"+keptCandidates.get(0).score);
		return keptCandidates.get(0).model;
	}
	
	
	

	static class Candidate {
		HiddenMarkovModel model;
		double score;
		List<Mutator> priorMutations;
		
		private Candidate(List<Mutator> mutations, int optSteps, List<HMMTrainingDocument> trainData, List<HMMTrainingDocument> testData,
				 Function<HiddenMarkovModel, Double> evaluator, Random pertubator) {
			priorMutations = mutations;
			
			model = HiddenMarkovModel.generateBasicModel();
			for (Mutator m : mutations) model = m.apply(model);
			model.baumWelchOptimize(optSteps, trainData, testData, true, false, evaluator, pertubator);
			score = evaluator.apply(model);
		}
		
		public static Candidate makeCandidate(List<Mutator> mutations, int optSteps, List<HMMTrainingDocument> trainData, List<HMMTrainingDocument> testData,
				 Function<HiddenMarkovModel, Double> evaluator, Random pertubator) {
			try {
				Candidate c = new Candidate(mutations, optSteps, trainData, testData, evaluator, pertubator);
				return c;
			}
			catch (Exception e) {
				return null;
			}
			
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
		
		private void save(String nameExcludingUniquePostfix, int tries) {
			final int MAX_TRIES = 4;
			
			try {
				String subFilename = nameExcludingUniquePostfix+"_"+System.nanoTime();
				subFilename = subFilename.replaceAll("\\.", "-");
				ObjectOutputStream objOut = new ObjectOutputStream(new FileOutputStream("./models/"+subFilename+".ser"));
				
				objOut.writeObject(model);
				
				objOut.close();
			}
			catch (Exception e) {
				System.out.println("Failed to save HMM:\n"+e.getMessage());
				if (tries < MAX_TRIES) {
					System.out.println("Trying again:");
					save(nameExcludingUniquePostfix, tries + 1);
				}
			}
		}
		
		public void save(String nameExcludingUniquePostfix) {
			save(nameExcludingUniquePostfix, 0);
			
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


