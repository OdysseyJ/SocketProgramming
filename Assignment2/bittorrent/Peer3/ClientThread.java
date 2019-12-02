import java.io.*;
import java.net.*;
import java.util.Random;

// 파일을 다운로드하는 역할을 하는 쓰레드.
public class ClientThread implements Runnable {
	
	Socket connectionSocket; // 데이터 전송을 위한 소켓
	OutputStream sendStream; // UploadThread로 파일을 전송하기 위한 스트림
	InputStream recvStream; // UploadThread에서 파일을 전송받기 위한 스트림
	DataInputStream dis; // 데이터 전송을 돕기 위한 input 스트림.
	DataOutputStream dos; // 데이터 전송을 돕기 위한 output 스트림.

	String response; // UploadThread에게 요청한 request에 대한 response를 저장.

	private int index; // 처음 시작하는 index
	private int firstindex; // 처음 시작하는 index 저장해서 seeder 판별시 사용한다.

	// 생성자 : 시작 인덱스를 초기화한다.
	public ClientThread(int index) {
		this.index = index;
		this.firstindex = index;
	}
	
	public void run() {
		try {
			process();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// UploadThread에게 Request요청을 보냅니다.
	void sendRequest(String command) throws IOException {
		dos.writeUTF(command);
		dos.flush();
	}

	// UploadThread에게서 Response를 받습니다.
	String getResponse() throws IOException {
		return dis.readUTF();
	}

	// UploadThread에게서 Information을 받습니다.
	void recv_information(int index) throws SocketTimeoutException, IOException {
		// 파일 이름 받기.
		Peer.friendsFileName[index] = dis.readUTF();
		// 연결한 UploadThread가 파일 정보가 있는 경우에만,
		if (!Peer.friendsFileName[index].equals("null")) {
			// chunkSize, chunkNum, chunkMap을 전달받는다.
			Peer.friendsChunkSize[index] = dis.readInt();
			Peer.friendsChunkNum[index] = dis.readInt();
			Peer.friendsChunkMap[index] = new byte[Peer.friendsChunkNum[index]];
			dis.read(Peer.friendsChunkMap[index], 0, Peer.friendsChunkMap[index].length);
			if (Peer.chunkMap == null) {
				Peer.set_information(Peer.friendsChunkSize[index], Peer.friendsChunkNum[index]);
			}
		}
	}

	// chunk맵을 UploadThread에게 전송한다.
	void send_ChunkMap() throws IOException {
		dos.write(Peer.chunkMap, 0, Peer.chunkMap.length);
		dos.flush();
	}

	
	// 현재 내 청크와 uploadThread가 가지고 있는 청크맵을 비교해서
	// 현재 내가 가지고 있지 않은 청크의 인덱스를 찾아낸다.
	// 이후 가지고 있지 않은 청크 인덱스 배열을 반환한다.
	int[] find_missing_index(byte[] temp) {
		byte[] current = Peer.chunkMap;
		// 결과가 담기는 배열 -1로 초기화.
		int[] result = new int[Peer.chunkNum];
		for (int k = 0; k < result.length; k++) {
			result[k] = -1;
		}

		int i = 0, j = 0;
		int count = 0;

		// 내가 가지고 있지 않은 청크의 인덱스를 result에 추가해준다.
		while (count < Peer.chunkNum && i < current.length) {
			if ((current[i] == (byte) 0) && (temp[i] == (byte) 1)) {
				count++;
				result[j] = i;
				j++;
			}
			i++;
		}
		// 해당 배열 반환.
		return result;
	}

	// 파라미터로 전달받은 청크맵이 가득차있는지 확인해준다.
	// 비어있다 -> false, 가득차있다 -> true
	public boolean checkChunkFull(byte[] chunkMap) {
		// 청크맵이 초기화되지 않은 경우,
		if (chunkMap == null) {
			return false;
		}
		// 청크맵이 비어있지 않은 경우.
		for (int i = 0; i < chunkMap.length; i++) {
			if (chunkMap[i] == (byte) 0)
				return false;
		}
		// 청크맵이 비어있는 경우.
		return true;
	}

	// 모든 피어가 Seeder인지 확인하는 함수.
	// 모든 피어가 Seeder이다 -> true, 모든 피어가 Seeder가 아니다 -> false
	boolean allPeerIsSeeder() {
		for (int i = 0; i < Peer.MAX_PEER_SIZE; i++) {
			if (Peer.seeders[i] == 0) {
				return false;
			}
		}
		return true;
	}

	// 친구 리스트에 존재하는지 확인하는 함수.
	// 해당 피어의 인덱스가 친구 목록에 존재한다 -> true, 친구목록에 존재하지 않는다 -> false
	boolean existInFriendList(int index) {
		for (int i = 0; i < Peer.friendsArr.length; i++) {
			if (index == Peer.friendsArr[i]) {
				return true;
			}
		}
		return false;
	}

	// 다른 쓰레드의 동시접근을 막는 synchronized 함수
	// friendsIndex를 동시에 증가시키는 경우를 막음.
	// 해당 피어의 friendArr의 인덱스를 받아서 
	synchronized int getFriendArrIndex() {
		Peer.friendsIndex += 1;
		Peer.friendsIndex = Peer.friendsIndex % 3;
		return Peer.friendsIndex;
	}

	// 다른 쓰레드의 동시접근을 막는 synchronized 함수
	// data[][]배열에 동시에 chunk를 쓰는 경우를 막음.
	// uploadThread에서 byte[]를 받아서 Peer의 data[][]배열에 저장시킨다.
	synchronized void readData(int receivedChunknumber) throws IOException {
		Peer.chunkMap[receivedChunknumber] = (byte) 1;
		recvStream.read(Peer.data[receivedChunknumber]);
	}

	// 쓰레드 실행
	public void process() {
		System.out.println("================================================");
		System.out.println("               Client Thread Start              ");
		System.out.println("================================================");
		int timeout = 5000;
		while (!checkChunkFull(Peer.chunkMap)) {
			try {
				System.out.println("Client - 소켓 연결을 시도합니다.");
				SocketAddress socketAddress = new InetSocketAddress(Peer.domain_arr[index], Peer.port_arr[index]);
				connectionSocket = new Socket();
				connectionSocket.setSoTimeout(timeout);
				connectionSocket.connect(socketAddress, timeout);
				recvStream = connectionSocket.getInputStream();
				sendStream = connectionSocket.getOutputStream();
				dis = new DataInputStream(recvStream);
				dos = new DataOutputStream(sendStream);
				System.out.println(Peer.domain_arr[index] + ":" + Peer.port_arr[index] + " 와 연결되었음");

				// 친구 리스트에 이미 들어있는지 검사 (다른 쓰레드와 이미 친구관계를 맺고있음)
				boolean alreadyConnected = false;
				if (existInFriendList(index)) {
					System.out.println("Client Thread - 이미 가지고 있는 친구입니다. 종료됩니다.");
					alreadyConnected = true;
					sendRequest("Already_Friend");
				}
				
				// 친구 리스트에 들어있지 않은 경우에.
				if (!alreadyConnected) {
					System.out.println("Client Thread - 친구 리스트에 없습니다. 새로운 연결을 시작합니다.");
					int friendArrIndex = getFriendArrIndex();
					Peer.friendsArr[friendArrIndex] = index;
					boolean isTimeOut = false;
					while (true) {
						try {
							Thread.sleep(3000);
							// 서버가 청크 있는지 없는지 물어보기. 있다면 청크맵도 받아옴.
							sendRequest("Request_Information");
							recv_information(index);

							// 청크가 있다고 답할 경우.
							if (!Peer.friendsFileName[index].equals("null")) {
								
								// 자신의 청크맵 전송 (UploadThread에서도 연결요청을 한 피어의 청크맵을 관리함.
								sendRequest("Send_ChunkMap");
								send_ChunkMap();
								// 만약, 청크가 가득 찬 경우, 연결 해제를 위한 반복문 탈출.
								if (checkChunkFull(Peer.chunkMap)) {
									break;
								}
								
								// 모자란 청크 개수 찾기.
								int[] missing_list = find_missing_index(Peer.friendsChunkMap[index]);
								int total = 0;
								for (int i = 0; i < missing_list.length; i++) {
									if (missing_list[i] != -1) {
										total++;
									}
								}

								System.out.println("Client - 부족한 청크 개수 : " + total + "개");
								
								int[] randomChunk = new int[3];
								int randomChunkLength;
								
								for (int i = 0; i < 3; i++) {
									randomChunk[i] = -1;
								}
								
								if (total > 3) {
									randomChunkLength = 3;
								} else {
									randomChunkLength = total;
								}
								

								// 원하는 청크 랜덤으로 고르기.
								Random r = new Random();
								for (int i = 0; i < randomChunkLength; i++) {
									randomChunk[i] = missing_list[r.nextInt(total)];
									for (int j = 0; j < i; j++) {
										if (randomChunk[i] == randomChunk[j]) {
											i--;
										}
									}
								}
								
								// 청크 요청하기
								sendRequest("Request_Chunks");
								dos.writeInt(randomChunkLength);
								dos.flush();
								
								// 랜덤청크 보내기.(최대 3개) 
								for (int i = 0; i < randomChunkLength; i++) {
									dos.writeInt(randomChunk[i]);
									dos.flush();
								}

								// 청크 요청 3개를 무사히 받은 경우에는 Received라는 응답이 되돌아옴.
								// 이후 해당 인덱스를 가진 chunk를 data배열에 저장시킴.


								for (int i = 0; i < randomChunkLength; i++) {
									readData(randomChunk[i]);
								}
								// 완료 요청.
								sendRequest("Completed");

								// 요청을 무사히 보냈으면 timeout이 아님.
								isTimeOut = false;

								// 서버가 청크 없는 경우 -> 연결해제. 다른녀석한테 요청하게끔.
							} else {
								System.out.println("Client - 연결한 서버가 청크 없음");
								break;
							}
						} catch (SocketTimeoutException e) {
							// 10초간 응답이 없는경우 -> 연결해제. 다른녀석한테 요청하게
							if (isTimeOut == true) {
								System.out.println("Client - 시간 만료. 다른 요청 찾기.");
								break;
							}
							// 5초간 응답을 못받은 경우 -> 일단 처음부터 다시 물어보면서 5초간 기다려봄.
							isTimeOut = true;
							System.out.println("Client - 5초간 응답이 없음. 비트맵 재전송");
						} catch (IOException e) {
							e.printStackTrace();
							System.out.println("Client - 입출력 에러.");
						}
						// 다시 반복문 시작.
					}

					// 친구 연결 해제 (친구 리스트에서 인덱스 삭제)
					Peer.friendsArr[friendArrIndex] = -1;
					recvStream.close();
					sendStream.close();
					dos.close();
					dis.close();
				}
			} catch (ConnectException e) {
				System.out.println("청크다운로드: 5초간 응답이 없어 " + Peer.domain_arr[index] + ":" + Peer.port_arr[index]
						+ "으로 연결에 실패했습니다. 다음 주소로 연결을 시도합니다.");
				try {
					Thread.sleep(5000);
				} catch (Exception s) {
					e.printStackTrace();
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					connectionSocket.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			// 인덱스 증가시키기 (다음 피어 결정) - 1,2,3,4 중에서 반복.
			index = ((index) % (Peer.MAX_PEER_SIZE - 1)) + 1;
		}
		// 현재 피어를 seeder로 설정. (config.txt파일의 인덱스를 따름)
		Peer.seeders[0] = 1;
		System.out.println("Client Thread - 파일 다운로드 종료");
		System.out.println("Client Thread -  파일 만들기.");
		// 파일 만들기
		try {
			FileOutputStream fos = new FileOutputStream(Peer.torrent_dir + "/" + Peer.torrent_filename);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			for (int i = 0; i < Peer.chunkNum; i++) {
				bos.write(Peer.data[i], 0, Peer.data[i].length);
			}
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// 시작인덱스로 다시 인덱스 초기화.
		index = firstindex;
		
		// 파일 다운로드가 다 끝나고, 이제 서버 종료를 위해 다른 피어들이 시더인지 판별함
		// 다른 피어가 모두 시더이면 -> 연결요청을 보낼 쓰레드가 더이상 존재하지 않으므로
		// 						 서버를 종료시킵니다.
		while (!allPeerIsSeeder()) {
			try {
				Thread.sleep(5000);
				SocketAddress socketAddress = new InetSocketAddress(Peer.domain_arr[index], Peer.port_arr[index]);
				connectionSocket = new Socket();
				connectionSocket.connect(socketAddress, timeout);
				recvStream = connectionSocket.getInputStream();
				sendStream = connectionSocket.getOutputStream();
				dis = new DataInputStream(recvStream);
				dos = new DataOutputStream(sendStream);
				System.out.println(Peer.domain_arr[index] + ":" + Peer.port_arr[index] + " 와 연결되었음");
				
				// Request_Seeder요청을 보냄.
				sendRequest("Request_Seeder");
				// 해당 index의 시더여부를 변경함. (seeder = 1, downloader = 0)
				Peer.seeders[index] = dis.readInt();
				recvStream.close();
				sendStream.close();
				dos.close();
				dis.close();

			} catch (SocketException e) {
				System.out.println("피어찾기: " + Peer.domain_arr[index] + ":" + Peer.port_arr[index]
						+ " 연결에 실패했습니다. 다음 주소로 연결을 시도합니다.");
				try {
					Thread.sleep(5000);
				} catch (Exception s) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					connectionSocket.close();
				} catch (Exception e) {
				}
			}
			// 인덱스 증가시키기.
			index = ((index) % (Peer.MAX_PEER_SIZE - 1)) + 1;
		}
		
		// 모든 피어가 시더가 됨 -> allPeerCompleted를 true로 바꿔서 서버가 종료할 수 있게끔 설정.
		Peer.allPeerCompleted = true;
		System.out.println("================================================");
		System.out.println("               Client Thread End              ");
		System.out.println("================================================");

	}
}