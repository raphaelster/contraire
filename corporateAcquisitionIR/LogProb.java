package corporateAcquisitionIR;

public class LogProb {
	double value;
	
	public LogProb(double normalProb) {
		value = Math.log(normalProb)/Math.log(2);
	}
	
	
	public double getValue() { return value; }
	
	public double getActualProbability() { return Math.pow(2, value); }
}
