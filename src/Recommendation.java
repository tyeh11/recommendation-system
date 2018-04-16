import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;

public class Recommendation {
	TestData testData;
	TrainingData trainData;
	int users;
	int movies;
	int[][] trainingData;
	double[] trainAVG;
	double[] IUF;
	double[] IUFAvg;
	double ibCS[][];
	double ibACS[][];
	double ibPC[][];
	Map<Integer, ArrayList<int[]>> rating; //key: userID, int[0]: movie id, int[1]: rate
	Map<Integer, ArrayList<Integer>> prediction;
	
	Recommendation(int users, int movies) {
		this.users = users;
		this.movies = movies;
		trainData = new TrainingData("train.txt", users, movies);
		testData = new TestData();
		trainingData =  trainData.getTrainData();
		
		IUF = new double[movies];
		for (int i = 0; i < movies; i++) {
			int rateCount = 0;
			for (int j = 0; j < users; j++) {
				if (trainingData[j][i] != 0) {
					rateCount++;
				}
			}
			if (rateCount != 0) {
				IUF[i] = Math.log10((double)users/(double)rateCount);
			}
		}
		
		trainAVG = new double[users];
		IUFAvg = new double[users];
		for (int i = 0; i < users; i++) {
			int rateCount = 0;
			for (int j = 0; j < movies; j++) {
				if (trainingData[i][j] != 0) {
					trainAVG[i] += trainingData[i][j];
					IUFAvg[i] += trainingData[i][j]*IUF[j];
					rateCount++;
				}
			}
			if (rateCount != 0) {
				trainAVG[i] /= rateCount; 
				IUFAvg[i] /= rateCount;
			}
		}
	}
	
	public void prepareTestData(String input) {
		testData.loadTestData(input);
		rating = testData.getRaing();
		prediction = testData.getPrediction();
	}
	
	public Map<Integer, ArrayList<int[]>> predictUBCS(int topK, boolean usingIUF, boolean usingCaseAmp) {
		long startTime = System.nanoTime();
		Map<Integer, ArrayList<int[]>> result = new LinkedHashMap<Integer, ArrayList<int[]>>();
		for (Entry<Integer, ArrayList<int[]>> users: rating.entrySet()) {
			ArrayList<int[]> ratedMovie = users.getValue();
			double userAVG = 0;
			for (int[] rate: ratedMovie) {
				userAVG += rate[1];
			}
			userAVG /= ratedMovie.size();
			//cosine similarty
			PriorityQueue<Weight> pQ = new PriorityQueue<Weight>(200, new Comparator<Weight>(){
				public int compare(Weight c1, Weight c2) {
					if (c2.weight > c1.weight) return 1;
					if (c2.weight < c1.weight) return -1;
					return 0;
				}
			});
			
			weightUBCS(ratedMovie, pQ, usingIUF);
			
			//predict
			result.put(users.getKey(), new ArrayList<int[]>());
			for (Integer rMovie: prediction.get(users.getKey())) {
				double sum = 0, weightAVG = 0;
				Iterator<Weight> itr = pQ.iterator();
				for (int i = 0; i < topK; i++){
					if (itr.hasNext()) {
						Weight cs = itr.next();
						if (usingCaseAmp) {
							cs.weight *= Math.pow(Math.abs(cs.weight), 1.5);
						}
						if (trainingData[cs.ID-1][rMovie-1] == 0) {
							i--;
							continue;
						}
						sum += cs.weight;
						if (usingIUF) {
							weightAVG += (cs.weight * trainingData[cs.ID-1][rMovie-1] *IUF[rMovie-1]);
						} else {
							weightAVG += (cs.weight * trainingData[cs.ID-1][rMovie-1]);
						}
					} else break;
				}
				if (sum != 0) {
					result.get(users.getKey()).add(new int[] {rMovie, (int) (weightAVG/sum)});
				} else {
					result.get(users.getKey()).add(new int[] {rMovie, (int) userAVG});
				}
			}
		}
		//output result
		long endTime   = System.nanoTime();
		long totalTime = endTime - startTime;
		System.out.println("UBCS time: " + totalTime);
		return result;
	}
	
	public Map<Integer, ArrayList<int[]>> predictUBPC(int topK, boolean usingIUF, boolean usingCaseAmp) {
		long startTime = System.nanoTime();
		Map<Integer, ArrayList<int[]>> result = new LinkedHashMap<Integer, ArrayList<int[]>>();
		
		for (Entry<Integer, ArrayList<int[]>> testUsers: rating.entrySet()) {
			ArrayList<int[]> ratedMovie = testUsers.getValue();
			double userAVG = 0;
			for (int[] rate: ratedMovie) {
				userAVG += rate[1];
			}
			userAVG /= (double)ratedMovie.size();
		
			PriorityQueue<Weight> pQ = new PriorityQueue<Weight>(200, new Comparator<Weight>(){
				public int compare(Weight c1, Weight c2) {
					if (c2.weight > c1.weight) return 1;
					if (c2.weight < c1.weight) return -1;
					return 0;
				}
			});
			
			weightUBPC(ratedMovie, pQ, userAVG, usingIUF);
			
			result.put(testUsers.getKey(), new ArrayList<int[]>());
			for (Integer rMovie: prediction.get(testUsers.getKey())) {
				double divisor = 0, dividend = 0;
				Iterator<Weight> itr = pQ.iterator();
				for (int i = 0; i < topK; i++){
					if (itr.hasNext()) {
						Weight w = itr.next();
						if (usingCaseAmp) {
							w.weight  *= Math.pow(Math.abs(w.weight), 1.5); //tho = 2.5
						}
						if (trainingData[w.ID-1][rMovie-1] == 0) {
							i--;
							continue;
						}
						divisor += Math.abs(w.weight);
						if (usingIUF) {
							dividend += (w.weight * ((trainingData[w.ID-1][rMovie-1]*IUF[rMovie-1]) - IUFAvg[i]));
						} else dividend += (w.weight * (trainingData[w.ID-1][rMovie-1] - trainAVG[i]));
					} else break;
				}
				double rating;
				if (divisor != 0) {
					rating =  Math.rint(userAVG + (dividend/divisor));
				} else {
					rating =  Math.rint(userAVG);
				}
				if ((int)rating >= 5) rating = 5.0;
				if ((int)rating <= 1) rating = 1.0;

				result.get(testUsers.getKey()).add(new int[] {rMovie, (int)rating});
			}
		}
		long endTime   = System.nanoTime();
		long totalTime = endTime - startTime;
		System.out.println("UBPC time: " + totalTime);
		return result;
		
	}
	
	public Map<Integer, ArrayList<int[]>> predictIBCS(boolean usingAdj) {
		if (ibCS == null) weightIBCS();
		if (ibACS == null) weightIBACS();
		long startTime   = System.nanoTime();
		double[][] weight = usingAdj? ibACS : ibCS;
		Map<Integer, ArrayList<int[]>> result = new LinkedHashMap<Integer, ArrayList<int[]>>();
		
		for (Entry<Integer, ArrayList<int[]>> testUsers: rating.entrySet()) {
			ArrayList<int[]> ratedMovie = testUsers.getValue();
			result.put(testUsers.getKey(), new ArrayList<int[]>());
			
			for (Integer rMovie: prediction.get(testUsers.getKey())) {
				double dividend = 0, divisor = 0;
				for (int[] rate: ratedMovie) {
					dividend += weight[rate[0]-1][rMovie-1] * rate[1];
					divisor += weight[rate[0]-1][rMovie-1];
				}
				double pRate;
				if (divisor != 0) {
					pRate = Math.rint(dividend/divisor);
				} else pRate = 3;
//				if ((int)pRate >= 5) pRate = 5.0;
//				if ((int)pRate <= 1) pRate = 1.0;
				result.get(testUsers.getKey()).add(new int[] {rMovie, (int)pRate});
			}
		}
		long endTime   = System.nanoTime();
		long totalTime = endTime - startTime;
		System.out.println("IBCS time: " + totalTime);
		return result;
	}
	
	public Map<Integer, ArrayList<int[]>> predictIBPC() {
		if (ibPC == null) weightIBPC();
		long startTime   = System.nanoTime();
		double[][] weight = ibPC;
		Map<Integer, ArrayList<int[]>> result = new LinkedHashMap<Integer, ArrayList<int[]>>();
		
		for (Entry<Integer, ArrayList<int[]>> testUsers: rating.entrySet()) {
			ArrayList<int[]> ratedMovie = testUsers.getValue();
			result.put(testUsers.getKey(), new ArrayList<int[]>());
			
			for (Integer rMovie: prediction.get(testUsers.getKey())) {
				double dividend = 0, divisor = 0;
				for (int[] rate: ratedMovie) {
					dividend += weight[rate[0]-1][rMovie-1] * rate[1];
					divisor += weight[rate[0]-1][rMovie-1];
				}
				double pRate;
				if (divisor != 0) {
					pRate = Math.rint(dividend/divisor);
				} else pRate = 3;
				result.get(testUsers.getKey()).add(new int[] {rMovie, (int)pRate});
			}
		}
		long endTime   = System.nanoTime();
		long totalTime = endTime - startTime;
		System.out.println("IBPC time: " + totalTime);
		return result;
	}
	//get user based cosine sim
	public void weightUBCS(ArrayList<int[]> ratedMovie, PriorityQueue<Weight> pQ, boolean usingIUF) {		
		for (int  i = 0; i < trainingData.length; i++) {
			double dividend = 0, divisor1 = 0, divisor2 = 0;
			for (int[] rate: ratedMovie) {
				if (trainingData[i][rate[0] - 1] == 0) continue;
				double aRate = trainingData[i][rate[0] - 1];
				if (usingIUF) aRate *= IUF[rate[0] - 1];
				dividend += (rate[1] *aRate);
				divisor1 += (rate[1] * rate[1]);
				divisor2 += (aRate * aRate);
			}
			Weight cosSim;
			if (divisor1 != 0) {
				cosSim = new Weight(i + 1, dividend / (Math.sqrt(divisor1) * Math.sqrt(divisor2)));
				pQ.add(cosSim);
			} else {
				cosSim = new Weight(i+1, 0);
			}
			pQ.add(cosSim);
		}
	}
	
	//weight of user based pearson cor
	public void weightUBPC(ArrayList<int[]> ratedMovie, PriorityQueue<Weight> pQ, double userAVG, boolean usingIUF) {
		for (int i = 0; i < trainingData.length; i++) {
			double dividend = 0, divisor1 = 0, divisor2 = 0;
			for (int[] rate: ratedMovie) {
				if (trainingData[i][rate[0] - 1] == 0) continue;
				double aRate = trainingData[i][rate[0] - 1];
				double avg = trainAVG[i];
				if (usingIUF) {
					aRate *= IUF[rate[0] - 1];
					avg = IUFAvg[i];
				}
				dividend += (rate[1] - userAVG) * (aRate - avg);
				divisor1 += (rate[1] - userAVG) * (rate[1] - userAVG);
				divisor2 += (aRate - avg) * (aRate - avg);
			}
			Weight cosSim;
			if (divisor1 != 0) {
				double pc = dividend / (Math.sqrt(divisor1) * Math.sqrt(divisor2));
				cosSim = new Weight(i + 1, pc);
			} else {
				cosSim = new Weight(i+1, 0);
			}
			pQ.add(cosSim);
		}
	}
	
	
	//weight of item based cosine similarity
	public void weightIBCS() {		
		ibACS = new double[trainingData[0].length][trainingData[0].length];
		for (int i = 0; i < movies; i++) {
			for (int j = i+1; j < movies; j++) {
				double dividend = 0, divisor1 = 0, divisor2 = 0;
				for (int k = 0; k < users; k++) {
					if (trainingData[k][i] == 0 || trainingData[k][j] == 0) continue;
					dividend += trainingData[k][i] * trainingData[k][j];
					divisor1 += trainingData[k][i] *trainingData[k][i];
					divisor2 += trainingData[k][j] *trainingData[k][j];
				}
				if (divisor1 != 0) {
					double r = dividend / (Math.sqrt(divisor1) * Math.sqrt(divisor2));
					ibACS[i][j] = r;
					ibACS[j][i] = r;
				}
			}
		}
	}
	
	//weight of item based adjusted cs
	public void weightIBACS() {		
		ibCS = new double[trainingData[0].length][trainingData[0].length];
		for (int i = 0; i < movies; i++) {
			for (int j = i+1; j < movies; j++) {
				double dividend = 0, divisor1 = 0, divisor2 = 0;
				for (int k = 0; k < users; k++) {
					if (trainingData[k][i] == 0 || trainingData[k][j] == 0) continue;
					dividend += (trainingData[k][i] - trainAVG[k]) * (trainingData[k][j] - trainAVG[k]);
					divisor1 += (trainingData[k][i] - trainAVG[k]) * (trainingData[k][i] - trainAVG[k]);
					divisor2 += (trainingData[k][j] - trainAVG[k]) * (trainingData[k][j] - trainAVG[k]);
				}
				if (divisor1 != 0) {
					double r = dividend / (Math.sqrt(divisor1) * Math.sqrt(divisor2));
					ibCS[i][j] = r;
					ibCS[j][i] = r;
				}
			}
		}
	}
	
	//weight of item based pearson cor
	public void weightIBPC() {
		ibPC = new double[movies][movies];
		
		double[] movieAVG = new double[movies];
		for (int i = 0; i < movies; i++) {
			double count = 0;
			for (int j = 0; j < users; j++) {
				if (trainingData[j][i] == 0) continue;
				movieAVG[i] += trainingData[j][i];
			}
			if (count != 0) {
				movieAVG[i] /= count;
			}
		}
		
		for (int i = 0; i < movies; i++) {
			for (int j = i+1; j < movies; j++) {
				double dividend = 0, divisor1 = 0, divisor2 = 0;
				for (int k = 0; k < users; k++) {
					if (trainingData[k][i] == 0 || trainingData[k][j] == 0) continue;
					dividend += (trainingData[k][i] - movieAVG[i]) * (trainingData[k][j] - movieAVG[j]);
					divisor1 += (trainingData[k][i] - movieAVG[i]) * (trainingData[k][i] - movieAVG[i]);
					divisor2 += (trainingData[k][j] - movieAVG[j]) * (trainingData[k][j] - movieAVG[j]);
				}
				if (divisor1 != 0) {
					double r = dividend / (Math.sqrt(divisor1) * Math.sqrt(divisor2));
					ibPC[i][j] = r;
					ibPC[j][i] = r;
				}
			}
		}
	}
	
	public void outputResult(String file, Map<Integer, ArrayList<int[]>>[] resultSet, double[] para) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(file, false));
			PrintWriter pw = new PrintWriter(bw);
			
			Map<Integer, ArrayList<int[]>> sample = resultSet[0];
			for (Entry<Integer, ArrayList<int[]>> a: sample.entrySet()) {
				for (int i = 0; i < a.getValue().size(); i++) {
					double sum = 0;
					for (int j = 0; j < resultSet.length; j++) {
						sum += (resultSet[j].get(a.getKey()).get(i)[1] * para[j]);
					}
					pw.println(a.getKey() + " " + a.getValue().get(i)[0] +  " " + (int)Math.rint(sum));
				}
			}
			pw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void getRecommendation(String input, String output, int topK) {
		testData.loadTestData(input);
		rating = testData.getRaing();
		prediction = testData.getPrediction();
		Map<Integer, ArrayList<int[]>>[] resultSet = new Map[3];
		resultSet[0] = predictIBCS(true);
		resultSet[1] = predictUBCS(topK, false, false);
		resultSet[2] = predictUBPC(topK, false, false);
		double[] para = new double[]{0.43,0.37,0.2};
		outputResult(output, resultSet, para);
		
	}
}

class Weight {
	int ID;
	double weight;
	
	Weight(int ID, double weight) {
		this.ID = ID;
		this.weight = weight;
	}
}
