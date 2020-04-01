package com.geekbrains.cloud.storage.firsttask;

import java.io.*;
import java.net.Socket;

public class Client {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("127.0.0.1", 1111);
        File file = new File("FirstTask/src/main/java/com/geekbrains/cloud/storage/firsttask/fileToSend.txt");
        byte[] bytes = new byte[(int) file.length()];
        BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));
        inputStream.read(bytes, 0, bytes.length);
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(bytes, 0, bytes.length);
        outputStream.flush();
        socket.close();
    }
}