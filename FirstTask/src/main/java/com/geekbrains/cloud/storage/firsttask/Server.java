package com.geekbrains.cloud.storage.firsttask;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(1111);
        while (true) {
            Socket socket = serverSocket.accept();
            byte[] bytes = new byte[1024];
            InputStream inputStream = socket.getInputStream();
            FileOutputStream fileOutputStream = new FileOutputStream("FirstTask/src/main/java/com/geekbrains/cloud/storage/firsttask/fileFromClient.txt");
            BufferedOutputStream outputStream = new BufferedOutputStream(fileOutputStream);
            int bytesRead = inputStream.read(bytes, 0, bytes.length);
            outputStream.write(bytes, 0, bytesRead);
            outputStream.close();
            socket.close();
            return;
        }
    }
}