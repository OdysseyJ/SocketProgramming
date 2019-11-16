import java.io.*;
import java.net.*;
import java.util.Random;

public class ClientThread implements Runnable {
	Socket connectionSocket;
	OutputStream sendStream;
	InputStream recvStream;
	DataInputStream dis;				// Used data input stream in socket
	DataOutputStream dos;				// Used data output stream in socket
	
	String response;					// Response message (connected to seeder)
	String command;						// Command message (connected to seeder)
	
	String peerFileName;
	int peerChunkSize;
	int peerChunkNum;
	byte[] peerChunkMap;
	
	// Send request to server (command channel)
	void sendRequest() {
		try {
			byte[] sendBuff = new byte[command.length()];
			sendBuff = command.getBytes();
			sendStream.write(sendBuff, 0, sendBuff.length);
		} catch (IOException ex) {
			System.err.println("IOException in sendRequest");
		}
	}
		
	// Receive response from server (command channel)
	void getResponse() {
		try {
			int dataSize;
			while ((dataSize = recvStream.available()) == 0);
			byte[] recvBuff = new byte[dataSize];
			recvStream.read(recvBuff, 0, dataSize);
			response = new String(recvBuff, 0, dataSize);
		} catch (IOException ex) {
			System.err.println("IOException in getResponse");
		}
	}
	
	void recv_information() throws IOException {
		this.peerFileName = dis.readUTF();
		System.out.println("Client - peerfilename" + this.peerFileName);
		if(this.peerFileName.equals("null")) {
		}
		else {
		this.peerChunkSize = dis.readInt();

		System.out.println("Client - peerfilename" + this.peerChunkSize);
		this.peerChunkNum = dis.readInt();

		System.out.println("Client - peerfilename" + this.peerChunkNum);
		peerChunkMap = new byte[this.peerChunkNum];
		dis.read(peerChunkMap, 0, peerChunkMap.length);
		if (Peer.chunkMap == null) {
		Peer.set_information(this.peerChunkSize, this.peerChunkNum);
		}
		}
	}
		
		/* Find missing chunk ID list by comparing with the other peer's
		 * chunk ID list. The number of missing ID's is limited 50 because of simulation.
		 */
	int[] find_missing_index(byte[] temp) {
		byte[] current = Peer.chunkMap;
		int[] result = new int[Peer.chunkNum];
		for (int k = 0; k < result.length; k++) {
			result[k] = -1;
		}
		
		int i = 0, j = 0;
		int count = 0;
		
		while ( count < Peer.chunkNum && i < current.length) {
			if ( (current[i] == (byte)0) && (temp[i] == (byte)1)) {
				count++;
				result[j] = i;
				j++;
			}
			i++;
		}
		return result;
	}
		
		
		// Check that chunk ID list is full
	public boolean checkChunkFull(byte[] chunkMap) {
		if (chunkMap == null) {
			return false;
		}
		for (int i = 0; i < chunkMap.length; i++) {
			if ( chunkMap[i] == (byte)0 )
				return false;
		}
		return true;
	}

	public void run() {
		try {
			process();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private boolean connectSocket(int index) throws IOException{
		try {
			connectionSocket = new Socket(Peer.domain_arr[index],Peer.port_arr[index]);
		} catch(IOException e) {
			return false;
		}
		return true;
	}
	
	boolean allPeerIsSeeder() {
		for (int i = 0; i < Peer.MAX_PEER_SIZE; i++) {
			if (Peer.seeders[i] == 0) {
				return false;
			}
		}
		return true;
	}
	
	public void process() throws Exception {
		Thread.sleep(1000);
		System.out.println("================================================");
		System.out.println("               Client Thread Start              ");
		System.out.println("================================================");
		int index = 1;
		while(!checkChunkFull(Peer.chunkMap)) {
			if (connectSocket(index)) {
				recvStream = connectionSocket.getInputStream();
				sendStream = connectionSocket.getOutputStream();
				dis = new DataInputStream(recvStream);
				dos = new DataOutputStream(sendStream);
				System.out.println("================================================");
				System.out.println("      Client Thread 서버연결 완료 - 청크다운로드      ");
				System.out.println("================================================");
				System.out.println(Peer.domain_arr[index]+":"+Peer.port_arr[index]+ " 와 연결되었음");
				command = "Request_ChunkMap";
				sendRequest();
				System.out.println("Client - 청크맵 요청 보내기");
				recv_information();
				Thread.sleep(1000);
				if(!peerFileName.equals("null")) {
					System.out.println("Client - 청크를 가지고 있는 서버와 연결");
					int[] missing_list = find_missing_index(peerChunkMap);
					int total = 0;
					for (int i = 0; i < missing_list.length; i++) {
						System.out.print(missing_list[i]);
						if(missing_list[i]!=-1) {
							total++;
						}
					}
					System.out.println("Client - 모자란 청크 개수(total) " + total);
					
					command = "Request_Chunks";
					sendRequest();
					dos.writeInt(total);
					dos.flush();
					
					int[] randomChunk;
					if (total>3) {
						randomChunk = new int[3];
					}
					else {
						randomChunk = new int[total];
					}
					Random r = new Random();
					for (int i = 0; i < randomChunk.length; i++) {
						randomChunk[i] = missing_list[r.nextInt(total)];
						for (int j = 0; j < i; j++) {
							if(randomChunk[i]==randomChunk[j]) {
								i--;
							}
						}
					}
					
					for (int i = 0; i < randomChunk.length; i++) {
						System.out.println("보내는 랜덤 청크" + randomChunk[i]);
						dos.writeInt(randomChunk[i]);
						dos.flush();
					}
					
					System.out.println("Client - 쓰기완료");
					
					for (int i = 0; i < randomChunk.length; i++) {
						System.out.println("Client - "+i+"번째 미싱청크 받기");
						Peer.chunkMap[randomChunk[i]] = (byte)1;
						recvStream.read(Peer.data[randomChunk[i]]);
					}

					Thread.sleep(2000);
					System.out.println("Client - 최대 3개의 청크 요청 읽기 완료");
				}
				else {
					System.out.println("Client - 연결한 서버가 청크 없음");
				}
				connectionSocket.close();
				recvStream.close();
				sendStream.close();
				dos.close();
				dis.close();
			}
			else {
				System.out.println(Peer.domain_arr[index]+":"+Peer.port_arr[index]+" 연결에 실패했습니다. 다음 주소로 연결을 시도합니다.");
			}
			Thread.sleep(3000);
			index = ((index)%(Peer.MAX_PEER_SIZE-1))+1;
		}
		Peer.seeders[0] = 1;
		System.out.println("Client Thread - 파일 다운로드 종료");
		index = 1;
		while (!allPeerIsSeeder()) {
			if (connectSocket(index)) {
				recvStream = connectionSocket.getInputStream();
				sendStream = connectionSocket.getOutputStream();
				dis = new DataInputStream(recvStream);
				dos = new DataOutputStream(sendStream);
				System.out.println("================================================");
				System.out.println("       Client Thread 서버연결 완료 - 시더판별         ");
				System.out.println("================================================");
				command = "Request_Seeder";
				sendRequest();
				Thread.sleep(1000);
				Peer.seeders[index] = dis.readInt();
				
				connectionSocket.close();
				recvStream.close();
				sendStream.close();
				dos.close();
				dis.close();
			}
			else {
				System.out.println(Peer.domain_arr[index]+":"+Peer.port_arr[index]+" 연결에 실패했습니다. 다음 주소로 연결을 시도합니다.");
			}
			Thread.sleep(3000);
			index = ((index)%(Peer.MAX_PEER_SIZE-1))+1;
		}
		Peer.allPeerCompleted = true;
		Thread.sleep(1000);
		System.out.println("================================================");
		System.out.println("               Client Thread End              ");
		System.out.println("================================================");
	
	}
}