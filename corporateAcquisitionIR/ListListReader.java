package corporateAcquisitionIR;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

public class ListListReader extends Reader {
	String str;
	int currentIdx;

	public ListListReader(List<List<String>> file) {
		currentIdx = 0;
		
		StringBuilder builder = new StringBuilder();
		
		for (List<String> line : file) {
			for (String token : line) {
				builder.append(token);
				builder.append(" ");
			}
			builder.append("\n");
		}
		
		str = builder.toString();
	}
	
	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		off += currentIdx;
		
		int hi = off + len;
		

		if (off >= str.length()) {
			return -1;
		}
		
		if (hi > str.length()) {
			hi = str.length();
		}
		
		str.getChars(off, hi, cbuf, 0);
		
		currentIdx += hi - off;
		return hi - off;
	}

	@Override
	public void close() throws IOException {}

}
