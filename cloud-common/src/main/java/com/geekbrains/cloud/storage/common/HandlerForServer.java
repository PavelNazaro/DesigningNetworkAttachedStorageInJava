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
import java.util.*;
import java.util.stream.Collectors;

public class HandlerForServer extends ChannelInboundHandlerAdapter {

    public static final String LOGIN = "login";
    public static final String PASSWORD = "password";

    public enum State {
        IDLE,
        NAME_LENGTH, NAME, FILE_LENGTH, FILE,
        GET_COUNT_FILES, GET_LENGTH_NAME_FILE, GET_NAME_FILE,
        SEND_COUNT_FILES,
        SEND_CONFIRM
    }
    private static final List<Channel> channels = new ArrayList<>();
    private String clientName;

    public static final byte BYTE_OF_OK = 17;
    public static final byte BYTE_OF_CONFIRM = 18;
    public static final byte BYTE_OF_AUTH_WRONG = 19;
    public static final byte BYTE_OF_AUTH_RIGHT = 20;
    public static final byte BYTE_OF_AUTH = 21;
    public static final byte BYTE_OF_DELETE_FILE = 22;
    public static final byte BYTE_OF_MOVE_FILE = 23;
    public static final byte BYTE_OF_COPY_FILE = 24;
    public static final byte BYTE_OF_SEND_FILE_FROM_SERVER = 25;
    public static final byte BYTE_OF_SEND_FILE_FROM_CLIENT = 26;
    public static final byte BYTE_OF_REFRESH = 27;
    public static final byte BYTE_OF_COUNT_OF_FILES = 28;

    private State currentState = State.IDLE;
    private int nextLength;
    private int length;
    private int countFiles;
    private int byteMemory;
    private long fileLength;
    private long receivedFileLength;
    private BufferedOutputStream out;
    private byte[] fileName;

    private static final String FOLDER_SERVER_FILES_NAME = "Server Files/";
    private static final String FOLDER_CLIENT_FILES_NAME = "Client Files/";
    private List<String> listOfNeedsFiles = new ArrayList<>();
    private static final Logger logger = (Logger) LogManager.getLogger(HandlerForServer.class);

    private ChannelHandlerContext ctx;

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
                        byteMemory == BYTE_OF_COPY_FILE ||
                        byteMemory == BYTE_OF_MOVE_FILE ||
                        byteMemory == BYTE_OF_DELETE_FILE){
                    logger.info("Auth, copy, move or delete");
                    listOfNeedsFiles.clear();
                    currentState = State.GET_COUNT_FILES;
                } else if (byteMemory == BYTE_OF_REFRESH){
                    logger.info("Refresh");
                    currentState = State.SEND_COUNT_FILES;
                } else if (byteMemory == BYTE_OF_CONFIRM){
                    logger.info("Confirm");
                    currentState = State.SEND_CONFIRM;
                } else {
                    logger.info("ERROR: Invalid first byte - " + byteMemory);
                    System.out.println("ERROR: Invalid first byte - " + byteMemory);
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
                    out = new BufferedOutputStream(new FileOutputStream(FOLDER_SERVER_FILES_NAME + new String(fileName, StandardCharsets.UTF_8)));
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
                    logger.info("Left: " + (fileLength - receivedFileLength));
                } else {
                    currentState = State.IDLE;
                    out.close();
                }
            }

            // Auth, copy, move and delete:

            if (currentState == State.GET_COUNT_FILES) {
                if (buf.readableBytes() >= 1) {
                    countFiles = buf.readInt();
                    if (byteMemory == BYTE_OF_AUTH){
                        logger.info("Auth");
                    } else {
                        logger.info("Copy, move or delete");
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
                    byte[] fileName = new byte[length];
                    buf.readBytes(fileName);
                    logger.info("File name: " + new String(fileName, StandardCharsets.UTF_8));

                    if (byteMemory == BYTE_OF_AUTH){
                        sendRequestAuth(new String(fileName, StandardCharsets.UTF_8));
                        logger.info("Byte of Auth");
                        currentState = State.IDLE;
                        break;
                    } else {
                        listOfNeedsFiles.add(new String(fileName, StandardCharsets.UTF_8));
                        countFiles--;
                        if (countFiles == 0) {

                            if (byteMemory == BYTE_OF_DELETE_FILE) {
                                listOfNeedsFiles.forEach(file -> {
                                    try {
                                        deleteFile(file);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
                                logger.info("Files delete");
                                sendByteOfConfirm();
                            } else {
                                buf = ByteBufAllocator.DEFAULT.directBuffer(1);
                                buf.writeByte(BYTE_OF_SEND_FILE_FROM_SERVER);
                                ctx.channel().writeAndFlush(buf);
                                logger.info("Send byte");

                                listOfNeedsFiles.forEach(file -> {
                                    try {
                                        if (byteMemory == BYTE_OF_COPY_FILE) {
                                            copyFile(ctx, file, false);
                                        } else {
                                            copyFile(ctx, file, true);
                                        }
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
                Path path = Paths.get(FOLDER_SERVER_FILES_NAME);
                if (!Files.exists(path)){
                    Files.createDirectories(path);
                    logger.info("Create new directory");
                }
                List<String> listOfFilesClient = Files.list(path)
                        .filter(p -> Files.isRegularFile(p))
                        .map(Path::toFile)
                        .map(File::getName)
                        .collect(Collectors.toList());
                Map<String, Long> map = new HashMap<>();

                listOfFilesClient.forEach(file -> {
                    long sizeFileName = 0;
                    try {
                        sizeFileName = Files.size(Paths.get(FOLDER_SERVER_FILES_NAME + file));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    map.put(file, sizeFileName);
                });

                sendByteOfConfirm();

                buf = ByteBufAllocator.DEFAULT.directBuffer(1);
                buf.writeByte(listOfFilesClient.size());
                ctx.channel().writeAndFlush(buf);

                map.forEach((file, length) -> {
                    ctx.channel().writeAndFlush(file);
                    ctx.channel().writeAndFlush(length);
                });
//                listOfFilesClient.forEach(file -> {
//                    ctx.channel().writeAndFlush(file);
//                });

                currentState = State.IDLE;
                break;
            }
            if (currentState == State.SEND_CONFIRM){
                sendByteOfConfirm();
            }
        }
        logger.info("Zero readable bytes, state: " + currentState);
        if (buf.readableBytes() == 0) {
            buf.release();
        }
    }

    private void sendByteOfConfirm() {
        ByteBuf buf;
        buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(BYTE_OF_CONFIRM);
        ctx.channel().writeAndFlush(buf);
        currentState = State.IDLE;
    }

    private void copyFile(ChannelHandlerContext ctx, String file, boolean isDeleteAfterCopy) throws IOException {
        sendFile(FOLDER_SERVER_FILES_NAME + file, ctx.channel());
        if (isDeleteAfterCopy) {
            logger.info("File move: " + file);
            deleteFile(file);
        }
        logger.info("File send");
    }

    private void deleteFile(String file) throws IOException {
        logger.info("File delete: " + file);
        Files.deleteIfExists(Paths.get(FOLDER_SERVER_FILES_NAME + file));
    }

    private void sendRequestAuth(String loginAndPassword) {
        String login = loginAndPassword.split(" ")[0];
        String password = loginAndPassword.split(" ")[1];
        logger.info("AUTH: "+ loginAndPassword);

        ByteBuf buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        if (login.equals(LOGIN) && password.equals(PASSWORD)){
            buf.writeByte(BYTE_OF_AUTH_RIGHT);
            ctx.channel().writeAndFlush(buf);
            logger.info("Auth OK");
        } else {
            buf.writeByte(BYTE_OF_AUTH_WRONG);
            logger.info("Auth wrong. Close channel!");
            ctx.channel().writeAndFlush(buf);
            ctx.channel().close();
        }
    }

    private void sendFile(String fileName, Channel channel) throws IOException {
        Path path = Paths.get(fileName);
        long sizeFileName = Files.size(path);

        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileName));

        if (sizeFileName != 0) {

            byte[] bytes = new byte[(int) sizeFileName];

            int count = bis.read(bytes);
            System.out.println("Bytes: " + Arrays.toString(bytes));
            System.out.println("Count: " + count);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            channel.writeAndFlush(bytes);

//        channel.writeAndFlush(bis.readNBytes((int) size));
            logger.info("Done");
        } else {
            logger.info("File is clear");
        }

        bis.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channels.add(ctx.channel());
        clientName = "Client #" + channels.size();
        logger.info(String.format("Client '%s' connected...\n", clientName));

        ctx.channel().writeAndFlush("Ok\n");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        channels.remove(ctx.channel());
        logger.info(String.format("Client '%s' disconnected...\n", clientName));
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        channels.remove(ctx.channel());
        cause.printStackTrace();
        ctx.close();
    }
}
