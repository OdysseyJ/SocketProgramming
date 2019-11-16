import java.io.*;
import java.net.*;

public class UploadThread implements Runnable {
	Socket connectionSocket;
	OutputStream sendStream;
	InputStream recvStream;
	DataOutputStream dos;				
	DataInputStream dis;				
	
	String response;					// Response message (connected to seeder)
	String command;						// Command message (connected to seeder)
	
	
	
	UploadThread(Socket connectionSocket) throws IOException{
		this.connectionSocket = connectionSocket;
		this.sendStream = connectionSocket.getOutputStream();
		this.recvStream = connectionSocket.getInputStream();
		this.dos = new DataOutputStream(sendStream);
		this.dis = new DataInputStream(recvStream);
	}
	
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
	
	void send_information() throws IOException {
		System.out.println("UploadThread - information 전송");

		if (Peer.chunkMap == null) {
			dos.writeUTF("null");
			dos.flush();
		}
		else {
		dos.writeUTF(Peer.torrent_filename);
		dos.flush();
		dos.writeInt(Peer.chunkSize);
		dos.flush();
		dos.writeInt(Peer.chunkNum);
		dos.flush();
		dos.write(Peer.chunkMap, 0, Peer.chunkMap.length);
		dos.flush();
		}
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
		getResponse();
		System.out.println("UploadThread 받은요청 : " + response);
		if (response.equalsIgnoreCase("Request_ChunkMap")) {
			System.out.println("UploadThread - ChunkMap 요청을 받음.");
			send_information();
			getResponse();
			if (response.equalsIgnoreCase("Request_Chunks")) {
				System.out.println("UploadThread - Chunks 요청을 받음");
				int total = dis.readInt();
				System.out.println("UploadThread - 전달받은 total " + total);
				int[] list;
				if (total>3) {
					list = new int[3];
				}
				else {
					list = new int[total];
				}
				for (int i = 0; i < list.length ; i++) {
					list[i] = dis.readInt();

					System.out.println("UploadThread - 요청 청크 " + list[i]);
				}
				for (int i = 0; i < list.length; i++) {
					sendStream.write(Peer.data[list[i]]);
					System.out.println("UploadThread - 해당 청크를 전송완료." + list[i]);
				}	
			}
		}
		else if (response.equalsIgnoreCase("Request_Seeder")) {
			System.out.println("UploadThread - Seeder 요청을 받음.");
			if(Peer.seeders[0] == 1) {
				dos.writeInt(1);
				dos.flush();
			}
			else {
				dos.writeInt(0);
				dos.flush();
			}
		}
		Thread.sleep(5000);
		System.out.println("UploadThread - 종료");
		connectionSocket.close();
		sendStream.close();
		recvStream.close();
		dos.close();
		dis.close();
	}
}