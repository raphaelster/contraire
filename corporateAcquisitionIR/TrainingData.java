package corporateAcquisitionIR;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


class TrainingData implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1785894868679539736L;
	
	public Map<String, ExtractionPattern> acquiredBusinessEdges;
	//public List<ExtractionPattern> acquiredBusinessEdges;
	public Map<String, ExtractionPattern> acquiredLocationEdges;
	public Map<String, ExtractionPattern> dollarAmountEdges;
	public Map<String, ExtractionPattern> statusEdges;
	public Map<String, ExtractionPattern> purchaseEdges;
	public Map<String, ExtractionPattern> acquiredEdges;
	public Map<String, ExtractionPattern> sellerEdges;
	
	public TrainingData(List<ExtractionPattern> acqbus, List<ExtractionPattern> acqloc, List<ExtractionPattern> dolAmt, List<ExtractionPattern> status,
						List<ExtractionPattern> pur, List<ExtractionPattern> acq, List<ExtractionPattern> sell) {

		acquiredBusinessEdges 	= new HashMap<String, ExtractionPattern>();
		acquiredLocationEdges 	= new HashMap<String, ExtractionPattern>();
		dollarAmountEdges 		= new HashMap<String, ExtractionPattern>();
		statusEdges 			= new HashMap<String, ExtractionPattern>();
		purchaseEdges 			= new HashMap<String, ExtractionPattern>();
		acquiredEdges 			= new HashMap<String, ExtractionPattern>();
		sellerEdges 			= new HashMap<String, ExtractionPattern>();
		
		addAllToMap(acquiredBusinessEdges, 	acqbus);
		addAllToMap(acquiredLocationEdges, 	acqloc);
		addAllToMap(dollarAmountEdges, 		dolAmt);
		addAllToMap(statusEdges, 			status);
		addAllToMap(purchaseEdges, 			pur);
		addAllToMap(acquiredEdges, 			acq);
		addAllToMap(sellerEdges, 			sell);
	}
	
	public TrainingData() {
		this(new ArrayList<ExtractionPattern>(), new ArrayList<ExtractionPattern>(), new ArrayList<ExtractionPattern>(), new ArrayList<ExtractionPattern>(),
		     new ArrayList<ExtractionPattern>(), new ArrayList<ExtractionPattern>(), new ArrayList<ExtractionPattern>());

	}
	
	private void addAllToMap(Map<String, ExtractionPattern> map, Collection<ExtractionPattern> list) {
		for (ExtractionPattern e : list) if (!map.containsKey(e.getIdentifier())) map.put(e.getIdentifier(), e);
	}
	
	public void add(TrainingData other) {
		addAllToMap(acquiredBusinessEdges, 	other.acquiredBusinessEdges.values());
		addAllToMap(acquiredLocationEdges, 	other.acquiredLocationEdges.values());
		addAllToMap(dollarAmountEdges, 		other.dollarAmountEdges.values());
		addAllToMap(statusEdges, 			other.statusEdges.values());
		addAllToMap(purchaseEdges, 			other.purchaseEdges.values());
		addAllToMap(acquiredEdges, 			other.acquiredEdges.values());
		addAllToMap(sellerEdges, 			other.sellerEdges.values());
	}
	
	private void eraseInMap(Map<String, ExtractionPattern> map, double prob, double min) {
		Map<String, ExtractionPattern> newMap = new HashMap<String, ExtractionPattern>();
		List<String> keysToRemove = new ArrayList<String>();
		
		for (String str : map.keySet()) {
			double actualCount = map.get(str).totalCount;
			double actualProb = map.get(str).correctCount / actualCount;
			
			if (!(actualCount >= min && actualProb >= prob)) keysToRemove.add(str);
		}
		
		for (String str : keysToRemove) map.remove(str);
	}
	
	int size() {
		return 	acquiredBusinessEdges.size() + acquiredLocationEdges.size() + dollarAmountEdges.size()
				+ statusEdges.size() + purchaseEdges.size() + acquiredEdges.size() + sellerEdges.size();
	}
	
	public void eraseBadRules(double probability, double minCount) {
		eraseInMap(acquiredBusinessEdges, 	probability, minCount);
		eraseInMap(acquiredLocationEdges, 	probability, minCount);
		eraseInMap(dollarAmountEdges, 		probability, minCount);
		eraseInMap(statusEdges, 			probability, minCount);
		eraseInMap(purchaseEdges, 			probability, minCount);
		eraseInMap(acquiredEdges, 			probability, minCount);
		eraseInMap(sellerEdges, 			probability, minCount);
	}
}