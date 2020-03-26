package com.geekbrains.cloud.storage.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
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

    public static final byte BYTE_OF_CONFIRM = 10;
    public static final byte BYTE_OF_REFRESH = 11;
    public static final byte BYTE_OF_NEW_USER = 12;
    public static final byte BYTE_OF_NEW_USER_RIGHT = 13;
    public static final byte BYTE_OF_NEW_USER_WRONG = 14;
    public static final byte BYTE_OF_AUTH = 15;
    public static final byte BYTE_OF_AUTH_RIGHT = 16;
    public static final byte BYTE_OF_AUTH_WRONG = 17;
    public static final byte BYTE_OF_COPY_FILE = 18;
    public static final byte BYTE_OF_MOVE_FILE = 19;
    public static final byte BYTE_OF_DELETE_FILE = 20;
    public static final byte BYTE_OF_RENAME_FILE = 21;
    public static final byte BYTE_OF_COUNT_OF_FILES = 22;
    public static final byte BYTE_OF_SEND_FILE_FROM_SERVER = 23;
    public static final byte BYTE_OF_SEND_FILE_FROM_CLIENT = 24;

    private static final Logger logger = (Logger) LogManager.getLogger(HandlerForServer.class);

    public enum State {
        IDLE,
        NAME_LENGTH, NAME, FILE_LENGTH, FILE,
        GET_COUNT_FILES, GET_LENGTH_NAME_FILE, GET_NAME_FILE,
        SEND_COUNT_FILES,
        SEND_CONFIRM
    }
    private State currentState = State.IDLE;

    private int clientId = 0;
    private int nextLength;
    private int length;
    private int countFiles;
    private byte byteMemory;
    private long fileLength;
    private long receivedFileLength;
    private byte[] fileName;

    private String folderServerFilesName;
    private List<Integer> clients = new ArrayList<>();
    private List<String> listOfOperationOfFiles = new ArrayList<>();

    private BufferedOutputStream out;
    private ChannelHandlerContext ctx;
    private Connection conn;
    private Statement stmt;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        this.ctx = ctx;
        ByteBuf buf = ((ByteBuf) msg);
        while (buf.readableBytes() > 0) {
            if (currentState == State.IDLE) {
                byteMemory = buf.readByte();
                logger.info("Byte of: " + byteMemory);
                if (byteMemory == BYTE_OF_SEND_FILE_FROM_CLIENT) {
                    logger.info("Send file from client");
                    currentState = State.NAME_LENGTH;
                    receivedFileLength = 0L;
                } else if (byteMemory == BYTE_OF_AUTH ||
                        byteMemory == BYTE_OF_NEW_USER ||
                        byteMemory == BYTE_OF_COPY_FILE ||
                        byteMemory == BYTE_OF_MOVE_FILE ||
                        byteMemory == BYTE_OF_DELETE_FILE ||
                        byteMemory == BYTE_OF_RENAME_FILE){
                    logger.info("Auth, copy, move or delete");
                    listOfOperationOfFiles.clear();
                    currentState = State.GET_COUNT_FILES;
                } else if (byteMemory == BYTE_OF_REFRESH){
                    logger.info("Refresh");
                    currentState = State.SEND_COUNT_FILES;
                } else if (byteMemory == BYTE_OF_CONFIRM){
                    logger.info("Confirm");
                    currentState = State.SEND_CONFIRM;
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
                            new FileOutputStream(folderServerFilesName
                                    + new String(fileName, StandardCharsets.UTF_8)));
                    currentState = State.FILE_LENGTH;
                }
            }

            if (currentState == State.FILE_LENGTH) {
                if (buf.readableBytes() >= 8) {
                    fileLength = buf.readLong();
                    currentState = State.FILE;
                    logger.info("File length: " + fileLength);
                }
            }

            if (currentState == State.FILE) {
                if (fileLength != 0) {
                    while (buf.readableBytes() > 0) {
                        out.write(buf.readByte());
                        receivedFileLength++;
                        if (fileLength == receivedFileLength) {
                            currentState = State.IDLE;
                            logger.info("File received");
                            out.close();
                            break;
                        }
                    }
                } else {
                    currentState = State.IDLE;
                    out.close();
                }
            }

            //------------------------------
            // Auth, copy, move and delete:
            if (currentState == State.GET_COUNT_FILES) {
                if (buf.readableBytes() >= 1) {
                    countFiles = buf.readInt();
                    if (byteMemory == BYTE_OF_AUTH){
                        logger.info("Auth");
                    } else if (byteMemory == BYTE_OF_NEW_USER){
                        logger.info("New user");
                    } else {
                        logger.info("Copy, move, delete or rename");
                        logger.info("Count of files: " + countFiles);
                    }
                    if (countFiles == 0){
                        currentState = State.IDLE;
                        break;
                    } else {
                        currentState = State.GET_LENGTH_NAME_FILE;
                    }
                }
            }

            if (currentState == State.GET_LENGTH_NAME_FILE) {
                if (buf.readableBytes() >= 4) {
                    length = buf.readInt();
                    currentState = State.GET_NAME_FILE;
                    logger.info("Length: " + length);
                }
            }

            if (currentState == State.GET_NAME_FILE) {
                if (buf.readableBytes() >= length) {
                    byte[] bytesFileName = new byte[length];
                    buf.readBytes(bytesFileName);
                    String fileName = new String(bytesFileName, StandardCharsets.UTF_8);
                    logger.info("File name: " + fileName);

                    if (byteMemory == BYTE_OF_AUTH){
                        sendRequestToDataBase("Auth",
                                BYTE_OF_AUTH_RIGHT, BYTE_OF_AUTH_WRONG,
                                fileName);
                        logger.info("Byte of Auth");
                        currentState = State.IDLE;
                        break;
                    } else if (byteMemory == BYTE_OF_NEW_USER){
                        sendRequestToDataBase("New user",
                                BYTE_OF_NEW_USER_RIGHT, BYTE_OF_NEW_USER_WRONG,
                                fileName);
                        logger.info("Byte of new user");
                        currentState = State.IDLE;
                        break;
                    } else {
                        listOfOperationOfFiles.add(fileName);
                        countFiles--;
                        if (countFiles == 0) {
                            if (byteMemory == BYTE_OF_DELETE_FILE) {
                                listOfOperationOfFiles.forEach(file -> {
                                    try {
                                        deleteFile(file);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
                                logger.info("Files delete");
                                sendByteBuf(1, BYTE_OF_CONFIRM);
                            } else if (byteMemory == BYTE_OF_RENAME_FILE) {
                                String file = listOfOperationOfFiles.get(0);
                                String newFileName = listOfOperationOfFiles.get(1);
                                Path path = Paths.get(folderServerFilesName + file);
                                Files.move(path, path.resolveSibling(newFileName));
                                logger.info("File rename: " + file + " to new name: " + newFileName);
                                sendByteBuf(1, BYTE_OF_CONFIRM);
                            } else {
                                sendByteBuf(1, BYTE_OF_SEND_FILE_FROM_SERVER);
                                listOfOperationOfFiles.forEach(file -> {
                                    try {
                                        copyFile(ctx, file, byteMemory);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
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
                logger.info("Send list of files from server");
                Path path = Paths.get(folderServerFilesName);
                if (!Files.exists(path)){
                    Files.createDirectories(path);
                    logger.info("Create new directory");
                }
                List<String> listOfFilesClient = Files.list(path)
                        .filter(p -> Files.isRegularFile(p))
                        .map(Path::toFile)
                        .map(File::getName)
                        .collect(Collectors.toList());
                Map<String, Long> mapFileNameAndSize = new HashMap<>();

                listOfFilesClient.forEach(file -> {
                    long sizeFileName = 0;
                    try {
                        sizeFileName = Files.size(Paths.get(folderServerFilesName + file));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mapFileNameAndSize.put(file, sizeFileName);
                });

                sendByteBuf(1, BYTE_OF_CONFIRM);
                sendByteBuf(1, listOfFilesClient.size());

                mapFileNameAndSize.forEach((file, length) -> {
                    ctx.channel().writeAndFlush(file);
                    ctx.channel().writeAndFlush(length);
                });

                currentState = State.IDLE;
                break;
            }

            //------------------------------
            //Return byte of confirm from client
            if (currentState == State.SEND_CONFIRM){
                sendByteBuf(1, BYTE_OF_CONFIRM);
                currentState = State.IDLE;
            }
        }
//        logger.info("Zero readable bytes, state: " + currentState);
        if (buf.readableBytes() == 0) {
            buf.release();
        }
    }

    private void copyFile(ChannelHandlerContext ctx, String file, byte byteMemory) throws IOException {
        sendFile(folderServerFilesName + file, ctx.channel());
        if (byteMemory == BYTE_OF_MOVE_FILE) {
            logger.info("File move: " + file);
            deleteFile(file);
        }
        logger.info("File send");
    }

    private void deleteFile(String file) throws IOException {
        logger.info("File delete: " + file);
        Files.deleteIfExists(Paths.get(folderServerFilesName + file));
    }

    private void sendRequestToDataBase(String operation, byte byteOfRight, byte byteOfWrong, String loginAndPassword) {
        String login = loginAndPassword.split(" ")[0];
        String password = loginAndPassword.split(" ")[1];
        logger.info(operation + ": "+ loginAndPassword);

        if (sendQueryFromDB(operation, login, password)){
            logger.info(operation + " OK");
            sendByteBuf(1, byteOfRight);
            if (operation.equals("Auth")) {
                clients.add(clientId);
                logger.info("Client id: " + clientId);
                folderServerFilesName = "Server Files/Client_Id_" + clientId + "/";
            } else {
                ctx.channel().close();
            }
        } else {
            logger.info(operation + " wrong. Close channel!");
            sendByteBuf(1, byteOfWrong);
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

        //if insert -> true
        return result > 0;
    }

    private void connectToDatabase() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:cloud-server/src/main/resources/mainDB.db");
        stmt = conn.createStatement();
    }

    private void sendFile(String fileName, Channel channel) throws IOException {
        Path path = Paths.get(fileName);
        long sizeFileName = Files.size(path);
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileName));

        if (sizeFileName != 0) {
            logger.info("Sending file...");
            byte[] bytes = new byte[(int) sizeFileName];
            int count = bis.read(bytes);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            channel.writeAndFlush(bytes);
            logger.info("File send");
        } else {
            logger.info("File is clear");
        }
        bis.close();
    }

    private void sendByteBuf(int i, int byteOfSendFileFromServer) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.directBuffer(i);
        buf.writeByte(byteOfSendFileFromServer);
        ctx.channel().writeAndFlush(buf);
        logger.info("Send byte");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx){
        logger.info("New client connected...");
        ctx.channel().writeAndFlush("Ok\n");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (clients.size() > 0) {
            clients.remove(Integer.valueOf(clientId));
            logger.info(String.format("Client '%s' disconnected...\n", clientId));
        } else {
            logger.info("New client disconnect...");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
