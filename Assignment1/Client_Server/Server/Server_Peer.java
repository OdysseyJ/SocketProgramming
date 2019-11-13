import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Server_Peer {
	private static ServerSocket welcomeSocket; // First connected socket
	private static Socket connectionSocket; // Connected socket with client
	private static String file_dir = "files"; // Name of file directory
	private static int port; // Seeder's port number
	private static String filename; // File name
	private static byte[][] data; // Stores chunked data
	private static int chunkSize; // A chunk size
	private static int chunkNum; // The number of chunks in data

	// Cunking files with 10KB and save it to data[][]
	public static byte[][] file_chunking(File f) throws IOException {
		chunkSize = 1024 * 10;
		chunkNum = (int) Math.ceil((double) f.length() / chunkSize); // Calculate chunk number
		int numRead = 0;

		FileInputStream fis = new FileInputStream(f);
		BufferedInputStream bis = new BufferedInputStream(fis);

		data = new byte[chunkNum][];
		for (int i = 0; i < chunkNum; i++) {
			data[i] = new byte[chunkSize];
		}
		int chunkIndex = 0;

		// Read bytes from file
		while (chunkIndex < chunkNum && (numRead = bis.read(data[chunkIndex], 0, data[chunkIndex].length)) != -1) {
			chunkIndex++;
		}
		bis.close();
		fis.close();

		return data;
	}

	public static void main(String[] args) throws Exception {
		// exception handling
		if (args.length < 2) {
			System.err.println("[Usage]");
			System.err.println("java Server_Peer <server's listening port number> <filename>");
			System.exit(0);
		}

		port = Integer.parseInt(args[0]);
		filename = args[1];

		// Directory path where file exist.
		Path p = Paths.get(System.getProperty("user.dir"), file_dir);

		File sendFile = new File(p.toString(), filename);
		if (!sendFile.exists()) {
			System.out.println("There is no " + filename + " in files directory");
		}

		// File Chunking by 10KB and save it to data.
		data = file_chunking(sendFile);

		System.out.println("==============================================================");
		System.out.println(" 					Server_Peer Start ");
		System.out.println("==============================================================");
		Thread.sleep(1000);
		System.out.println("==============================================================");
		System.out.println(" 				Waiting Clients Connection ");
		System.out.println("==============================================================");

		// Socket Start
		welcomeSocket = new ServerSocket(port);
		// Listen port and waiting clients connection.
		connectionSocket = welcomeSocket.accept();

		System.out.println("Peer connected in port " + port);

		// Create outputStream and send data to socket.
		OutputStream OutputStream = connectionSocket.getOutputStream();
		for (int i = 0; i < data.length; i++) {
			OutputStream.write(data[i]);
		}

		System.out.println("Send Completed");
		System.out.println("Send " + chunkNum + " chunks to client");

		OutputStream.close();
		welcomeSocket.close();
		connectionSocket.close();
	}
}
