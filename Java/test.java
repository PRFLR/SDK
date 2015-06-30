public class Program {
	public static void main(String[] args){
		try {
			PRFLR.init("192.168.1.45-testAppn", "testKey@prflr.org:4000");
		} catch (Exception e) {
			e.printStackTrace();
		}
		PRFLR.overflowCount = 50;
		PRFLR.begin("mongoDB.save");
		for(int i = 0; i < 10; i++) {
			PRFLR.begin("mongoDB.save step" + i);
			try {
				Thread.sleep(10);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			try {
				PRFLR.end("mongoDB.save step" + i, "step " + i);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			PRFLR.end("mongoDB.save", "Good!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
