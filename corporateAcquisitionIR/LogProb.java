package corporateAcquisitionIR;

public class LogProb {
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
