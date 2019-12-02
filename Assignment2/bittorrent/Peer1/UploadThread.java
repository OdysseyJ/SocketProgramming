import java.io.*;
import java.net.*;

public class UploadThread implements Runnable {
	Socket connectionSocket;
	OutputStream sendStream;
	InputStream recvStream;
	DataOutputStream dos;
	DataInputStream dis;

	// 해당 ClientThread가 보낸 요청을 저장하는 멤버변수.
	String response; // Response message (connected to seeder)

	// 연결된 클라이언트 쓰레드의 청크맵을 저장하는 배열.
	byte[] connectedClientChunkMap;

	// 입출력 스트림 초기화.
	UploadThread(Socket connectionSocket) throws IOException {
		this.connectionSocket = connectionSocket;
		this.sendStream = connectionSocket.getOutputStream();
		this.recvStream = connectionSocket.getInputStream();
		this.dos = new DataOutputStream(sendStream);
		this.dis = new DataInputStream(recvStream);
	}

	// UploadThread가 Client Thread에게 요청 보내기.
	void sendRequest(String command) throws IOException {
		dos.writeUTF(command);
		dos.flush();
	}

	// Client Thread가 해당 UploadThread로 보내는 요청 읽기.
	String getResponse() throws IOException {
		return dis.readUTF();
	}

	// Client Thread에게 information을 전송함.
	void send_information() throws IOException {
		// 현재 피어의 chunkMap이 없는 경우.
		// 다른 시더와 연결한 적이 없음, 자신은 다운로더인 경우.
		if (Peer.chunkMap == null) {
			dos.writeUTF("null");
			dos.flush();
		} else {
			// 만약, chunkMap이 존재하는 경우
			// (다른시더와 연결한 적이 있어서 파일 정보를 들고 있거나/ 자신이 시더인 경우)
			dos.writeUTF(Peer.torrent_filename);
			dos.writeInt(Peer.chunkSize);
			dos.writeInt(Peer.chunkNum);
			dos.write(Peer.chunkMap, 0, Peer.chunkMap.length);
			dos.flush();
		}
	}

	// 클라이언트 Thread에게서 청크맵을 전송받아서 connectedClientChunkMap에 저장시킴.
	void recv_ChunkMap() throws IOException {
		this.connectedClientChunkMap = new byte[Peer.chunkMap.length];
		dis.read(this.connectedClientChunkMap, 0, this.connectedClientChunkMap.length);
	}

	// 현재 연결된 클라이언트가 보내준 청크맵이 꽉 차있는지 여부를 판별함
	// 만약 꽉차있으면, 해당 연결을 종료하기 위해서.
	public boolean checkChunkFull() {
		if (this.connectedClientChunkMap == null) {
			return false;
		}
		if (this.connectedClientChunkMap.length == 0) {
			return false;
		}
		for (int i = 0; i < this.connectedClientChunkMap.length; i++) {
			if (this.connectedClientChunkMap[i] == (byte) 0)
				return false;
		}
		return true;
	}

	public void run() {
		try {
			process();
		} catch (Exception e) {
		}
	}

	// 쓰레드 시작.
	public void process() throws Exception {
		System.out.println("================================================");
		System.out.println("               UploadThread Start               ");
		System.out.println("================================================");
		while (true) {
			response = getResponse();
			System.out.println("UploadThread 받은요청 : " + response);
			// 요청에 대한 처리.
			switch (response) {
				// 특정 청크를 요청하는 경우.
				case ("Request_Chunks"): {
					// 총 청크 개수 읽기.
					int total = dis.readInt();
	
					int[] list = new int[total];
					// list에 전달받은 랜덤 청크 index 저장시키기.
					for (int i = 0; i < total; i++) {
						list[i] = dis.readInt();
					}
	
					for (int i = 0; i < total; i++) {
						// 랜덤 청크 인덱스에 해당하는 청크 데이터를 전송.
						sendStream.write(Peer.data[list[i]]);
					}
					// 잘 전송되었는지 여부 전달받기.
					response = getResponse();
					break;
				}
				// 시더인지 아닌지 판별하는 경우.
				case ("Request_Seeder"): {
					// 자기 자신이 seeder인 경우
					if (Peer.seeders[0] == 1) {
						dos.writeInt(1);
						dos.flush();
					// 자기 자신이 시더가 아닌 경우.
					} else {
						dos.writeInt(0);
						dos.flush();
					}
					break;
				}
				// chunkMap을 보내주는 경우.
				case ("Send_ChunkMap"): {
					recv_ChunkMap();
					break;
				}
				// Information을 요청하는 경우.
				case ("Request_Information"): {
					send_information();
					break;
				}
				// 이미 친구인 경우.
				case ("Already_Friend"): {
					break;
				}
			}

			// 친구 연결을 유지하지 않고 해제하는 경우.
			// 1.시더연결
			// 2.자신이 빈seeder라서 chunkMap null을 전송하는 경우
			// 3.청크가 가득 찬 경우
			// 4.이미친구인 경우.
			if (response.equals("Request_Seeder")
					|| (response.equals("Request_Information") && Peer.chunkMap == null)
					|| response.equals("Already_Friend")
					|| checkChunkFull()) {
				break;
			}
		}
		System.out.println("================================================");
		System.out.println("                 UploadThread End               ");
		System.out.println("================================================");
		connectionSocket.close();
		sendStream.close();
		recvStream.close();
		dos.close();
		dis.close();
	}
}
