import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringTokenizer;

public class Peer {
	
	//******* 공유되는 변수입니다. 모든 쓰레드에서 접근이 가능합니다.*******
	
	final static int MAX_FRIENDS_SIZE = 3; // 한 피어가 연결할 수 있는 최대 친구수
	final static int MAX_PEER_SIZE = 5; // 최대 피어의 수
	final static String config_dir = "config"; // config file이 위치하는 디렉토리 이름
	final static String torrent_dir = "files"; // 공유 / 다운로드 받을 파일의 디렉토리 이름.
	
	static int chunkSize; // 파일이 가지는 청크의 크기
	static int chunkNum; // 파일이 가지는 청크의 수 
	
	static int[] seeders; // seeder인 피어의 정보를 저장하고 있는 배열 (Seeder = 1, Downloader = 0)
	static boolean allPeerCompleted; // 모든 피어가 Seeder인지의 여부 판별 ( 모두 Seeder = true)
	
	static String config_filename; // 자신과, 다른 피어들의 정보를 담고있는 config파일의 이름.
	static String torrent_filename; // 공유 / 다운로드 받을 파일의 이름 
	static String[] domain_arr; // 각 피어의 도메인을 저장하고 있는 배열
	static int[] port_arr; // 각 피어의 포트번호를 저장하고 있는 배열
	
	static byte[][] data; // 청크가 저장되는 배열
	static byte[] chunkMap; // 현재 어떤 청크를 가지고 있는지를 나타내는 배열 (1 = 해당인덱스 청크있음, 0 = 해당인덱스 청크없음)
	
	static int[] friendsArr; // 해당 피어가 현재 어떤 친구와 연결되어 있는지를 저장
	static int friendsIndex; // friendArr의 인덱스를 위한 변수.
	static byte[][] friendsChunkMap; // 모든 피어들의 chunkMap을 저장하고 있다가 친구 index와 매칭
	static String[] friendsFileName; // 모든 피어들의 FileName을 저장하고 있다가 친구 index와 매칭
	static int[] friendsChunkSize; // 모든 피어들의 chunkSize을 저장하고 있다가 친구 index와 매칭
	static int[] friendsChunkNum; // 모든 피어들의 chunkNum을 저장하고 있다가 친구 index와 매칭
	
	// 각 멤버변수의 초기화.
	static void init() {
		allPeerCompleted = false;
		seeders = new int[MAX_PEER_SIZE];
		friendsArr = new int[MAX_FRIENDS_SIZE];
		friendsIndex = 0;
		friendsChunkMap = new byte[MAX_PEER_SIZE+1][];
		friendsFileName = new String[MAX_PEER_SIZE+1];
		friendsChunkSize = new int[MAX_PEER_SIZE+1];
		friendsChunkNum = new int[MAX_PEER_SIZE+1];
		
		// friend배열은 친구의 인덱스를 저장하기 때문에 0번 인덱스를 사용하기위해 -1로 초기화함.
		for (int i = 0; i < friendsArr.length; i++) {
			friendsArr[i] = -1;
		}
	}
	
	// config.txt파일을 config 파일 경로에서 읽어온다.
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
		
		
		// 읽어온 정보를 domain_arr, port_arr에 저장한다.
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
	
	// 공유 / 다운로드 받은 파일을 경로에서 읽어온다.
	static void readTorrentFile(String fn) throws Exception {
		Path p = Paths.get(System.getProperty("user.dir"), torrent_dir);
		torrent_filename = fn;
		File torrent_file = new File(p.toString(), torrent_filename);
		// 해당 경로에 이미 파일이 존재하는 경우 -> Seeder임.
		if (torrent_file.exists()) {
			chunkSize = 1024*10;
			chunkNum = (int) Math.ceil((double) torrent_file.length() / chunkSize); // Calculate chunk number
			FileInputStream fis = new FileInputStream(torrent_file);
			BufferedInputStream bis = new BufferedInputStream(fis);
			
			// 청크 데이터 저장시키기.
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
			
			// 현재 피어 (index0 - config.txt에서의 인덱스)가 seeder라고 설정함.
			seeders[0] = 1;
			
			fis.close();
			bis.close();
		}
		// 해당 경로에 파일이 없는 경우에는 -> downloader임.
		else {
			System.out.println("There is no such file in files folder");
			System.out.println("File Download Start");
		}
	}
	
	// 전달받은 청크맵에 청크가 가득 차있는지 판별하는 함수.
	// 가득 차 있음 -> true, 가득차 있지 않음 -> false
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
	
	// 해당 피어의 정보를 setting한다.
	// 만약 해당 피어의 경로에 파일이 있다면 파일정보를 초기화 할 수 있겠지만
	// 해당 경로에 파일이 없는 다운로더는 파일 정보를 알 수 없으므로 시더에게 물어본 정보를 초기화해줘야한다.
	public static void set_information( int chunksize, int chunknum) {
		chunkSize = chunksize;
		chunkNum = chunknum;
		chunkMap = new byte[chunkNum];
		data = new byte[chunkNum][];
		for (int i = 0; i < chunkNum; i++) {
			data[i] = new byte[chunkSize];
		}
	}
	
    // 
	public static void main(String[] args) throws Exception {
		if(args.length<2) {
			System.err.println("[Usage]");
			System.err.println("java Peer <config file name> <target filename>");
			System.exit(0);
		}
		System.out.println("================================================");
		System.out.println("                  Peer Start                    ");
		System.out.println("================================================");

		// 각종 멤버변수 초기화
		init();
		
		//config, torrent file 읽기.
		readConfigFile(args[0]);
		readTorrentFile(args[1]);
		
		//만약 내가 파일이 있는 시더-> //chunkSize, chunkNum, data, chunkMap, seeders[0]가 세팅되어 있다.
		//만약 내가 파일이 없는 다운로더-> //chunkSize, chunkNum, data, chunkMap, seeders[0]가 세팅되어 있지 않음.
		
		// ServerThread 실행.
		int listenPort = port_arr[0];
		ServerThread s_thread = new ServerThread(listenPort);
		Thread server_Thread = new Thread(s_thread);
		server_Thread.start();
		
		// ClientThread 실행.
		ClientThread c_thread = new ClientThread(1);
		ClientThread c_thread2 = new ClientThread(2);
		ClientThread c_thread3 = new ClientThread(3);
		Thread client_Thread = new Thread(c_thread);
		Thread client_Thread2 = new Thread(c_thread2);
		Thread client_Thread3 = new Thread(c_thread3);
		client_Thread.start();
		client_Thread2.start();
		client_Thread3.start();
		

		System.out.println("================================================");
		System.out.println("                   Peer End                    ");
		System.out.println("================================================");
	}
	
}
