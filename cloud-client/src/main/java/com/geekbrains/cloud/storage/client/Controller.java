package com.geekbrains.cloud.storage.client;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class Controller implements Initializable, Closeable {

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

    @FXML private HBox mainPanel;
    @FXML private VBox authPanel;
    @FXML private PasswordField passField;
    @FXML private Button btnAuth;
    @FXML private TextField loginField;

    @FXML private ListView<String> listClientFiles;
    @FXML private Button buttonMove;
    @FXML private Button buttonRefreshAll;
    @FXML private Button buttonCopy;
    @FXML private Button buttonDelete;
    @FXML private Button buttonSelectAll;
    @FXML private ListView<String> listServerFiles;

    private final String FOLDER_CLIENT_FILES_NAME = "Client Files/";
    private CountDownLatch latchNetworkStarter;
    private CountDownLatch latchAuth;
    private static final Logger logger = (Logger) LogManager.getLogger(Controller.class);

    private enum Selected{
        CLEAR, LEFT, RIGHT
    }
    private Selected selected = Selected.CLEAR;

    private Socket socket;
    private DataOutputStream out;
    private Scanner in;


    private long time;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        MultipleSelectionModel<String> lagsSelectionModel;
        lagsSelectionModel = listClientFiles.getSelectionModel();
        lagsSelectionModel.setSelectionMode(SelectionMode.MULTIPLE);
        lagsSelectionModel = listServerFiles.getSelectionModel();
        lagsSelectionModel.setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void connectToServer(String login, String password) {
        logger.info(String.format("Connect to server with login: '%s' and password: '%s'", login, password));

        try {
            socket = new Socket("localhost", 8189);
            out = new DataOutputStream(socket.getOutputStream());
            in = new Scanner(socket.getInputStream());

        } catch (Exception e) {
            showAlert("Error!", "Connection to server refused!", "Restart server and try again!");
            e.printStackTrace();
            return;
        }

        logger.info("Network");
        String x = in.nextLine();
        logger.info("X: " + x);

        if (x.equals("Ok")) {
            try {
                requestAuth(login, password);
            } catch (IOException e) {
                e.printStackTrace();
            }

            logger.info("Auth");
            int b = in.nextInt();
            logger.info("Byte: " + b);
            if (b == BYTE_OF_AUTH_RIGHT){
                authRight();
            } else {
                authWrong();
            }
            logger.info("Auth2");
        } else {
            showAlert("Error!","Error in connect to server!","Restart server and try again!");
        }
    }

    public void authRight() {
        logger.info("Auth OK");
        buttonRefreshAll.setVisible(true);
        buttonCopy.setVisible(true);
        buttonMove.setVisible(true);
        buttonDelete.setVisible(true);

        new Thread(this::refreshAll).start();

        authPanel.setVisible(false);
        mainPanel.setVisible(true);
    }

    public void authWrong() {
        showAlert("Error!", "Incorrect login or password!", "Check you login and password and try again");
    }

    private void requestAuth(String login, String password) throws IOException {
        String loginAndPassword = login + " " + password;

        out.writeByte(BYTE_OF_AUTH);
        out.writeInt(1);
        out.writeInt(loginAndPassword.length());

        byte[] stringBytes = loginAndPassword.getBytes(StandardCharsets.UTF_8);
        out.write(stringBytes);
        logger.info("Send request auth");
    }

    @FXML
    public void pressButtonRefreshAll(ActionEvent actionEvent) {
        refreshAll();
    }

    private void refreshAll() {
        listServerFiles.getItems().clear();
        listClientFiles.getItems().clear();

        try {
            refreshListOfClientFiles();
            refreshListOfServerFiles();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (listServerFiles.getItems().size() == 0){
            listServerFiles.getItems().add("Empty");
        }
        if (listClientFiles.getItems().size() == 0){
            listClientFiles.getItems().add("Empty");
        }
        logger.info("Refresh all done");
        logger.info("__________________________");
    }

    private void refreshListOfServerFiles() throws IOException {
        logger.info("Send byte refresh");
        out.writeByte(BYTE_OF_REFRESH);

        byte b = in.nextByte();
        logger.info("Byte: " + b);
        if (b == BYTE_OF_CONFIRM) {
            int countFiles = in.nextInt();
            logger.info("Count of files: " + countFiles);

            while (countFiles != 0){
                String str = in.next();
                listServerFiles.getItems().add(str);
                logger.info("File name: " + str);
                countFiles--;
            }
            logger.info("Refresh list servers");
        } else {
            logger.info("Error in inner byte!!");
        }
    }

    private void refreshListOfClientFiles() {
        try {
            Path path = Paths.get(FOLDER_CLIENT_FILES_NAME);
            if (Files.exists(path)){
                List<String> listOfFilesClientFiles = Files.list(path)
                        .filter(p -> Files.isRegularFile(p))
                        .map(Path::toFile)
                        .map(File::getName)
                        .collect(Collectors.toList());
                listClientFiles.getItems().addAll(listOfFilesClientFiles);
            } else {
                Files.createDirectories(path);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("Refresh list clients");
    }

    @FXML
    public void pressButtonCopy(ActionEvent actionEvent) {
        doCopyMoveDelete("Copy", BYTE_OF_COPY_FILE);
    }

    @FXML
    public void pressButtonMove(ActionEvent actionEvent) {
        doCopyMoveDelete("Move", BYTE_OF_MOVE_FILE);
    }

    @FXML
    public void pressButtonDelete(ActionEvent actionEvent) {
        doCopyMoveDelete("Delete", BYTE_OF_DELETE_FILE);
    }

    private void doCopyMoveDelete(String operation, byte byteOfOperation) {
        time = System.currentTimeMillis();
        if (selected != Selected.CLEAR) {
            if (selected == Selected.LEFT) {
                logger.info(operation + " from client");
                getListOfSelectedAndAction(operation);
            } else {
                logger.info(operation + " from server");
                sendListToGetFromServer(byteOfOperation);
            }
        }
        logger.info("----------Seconds----------");
        logger.info("Milli seconds left: " + (System.currentTimeMillis() - time));
        logger.info("Seconds left: " + (System.currentTimeMillis() - time) / 1000);
        logger.info("---------------------------");
        refreshAll();
    }

    @FXML
    public void pressButtonSelectAll(ActionEvent actionEvent) {
        if (selected != Selected.CLEAR) {
            if (buttonSelectAll.getText().equals("<- Select all")) {
                listClientFiles.getSelectionModel().selectAll();
            } else {
                listServerFiles.getSelectionModel().selectAll();
            }
        }
    }

    private void getListOfSelectedAndAction(String action) {
        ObservableList<String> selectedItemsListClients = listClientFiles.getSelectionModel().getSelectedItems();
        if (selectedItemsListClients.size() != 0) {
            selectedItemsListClients.forEach(file -> {
                try {
                    if (action.equals("Copy")) {
                        copyFile(file, false);
                    } else if (action.equals("Move")) {
                        copyFile(file, true);
                    } else {
                        deleteFile(file);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            refreshAll();
        } else {
            showAlert("Error!", "Nothing selected!", "Please select any file!");
        }
    }

    private void copyFile(String fileName, boolean isDeleteAfterCopy) throws IOException {
        sendFile(FOLDER_CLIENT_FILES_NAME + fileName);
        if (isDeleteAfterCopy) {
            logger.info("File move: " + fileName);
            deleteFile(fileName);
        }
        logger.info("File send");
    }

    private void deleteFile(String file) throws IOException {
        boolean bool = Files.deleteIfExists(Paths.get(FOLDER_CLIENT_FILES_NAME + file));
        logger.info("File delete: " + file + "; Result: " + bool);
    }

    private void sendFile(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        long size = Files.size(path);

        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileName));

        out.writeByte(BYTE_OF_SEND_FILE_FROM_CLIENT);

        int length = path.getFileName().toString().length();
        out.writeInt(length);
        logger.info("Send length: " + length);

        byte[] filenameBytes = path.getFileName().toString().getBytes(StandardCharsets.UTF_8);
        out.write(filenameBytes);
        logger.info("Send fileNameBytes: " + new String(filenameBytes, StandardCharsets.UTF_8));

        out.writeLong(size);

        byte[] byteArray = new byte[1];
        while ((bis.read(byteArray)) != -1){
            out.writeByte(byteArray[0]);
        }

        logger.info("Last byte: " + byteArray[0]);
        bis.close();
    }

    private void sendListToGetFromServer(byte byteOfOperation){
        int countNeedsFilesFromServer = listServerFiles.getSelectionModel().getSelectedItems().size();
        if (countNeedsFilesFromServer != 0) {
            logger.info("Send list of need to server");
            try {
                out.writeByte(byteOfOperation);
                out.writeInt(countNeedsFilesFromServer);
            } catch (IOException e) {
                e.printStackTrace();
            }

            listServerFiles.getSelectionModel().getSelectedItems().forEach(fileName -> {
                logger.info("Need: " + fileName);
                try {
                    out.writeInt(fileName.length());
                    byte[] stringBytes = fileName.getBytes(StandardCharsets.UTF_8);
                    out.write(stringBytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            logger.info("Operation send to server");

            if (byteOfOperation != BYTE_OF_DELETE_FILE) {

                listServerFiles.getSelectionModel().getSelectedItems().forEach(fileName -> {
                    getFile();
                });

                byte by = in.nextByte();
                logger.info("Byte: " + by);

                if (by != BYTE_OF_CONFIRM) {
                    logger.info("Error in inner byte!!");
                }
            }
            logger.info("Files gets from server");
        } else {
            showAlert("Error!", "Nothing selected!", "Please select any file!");
        }
    }

    private void getFile() {
        byte b = in.nextByte();
        logger.info("Byte: " + b);
        if (b == BYTE_OF_SEND_FILE_FROM_SERVER) {

            String fileName = in.next();
            listServerFiles.getItems().add(fileName);
            logger.info("File name: " + fileName);

            long receivedFileLength = 0L;
            try {
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(FOLDER_CLIENT_FILES_NAME + fileName));

                long fileLength = in.nextLong();
                logger.info("Length file: " + fileLength);

                if (fileLength != 0) {
                    while (fileLength >= receivedFileLength) {
                        byte by = in.nextByte();
                        bufferedOutputStream.write(by);
                        receivedFileLength++;
                        if (fileLength == receivedFileLength) {
                            logger.info("Last byte: " + by);
                            logger.info("File received");
                            bufferedOutputStream.close();
                            break;
                        }
                    }
                } else {
                    logger.info("File is clear");
                    bufferedOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            logger.info("Error in inner byte!!");
        }
    }

    public void mouseClickedListClients(MouseEvent mouseEvent) {
        listServerFiles.getSelectionModel().clearSelection();
        if (listClientFiles.getItems().get(0).equals("Empty")){
            listClientFiles.getSelectionModel().clearSelection();
            setButtonsCaptureClear();
        } else {
            buttonSelectAll.setText("<- Select all");
            buttonCopy.setText("Copy to ->");
            buttonMove.setText("Move to ->");
            buttonDelete.setText("<- Delete");
            selected = Selected.LEFT;
        }
    }

    public void mouseClickedListServers(MouseEvent mouseEvent) {
        listClientFiles.getSelectionModel().clearSelection();
        if (listServerFiles.getItems().get(0).equals("Empty")){
            listServerFiles.getSelectionModel().clearSelection();
            selected = Selected.CLEAR;
            setButtonsCaptureClear();
        } else {
            buttonSelectAll.setText("Select all ->");
            buttonCopy.setText("<- Copy to");
            buttonMove.setText("<- Move to");
            buttonDelete.setText("Delete ->");
            selected = Selected.RIGHT;
        }
    }

    private void setButtonsCaptureClear() {
        buttonSelectAll.setText("Select all");
        buttonCopy.setText("Copy to");
        buttonMove.setText("Move to");
        buttonDelete.setText("Delete");
        selected = Selected.CLEAR;
    }

    private void showAlert(String s1, String s2, String s3) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(s1);
        alert.setHeaderText(s2);
        alert.setContentText(s3);
        alert.showAndWait();
    }

    @FXML
    public void sendAuth(ActionEvent actionEvent) {
        String login = loginField.getText();
        String password = passField.getText();
        if ((login.equals("") || password.equals(""))){
            showAlert("Error!", "Login or password is empty!", "Enter you login and password");
        } else {
            connectToServer(login, password);
        }
    }

    public void handleExitAction(ActionEvent actionEvent) {
        Platform.exit();
    }

    @Override
    public void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }
}
