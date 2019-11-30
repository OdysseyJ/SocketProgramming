import java.io.*;
import java.net.*;

public class UploadThread implements Runnable {
	Socket connectionSocket;
	OutputStream sendStream;
	InputStream recvStream;
	DataOutputStream dos;
	DataInputStream dis;

	String response; // Response message (connected to seeder)
	String command; // Command message (connected to seeder)

	byte[] connectedClientChunkMap;

	UploadThread(Socket connectionSocket) throws IOException {
		this.connectionSocket = connectionSocket;
		this.sendStream = connectionSocket.getOutputStream();
		this.recvStream = connectionSocket.getInputStream();
		this.dos = new DataOutputStream(sendStream);
		this.dis = new DataInputStream(recvStream);
	}

	// Send request to server (command channel)
	void sendRequest(String command) throws IOException {
		dos.writeUTF(command);
		dos.flush();
	}

	// Receive response from server (command channel)
	String getResponse() throws IOException {
		return dis.readUTF();
	}

	void send_information() throws IOException {
		System.out.println("UploadThread - information 전송");

		if (Peer.chunkMap == null) {
			dos.writeUTF("null");
			dos.flush();
		} else {
			dos.writeUTF(Peer.torrent_filename);
			dos.writeInt(Peer.chunkSize);
			dos.writeInt(Peer.chunkNum);
			dos.write(Peer.chunkMap, 0, Peer.chunkMap.length);
			dos.flush();
		}
	}

	void recv_ChunkMap() throws IOException {
		System.out.println("UploadThread - receiveChunkMap ");
		this.connectedClientChunkMap = new byte[Peer.chunkMap.length];
		dis.read(this.connectedClientChunkMap, 0, this.connectedClientChunkMap.length);
	}

	// Check that chunk ID list is full
	public boolean checkChunkFull() {
		if (this.connectedClientChunkMap == null) {
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
			e.printStackTrace();
		}
	}

	public void process() throws Exception {
		System.out.println("================================================");
		System.out.println("               UploadThread Start               ");
		System.out.println("================================================");
		// Request_ChunkMap or Request_Seeder를 받음.
		while (true) {
			response = getResponse();
			System.out.println("UploadThread 받은요청 : " + response);
			switch (response) {
				case ("Request_Chunks"): {
					System.out.println("UploadThread - Request_Chunks");
					int total = dis.readInt();
	
					int[] list;
					if (total > 3) {
						list = new int[3];
					} else {
						list = new int[total];
					}
					for (int i = 0; i < list.length; i++) {
						list[i] = dis.readInt();
					}
	
					sendRequest("Received");
					for (int i = 0; i < list.length; i++) {
						sendStream.write(Peer.data[list[i]]);
						System.out.println("UploadThread - 해당 청크를 전송완료." + list[i]);
					}
					response = getResponse();
					break;
				}
				case ("Request_Seeder"): {
					System.out.println("UploadThread - Seeder 요청을 받음.");
					if (Peer.seeders[0] == 1) {
						dos.writeInt(1);
						dos.flush();
					} else {
						dos.writeInt(0);
						dos.flush();
					}
					System.out.println("UploaderThread - Seeder요청 전송 완료.");
					break;
				}
				case ("Send_ChunkMap"): {
					System.out.println("UploaderThread - Send_ChunkMap요청 받음.");
					recv_ChunkMap();
					break;
				}
				case ("Request_Information"): {
					System.out.println("UploaderThread - Request_Information요청 받음.");
					send_information();
					break;
				}
				case ("Already_Friend"): {
					System.out.println("UploaderThread - Already_Friend요청 받음.");
					break;
				}
			}

			// 시더연결, 자신이 빈seeder라서 chunkMap null을 전송하는 경우, 청크가 가득 찬 경우, 이미친구인 경우.
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
