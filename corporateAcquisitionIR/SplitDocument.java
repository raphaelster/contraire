package corporateAcquisitionIR;

import java.util.ArrayList;
import java.util.List;

public class SplitDocument<T> {
	public List<List<T>> doc;
	public List<T> flattened;
	
	public SplitDocument(List<List<T>> d) {
		doc = d;
		flattened = new ArrayList<T>();
		
		for (List<T> sentence : doc) flattened.addAll(sentence);
	}
}
