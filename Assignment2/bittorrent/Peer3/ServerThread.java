import java.net.*;

public class ServerThread implements Runnable {
	ServerSocket welcomeSocket;
	Socket connectionSocket;
	
	// 서버 소켓용 listen 포트
	private int listenPort;

	ServerThread(int listenPort) {
		this.listenPort = listenPort;
	}

	public void run() {
		try {
			process();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void process() {
		System.out.println("================================================");
		System.out.println("               Server Thread Start              ");
		System.out.println("================================================");
		// 모든 피어가 완료되었을 경우 -> ClientThread에서 모든 피어가 시더인 경우를 판별해 바꿔준다.
		while (!Peer.allPeerCompleted) {
			try {
				// welcome socket 만들기.
				welcomeSocket = new ServerSocket(listenPort);
				
				// 20초간 연결이 없을경우 서버 재시작.
				welcomeSocket.setSoTimeout(20000);
				while (true) {
					// 서버는 연결 요청이 들어오면 UploadThread에게 해당 요청을 넘겨줌
					// 이후 다른 요청을 위한 대기상태가 됨.
					connectionSocket = welcomeSocket.accept();
					UploadThread uploadThread = new UploadThread(connectionSocket);
					Thread thread = new Thread(uploadThread);
					thread.start();
				}
			} catch (Exception e) {
				try {
					welcomeSocket.close();
				} catch (Exception ex) {
				}
			}
		}

		System.out.println("================================================");
		System.out.println("     All peers in server download completed     ");
		System.out.println("================================================");
	}
}