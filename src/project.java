
public class project {
	static public void main(String args[]) {
		Recommendation r = new Recommendation(200, 1000);
		r.getRecommendation("test5.txt", "result5_combo.txt", 10);
		r.getRecommendation("test10.txt", "result10_combo.txt", 10);
		r.getRecommendation("test20.txt", "result20_combo.txt", 10);
	}
}
