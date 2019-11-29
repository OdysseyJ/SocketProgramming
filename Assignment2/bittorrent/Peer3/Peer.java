import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringTokenizer;

public class Peer {
	final static int MAX_DOWNLOAD_THREAD = 3;
	final static int MAX_PEER_SIZE = 5;
	static int chunkSize;
	static int chunkNum;
	
	static int[] seeders;
	static boolean allPeerCompleted = false;
	
	static String config_dir = "config";
	static String config_filename;
	
	// configfile 읽은정보 저
	static String[] domain_arr;
	static int[] port_arr;
	
	static String torrent_dir = "files";
	static String torrent_filename;
	static byte[][] data;
	static byte[] chunkMap;
	
	static int[] time = new int[MAX_DOWNLOAD_THREAD];
	
	static void readConfigFile(String fn) throws Exception {
		Path p = Paths.get(System.getProperty("user.dir"), config_dir);
		config_filename = fn;
		File config_file = new File(p.toString(), config_filename);
		if (!config_file.exists()) {
			System.err.println("There is no config file in config directory");
			System.exit(0);
		}
		FileInputStream fis = new FileInputStream(config_file);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		
		domain_arr = new String[MAX_PEER_SIZE];
		port_arr = new int[MAX_PEER_SIZE];
		
		for (int i = 0; i < MAX_PEER_SIZE; i++) {
			StringTokenizer stk = new StringTokenizer(br.readLine(), " ");
			domain_arr[i] = stk.nextToken();
			port_arr[i] = Integer.parseInt(stk.nextToken());
		}
		fis.close();
		br.close();
	}
	
	static void readTorrentFile(String fn) throws Exception {
		Path p = Paths.get(System.getProperty("user.dir"), torrent_dir);
		torrent_filename = fn;
		File torrent_file = new File(p.toString(), torrent_filename);
		//chunkSize, chunkNum, data, chunkMap, seeders[0]가 있
		if (torrent_file.exists()) {
			chunkSize = 1024*10;
			chunkNum = (int) Math.ceil((double) torrent_file.length() / chunkSize); // Calculate chunk number
			FileInputStream fis = new FileInputStream(torrent_file);
			BufferedInputStream bis = new BufferedInputStream(fis);
			
			data = new byte[chunkNum][];
			for (int i = 0; i < chunkNum; i++) {
				data[i] = new byte[chunkSize];
			}
			int chunkIndex = 0;
			int numRead = 0;

			// Read bytes from file
			while (chunkIndex < chunkNum && (numRead = bis.read(data[chunkIndex], 0, data[chunkIndex].length)) != -1) {
				chunkIndex++;
			}
			
			// fill chunkmap
			chunkMap = new byte[chunkNum];
			for (int i = 0; i < chunkNum; i++) {
				chunkMap[i] = (byte)1;
			}
			
			seeders[0] = 1;
			
			fis.close();
			bis.close();
		}
		//chunkSize, chunkNum, data, chunkMap, seeders[0]가 없음.
		else {
			System.out.println("There is no such file in files folder");
			System.out.println("File Download Start");
		}
	}
	
	public static boolean check_chunkFull(byte[] chunkMap) {
		if (chunkMap == null) {
			return false;
		}
		for (int i = 0; i < chunkMap.length; i++) {
			if ( chunkMap[i] == (byte)0 )
				return false;
		}
		return true;
	}
	
	public static void set_information( int chunksize, int chunknum) {
		chunkSize = chunksize;
		chunkNum = chunknum;
		chunkMap = new byte[chunkNum];
		data = new byte[chunkNum][];
		for (int i = 0; i < chunkNum; i++) {
			data[i] = new byte[chunkSize];
		}
	}
	
	// 쓰레드가 다 없어지면 프로세스 종료.
	public static void main(String[] args) throws Exception {
		if(args.length<2) {
			System.err.println("[Usage]");
			System.err.println("java Peer <config file name> <target filename>");
			System.exit(0);
		}
		System.out.println("================================================");
		System.out.println("                  Peer Start                    ");
		System.out.println("================================================");

		seeders = new int[MAX_PEER_SIZE];
		
		//config, torrent file 읽기.
		readConfigFile(args[0]);
		readTorrentFile(args[1]);
		
		//만약 내가 파일이 있는 시더다-> //chunkSize, chunkNum, data, chunkMap, seeders[0]가 세팅되어 있다.
		//만약 내가 파일이 없는 클라다-> //chunkSize, chunkNum, data, chunkMap, seeders[0]가 세팅되어 있지 않음.
		
		//서버스레드, 클라이언트 스레드 구동.
		int listenPort = port_arr[0];
		ServerThread s_thread = new ServerThread(listenPort);
		ClientThread c_thread = new ClientThread();
		Thread server_Thread = new Thread(s_thread);
		Thread client_Thread = new Thread(c_thread);
		server_Thread.start();
		client_Thread.start();
		

		System.out.println("================================================");
		System.out.println("                   Peer End                    ");
		System.out.println("================================================");
	}
	
}
