
public class TimerThread implements Runnable {
	int threadnum;
	
	TimerThread(int threadnum){
		this.threadnum = threadnum;
	}
	
	public void run() {
		try {
			process();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void process() throws Exception {
		while(Peer.time[threadnum] != 10) {
			Peer.time[threadnum]++;
			Thread.sleep(1000);
		}
	}
}
