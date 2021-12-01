package corporateAcquisitionIR;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LogProb implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3036461688922925830L;
	double value;
	
	public LogProb(double normalProb) {
		value = Math.log(normalProb)/Math.log(2);
	}
	
	public double getValue() { return value; }
	
	public double getActualProbability() { return Math.pow(2, value); }
	
	public LogProb add(LogProb other) {
		LogProb out = new LogProb(1.0);
		out.value += this.value;
		out.value += other.value;
		
		return out;
	}
	
	public static LogProb makeFromExponent(double exp) {
		LogProb out = new LogProb(0.0);
		out.value = exp;
		return out;
	}

	public static LogProb safeSum(Collection<LogProb> list) {
		LogProb max = new LogProb(0.0);
		
		for (LogProb l : list) if (l.getValue() > max.getValue()) max = l;
		
		if (Double.isInfinite(max.getValue())) return new LogProb(0);
		
		double sum = 0.0;
		for (LogProb l : list) sum += l.sub(max).getActualProbability();
		
		return new LogProb(sum).add(max);
	}
	
	public static LogProb safeSum(LogProb a, LogProb b) {
		LogProb max = a;
		
		if (b.getValue() > a.getValue()) max = b;
		
		if (Double.isInfinite(max.getValue())) return new LogProb(0);
		
		double sum = a.sub(max).getActualProbability() + b.sub(max).getActualProbability();
		
		
		return new LogProb(sum).add(max);
	}

	public static List<LogProb> additiveSmoothing(List<LogProb> in, LogProb smooth) {
		LogProb smoothTotalFactor = new LogProb(in.size());
		
		List<LogProb> out = new ArrayList<LogProb>();
		
		LogProb total = LogProb.safeSum(in);
		
		for (LogProb p : in) {
			out.add(p.add(smooth).sub(total).sub(smoothTotalFactor));
		}
		
		return out;
		
	}
	
	public LogProb sub(LogProb other) {
		LogProb out = new LogProb(1.0);
		out.value += this.value;
		out.value -= other.value;
		
		return out;
	}
	
	public String toString() {
		return "2^" + String.format("%.2f", value);
	}
}
