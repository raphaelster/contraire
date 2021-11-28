package corporateAcquisitionIR;

public class Timer {
	private Long startTime;
	private Long storedTime;
	
	public Timer() {
		startTime = null;
		storedTime = 0l;
	}
	
	public void start() {
		startTime = System.currentTimeMillis(); 
	}
	
	private long stopIgnoreStored() {
		if (startTime == null) return 0l;
		
		long endTime = System.currentTimeMillis();
		long tmp = startTime;
		startTime = null;
		
		
		return endTime - tmp; 
	}
	
	public long stop() {
		long tmp = storedTime;
		storedTime = 0l;
		
		return tmp + stopIgnoreStored();
	}
	
	public void pause() {
		storedTime += stopIgnoreStored();
		startTime = null;
	}
	
	public void stopAndPrint(String prefix, String suffix) {
		System.out.print(prefix);
		System.out.print(stop());
		System.out.print(suffix);;
	}
	
	public void stopAndPrintFuncTiming(String funcName) {
		stopAndPrint("Timing for \""+funcName+"\": ", "ms\n");
	}
}
