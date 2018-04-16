import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestData {
	private Map<Integer, ArrayList<int[]>> rating;
	private Map<Integer, ArrayList<Integer>> prediction;
	
	TestData() {
		rating = new LinkedHashMap<Integer, ArrayList<int[]>>();
		prediction = new LinkedHashMap<Integer, ArrayList<Integer>>();
	}
	
	public void loadTestData(String file) {
		try {
			rating = new LinkedHashMap<Integer, ArrayList<int[]>>();
			prediction = new LinkedHashMap<Integer, ArrayList<Integer>>();
			FileReader fR = new FileReader(file);
			BufferedReader bR = new BufferedReader(fR);
			int size = 0;
			int size1 = 0;
			String raw;
			int userId = -1;
			while ((raw = bR.readLine()) != null) {
				String[] data = raw.trim().split(" ");
				int id = Integer.parseInt(data[0]);
				if (userId != id) {
					rating.put(id, new ArrayList<int[]>());
					prediction.put(id, new ArrayList<Integer>());
					userId = id;
				}
				if (data[2].equals("0")) {
					prediction.get(userId).add(Integer.parseInt(data[1]));
					size++;
				} else {
					rating.get(userId).add(new int[]{Integer.parseInt(data[1]), Integer.parseInt(data[2])});
					size1++;
				}				
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	public Map<Integer, ArrayList<int[]>> getRaing() {
		return rating;
	}
	
	public Map<Integer, ArrayList<Integer>> getPrediction() {
		return prediction;
	}
}
