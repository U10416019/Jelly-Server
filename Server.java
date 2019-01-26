import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.awt.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Date;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

public class Server {

    private Connection con = null;
    private Statement st = null;
    private ResultSet rs = null;
    private Statement stat = null;
    private ResultSet resu = null;
    private ServerSocket serverSocket;
    private ServerThread serverThread;
    private ServerSocket fileSocket;
    private FileThread fileThread;
    private ArrayList<ClientThread> clientList;
    private ArrayList<Long> onlineList = new ArrayList<Long>();
    private ArrayList<Long> sendList = new ArrayList<Long>();
    private User user;
    private boolean isStart = false;
    String receive = "", show = "";
    

    public Server() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            con = DriverManager.getConnection(
                    "jdbc:mysql://163.21.245.147:3306/testClient?useUnicode=true&characterEncoding=utf-8&useSSL=false",
                    "jessie33", "j5e0s733");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try {
            openServer();
        } catch (BindException e) {
            e.printStackTrace();
        }
    }

    public void openServer() throws BindException {
        try {
            clientList = new ArrayList<ClientThread>();
            serverSocket = new ServerSocket(6666);
            System.out.println("[Server] Server Open...");
            isStart = true;
            serverThread = new ServerThread(serverSocket);
            serverThread.start();

            fileSocket = new ServerSocket(8888);
            fileThread = new FileThread(fileSocket);
            fileThread.start();
        } catch (BindException e) {
            isStart = false;
            e.printStackTrace();
        } catch (Exception e) {
            isStart = false;
            e.printStackTrace();
        }
    }

    public class ServerThread extends Thread {

        private ServerSocket serverSocket;

        public ServerThread(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        public void run() {
            while (isStart) {
                try {
                    Socket socket = serverSocket.accept();

                    System.out.println("[Server] " + socket.getRemoteSocketAddress().toString() + " has connected.");
                    ClientThread client = new ClientThread(socket);
                    client.start();
                    clientList.add(client);
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }

        }
    }

    public class FileThread extends Thread {

        private ServerSocket fileSocket;

        public FileThread(ServerSocket fileSocket) {
            this.fileSocket = fileSocket;
            System.out.println("[Server] FileThread Open...");
        }

        public void run() {
            while (true) {
                try {
                    Socket socket = fileSocket.accept();
                    System.out.println("[Server] " + socket.getRemoteSocketAddress().toString() + " has connected.");
                    BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                    String msg = br.readLine();
                    if (msg.equals("RECEIVEFILE")) {
                        String filename = br.readLine();
                        sendFile(socket, br, filename);
                    } else if (msg.equals("SENDFILE")) {
                        String filename = br.readLine();
                        int type = Integer.parseInt(br.readLine());
                        bw.write("go\n");
                        bw.flush();

                        receiveFile(socket, br, filename);
                        if (type == 2) {
                            imageRisize(filename);
                        } else if (type == 3) {
                            sendFileMessage();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class ClientThread extends Thread {

        private Socket socket;
        private BufferedReader br;
        private BufferedWriter bw;
        private User user;
        boolean flag = false;

        public ClientThread(Socket socket) {
            try {
                this.socket = socket;
                br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                flag = true;
                user = new User(0, "", "", socket.getRemoteSocketAddress().toString());
                System.out.println("[Server] ClientTread starts to wait messages...");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                String msg = null;
                while ((msg = br.readLine()) != null && flag) {
                    StringTokenizer st = new StringTokenizer(msg, "$");
                    String state = st.nextToken();
                    System.out.println("[Client] " + user.getShowname() + " send : " + state);
                    if (state.equals("USERPASS")) {
                        String username = st.nextToken();
                        String pass = st.nextToken();
                        System.out.println(">>> " + username + " " + pass);

                        long id = searchUser(username, user);
                        if (id == 0) {
                            bw.write("LOGIN$noExist\n");
                            bw.flush();
                            System.out.println("no exist");
                        } else {
                            if (isPasswordCorrect(id, pass)) {
                            	String image = getUserImage(username);
                            	System.out.println("userid: " + id + "userImage: " + image);
                                bw.write("LOGIN$correct$" + id + "$" + user.getUsername() + "$" + user.getShowname() + "$" + image);
                                bw.flush();
                                System.out.println("password correct");
                            } else {
                                bw.write("LOGIN$incorrect\n");
                                bw.flush();
                                System.out.println("password incorrect");
                            }
                        }
                    } else if (state.equals("SIGNUP")) {
                        long id = Long.parseLong(st.nextToken());
                        String name = st.nextToken();
                        String pass = st.nextToken();
                        String image = st.nextToken();
                        System.out.println(">>> " + name + " " + pass + " " + image);

						if(isSameAccount(name)){
							if (isSignupCorrect(id, name, pass, image)) {
	                            bw.write("SIGNUP$finish$id$name$image");
	                            bw.flush();
    	                        System.out.println("finish");
        	                } else {
            	                bw.write("SIGNUP$error\n");
                	            bw.flush();
                    	        System.out.println("error");
                        	}
						}
						else{
							bw.write("SIGNUP$sameAccount\n");
                	        bw.flush();
                    	    System.out.println("sameAccount");
						}
                        
                    }
                    else if(state.equals("LOADQUEUE")){
                    	long user_id = Long.parseLong(st.nextToken());
                    	handleQueue(user_id);
                    }
                     else if (state.equals("LOADRECORD")) {
                        long id = Long.parseLong(st.nextToken());
                        long last = Long.parseLong(st.nextToken());
                        System.out.println(">>> " + id + " " + last);
                        String str = loadRecord(id, last);
                        bw.write(str);
                        bw.flush();
                    } else if (state.equals("LOADFRIEND")) {
                        String str = loadFriend(Long.parseLong(st.nextToken()));
                        bw.write(str);
                        bw.flush();
                        //handleQueue(user.getId());
                    } else if (state.equals("LOADADDFRIEND")) {
                        String str = loadAddFriend(Long.parseLong(st.nextToken()));
                        bw.write(str);
                        bw.flush();
                        //handleQueue(user.getId());
                    } else if (state.equals("LOADROOM")) {
                    	
                        String str = loadRoom(Long.parseLong(st.nextToken()));
                        bw.write(str);
                        bw.flush();
                        //handleQueue(user.getId());
                        
                    } else if (state.equals("LOGIN")) {
                        System.out.print("[Server] ClientList : ");
                        for (int j = 0; j < clientList.size(); j++) {
                            System.out.print(clientList.get(j).getUser().getShowname() + " ");
                        }
                        System.out.println();
                        
                        onlineList.add(user.getId());
                        System.out.print("[Server] Online : ");
                        if (onlineList.isEmpty()) {
                            System.out.print("null");
                        } else {
                            for (int i = 0; i < onlineList.size(); i++) {
                                for (int j = 0; j < clientList.size(); j++) {
                                    if (onlineList.get(i) == clientList.get(j).getUser().getId()) {
                                        System.out.print(clientList.get(j).getUser().getShowname() + " ");
                                        break;
                                    }
                                }
                            }
                        }
                        System.out.println();
                        //handleQueue(user.getId());
                    } else if (state.equals("LOGOUT")) {
                        System.out.print("[Server] ClientList : ");
                        for (int j = 0; j < clientList.size(); j++) {
                            System.out.print(clientList.get(j).getUser().getShowname() + " ");
                        }
                        System.out.println();
                        
                        System.out.print("[Server] Online : ");
                        for (int i = 0; i < onlineList.size(); i++) {
                            System.out.print(onlineList.get(i) + " ");
                        }
                        System.out.println();
                        
                        System.out.println("remove " + user.getId());
                        if (onlineList.isEmpty()) {
                            System.out.print("null");
                        } else {
                            for (int i = 0; i < onlineList.size(); i++) {
                                if (onlineList.get(i) == user.getId()) {
                                    onlineList.remove(i);
                                    break;
                                }
                            }
                        }
                        System.out.print("[Server] Online : ");
                        for (int i = 0; i < onlineList.size(); i++) {
                            System.out.print(onlineList.get(i) + " ");
                        }
                        System.out.println();
                    } else if (state.equals("MESSAGE")) {
                        long room_id = Long.parseLong(st.nextToken());
                        long friend_id = Long.parseLong(st.nextToken());
                        String room_name = st.nextToken();
                        long user_id = Long.parseLong(st.nextToken());
                        String user_name = st.nextToken();
                        String message = st.nextToken();
                        long send_time = System.currentTimeMillis();
                        int type = 0;
                        
                        Date date = new Date(send_time);
		                Locale locale = new Locale("zh", "zh_TW");
						SimpleDateFormat df = new SimpleDateFormat("hh:mm a");
						//SimpleDateFormat df = new SimpleDateFormat("hh:mm a", locale);
						//df.format(date)
				
                        bw.write("RESPONSE$" + room_id + "$" + user_id + "$" + user_name + "$" + room_name + "$" + df.format(date) + "$" + type + "$" + message + "\n");
                        bw.flush();
                        handleMessage(user.getShowname(), user.getId(), room_id, room_name, send_time, type, message);
                    } else if (state.equals("RESPONSE")) {
                        long room_id = Long.parseLong(st.nextToken());
                        System.out.print("room_id : " + room_id);
                        long sender_id = Long.parseLong(st.nextToken());
                        System.out.print("  sender_id : " + sender_id);
                        long send_time = Long.parseLong(st.nextToken());
                        System.out.print("  send_time : " + send_time);
                        int type = Integer.parseInt(st.nextToken());
                        System.out.print("  type : " + type);
                        String message = st.nextToken();
                        System.out.print("  message : " + message);
                        if (type == 1 || type == 3) {
                            message = message + "$" + st.nextToken();
                        }
                        System.out.print("  get id : " + user.getId() + "\n");
                        responseMessage(room_id, sender_id, send_time, user.getId(), type, message);
                    }
                    else if(state.equals("INSERTQUEUE")){
                    	String sender = st.nextToken();
                    	long sender_id = Long.parseLong(st.nextToken());
                    	long sendto = Long.parseLong(st.nextToken());
                    	long room_id = Long.parseLong(st.nextToken());
                    	long send_time = Long.parseLong(st.nextToken());
                    	int type = Integer.parseInt(st.nextToken());
                    	String message = st.nextToken();
                    	
                    	insertQueue(sender, sender_id, sendto, room_id, send_time, type, message);
                    
                    }
                    // else if(state.equals("GETQUEUEMESSAGE")){
//                     	long user_id = Long.parseLong(st.nextToken());
//                     	handleQueue(user_id);
//                     
//                     }
					else if (state.equals("SENDFILE")) {
                        long room_id = Long.parseLong(st.nextToken());
                        String room_name = st.nextToken();
                        long id = Long.parseLong(st.nextToken());
                        int type = Integer.parseInt(st.nextToken());
                        String filepath = st.nextToken();
                        String filename = st.nextToken();
                        String filesize = st.nextToken();
                        long send_time = System.currentTimeMillis();
                        bw.write("SENDFILE$" + filepath + "$" + filename + "$" + type + "\n");
                        bw.flush();

                        bw.write("RESPONSE$" + room_id + "$" + id + "$" + send_time + "$" + type + "$" + filename + "$" + filesize + "\n");
                        bw.flush();
                        
                        if (type == 3) {
                            sendList.clear();
                            receive = "RECEIVEFILE$./File/" + filename + "$" + filename + "$" + type + "\n";
                            show = "MESSAGE$" + user.getShowname() + "$" + user.getId() + "$" + room_id + "$" + send_time + "$"
                                    + type + "$" + filename + "$" + filesize + "\n";
                        }                       
                        handleMessage(user.getShowname(), user.getId(), room_id, room_name, send_time, type, filename + "$" + filesize);
                    } else if (state.equals("SENDIMAGE")) {
                        long room_id = Long.parseLong(st.nextToken());
                        String room_name = st.nextToken();
                        long id = Long.parseLong(st.nextToken());
                        String filepath = st.nextToken();
                        String filename = st.nextToken();
                        long send_time = System.currentTimeMillis();
                        int type = 2;

                        bw.write("SENDFILE$" + filepath + "$" + filename + "$" + type + "\n");
                        bw.flush();

                        bw.write("RESPONSE$" + room_id + "$" + id + "$" + send_time + "$" + type + "$" + filename + "\n");
                        bw.flush();

                        sendList.clear();
                        sendList.add(user.getId());
                        handleMessage(user.getShowname(), user.getId(), room_id, room_name, send_time, type, filename);                        
                        
                        receive = "RECEIVEIMAGE$" + room_id + "$" + user.getShowname() + "$" + send_time + "$./File/archive" 
                                        + filename + "$" + filename + "$" + type + "\n";                       
                        show = "MESSAGE$" + user.getShowname() + "$" + user.getId() + "$" + room_id + "$"
                                + send_time + "$" + type + "$" + filename + "\n";                        
                    } else if (state.equals("REMOVE")) {
                        long room_id = Long.parseLong(st.nextToken());
                        String sender = st.nextToken();
                        long time = Long.parseLong(st.nextToken());
                        int type = Integer.parseInt(st.nextToken());
                        String message = st.nextToken();
                        String filesize = "";
                        if (type == 1) {
                            filesize = st.nextToken();
                            message = message + "$" + filesize;
                        }                        
                        removeMessage(room_id, sender, user.getId(), time, type, message);
                    } else if (state.equals("REMOVERESPONE")) {
                        
                    } else if (state.equals("ISFRIEND")) {
                        long user_id = Long.parseLong(st.nextToken());
                        String friend_name = st.nextToken();
                        String str = isFriend(user_id, friend_name);
                        bw.write(str);
                        bw.flush();
                    } else if (state.equals("NEWROOM")) {
                        long room_id = Long.parseLong(st.nextToken());
                        String room_name = st.nextToken();
                        long friend_id = Long.parseLong(st.nextToken());
                        String user_name = st.nextToken();
                        long user_id = Long.parseLong(st.nextToken());
                        int isgroup = Integer.parseInt(st.nextToken());
                        int hide = Integer.parseInt(st.nextToken());
                        long get_room_id = newRoom(room_id, room_name, friend_id, user_name, user_id, isgroup);
                        bw.write("GETROOMID$" + get_room_id + "\n");
                        bw.flush();
                    } else if (state.equals("NEWGROUP")) {
                        long room_id = Long.parseLong(st.nextToken());
                        String room_name = st.nextToken();
                        //newRoom(room_id, room_name, friend_id, user_name, user.getId(), 1);
                        ArrayList<String> list = new ArrayList<String>();
                        while (st.hasMoreTokens()) {
                            list.add(st.nextToken());
                        }
                        newGroup(room_id, room_name, user.getShowname(), user.getId(), list);
                    } else if (state.equals("SEARCHFRIEND")) {
                        long user_id = Long.parseLong(st.nextToken());
                        String search = st.nextToken();
                        String str = searchFriend(user_id, search);
                        bw.write(str);
                        bw.flush();
                    } else if (state.equals("ADDFRIEND")) {
                        long user_id = Long.parseLong(st.nextToken());
                        String name = st.nextToken();
                        long friend_id = Long.parseLong(st.nextToken());
                        System.out.println(">>> " + user_id + " " + name + " " + friend_id);
                        addFriend(user_id, name, friend_id);
                    } else if (state.equals("CONFIRMFRIEND")) {
                        long request_id = Long.parseLong(st.nextToken());
                        long confirm_id = Long.parseLong(st.nextToken());
                        confirmFriend(request_id, confirm_id);
                    }
                    else if(state.equals("LOADROOMMESSAGE")){
                    	long room_id = Long.parseLong(st.nextToken());
                    	long user_id = Long.parseLong(st.nextToken());
                    	System.out.println("in loadroommessage=>  room_id: " + room_id + "  user_id: " + user_id);
                    	String roomMessage = loadRoomMessage(room_id, user_id);
                    	bw.write(roomMessage);
                    	bw.flush();
                    	
                    }
                    else if(state.equals("CHANGEMSGTYPE")){
                    	long room_id = Long.parseLong(st.nextToken());
                    	long user_id = Long.parseLong(st.nextToken());
                    	sendMessage(user_id, "CHANGEMSGTYPE$");
                    
                    }
                    else if(state.equals("ADDNOTE")){
                    	long room_id = Long.parseLong(st.nextToken());
                    	long friend_id = Long.parseLong(st.nextToken());
                    	String friend_name = st.nextToken();
                    	long user_id = Long.parseLong(st.nextToken());
                    	String user_name = st.nextToken();
                    	String message = st.nextToken();
                    	addNote(room_id, friend_id, friend_name, user_id, user_name, message);
                    	bw.write("ADDNOTE$success");
                    	bw.flush();
                    }
                    else if(state.equals("LOADNOTE")){
                    	long user_id = Long.parseLong(st.nextToken());
                    	String loadNoteMsg = loadNote(user_id);
                    	bw.write(loadNoteMsg);
                    	bw.flush();
                    
                    }
                    else if(state.equals("LOADFRIENDNOTE")){
                    	long user_id = Long.parseLong(st.nextToken());
                    	long room_id = Long.parseLong(st.nextToken());
                    	String loadFriendNoteMsg = loadFriendNote(user_id, room_id);
                    	bw.write(loadFriendNoteMsg);
                    	bw.flush();
                    
                    }
                    else if(state.equals("DELETENOTE")){
                    	long friend_id = Long.parseLong(st.nextToken());
                    	String friend_name = st.nextToken();
                    	long user_id = Long.parseLong(st.nextToken());
                    	String user_name = st.nextToken();
                    	String message = st.nextToken();
                    	deleteNote(friend_id, friend_name, user_id, user_name, message);
                    	bw.write("DELETENOTE$success");
                    	bw.flush();
                    	
                    } else if (state.equals("CLOSE")) {
                        bw.write("CLOSE\n");
                        bw.flush();
                        flag = false;
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            closeConnect();
        }

        public void closeConnect() {
            try {
                br.close();
                bw.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (int i = 0; i < onlineList.size(); i++) {
                if (onlineList.get(i) == user.getId()) {
					onlineList.remove(i);
                    break;
                }
            }
            for (int i = 0; i < clientList.size(); i++) {
                if (clientList.get(i).getUser().getId() == user.getId()) {
                    clientList.get(i).interrupt();
                    clientList.remove(i);
                    break;
                }
            }
            if (user.getShowname().equals("")) {
                System.out.println("[Server] " + user.getId() + " Program Close...");
            } else {
                System.out.println("[Server] " + user.getShowname() + "'s Program Close...");
            }
            clientList.clear();
            user = null;
        }

        public BufferedWriter getWrite() {
            return bw;
        }

        public User getUser() {
            return user;
        }

    }
    
    
    

    
    public long searchUser(String username, User user) {
        try {
            long id = 0;
            String showname = "";
            st = con.createStatement();
            rs = st.executeQuery("SELECT user_id, showname FROM USER WHERE username = '" + username + "'");
            while (rs.next()) {
                id = rs.getLong("user_id");
                System.out.println("setUserID: " + id);
                showname = rs.getString("showname");
            }
            user.setId(id);
            user.setUsername(username);
            user.setShowname(showname);
            return id;
        } catch (SQLException e) {
            System.out.println(e.toString());
            return 0;
        }
    }

    public boolean isPasswordCorrect(long user_id, String password) {
        try {
            st = con.createStatement();
            rs = st.executeQuery("SELECT password FROM USER WHERE user_id = " + user_id);
            String pass = "";
            while (rs.next()) {
                pass = rs.getString("password");
            }
            if (pass.equals(password)) {
                return true;
            }
        } catch (SQLException e) {
            System.out.println(e.toString());
        }
        return false;
    }
    
    public String getUserImage(String username){
    	try {
            String userImage = "";
            st = con.createStatement();
            rs = st.executeQuery("SELECT image FROM USER WHERE username = '" + username + "'");
            while (rs.next()) {
                userImage = rs.getString("image");
            }
            
            return userImage;
            
        } catch (SQLException e) {
            System.out.println(e.toString());
            return "-1";
        }
    }

	public boolean isSameAccount(String username){
		try {
            long id = 0;
            String showname = "";
            st = con.createStatement();
            rs = st.executeQuery("SELECT user_id, showname FROM USER WHERE username = '" + username + "'");
            while (rs.next()) {
                id = rs.getLong("user_id");
                showname = rs.getString("showname");
            }
            
            if(showname == ""){
            	return true;
    	    }
    	    else{
    	    	return false;
    	    }
            
        } catch (SQLException e) {
            System.out.println(e.toString());
            return false;
        }
	}

    public boolean isSignupCorrect(long user_id, String username, String password, String image) {
    	try {
	        st = con.createStatement();
    	    st.executeUpdate("INSERT INTO USER(user_id, username, showname, password, image) VALUES('" + user_id + "','"
    	            + username + "','" + username + "','" + password + "','" + image + "')");
        	return true;
 	    } catch (SQLException e) {
        	System.out.println(e.toString());
        	return false;
		}
        
        
    }

    public String loadRecord(long user_id, long last) {
        String record = "";
        try {
            st = con.createStatement();
            rs = st.executeQuery("SELECT RECORD.*, USER.showname "
                               + "FROM "
                                    + "RECORD, USER, "
                                    + "("
                                        + "SELECT room_id FROM ROOM WHERE user_id = " + user_id
                                    + ") R "
                               + "WHERE RECORD.room_id = R.room_id "
                               + "AND RECORD.sender_id = USER.user_id "
                               + "AND RECORD.send_time > " + last);
            while (rs.next()) {
                long room_id = rs.getLong("room_id");
                String showname = rs.getString("showname");
                long send_time = rs.getLong("send_time");
                int type = rs.getInt("type");
                String message = rs.getString("message");           
                System.out.println(">>> showname:" + showname + " type:" + type + " message:" + message);            
                record += room_id + "$" + showname + "$" + send_time + "$" + type + "$" + message + "$";
            }
            return record + "\n";
        } catch (SQLException e) {
            System.out.println(e.toString());
            return null;
        }
    }

    public String loadFriend(long user_id) {
        String friend = "LOADFRIEND$";
        try {
            st = con.createStatement();
            rs = st.executeQuery("SELECT friend_id, showname FROM USER, FRIENDSHIP WHERE FRIENDSHIP.user_id = "
                    + user_id + " AND FRIENDSHIP.friend_id = USER.user_id");
            System.out.print(">>> ");
            while (rs.next()) {
            	String friend_image = "";
            	stat = con.createStatement();
			    resu = stat.executeQuery("SELECT image FROM USER WHERE user_id = " + user_id);
            	while (resu.next()) {
 			       friend_image = resu.getString("image");
        	    }
        	    
                System.out.print(rs.getString("showname") + " ");
                friend += rs.getLong("friend_id") + "$" + rs.getString("showname") + "$" + friend_image + "$";
            }

            if (friend.equals("")) {
                System.out.print("null");
            }
            System.out.println();
            return friend;
        } catch (SQLException e) {
            System.out.println(e.toString());
            return null;
        }
    }

    public String loadRoom(long user_id) {
        String room = "LOADROOM$";
        String lastRecord = "";
        String lastQueue = "";
        long lastSendTime = 0;
        String lastTimeFormat = "";
        long lastSenderID = 0;
        int lastType = 0;
        String lastSenderName = "";
        //String lastSenderImage = "";
        long roomID = 0;
        long friendID = 0;
        String roomImage = "";
        String roomName = "";
        int unreadCount = 0;
        
        try {
            st = con.createStatement();
            rs = st.executeQuery("SELECT * " + "FROM " + "ROOM, " + "("
                    + "SELECT room_id, COUNT(*) AS count FROM ROOM GROUP BY room_id HAVING count(*) > 0"
                    + ") ROOM_GROUP " + "WHERE " + "ROOM.room_id = ROOM_GROUP.room_id "
                    + "AND ROOM.user_id = " + user_id);
            System.out.print(">>> ");
            while (rs.next()) {
            	roomID = rs.getLong("room_id");
            	unreadCount = 0;
            	lastRecord = "";
            	lastQueue = "";
            	lastSendTime = 0;
            	lastTimeFormat = "";
        		lastSenderID = 0;
        		lastType = 0;
        		lastSenderName = "";
        		friendID = 0;
        		roomImage = "";
        		roomName = "";
            	
            	stat = con.createStatement();
			    resu = stat.executeQuery("SELECT * FROM RECORD WHERE room_id = " + roomID);
			    while(resu.next()){
			    	lastSenderID = resu.getLong("sender_id");
			    	lastSendTime = resu.getLong("send_time");
			    	lastType = resu.getInt("type");
			    	lastRecord = resu.getString("message");
			    }
			    System.out.println("Last Record =>  lastSenderID: " + lastSenderID + "  lastSendTime: " + lastSendTime + "  lastType: " + lastType + "  lastRecord: " + lastRecord);
            	
            	stat = con.createStatement();
            	resu = stat.executeQuery("SELECT * FROM QUEUE WHERE room_id = " + roomID);
			    while(resu.next()){
			    	unreadCount++;
			    	lastSenderID = resu.getLong("sender_id");
			    	lastSendTime = resu.getLong("send_time");
			    	lastType = resu.getInt("type");
			    	lastQueue = resu.getString("message");
			    }
			    System.out.println("Last Queue =>  lastSenderID: " + lastSenderID + "  lastSendTime: " + lastSendTime + "  lastType: " + lastType + "  lastQueue: " + lastQueue);
            	
            	if(lastSenderID == user_id || lastRecord == ""){
            		stat = con.createStatement();
            		resu = stat.executeQuery("SELECT user_id FROM ROOM WHERE room_id = " + roomID + " AND user_id != " + user_id);
				    while(resu.next()){
			    		friendID = resu.getLong("user_id");
				    }
				    stat = con.createStatement();
            		resu = stat.executeQuery("SELECT * FROM USER WHERE user_id = " + friendID);
				    while(resu.next()){
				    	roomName = resu.getString("username");
			    		roomImage = resu.getString("image");
				    }
				    
				    unreadCount = 0;
            	}
            	else{
            		stat = con.createStatement();
            		resu = stat.executeQuery("SELECT * FROM USER WHERE user_id = " + lastSenderID);
				    while(resu.next()){
				    	roomName = resu.getString("username");
				    	roomImage = resu.getString("image");
				    }
			    }
			    
			    stat = con.createStatement();
            	resu = stat.executeQuery("SELECT username FROM USER WHERE user_id = " + lastSenderID);
			    while(resu.next()){
		    		lastSenderName = resu.getString("username");
			    }
			    
			    if(lastSendTime != 0){
			    	Date date = new Date(lastSendTime);
					SimpleDateFormat df = new SimpleDateFormat("hh:mm a");
					lastTimeFormat = df.format(date);
			    }
			    
            	if(lastQueue == ""){
            		System.out.println("Send Last Record =>  room_id: " + roomID + "  lastSenderID: " + lastSenderID + "  lastSenderName: " + lastSenderName + "  roomName: " + roomName + "  roomImage: " + roomImage + "  lastRecord: " + lastRecord + "  lastSendTime: " + lastSendTime + "  lastTimeFormat: " + lastTimeFormat + "  lastType: " + lastType + "  unreadCount: " + unreadCount + "  friendID: " + friendID + "  friendName: " + roomName);
            		room += roomID + "$" + lastSenderID + "$" + lastSenderName + "$" + roomName + "$" + roomImage + "$" + lastRecord + "$" + lastSendTime + "$" + lastTimeFormat + "$" + lastType + "$" + unreadCount + "$" + friendID + "$" + roomName + "$";
            	}
            	else{
            		System.out.println("Send Last Queue =>  room_id: " + roomID + "  lastSenderID: " + lastSenderID + "  lastSenderName: " + lastSenderName + "  roomName: " + roomName + "  roomImage: " + roomImage + "  lastQueue: " + lastQueue + "  lastSendTime: " + lastSendTime + "  lastTimeFormat: " + lastTimeFormat + "  lastType: " + lastType + "  unreadCount: " + unreadCount + "  friendID: " + friendID + "  friendName: " + roomName);
            		room += roomID + "$" + lastSenderID + "$" + lastSenderName + "$" + roomName + "$" + roomImage + "$" + lastQueue + "$" + lastSendTime + "$" + lastTimeFormat + "$" + lastType + "$" + unreadCount + "$" + friendID + "$" + roomName + "$";
            	}
            	
                //System.out.print(roomID + "$" + rs.getString("room_name") + "$" + rs.getInt("count") + "$"
                //        + rs.getInt("isgroup") + "\n");
                //room += roomID + "$" + rs.getString("room_name") + "$" + rs.getInt("count") + "$"
                //        + rs.getInt("isgroup") + "$";
            }
            

            if (room.equals("")) {
                System.out.print("null");
            }
            System.out.println();
            return room;
        } catch (SQLException e) {
            System.out.println(e.toString());
            return null;
        }
    }

    public String loadAddFriend(long user_id) {
        String friend = "LOADADDFRIEND$";
        try {
            st = con.createStatement();
            rs = st.executeQuery(
                    "SELECT FRIENDSHIP.user_id, showname FROM USER, FRIENDSHIP WHERE FRIENDSHIP.status = '0' AND FRIENDSHIP.friend_id = "
                    + user_id + " AND FRIENDSHIP.user_id = USER.user_id");
            System.out.print(">>> ");
            while (rs.next()) {
            	String friend_image = "";
            	stat = con.createStatement();
			    resu = stat.executeQuery("SELECT image FROM USER WHERE user_id = " + user_id);
            	while (resu.next()) {
 			       friend_image = resu.getString("image");
        	    }
            
                System.out.print(rs.getString("showname") + " ");
                friend += rs.getLong("user_id") + "$" + rs.getString("showname") + "$" + friend_image + "$";
            }

            if (friend.equals("")) {
                System.out.print("null");
            }
            System.out.println();
            return friend;
        } catch (SQLException e) {
            System.out.println(e.toString());
            return null;
        }
    }

    public void handleQueue(long user_id) {
        try {
            st = con.createStatement();
            rs = st.executeQuery("SELECT * FROM QUEUE WHERE sendto = " + user_id);
            String sendAllMessage = "MESSAGE$";
            int type = 0;
            String sender_image = "";
            while (rs.next()) {
                String sender = rs.getString("sender");
                long sender_id = rs.getLong("sender_id");
                long room_id = rs.getLong("room_id");
                long sendto = rs.getLong("sendto");
                long send_time = rs.getLong("send_time");
                String message = rs.getString("message");
                type = rs.getInt("type");
                
                Date date = new Date(send_time);
                Locale locale = new Locale("zh", "zh_TW");
				SimpleDateFormat df = new SimpleDateFormat("hh:mm a");
				//SimpleDateFormat df = new SimpleDateFormat("hh:mm a", locale);
				//df.format(date)
                
                stat = con.createStatement();
                resu = stat.executeQuery("SELECT image FROM USER WHERE user_id = " + sender_id);
                while (resu.next()) {
                    sender_image = resu.getString("image");
                }
                
                sendAllMessage += sender + "$" + sender_id + "$" + sender_image + "$" + room_id + "$" + send_time + "$" + df.format(date) + "$" + type + "$" + message + "$QUEUE$";
                //System.out.println(">>> " + sendAllMessage);
                //System.out.println(">>> in handleQueue ==>  sender: " + sender + "  sender_id: " + sender_id + "  room_id: " + room_id + "  sendto: " + sendto + "  send_time: " + send_time + "  message: " + message + "  type: " + type);

                if (type == 2) {
                    String filename = "archive" + message;
                    String msg = "RECEIVEFILE$./File/" + filename + "$" + filename + "$" + type + "\n";
                    sendMessage(sendto, msg);
                    msg = "MESSAGE$" + sender + "$" + sender_id + "$" + room_id + "$" + send_time + "$" + type + "$" + message + "\n";
                    sendMessage(sendto, msg);
                } else if (type == 3) {
                    StringTokenizer st = new StringTokenizer(message, "$");
                    String filename = st.nextToken();
                    String spendTime = st.nextToken();
                    String msg = "RECEIVEFILE$./File/" + filename + "$" + filename + "$" + type + "\n";
                    sendMessage(sendto, msg);
                    msg = "MESSAGE$" + sender + "$" + sender_id + "$" + room_id + "$" + send_time + "$" + type + "$" + message + "\n";
                    sendMessage(sendto, msg);
                } else if (type == 4) {
                    String msg = "REMOVE$" + room_id + "$" + sender + "$" + sender_id + "$" + send_time + "$" + 0 + "$" + message + "\n";
                    sendMessage(sendto, msg);
                } else if (type == 5) {
                    String msg = "REMOVE$" + room_id + "$" + sender + "$" + sender_id + "$" + send_time + "$" + 1 + "$" + message + "\n";
                    sendMessage(sendto, msg);
                } else {
                    //String msg = "MESSAGE$" + sender + "$" + sender_id + "$" + room_id + "$" + send_time + "$" + type + "$" + message + "$QUEUE\n";
                    //System.out.println(">>> in handleQueue : message = " + message);
                    //sendMessage(sendto, msg);
                }
            }
            
            if(type == 0 || type == 1){
            	sendMessage(user_id, sendAllMessage);
            }
            
        } catch (SQLException e) {
            System.out.println(e.toString());
        }
    }

    public void handleMessage(String sender, long sender_id, long room_id, String room_name, long send_time, int type, String message) {
        try {
        	System.out.println("in handleMessage, room id : " + room_id);
            boolean first = true;
            long sendto = 0;
            String sender_image = "";
            st = con.createStatement();
            rs = st.executeQuery("SELECT user_id FROM ROOM WHERE room_id = " + room_id + " AND user_id != " + sender_id);
            //System.out.println("rs : " + rs.next());
            while (rs.next()) {
                sendto = rs.getLong("user_id");
                System.out.print(">>> sendto : " + sendto + " ");
                first = false;
                int status = -1;
                
                stat = con.createStatement();
                resu = stat.executeQuery("SELECT status FROM FRIENDSHIP WHERE user_id = " + sender_id + " AND friend_id = " + sendto);
                while (resu.next()) {
                    status = resu.getInt("status");
                }
                
                stat = con.createStatement();
			    resu = stat.executeQuery("SELECT image FROM USER WHERE user_id = " + sender_id);
            	while (resu.next()) {
 			       sender_image = resu.getString("image");
        	    }
                
                if (status == 1) {
                    boolean online = false, data = false;
                    for (int i = 0; i < onlineList.size(); i++) {
                        if (onlineList.get(i) == sendto) {
                            online = true;
                            break;
                        }
                    }
                    System.out.println("in status == 1");
                    stat = con.createStatement();
                    resu = stat.executeQuery("SELECT * FROM QUEUE WHERE sendto = " + sendto + " AND room_id = " + room_id);
                    while (resu.next()) {
                        data = true;
                        System.out.println("in while data = true");
                    }
                    System.out.print("online : " + online + " data : " + data + "\n");
                    
                    if (online && data == false) {
                        if (type == 2) {
                            sendList.add(sendto);
                        } else if (type == 3) {
                            sendList.add(sendto);
                        } else {
                        
                        	stat = con.createStatement();
                            stat.executeUpdate("INSERT INTO QUEUE(sender, sender_id, sendto, room_id, send_time, type, message) "
                                    + "VALUES('" + sender + "','" + sender_id + "','" + sendto + "','" + room_id
                                    + "','" + send_time + "','" + type + "','" + message + "')");
                                 
                        	Date date = new Date(send_time);
			                Locale locale = new Locale("zh", "zh_TW");
							SimpleDateFormat df = new SimpleDateFormat("hh:mm a");
							//SimpleDateFormat df = new SimpleDateFormat("hh:mm a", locale);
							//df.format(date)
						   
                            String msg = "MESSAGE$" + sender + "$" + sender_id + "$" + sender_image + "$" + room_id + "$" + send_time + "$" + df.format(date) + "$"
                                    + type + "$" + message + "$RECORD\n";
                            System.out.println(">>> in handleMessage : message = " + message);
                            sendMessage(sendto, msg);
                        }
                    }
                    else if(online && data == true){
                    	stat = con.createStatement();
                        stat.executeUpdate("INSERT INTO QUEUE(sender, sender_id, sendto, room_id, send_time, type, message) "
                                + "VALUES('" + sender + "','" + sender_id + "','" + sendto + "','" + room_id
                                + "','" + send_time + "','" + type + "','" + message + "')");
                                 
                    	Date date = new Date(send_time);
		                Locale locale = new Locale("zh", "zh_TW");
						SimpleDateFormat df = new SimpleDateFormat("hh:mm a");
						//SimpleDateFormat df = new SimpleDateFormat("hh:mm a", locale);
						//df.format(date)
						   
                        String msg = "MESSAGE$" + sender + "$" + sender_id + "$" + sender_image + "$" + room_id + "$" + send_time + "$" + df.format(date) + "$"
                                + type + "$" + message + "$TEMPQUEUE\n";
                        System.out.println(">>> in handleMessage : message = " + message);
                        sendMessage(sendto, msg);
                    	
                    } else {
                        //System.out.println("save");
                        try {
                            stat = con.createStatement();
                            stat.executeUpdate("INSERT INTO QUEUE(sender, sender_id, sendto, room_id, send_time, type, message) "
                                    + "VALUES('" + sender + "','" + sender_id + "','" + sendto + "','" + room_id
                                    + "','" + send_time + "','" + type + "','" + message + "')");
                                    
                            Date date = new Date(send_time);
			                Locale locale = new Locale("zh", "zh_TW");
							SimpleDateFormat df = new SimpleDateFormat("hh:mm a");
							//SimpleDateFormat df = new SimpleDateFormat("hh:mm a", locale);
							//df.format(date)
                                    
                        	String msg = "MESSAGE$" + sender + "$" + sender_id + "$" + sender_image + "$" + room_id + "$" + send_time + "$" + df.format(date) + "$"
                                    + type + "$" + message + "$QUEUE\n";
                            System.out.println(">>> in handleMessage : message = " + message);
                            sendMessage(sendto, msg);
                            
                        } catch (SQLException e) {
                            System.out.println(e.toString());
                        }
                    }
                }
                break;
            }
            if (first) {
                rs = st.executeQuery("SELECT user_id FROM USER WHERE showname = '" + room_name + "'");
                while (rs.next()) {
                    sendto = rs.getLong("user_id");
                }
                boolean online = false;
                for (int i = 0; i < onlineList.size(); i++) {
                    if (onlineList.get(i) == sendto) {
                        online = true;
                        break;
                    }
                }
                if (online) {
                    sendMessage(sendto, "NEWROOM$" + room_id + "$" + sender + "\n");
                }
                newRoom(room_id, sender, sender_id, room_name, sendto, 0);
                //newRoom(long room_id, String room_name, long friend_id, String user_name, long user_id, int isgroup)
                handleMessage(sender, sender_id, room_id, room_name, send_time, type, message);
                //handleMessage(String sender, long sender_id, long room_id, String room_name, long send_time, int type, String message)
            }
        } catch (SQLException e) {
            System.out.println(e.toString());
        }
    }

    public void responseMessage(long room_id, long sender_id, long send_time, long sendto, int type, String message) {
        try {
            st = con.createStatement();
            if (message.equals("NEWGROUP")) {
                st.executeUpdate("DELETE FROM QUEUE WHERE send_time = '" + send_time + "' AND sendto = '" + sendto + "'");
            } else if (message.equals("REMOVE")) {
                st.executeUpdate("DELETE FROM QUEUE WHERE send_time = '" + send_time + "' AND sendto = '" + sendto + "'");
            } else {
                st.executeUpdate("DELETE FROM QUEUE WHERE send_time = " + send_time);
                if (sender_id != sendto) {
                    st.executeUpdate("INSERT INTO RECORD(room_id, sender_id, send_time, type, message) " + "VALUES('"
                            + room_id + "','" + sender_id + "','" + send_time + "','" + type + "','" + message + "')");
                }
                //sendMessage(sender_id, "CHANGEMSGTYPE$");
                //sendMessage(sendto, "CHANGEMSGTYPE$");
            }
        } catch (SQLException e) {
            System.out.println(e.toString());
        }
    }
    
    public void insertQueue(String sender, long sender_id, long sendto, long room_id, long send_time, int type, String message){
    	try {
            st = con.createStatement();
            st.executeUpdate("INSERT INTO QUEUE(sender, sender_id, sendto, room_id, send_time, type, message) "
                    + "VALUES('" + sender + "','" + sender_id + "','" + sendto + "','" + room_id
                    + "','" + send_time + "','" + type + "','" + message + "')");
        } catch (SQLException e) {
            System.out.println(e.toString());
        }
    }

    public void removeMessage(long room_id, String sender, long sender_id, long send_time, int type, String message) {
        try {
            st = con.createStatement();
            rs = st.executeQuery("SELECT user_id FROM ROOM WHERE room_id = " + room_id + " AND user_id != " + sender_id);
            while (rs.next()) {
                long sendto = rs.getLong("user_id");
                boolean online = false;
                for (int i = 0; i < onlineList.size(); i++) {
                    if (onlineList.get(i) == sendto) {
                        online = true;
                        break;
                    }
                }
                if (online) {
                    String msg = "REMOVE$" + room_id + "$" + sender + "$" + sender_id + "$" + send_time + "$" + type + "$" + message + "\n";
                    sendMessage(sendto, msg);
                } else {
                    stat = con.createStatement();
                    if (type == 0) {
                        stat.executeUpdate("INSERT INTO QUEUE(sender, sender_id, sendto, room_id, send_time, type, message) "
                                        + "VALUES('" + sender + "','" + sender_id + "','" + sendto + "','" + room_id
                                        + "','" + send_time + "','" + 4 + "','" + message + "')");
                    } else if (type == 1) {
                        stat.executeUpdate("INSERT INTO QUEUE(sender, sender_id, sendto, room_id, send_time, type, message) "
                                        + "VALUES('" + sender + "','" + sender_id + "','" + sendto + "','" + room_id
                                        + "','" + send_time + "','" + 5 + "','" + message + "')");
                    }
                }
                
            }
            st = con.createStatement();
            stat.executeUpdate("DELETE FROM RECORD WHERE room_id = '" + room_id + "' AND sender_id = '" + sender_id 
                            + "' AND send_time = '" + send_time + "' AND type = '" + type + "' AND message = '" + message + "'");
        } catch (SQLException e) {
            System.out.println(e.toString());
        }
    }
    
    public void sendMessage(long sendto, String msg) {
        for (int i = 0; i < clientList.size(); i++) {
            if (clientList.get(i).getUser().getId() == sendto) {
                //System.out.println("[Server] send :\n" + msg);
                try {
                	System.out.println("in sendMessage: " + msg);
                    clientList.get(i).getWrite().write(msg);
                    clientList.get(i).getWrite().flush();
                } catch (IOException e) {
                    System.out.println(e.toString());
                }
                break;
            }
        }
    }

    public long newRoom(long room_id, String room_name, long friend_id, String user_name, long user_id, int isgroup) {
        try {
        	boolean first = true;
        	long room_ID = room_id;
            st = con.createStatement();
            rs = st.executeQuery("SELECT room_id FROM ROOM WHERE room_name = '" + room_name + "' AND user_id = " + user_id);
            while (rs.next()) {
                first = false;
                room_ID = rs.getLong("room_id");
                System.out.println(">>> in newRoom first = false" + room_ID);
                //BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()));
                //bw.write("GETROOMID$" + room);
                //bw.flush();
                return room_ID;
                //break;
            }
            if (first) {
            	System.out.println(">>> in newRoom first = true");
            	rs = st.executeQuery("SELECT room_id FROM ROOM WHERE room_name = '" + user_name + "' AND user_id = " + friend_id);
            	boolean friendHasRoom = false;
            	while (rs.next()) {
                	friendHasRoom = true;
                	room_ID = rs.getLong("room_id");
                	System.out.println(">>> friend has room");
           	 	}
           	 	
           	 	if(friendHasRoom == true){
	                st = con.createStatement();
		            st.executeUpdate("INSERT INTO ROOM(room_id, room_name, user_id, isgroup) " + "VALUES('" + room_ID
    		                + "','" + room_name + "','" + user_id + "','" + isgroup + "')");
           	 	}
            	else if(friendHasRoom == false){
            		st = con.createStatement();
		            st.executeUpdate("INSERT INTO ROOM(room_id, room_name, user_id, isgroup) " + "VALUES('" + room_id
    		                + "','" + room_name + "','" + user_id + "','" + isgroup + "')");
            	}
                
                if (isgroup == 1) {
            		rs = st.executeQuery("SELECT user_id, count " + "FROM " + "ROOM, " + "("
                	        + "SELECT room_id, COUNT(*) AS count FROM ROOM GROUP BY room_id" + ") ROOM_GROUP "
                		    + "WHERE ROOM.room_id = ROOM_GROUP.room_id " + "AND ROOM.room_id = " + room_id);
		            while (rs.next()) {
    	                long sendto = rs.getLong("user_id");
    		            int count = rs.getInt("count");
            		    boolean online = false;
                	    for (int i = 0; i < onlineList.size(); i++) {
                		    if (onlineList.get(i) == sendto) {
                    		    online = true;
                        		break;
	                        }
		                }
        		        System.out.println(">>> " + sendto + " " + count);
            		    if (online) {
                	        sendMessage(sendto, "GROUPCOUNT$" + room_id + "$" + count + "\n");                        
                		}
		            }
    	        }
    	        
    	        return room_ID;
            }
        } catch (SQLException e) {
            System.out.println(e.toString());
        }
        return -1;
    }

    public void newGroup(long room_id, String room_name, String sender, long sender_id, ArrayList<String> list) {
        try {
            for (int i = 0; i < list.size(); i++) {
                System.out.println(list.get(i));
            }
            st = con.createStatement();
            for (int i = 0; i < list.size(); i++) {
                long id = 0;
                rs = st.executeQuery("SELECT user_id FROM USER WHERE showname = '" + list.get(i) + "'");
                while (rs.next()) {
                    id = rs.getLong("user_id");
                }
                boolean online = false;
                for (int j = 0; j < onlineList.size(); j++) {
                    if (onlineList.get(j) == id) {
                        online = true;
                        break;
                    }
                }
                if (online) {
                    sendMessage(id, "NEWGROUP$" + room_id + "$" + room_name + "$" + sender + "\n");                   
                } else {
                    stat = con.createStatement();
                    stat.executeUpdate("INSERT INTO QUEUE(sender, sender_id, sendto, room_id, send_time, message) "
                            + "VALUES('" + sender + "','" + sender_id + "','" + id + "','" + room_id + "','"
                            + System.currentTimeMillis() + "','" + room_name + "')");
                }
            }
        } catch (SQLException e) {
            System.out.println(e.toString());
        }
    }

    public String searchFriend(long user_id, String msg) {
        try {
            boolean exist = false, have = false;
            long friend_id = 0;
            String friend_name = "";
            String result = "SEARCHFRIEND";
            st = con.createStatement();
            rs = st.executeQuery("SELECT user_id, showname FROM USER WHERE username = '" + msg + "'");
            while (rs.next()) {
                exist = true;
                friend_id = rs.getLong("user_id");
                friend_name = rs.getString("showname");
            }
            if (exist == false) {
                return result + "$NOTEXIST\n";
            }
            if (user_id == friend_id) {
                return result + "$SELF$" + friend_name + "\n";
            }

            rs = st.executeQuery(
                    "SELECT * FROM FRIENDSHIP WHERE user_id = " + user_id + " AND friend_id = " + friend_id);
            while (rs.next()) {
                have = true;
            }
            if (have) {
                return result + "$HAVE$" + friend_name + "\n";
            } else {
                return result + "$NEW$" + friend_id + "$" + friend_name + "\n";
            }
        } catch (SQLException e) {
            System.out.println(e.toString());
            return null;
        }
    }

    public String isFriend(long user_id, String friend_name) {
        try {
            String isfriend = "ISFRIEND$";
            int status = -1;
            st = con.createStatement();
            rs = st.executeQuery("SELECT status FROM USER, FRIENDSHIP WHERE USER.showname = '" + friend_name
                    + "' AND FRIENDSHIP.user_id = " + user_id + " AND FRIENDSHIP.friend_id = USER.user_id");
            while (rs.next()) {
                status = rs.getInt("status");
            }
            if (status == 1) {
                System.out.println(friend_name + " -> true");
                isfriend += true;
            } else {
                System.out.println(friend_name + " -> false");
                isfriend += false;
            }
            return isfriend + "\n";
        } catch (SQLException e) {
            System.out.println(e.toString());
            return null;
        }
    }

    public void addFriend(long user_id, String name, long friend_id) {
        try {
            boolean online = false;
            for (int i = 0; i < onlineList.size(); i++) {
                if (onlineList.get(i) == friend_id) {
                    online = true;
                    break;
                }
            }
            
            if (online) {
                sendMessage(friend_id, "FRIENDREQUEST$" + user_id + "$" + name);
            }
            st = con.createStatement();
            st.executeUpdate("INSERT INTO FRIENDSHIP(user_id, friend_id, status) VALUES('" + user_id + "','" + friend_id
                    + "','0')");
        } catch (SQLException e) {
            System.out.println(e.toString());
        }
    }

    public void confirmFriend(long request_id, long confirm_id) {
        try {
            st = con.createStatement();
            st.executeUpdate("UPDATE FRIENDSHIP SET status = '1' WHERE user_id = " + request_id + " AND friend_id = "
                    + confirm_id);
            st.executeUpdate("INSERT INTO FRIENDSHIP(user_id, friend_id, status) VALUES('" + confirm_id + "','"
                    + request_id + "','1')");
        } catch (SQLException e) {
            System.out.println(e.toString());
        }
    }
    
    public String loadRoomMessage(long room_id, long user_id){
    	String roomMessage = "LOADROOMMESSAGE$";
    	try{
            st = con.createStatement();
            rs = st.executeQuery("SELECT * FROM RECORD WHERE room_id = " + room_id);
            roomMessage += "RECORD$";
            while (rs.next()) {
            	long sender_id = rs.getLong("sender_id");
            	long send_time = rs.getLong("send_time");
            	int type = rs.getInt("type");
            	String message = rs.getString("message");
            	String sender_image = "";
            	String sender = "";
            	
            	Date date = new Date(send_time);
                Locale locale = new Locale("zh", "zh_TW");
                SimpleDateFormat df = new SimpleDateFormat("hh:mm a");
				//SimpleDateFormat df = new SimpleDateFormat("hh:mm a", locale);
            	
            	stat = con.createStatement();
                resu = stat.executeQuery("SELECT * FROM USER WHERE user_id = " + sender_id);
                while (resu.next()) {
                	sender = resu.getString("username");
                    sender_image = resu.getString("image");
                }
                
            	System.out.println("RECORD ==> sender_id: " + sender_id + "  sender:" + sender + "  sender_image: " + sender_image + "  send time: " + df.format(date) + "  type: " + type + "  message: " + message);
                roomMessage += sender_id + "$" + sender + "$" + sender_image + "$" + df.format(date) + "$" + type + "$" + message + "$";
            }
            
            st = con.createStatement();
            rs = st.executeQuery("SELECT * FROM QUEUE WHERE room_id = " + room_id);
            roomMessage += "QUEUE$";
            while (rs.next()) {
            	long sender_id = rs.getLong("sender_id");
            	long send_time = rs.getLong("send_time");
            	int type = rs.getInt("type");
            	String message = rs.getString("message");
            	String sender_image = "";
            	String sender = "";
            	
            	Date date = new Date(send_time);
                Locale locale = new Locale("zh", "zh_TW");
                SimpleDateFormat df = new SimpleDateFormat("hh:mm a");
				//SimpleDateFormat df = new SimpleDateFormat("hh:mm a", locale);
            	
            	stat = con.createStatement();
                resu = stat.executeQuery("SELECT * FROM USER WHERE user_id = " + sender_id);
                while (resu.next()) {
                	sender = resu.getString("username");
                    sender_image = resu.getString("image");
                }
                
            	System.out.println("QUEUE ==> sender_id: " + sender_id + "  sender:" + sender + "  sender_image: " + sender_image + "  send time: " + df.format(date) + "  type: " + type + "  message: " + message);
                roomMessage += sender_id + "$" + sender + "$" + sender_image + "$" + df.format(date) + "$" + type + "$" + message + "$";
            }
            
            return roomMessage;
    		
    	} catch(SQLException e){
    		System.out.println(e.toString());
    		return null;
    	}
    }
    
    public void addNote(long room_id, long friend_id, String friend_name, long user_id, String user_name, String message){
    	try {
            st = con.createStatement();
            st.executeUpdate("INSERT INTO NOTE(room_id, friend_id, friend_name, user_id, user_name, message) "
                    + "VALUES('" + room_id + "','" + friend_id + "','" + friend_name + "','" + user_id
                    + "','" + user_name + "','" + message + "')");
        } catch (SQLException e) {
            System.out.println(e.toString());
        }
    }
    
    public String loadNote(long user_id){
    	String loadNoteMsg = "LOADNOTE$";
    	try{
    		st = con.createStatement();
            rs = st.executeQuery("SELECT * FROM NOTE WHERE user_id = " + user_id);
            while (rs.next()) {
            	long friend_id = rs.getLong("friend_id");
            	String friend_name = rs.getString("friend_name");
            	String message = rs.getString("message");
            	//String friend_image = getUserImage(friend_name);
            	
            	stat = con.createStatement();
                resu = stat.executeQuery("SELECT image FROM USER WHERE user_id = " + friend_id);
                String friend_image = "";
                while (resu.next()) {
                    friend_image = resu.getString("image");
                }
            	
            	System.out.println("load note ==> friend_id: " + friend_id + "  friend_name:" + friend_name + "  friend_image: " + friend_image + "  message: " + message);
                loadNoteMsg += friend_id + "$" + friend_name + "$" + friend_image + "$" + message + "$";
            }
            
            return loadNoteMsg;
    		
    	} catch(SQLException e){
    		System.out.println(e.toString());
    		return null;
    	}
    
    }
    
    public String loadFriendNote(long user_id, long room_id){
    	String loadFriendNoteMsg = "LOADFRIENDNOTE$";
    	try{
    		st = con.createStatement();
            rs = st.executeQuery("SELECT * FROM NOTE WHERE user_id = " + user_id + " AND room_id = " + room_id);
				    
            while (rs.next()) {
            	long friend_id = rs.getLong("friend_id");
            	String friend_name = rs.getString("friend_name");
            	String message = rs.getString("message");
            	//String friend_image = getUserImage(friend_name);
            	
            	stat = con.createStatement();
                resu = stat.executeQuery("SELECT image FROM USER WHERE user_id = " + friend_id);
                String friend_image = "";
                while (resu.next()) {
                    friend_image = resu.getString("image");
                }
            	
            	System.out.println("load note ==> friend_id: " + friend_id + "  friend_name:" + friend_name + "  friend_image: " + friend_image + "  message: " + message);
                loadFriendNoteMsg += friend_id + "$" + friend_name + "$" + friend_image + "$" + message + "$";
            }
            
            return loadFriendNoteMsg;
    		
    	} catch(SQLException e){
    		System.out.println(e.toString());
    		return null;
    	}
    }
    
    public void deleteNote(long friend_id, String friend_name, long user_id, String user_name, String message){
    	try {
            st = con.createStatement();
            st.executeUpdate("DELETE FROM NOTE WHERE friend_id = '" + friend_id + "' AND user_id = '" + user_id + "' AND message = '" + message + "'");
        } catch (SQLException e) {
            System.out.println(e.toString());
        }
    }

    public void imageRisize(String filename) {
        int targetWidth = 720;
        int targetHeight = 600;
        double targetRate = (double) targetWidth / targetHeight;

        System.out.printf("[Server] Target w:%s, h:%s, r:%s\n", targetWidth, targetHeight, targetRate);
        System.out.println(">>> " + filename);
        BufferedImage image;
        try {
            image = ImageIO.read(new File("./File/" + filename));
            int type = image.getType();
            if (type == 0) {
                type = BufferedImage.TYPE_INT_ARGB;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            double rate = (double) width / height;
            System.out.printf("[Server] Source w:%s, h:%s, r:%s\n", width, height, rate);

            /* 等比例縮小至指定大小內 */
            int rWidth = targetWidth;
            int rHeight = targetHeight;

            if (width < targetWidth && height < targetHeight) {
                rWidth = width;
                rHeight = height;
            } else if (rate > targetRate) {
                rHeight = (int) (targetWidth / rate);
            } else {
                rWidth = (int) (targetHeight * rate);
            }
            System.out.printf("[Server] Resize w:%s, h:%s\n", rWidth, rHeight);

            BufferedImage resize = new BufferedImage(rWidth, rHeight, type);
            Graphics g = resize.getGraphics();
            g.drawImage(image, 0, 0, rWidth, rHeight, null);
            g.dispose();

            ImageIO.write(resize, "jpg", new File("./File/archive" + filename));           
            sendFileMessage();
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }
    
    public void sendFileMessage() {
        for (int i = 0; i < sendList.size(); i++) {
            System.out.println("[Server] Send to " + sendList.get(i));
            sendMessage(sendList.get(i), receive);
            sendMessage(sendList.get(i), show);
        }
    }

    public void receiveFile(Socket socket, BufferedReader br, String filename) throws IOException {
        File file = new File("./File/" + filename);
        byte[] b = new byte[1024];
        int len = 0;
        int bytcount = 0;
        FileOutputStream inFile = new FileOutputStream(file);
        InputStream is = socket.getInputStream();
        BufferedInputStream bis = new BufferedInputStream(is, 1024);
        System.out.println("[Server] Start to receive file...");
        while ((len = bis.read(b, 0, 1024)) != -1) {
            //System.out.println(len);
            bytcount = bytcount + len;
            inFile.write(b, 0, len);
        }
        System.out.println("[Server] Receiving file finished...");
        System.out.println("[Server] Bytes Writen : " + bytcount);
        bis.close();
        inFile.close();
    }

    public void sendFile(Socket socket, BufferedReader br, String filename) throws IOException, ClassNotFoundException {
        File file = new File("./File/" + filename);
        byte[] buf = new byte[1024];
        OutputStream os = socket.getOutputStream();
        BufferedOutputStream out = new BufferedOutputStream(os, 1024);
        FileInputStream in = new FileInputStream(file);
        int i = 0;
        int bytecount = 1024;

        while ((i = in.read(buf, 0, 1024)) != -1) {
            //System.out.println(i);
            bytecount = bytecount + i;
            out.write(buf, 0, i);
            out.flush();

        }
        socket.shutdownOutput();
        /* important */
        System.out.println("[Server] Bytes Sent :" + bytecount);

        out.close();
        in.close();
        br.close();
        socket.close();
    }

    public class User {

        private long id;
        private String username;
        private String showname;
        private String ip;

        public User(long id, String username, String showname, String ip) {
            this.username = username;
            this.showname = showname;
            this.id = id;
            this.ip = ip;
        }

        public long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getShowname() {
            return showname;
        }

        public String getIp() {
            return ip;
        }

        public void setId(long id) {
            this.id = id;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public void setShowname(String showname) {
            this.showname = showname;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

    }

    public static void main(String[] args) {
        new Server();
    }

}
