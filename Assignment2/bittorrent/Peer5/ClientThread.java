import java.io.*;
import java.net.*;
import java.util.Random;

public class ClientThread implements Runnable {
	Socket connectionSocket;
	OutputStream sendStream;
	InputStream recvStream;
	DataInputStream dis; // Used data input stream in socket
	DataOutputStream dos; // Used data output stream in socket

	String response; // Response message (connected to seeder)
	String command; // Command message (connected to seeder)

	private int index;
	private int firstindex;

	public ClientThread(int index) {
		this.index = index;
		this.firstindex = index;
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

	void recv_information(int index) throws SocketTimeoutException, IOException {
		Peer.friendsFileName[index] = dis.readUTF();
		System.out.println("Client - peerfilename " + Peer.friendsFileName[index]);
		if (!Peer.friendsFileName[index].equals("null")) {
			Peer.friendsChunkSize[index] = dis.readInt();
			Peer.friendsChunkNum[index] = dis.readInt();
			Peer.friendsChunkMap[index] = new byte[Peer.friendsChunkNum[index]];
			dis.read(Peer.friendsChunkMap[index], 0, Peer.friendsChunkMap[index].length);
			if (Peer.chunkMap == null) {
				Peer.set_information(Peer.friendsChunkSize[index], Peer.friendsChunkNum[index]);
			}
		}
	}

	void send_ChunkMap() throws IOException {
		dos.write(Peer.chunkMap, 0, Peer.chunkMap.length);
		dos.flush();
	}

	/*
	 * Find missing chunk ID list by comparing with the other peer's chunk ID list.
	 * The number of missing ID's is limited 50 because of simulation.
	 */
	int[] find_missing_index(byte[] temp) {
		byte[] current = Peer.chunkMap;
		int[] result = new int[Peer.chunkNum];
		for (int k = 0; k < result.length; k++) {
			result[k] = -1;
		}

		int i = 0, j = 0;
		int count = 0;

		while (count < Peer.chunkNum && i < current.length) {
			if ((current[i] == (byte) 0) && (temp[i] == (byte) 1)) {
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
			if (chunkMap[i] == (byte) 0)
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

	boolean allPeerIsSeeder() {
		for (int i = 0; i < Peer.MAX_PEER_SIZE; i++) {
			if (Peer.seeders[i] == 0) {
				return false;
			}
		}
		return true;
	}

	boolean existInFriendList(int index) {
		for (int i = 0; i < Peer.friendsArr.length; i++) {
			if (index == Peer.friendsArr[i]) {
				return true;
			}
		}
		return false;
	}

	synchronized int getFriendArrIndex() {
		Peer.friendsIndex += 1;
		Peer.friendsIndex = Peer.friendsIndex % 3;
		return Peer.friendsIndex;
	}

	synchronized void writeData(int receivedChunknumber) throws IOException {
		Peer.chunkMap[receivedChunknumber] = (byte) 1;
		recvStream.read(Peer.data[receivedChunknumber]);

	}

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
				System.out.println("================================================");
				System.out.println("      Client Thread 서버연결 완료 - 청크다운로드      ");
				System.out.println("================================================");
				System.out.println(Peer.domain_arr[index] + ":" + Peer.port_arr[index] + " 와 연결되었음");

				boolean alreadyConnected = false;
				if (existInFriendList(index)) {
					System.out.println("Client Thread - 이미 가지고 있는 친구입니다. 종료됩니다.");
					alreadyConnected = true;
					sendRequest("Already_Friend");
				}

				if (!alreadyConnected) {
					System.out.println("Client Thread - 친구 리스트에 없습니다. 새로운 연결을 시작합니다.");
					System.out.println();
					int friendArrIndex = getFriendArrIndex();
					Peer.friendsArr[friendArrIndex] = index;
					boolean isTimeOut = false;
					while (true) {
						try {
							Thread.sleep(3000);
							// 서버가 청크 있는지 없는지 물어보기. 있다면 청크맵도 받아옴.
							System.out.println("Client - 정보 물어보기");
							sendRequest("Request_Information");
							recv_information(index);
							System.out.println("Client - 정보 물어보기 완료");

							// 청크가 있다고 답할 경우.
							if (!Peer.friendsFileName[index].equals("null")) {
								System.out.println("Client - 청크를 가지고 있는 서버와 연결");

								// 모자란 청크 개수 찾기.
								int[] missing_list = find_missing_index(Peer.friendsChunkMap[index]);
								int total = 0;
								for (int i = 0; i < missing_list.length; i++) {
									if (missing_list[i] != -1) {
										total++;
									}
								}

								System.out.println("Client - 비트맵 비교 --- 모자란 청크 개수(total) " + total);

								sendRequest("Send_ChunkMap");
								send_ChunkMap();

								if (checkChunkFull(Peer.chunkMap)) {
									break;
								}
								// 청크 요청하기
								System.out.println("Client - 청크 요청하기");
								sendRequest("Request_Chunks");
								dos.writeInt(total);

								int[] randomChunk;
								if (total > 3) {
									randomChunk = new int[3];
								} else {
									randomChunk = new int[total];
								}
								// 원하는 청크 랜덤으로 고르기.
								Random r = new Random();
								for (int i = 0; i < randomChunk.length; i++) {
									randomChunk[i] = missing_list[r.nextInt(total)];
									for (int j = 0; j < i; j++) {
										if (randomChunk[i] == randomChunk[j]) {
											i--;
										}
									}
								}

								// 랜덤청크 보내기.
								for (int i = 0; i < randomChunk.length; i++) {
									dos.writeInt(randomChunk[i]);
								}
								dos.flush();

								response = getResponse();
								if (response.equals("Received")) {
									System.out.println("Client - 쓰기완료");

									for (int i = 0; i < randomChunk.length; i++) {
										writeData(randomChunk[i]);
										System.out.println("Client - " + i + "번째 미싱청크 받기 number = " + randomChunk[i]);
									}
								}
								System.out.println("Client - 최대 3개의 청크 요청 읽기 완료");

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
					System.out.println("Client - 소켓연결 종료 다음녀석 찾기");
					connectionSocket.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			index = ((index) % (Peer.MAX_PEER_SIZE - 1)) + 1;
		}
		Peer.seeders[0] = 1;
		System.out.println("Client Thread - 파일 다운로드 종료");
		System.out.println("Client Thread -  파일 만들기.");
		try {
			FileOutputStream fos = new FileOutputStream(Peer.torrent_dir + "/" + Peer.torrent_filename);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			for (int i = 0; i < Peer.chunkNum; i++) {
				bos.write(Peer.data[i], 0, Peer.data[i].length);
			}
			fos.close();
			bos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		index = firstindex;
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
				System.out.println("================================================");
				System.out.println("       Client Thread 서버연결 완료 - 시더판별         ");
				System.out.println("================================================");
				System.out.println(Peer.domain_arr[index] + ":" + Peer.port_arr[index] + " 와 연결되었음");
				sendRequest("Request_Seeder");
				System.out.println("Client - 요청 Request_Seeder");
				Peer.seeders[index] = dis.readInt();
				System.out.println();
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
				System.out.println("Client - Exception이 발생했습니다.");
				e.printStackTrace();
			} finally {
				try {
					connectionSocket.close();
				} catch (Exception e) {
					System.out.println("Client - 소켓 닫기 오류.");
					e.printStackTrace();
				}
			}
			index = ((index) % (Peer.MAX_PEER_SIZE - 1)) + 1;
		}
		try {
			// 서버 꺼지기전에 잠깐 기다려줌. 나머지 연결 처리.
			Thread.sleep(10000);
		} catch (Exception e) {
			e.printStackTrace();
		}
		Peer.allPeerCompleted = true;
		System.out.println("================================================");
		System.out.println("               Client Thread End              ");
		System.out.println("================================================");

	}
}