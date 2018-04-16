import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class TrainingData {
	int[][] trainData;
	
	TrainingData(String file, int users, int movies) {
		trainData = new int[users][movies];
		try {
			FileReader fR = new FileReader(file);
			BufferedReader bR = new BufferedReader(fR);
			int row = 0;
			String rating;
			while ((rating = bR.readLine()) != null) {
				for (int i = 0; i < rating.length(); i += 2) {
					trainData[row][i/2] = rating.charAt(i) - '0';
				}
				row++;
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public int[][] getTrainData() {
//		for (int i = 0;  i < 3; i++) {
//			System.out.print("Train Data: ");
//			System.out.print(trainData[i][0]);
//			System.out.print(" ");
//			System.out.println(trainData[i][1]);
//		}
		return trainData;
	}
}
