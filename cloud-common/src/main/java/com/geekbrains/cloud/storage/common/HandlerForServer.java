package com.geekbrains.cloud.storage.common;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class HandlerForServer extends ChannelInboundHandlerAdapter {

    private static final Logger logger = (Logger) LogManager.getLogger(HandlerForServer.class);

    public enum State {
        IDLE,
        NAME_LENGTH, NAME, FILE_LENGTH, FILE,
        GET_COUNT_FILES, GET_LENGTH_NAME_FILE, GET_NAME_FILE,
        SEND_COUNT_FILES,
        SEND_CONFIRM,
        LEVEL_UP,
        SEND_FILE
    }
    private State currentState = State.IDLE;

    private int nextLength;
    private int length;
    private int countFiles;
    private byte byteMemory;
    private long fileLength;
    private long receivedFileLength;
    private byte[] fileName;

    private int clientId = 0;
    private String currentFolderServerFilesName;
    private String rootFolderServerFilesName;
    private static final String FOLDER_SERVER_FILES_NAME = "Server Files/";

    private List<Integer> clients = new ArrayList<>();
    private List<String> listOperationOfFiles = new ArrayList<>();

    private BufferedOutputStream out;
    private ChannelHandlerContext ctx;
    private Connection conn;
    private Statement stmt;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = ((ByteBuf) msg);
        System.out.println(buf);
        while (buf.readableBytes() > 0) {
            if (currentState == State.IDLE) {
                byteMemory = buf.readByte();
                logger.info("Byte of: " + byteMemory);
                if (byteMemory == Bytes.BYTE_OF_SEND_FILE_FROM_CLIENT.toByte()) {
                    logger.info("Send file from client");
                    currentState = State.NAME_LENGTH;
                    receivedFileLength = 0L;
                } else if (byteMemory == Bytes.BYTE_OF_AUTH.toByte() ||
                        byteMemory == Bytes.BYTE_OF_ENTER_CATALOG.toByte() ||
                        byteMemory == Bytes.BYTE_OF_SEND_CATALOG_FROM_CLIENT.toByte() ||
                        byteMemory == Bytes.BYTE_OF_NEW_USER.toByte() ||
                        byteMemory == Bytes.BYTE_OF_COPY_FILE.toByte() ||
                        byteMemory == Bytes.BYTE_OF_MOVE_FILE.toByte() ||
                        byteMemory == Bytes.BYTE_OF_DELETE_FILE.toByte() ||
                        byteMemory == Bytes.BYTE_OF_RENAME_FILE.toByte()){
                    logger.info("Auth, enter catalog, send catalog, copy, move or delete");
                    listOperationOfFiles.clear();
                    currentState = State.GET_COUNT_FILES;
                } else if (byteMemory == Bytes.BYTE_OF_CATALOG_LEVEL_UP.toByte()){
                    logger.info("Level up");
                    currentState = State.LEVEL_UP;
                } else if (byteMemory == Bytes.BYTE_OF_REFRESH.toByte()){
                    logger.info("Refresh");
                    currentState = State.SEND_COUNT_FILES;
                } else if (byteMemory == Bytes.BYTE_OF_CONFIRM.toByte()){
                    logger.info("Confirm");
                    currentState = State.SEND_CONFIRM;
                } else if (byteMemory == Bytes.BYTE_OF_CONFIRM_GET_FILE.toByte()){
                    logger.info("Confirm get file");
                } else {
                    logger.info("ERROR: Invalid first byte - " + byteMemory);
                }
            }

            if (currentState == State.NAME_LENGTH) {
                if (buf.readableBytes() >= 4) {
                    nextLength = buf.readInt();
                    fileName = new byte[nextLength];
                    currentState = State.NAME;
                    logger.info("Length: " + nextLength);
                }
            }

            if (currentState == State.NAME) {
                if (buf.readableBytes() >= nextLength) {
                    logger.info("Readable bytes: " + buf.readableBytes());
                    buf.readBytes(fileName);
                    logger.info("File name: " + new String(fileName, StandardCharsets.UTF_8));
                    out = new BufferedOutputStream(
                            new FileOutputStream(currentFolderServerFilesName
                                    + new String(fileName, StandardCharsets.UTF_8)));
                    currentState = State.FILE_LENGTH;
                }
            }

            if (currentState == State.FILE_LENGTH) {
                if (buf.readableBytes() >= 8) {
                    fileLength = buf.readLong();
                    logger.info("File length: " + fileLength);
                    currentState = State.FILE;
                }
            }

            if (currentState == State.FILE) {
                if (fileLength != 0) {
                    int readable = buf.readableBytes();
                    if (readable > 0) {
                        if (readable >= fileLength) {
                            readable = (int) fileLength;
                            fileLength = 0;
                        } else {
                            fileLength -= readable;
                        }

                        buf.readBytes(out, readable);

                        if (fileLength == 0) {
                            logger.info("File received");
                            currentState = State.IDLE;
                            out.close();
                        }
                    }
                } else {
                    currentState = State.IDLE;
                    out.close();
                }
            }

            //------------------------------
            // Auth, enter catalog, send catalog, copy, move, delete or rename:
            if (currentState == State.GET_COUNT_FILES) {
                if (buf.readableBytes() >= 4) {
                    logger.info("Wait byte...");
                    countFiles = buf.readInt();
                    logger.info("Count of files: " + countFiles);
                    if (byteMemory == Bytes.BYTE_OF_AUTH.toByte()){
                        logger.info("Auth");
                    } else if (byteMemory == Bytes.BYTE_OF_ENTER_CATALOG.toByte()){
                        logger.info("Enter catalog");
                    } else if (byteMemory == Bytes.BYTE_OF_SEND_CATALOG_FROM_CLIENT.toByte()){
                        logger.info("Send catalog");
                    } else if (byteMemory == Bytes.BYTE_OF_NEW_USER.toByte()){
                        logger.info("New user");
                    } else {
                        logger.info("Copy, move, delete or rename");
                    }
                    if (countFiles == 0){
                        logger.info("Count files: " + countFiles);
                        currentState = State.IDLE;
                        break;
                    } else {
                        currentState = State.GET_LENGTH_NAME_FILE;
                    }
                }
            }

            if (currentState == State.GET_LENGTH_NAME_FILE) {
                if (buf.readableBytes() >= 4) {
                    logger.info("Wait byte...");
                    length = buf.readInt();
                    logger.info("Length: " + length);
                    currentState = State.GET_NAME_FILE;
                }
            }

            if (currentState == State.GET_NAME_FILE) {
                if (buf.readableBytes() >= length) {
                    byte[] bytesFileName = new byte[length];
                    buf.readBytes(bytesFileName);
                    String fileName = new String(bytesFileName, StandardCharsets.UTF_8);
                    logger.info("File name: " + fileName);

                    if (byteMemory == Bytes.BYTE_OF_AUTH.toByte()){
                        sendRequestToDataBase("Auth",
                                Bytes.BYTE_OF_AUTH_RIGHT.toByte(),
                                fileName);
                        logger.info("Byte of Auth");
                        currentState = State.IDLE;
                        break;
                    } else if (byteMemory == Bytes.BYTE_OF_ENTER_CATALOG.toByte()){
                        if (fileName.equals("./")){
                            folderLevelUp();
                        } else {
                            currentFolderServerFilesName += fileName;
                        }
                        sendBytesObjectToClient(Bytes.BYTE_OF_CONFIRM.toByte());
                        logger.info("Byte of enter catalog");
                        currentState = State.IDLE;
                        break;
                    } else if (byteMemory == Bytes.BYTE_OF_SEND_CATALOG_FROM_CLIENT.toByte()){
                        currentFolderServerFilesName += fileName;
                        File file = new File(currentFolderServerFilesName);
                        //noinspection ResultOfMethodCallIgnored
                        file.mkdir();

                        sendBytesObjectToClient(Bytes.BYTE_OF_CONFIRM.toByte());
                        logger.info("Byte of enter catalog");
                        currentState = State.IDLE;
                        break;
                    } else if (byteMemory == Bytes.BYTE_OF_NEW_USER.toByte()){
                        sendRequestToDataBase("New user",
                                Bytes.BYTE_OF_NEW_USER_RIGHT.toByte(),
                                fileName);
                        logger.info("Byte of new user");
                        currentState = State.IDLE;
                        break;
                    } else {
                        listOperationOfFiles.add(fileName);
                        countFiles--;
                        if (countFiles == 0) {
                            if (byteMemory == Bytes.BYTE_OF_DELETE_FILE.toByte()) {
                                logger.info("Delete");
                                listOperationOfFiles.forEach(file -> {
                                    logger.info("File: " + file);
                                    try {
                                        deleteFile(currentFolderServerFilesName + file);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
                                logger.info("Files delete");
                                sendBytesObjectToClient(Bytes.BYTE_OF_CONFIRM.toByte());
                            } else if (byteMemory == Bytes.BYTE_OF_RENAME_FILE.toByte()) {
                                String file = listOperationOfFiles.get(0);
                                String newFileName = listOperationOfFiles.get(1);
                                Path path = Paths.get(currentFolderServerFilesName + file);
                                Files.move(path, path.resolveSibling(newFileName));
                                logger.info("File rename: " + file + " to new name: " + newFileName);
                                sendBytesObjectToClient(Bytes.BYTE_OF_CONFIRM.toByte());
                            } else {
                                logger.info("Copy or move");
                                String actionOnFiles = "Copy";
                                if (byteMemory == Bytes.BYTE_OF_MOVE_FILE.toByte()) {
                                    actionOnFiles = "Move";
                                }
                                logger.info("Action: " + actionOnFiles);

                                for (String file : listOperationOfFiles) {
                                    logger.info("File name: " + fileName);
                                    try {
                                        copyFile(currentFolderServerFilesName + file, file, actionOnFiles);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                logger.info("Files sends");
                                Thread.sleep(100);
                                sendBytesObjectToClient(Bytes.BYTE_OF_END_OF_SEND_FROM_SERVER.toByte());
                            }
                            currentState = State.IDLE;
                            break;
                        }
                        currentState = State.GET_LENGTH_NAME_FILE;
                    }
                }
            }

            //------------------------------
            //Send list of files from server
            if (currentState == State.SEND_COUNT_FILES) {
                logger.info("Send list of files from server. Path folder: " + currentFolderServerFilesName);
                Path path = Paths.get(currentFolderServerFilesName);
                if (! Files.exists(path)){
                    Files.createDirectories(path);
                    logger.info("Create new directory");
                }

                Map<String, String> mapFileNameAndSize = new LinkedHashMap<>();
                List<String> listOfServerFolders = Files.list(path)
                        .filter(p -> Files.isDirectory(p))
                        .map(Path::getFileName)
                        .map(path1 -> path1.toString() + "/")
                        .collect(Collectors.toList());
                if (! rootFolderServerFilesName.equals(currentFolderServerFilesName)){
                    mapFileNameAndSize.put("./", "Level_Up");
                }
                listOfServerFolders.forEach(fileName -> mapFileNameAndSize.put(fileName,"folder"));

                List<String> listOfServerFiles = Files.list(path)
                        .filter(p -> Files.isRegularFile(p))
                        .map(Path::toFile)
                        .map(File::getName)
                        .collect(Collectors.toList());
                listOfServerFiles.forEach(fileName -> {
                    long sizeFileName = 0;
                    try {
                        sizeFileName = Files.size(Paths.get(currentFolderServerFilesName + fileName));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mapFileNameAndSize.put(fileName, String.valueOf(sizeFileName));
                });

                sendBytesObjectToClient(Bytes.BYTE_OF_CONFIRM.toByte());
                sendBytesObjectToClient(mapFileNameAndSize.size());

                mapFileNameAndSize.forEach((fileName, fileSize) -> {
                    sendBytesObjectToClient(fileName);
                    sendBytesObjectToClient(fileSize);
                });

                currentState = State.IDLE;
                break;
            }

            //------------------------------
            //Return byte of confirm from client
            if (currentState == State.SEND_CONFIRM){
                sendBytesObjectToClient(Bytes.BYTE_OF_CONFIRM.toByte());
                currentState = State.IDLE;
            }

            //------------------------------
            //Folder level up
            if (currentState == State.LEVEL_UP){
                folderLevelUp();
                currentState = State.IDLE;
            }
        }
        if (buf.readableBytes() == 0) {
            buf.release();
        }
    }

    private void folderLevelUp() {
        StringBuilder stringBuilder = new StringBuilder(currentFolderServerFilesName);
        stringBuilder.delete(stringBuilder.lastIndexOf("/") - 1, stringBuilder.length());
        stringBuilder.delete(stringBuilder.lastIndexOf("/") + 1, stringBuilder.length());
        currentFolderServerFilesName = stringBuilder.toString();
    }

    private void copyFile(String filePath, String fileName, String action) throws IOException {
        logger.info("File path: " + filePath);
        if (Files.isDirectory(Paths.get(filePath))) {
            logger.info("File is directory: " + fileName);
            sendBytesObjectToClient(Bytes.BYTE_OF_SEND_CATALOG_FROM_SERVER.toByte());
            sendBytesObjectToClient(fileName);

            List<String> files = Files.list(Paths.get(filePath))
                    .map(Path::toFile)
                    .map(File::getName)
                    .collect(Collectors.toList());
            logger.info("Inner files: " + files);
            if (files.size() != 0) {
                for (String enteredFile : files) {
                    if (Files.isDirectory(Paths.get(filePath + enteredFile))) {
                        logger.info("is directory");
                        enteredFile += "/";
                    }
                    logger.info("Copy: Path: " + filePath + enteredFile+ " file: " + enteredFile);
                    try {
                        copyFile(filePath + enteredFile, enteredFile, action);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            waitOneHundredMs();
            sendBytesObjectToClient(Bytes.BYTE_OF_CATALOG_LEVEL_UP.toByte());
        } else {
            waitOneHundredMs();
            sendBytesObjectToClient(Bytes.BYTE_OF_SEND_FILE_FROM_SERVER.toByte());
            sendBytesObjectToClient(fileName);

            sendFile(filePath);
        }
        if (action.equals("Move")) {
            deleteFile(filePath);
        }
        logger.info("File send: " + fileName);
    }

    private void waitOneHundredMs() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendFile(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        long sizeFile = Files.size(path);

        sendBytesObjectToClient(String.valueOf(sizeFile));

        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileName));

        if (sizeFile != 0) {
            logger.info("Sending file...");
            byte[] bytes = new byte[(int) sizeFile];
            //noinspection ResultOfMethodCallIgnored
            bis.read(bytes);

            waitOneHundredMs();

            ByteBuf buf = ctx.alloc().buffer(bytes.length);
            buf.writeBytes(bytes);
            ctx.writeAndFlush(buf);
        } else {
            logger.info("File is clear");
        }
        bis.close();
    }

    private void deleteFile(String fileName) throws IOException {
        logger.info("File name: " + fileName);
        if (Files.isDirectory(Paths.get(fileName))) {
            logger.info("is directory");
            List<String> files = Files.list(Paths.get(fileName))
                    .map(Path::toFile)
                    .map(File::getName)
                    .collect(Collectors.toList());
            logger.info(files);
            if (files.size() != 0) {
                for (String enteredFile : files) {
                    if (Files.isDirectory(Paths.get(fileName + enteredFile))) {
                        logger.info("is directory");
                        enteredFile += "/";
                    }
                    deleteFile(fileName + enteredFile);
                }
            }
        }
        Files.deleteIfExists(Paths.get(fileName));
        logger.info("Delete: " + fileName);
    }

    private void sendRequestToDataBase(String operation, byte byteOfRight, String loginAndPassword) {
        String login = loginAndPassword.split(" ")[0];
        String password = loginAndPassword.split(" ")[1];
        logger.info(operation + ": "+ loginAndPassword);

        if (sendQueryFromDB(operation, login, password)){
            logger.info(operation + " OK");
            sendBytesObjectToClient(byteOfRight);
            sendBytesObjectToClient(clientId);
            if (operation.equals("Auth")) {
                clients.add(clientId);
                logger.info("Client id: " + clientId);
                currentFolderServerFilesName = FOLDER_SERVER_FILES_NAME + "user" + clientId + "/";
                rootFolderServerFilesName = currentFolderServerFilesName;
            } else {
                ctx.channel().close();
            }
        } else {
            logger.info(operation + " wrong. Close channel!");
            sendBytesObjectToClient(Bytes.BYTE_OF_CONFIRM.toByte());
            ctx.channel().close();
        }
    }

    private boolean sendQueryFromDB(String operation, String login, String password) {
        int id = 0;
        int result = 0;
        try {
            connectToDatabase();

            ResultSet rs;
            if (operation.equals("New user")){
                rs = stmt.executeQuery("SELECT id FROM auth WHERE login ='" + login + "';");
            } else {
                rs = stmt.executeQuery("SELECT id FROM auth WHERE login ='" + login + "' AND password='" + password + "';");
            }

            while (rs.next()) {
                id = rs.getInt(1);
            }
            logger.info("Id from db: " + id);

            if (operation.equals("New user")) {
                if (id == 0) {
                    result = stmt.executeUpdate("INSERT INTO auth (login, password) VALUES ('" + login + "','" + password + "');");
                    logger.info("ID = 0; Result: " + result);
                }
            } else {
                clientId = id;
                result = id;
            }

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result > 0;
    }

    private void connectToDatabase() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:cloud-server/src/main/resources/mainDB.db");
        stmt = conn.createStatement();
    }

    private void sendBytesObjectToClient(Object bytesToSend) {
        byte[] arr = (bytesToSend + "\n").getBytes();
        ByteBuf buf = ctx.alloc().buffer(arr.length);
        buf.writeBytes(arr);
        ctx.writeAndFlush(buf);
        logger.info("Send byte: " + bytesToSend);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx){
        this.ctx = ctx;
        logger.info("New client connected...");
        sendBytesObjectToClient(Bytes.BYTE_OF_CONFIRM.toByte());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (clients.size() > 0) {
            clients.remove(Integer.valueOf(clientId));
            logger.info(String.format("Client '%s' disconnected...\n", clientId));
        } else {
            logger.info("New client disconnect...");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
