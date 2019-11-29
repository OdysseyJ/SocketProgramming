import java.net.*;

public class ServerThread implements Runnable {
	ServerSocket welcomeSocket;
	Socket connectionSocket;
	
	private int listenPort;
	
	ServerThread (int listenPort){
	this.listenPort = listenPort;
	}
	
	public void run() {
		try {
			process();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void process() throws Exception {
		System.out.println("================================================");
		System.out.println("               Server Thread Start              ");
		System.out.println("================================================");
		// 모두가 seeder가되면 끝남.
		welcomeSocket = new ServerSocket(listenPort);
		// 서버소켓 연결 종료시간 30
		welcomeSocket.setSoTimeout(30000);
		while(!Peer.allPeerCompleted) {
		connectionSocket = welcomeSocket.accept();
		UploadThread uploadThread = new UploadThread(connectionSocket);
		Thread thread = new Thread(uploadThread);
		thread.start();

		}
		welcomeSocket.close();
		connectionSocket.close();
		Thread.sleep(1000);
		System.out.println("================================================");
		System.out.println("     All peers in server download completed     ");
		System.out.println("================================================");
	}
}