import java.io.*;
import java.net.*;

public class Client_Peer {
	private static Socket socket; // Connected socket with server
	private static int chunkSize = 1024 * 10; // File chunk size
	private static int chunkNum = 0; // To count chunks

	public static void main(String[] args) throws Exception {
		// exception handling
		if (args.length < 3) {
			System.err.println("[Usage]");
			System.err.println("java Client_Peer <server's ip> <server's listening port number> <filename>");
			System.exit(0);
		}

		String serverIP = args[0];
		int port = Integer.parseInt(args[1]);
		String fileName = args[2];

		System.out.println("==============================================================");
		System.out.println("                       Client_Peer Start                     ");
		System.out.println("==============================================================");

		// Create file output stream to make file
		FileOutputStream fos = new FileOutputStream("files/" + fileName);

		// Create Socket
		socket = new Socket(serverIP, port);
		if (!socket.isConnected()) {
			System.out.println("Socket Connect Fail");
			System.exit(0);
		}

		System.out.println("Connected to Server (" + serverIP + ":" + port + ")");
		System.out.println("Download Start " + fileName);

		// Create inpusStream and get files
		InputStream InputStream = socket.getInputStream();
		byte[] buffer = new byte[chunkSize];
		int readBytes;
		while ((readBytes = InputStream.read(buffer)) != -1) {
			fos.write(buffer, 0, readBytes);
			chunkNum++;
		}

		System.out.println("Client receive " + chunkNum + " chunks to Server");
		System.out.println("Download End " + fileName);

		socket.close();
		fos.close();
		InputStream.close();
	}
}
