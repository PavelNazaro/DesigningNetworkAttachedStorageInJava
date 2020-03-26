package com.geekbrains.cloud.storage.client;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
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
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Controller implements Initializable, Closeable {

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

    public static final int BYTES = 1024*32;
    private static final Logger logger = (Logger) LogManager.getLogger(Controller.class);
    private static final String FOLDER_CLIENT_FILES_NAME = "Client Files/";

    private enum Selected{
        CLEAR, LEFT, RIGHT
    }
    private Selected selected = Selected.CLEAR;

    private Socket socket;
    private DataOutputStream out;
    private Scanner in;

    @FXML private HBox mainPanel;
    @FXML private BorderPane authPanel;
    @FXML private BorderPane signUpPanel;
    @FXML private TextField loginFieldAdd;
    @FXML private TextField loginFieldAuth;
    @FXML private PasswordField passFieldAdd;
    @FXML private PasswordField passFieldAuth;
    @FXML private Button btnSignInAdd;
    @FXML private Button btnSignInAuth;
    @FXML private Button btnSignUpAdd;
    @FXML private Button btnSignUpAuth;
    @FXML private Button buttonRefreshAll;
    @FXML private Button buttonSelectAll;
    @FXML private Button buttonCopy;
    @FXML private Button buttonMove;
    @FXML private Button buttonDelete;
    @FXML private Button buttonRename;
    @FXML private ListView<String> listClientFiles;
    @FXML private ListView<String> listServerFiles;
    @FXML private ListView<Long> listSizeClientFiles;
    @FXML private ListView<Long> listSizeServerFiles;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        MultipleSelectionModel<String> lagsSelectionModel;
        lagsSelectionModel = listClientFiles.getSelectionModel();
        lagsSelectionModel.setSelectionMode(SelectionMode.MULTIPLE);
        lagsSelectionModel = listServerFiles.getSelectionModel();
        lagsSelectionModel.setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void connectToServer(String operation, String login, String password) {
        logger.info(String.format("Login to server with login: '%s' and password: '%s'", login, password));

        if (connectToServer()){
            logger.info("Wait connect");
            String x = in.nextLine();
            logger.info("Network: " + x);

            if (x.equals("Ok")) {
                if (operation.equals("Auth")){
                    logger.info("Auth");

                    if (requestToServer(BYTE_OF_AUTH, login, password) == BYTE_OF_AUTH_RIGHT){
                        authRight();
                        return;
                    } else {
                        authWrong();
                    }
                } else {
                    logger.info("New user");

                    if (requestToServer(BYTE_OF_NEW_USER, login, password) == BYTE_OF_NEW_USER_RIGHT){
                        newUserRight();
                    } else {
                        newUserWrong();
                    }
                }

                try {
                    close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else {
                showAlert("Error!","Error in connect to server!","Restart server and try again!");
            }
        }
    }

    private boolean connectToServer() {
        try {
            socket = new Socket("localhost", 8189);
            out = new DataOutputStream(socket.getOutputStream());
            in = new Scanner(socket.getInputStream());
        } catch (Exception e) {
            showAlert("Error!", "Connection to server refused!", "Restart server and try again!");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private int requestToServer(byte byteSend, String login, String password) {
        try {
            sendByteToServer(byteSend, login, password);
        } catch (IOException e) {
            e.printStackTrace();
        }

        int b = in.nextInt();
        logger.info("Byte: " + b);
        return b;
    }

    private void sendByteToServer(byte b, String login, String password) throws IOException {
        String loginAndPassword = login + " " + password;

        out.writeByte(b);
        out.writeInt(1);
        out.writeInt(loginAndPassword.length());

        byte[] stringBytes = loginAndPassword.getBytes(StandardCharsets.UTF_8);
        out.write(stringBytes);
    }

    private void authRight() {
        logger.info("Auth OK");

        new Thread(this::refreshAll).start();

        authPanel.setVisible(false);
        mainPanel.setVisible(true);
    }

    private void authWrong() {
        showAlert("Error!", "Incorrect login or password!", "Check you login and password and try again");
    }

    private void newUserRight() {
        showAlert("Successful", "Add new user done", "You can login");
        logger.info("New user add OK");
        signUpPanel.setVisible(false);
        authPanel.setVisible(true);
    }

    private void newUserWrong() {
        showAlert("Error!", "This login has already been added!", "Come up with a new login and try again");
    }

    @FXML
    public void pressButtonRefreshAll(ActionEvent actionEvent) {
        refreshAll();
    }

    private void refreshAll() {
        listServerFiles.getItems().clear();
        listClientFiles.getItems().clear();
        listSizeClientFiles.getItems().clear();
        listSizeServerFiles.getItems().clear();

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

        if (Thread.currentThread().getName().equals("JavaFX Application Thread")) {
            setListsViewClear();
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
                long size = in.nextLong();
                listSizeServerFiles.getItems().add(size);
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
                listOfFilesClientFiles.forEach(file -> {
                    long sizeFileName = 0;
                    try {
                        sizeFileName = Files.size(Paths.get(FOLDER_CLIENT_FILES_NAME + file));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    listSizeClientFiles.getItems().add(sizeFileName);
                });
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
        doCopyMoveDeleteRename("Copy", BYTE_OF_COPY_FILE);
    }

    @FXML
    public void pressButtonMove(ActionEvent actionEvent) {
        doCopyMoveDeleteRename("Move", BYTE_OF_MOVE_FILE);
    }

    @FXML
    public void pressButtonDelete(ActionEvent actionEvent) {
        doCopyMoveDeleteRename("Delete", BYTE_OF_DELETE_FILE);
    }

    @FXML
    public void pressButtonRename(ActionEvent actionEvent) {
        doCopyMoveDeleteRename("Rename", BYTE_OF_RENAME_FILE);
    }

    private void doCopyMoveDeleteRename(String operation, byte byteOfOperation) {
        long time = System.currentTimeMillis();
        if (selected != Selected.CLEAR) {
            if (selected == Selected.LEFT) {
                logger.info(operation + " from client");
                getListOfSelectedAndAction(operation);
            } else {
                logger.info(operation + " from server");
                sendListToGetOrDeleteFromServer(byteOfOperation);
            }
        } else {
            showAlert("Error!", "Nothing selected!", "Please select any file!");
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
            if (action.equals("Rename")) {
                if (selectedItemsListClients.size() == 1) {
                    String fileName = selectedItemsListClients.get(0);

                    Optional<String> result = setNewFileName(fileName);
                    if (result.isPresent()){
                        String newFileName = result.get();
                        try {
                            setNewFileName(fileName, newFileName);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    showAlert("Warning", "Can't rename many files!", "Select one file and try again");
                }
            } else {
                selectedItemsListClients.forEach(file -> {
                    try {
                        if (action.equals("Copy") || action.equals("Move")) {
                            copyFile(file, action);
                        } else {
                            deleteFile(file);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }

            refreshAll();
        }
    }

    private Optional<String> setNewFileName(String fileName) {
        TextInputDialog textInputDialog = new TextInputDialog(fileName);
        textInputDialog.setTitle("New name");
        textInputDialog.setHeaderText("Enter new name for file");
        textInputDialog.setContentText("New name:");
        return textInputDialog.showAndWait();
    }

    private void copyFile(String fileName, String action) throws IOException {
        sendFile(FOLDER_CLIENT_FILES_NAME + fileName);
        logger.info("File send" + fileName);
        if (action.equals("Move")) {
            deleteFile(fileName);
        }
    }

    private void deleteFile(String file) throws IOException {
        boolean bool = Files.deleteIfExists(Paths.get(FOLDER_CLIENT_FILES_NAME + file));
        logger.info("File delete: " + file + "; Result: " + bool);
    }

    private void setNewFileName(String file, String newFileName) throws IOException {
        Path path = Paths.get(FOLDER_CLIENT_FILES_NAME + file);
        Files.move(path, path.resolveSibling(newFileName));
        logger.info("File rename: " + file + " to new name: " + newFileName);
    }

    private void sendFile(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        long sizeFile = Files.size(path);

        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileName));

        out.writeByte(BYTE_OF_SEND_FILE_FROM_CLIENT);

        int length = path.getFileName().toString().length();
        out.writeInt(length);
        logger.info("Send length: " + length);

        byte[] filenameBytes = path.getFileName().toString().getBytes(StandardCharsets.UTF_8);
        out.write(filenameBytes);
        logger.info("Send fileNameBytes: " + new String(filenameBytes, StandardCharsets.UTF_8));

        out.writeLong(sizeFile);
        logger.info("Send size of file: " + sizeFile);

        logger.info("Sending file...");
        byte[] byteArray = new byte[1];
        while ((bis.read(byteArray)) != -1){
            out.writeByte(byteArray[0]);
        }
        logger.info("File send");
        bis.close();
    }

    private void sendListToGetOrDeleteFromServer(byte byteOfOperation){
        int countNeedsFilesFromServer = listServerFiles.getSelectionModel().getSelectedItems().size();
        if (countNeedsFilesFromServer != 0) {
            if (byteOfOperation == BYTE_OF_RENAME_FILE) {
                if (countNeedsFilesFromServer == 1) {
                    try {
                        String fileName = listServerFiles.getSelectionModel().getSelectedItem();

                        Optional<String> result = setNewFileName(fileName);
                        if (result.isPresent()){
                            String newFileName = result.get();
                            out.writeByte(byteOfOperation);
                            out.writeInt(2);
                            logger.info("Need to rename: " + fileName);

                            byte[] stringBytes;
                            out.writeInt(fileName.length());
                            stringBytes = fileName.getBytes(StandardCharsets.UTF_8);
                            out.write(stringBytes);

                            out.writeInt(newFileName.length());
                            stringBytes = newFileName.getBytes(StandardCharsets.UTF_8);
                            out.write(stringBytes);
                            logger.info("Rename send to server");

                            waitByteOfConfirm();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    showAlert("Warning", "Can't rename many files!", "Select one file and try again");
                }
            } else {
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

                    logger.info("Byte wait");
                    byte b = in.nextByte();
                    logger.info("Byte: " + b);

                    if (b == BYTE_OF_SEND_FILE_FROM_SERVER) {
                        int id = 0;
                        List<Integer> ints = listServerFiles.getSelectionModel().getSelectedIndices();
                        for (String fileName : listServerFiles.getSelectionModel().getSelectedItems()) {
                            getFile(fileName, listSizeServerFiles.getItems().get(ints.get(id++)));
                        }
                    } else {
                        logger.info("Error in inner byte!!!!!!!!!!!!!!!!!!!!");
                    }

                    try {
                        in = new Scanner(socket.getInputStream());
                        out.writeByte(BYTE_OF_CONFIRM);
                        Thread.sleep(10);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    logger.info("Files gets from server");
                }

                waitByteOfConfirm();
            }
        }
    }

    private void waitByteOfConfirm() {
        logger.info("Byte wait");
        byte b = in.nextByte();
        logger.info("Byte: " + b);
        if (b != BYTE_OF_CONFIRM) {
            logger.info("Error in inner byte!!!!!!!!!!!!!!!!!!!!");
        }
    }

    private void getFile(String fileName, long sizeFileName) {
        logger.info("File name: " + fileName);
        logger.info("Length file: " + sizeFileName);
        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(FOLDER_CLIENT_FILES_NAME + fileName));
            BufferedInputStream bufferedInputStream = new BufferedInputStream(socket.getInputStream());

            if (sizeFileName != 0) {
                byte[] bytes;
                logger.info("here");

                long leftBytes = 0;
                while (sizeFileName > leftBytes) {
                    int available = bufferedInputStream.available();
                    if (available == 0){
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        continue;
                    }
                    if (available < BYTES){
                        bytes = new byte[available];
                        leftBytes += available;
                    } else {
                        bytes = new byte[BYTES];
                        leftBytes += BYTES;
                    }

                    int count = bufferedInputStream.read(bytes);
                    bufferedOutputStream.write(bytes);
                }
                logger.info("Left: " + leftBytes);
                logger.info("File received");
            } else {
                logger.info("File is clear");
            }
            bufferedOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
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
            buttonRename.setText("<- Rename");
            selected = Selected.LEFT;
        }
    }

    @FXML
    public void mouseClickedListServers(MouseEvent mouseEvent) {
        listClientFiles.getSelectionModel().clearSelection();
        if (listServerFiles.getItems().get(0).equals("Empty")){
            listServerFiles.getSelectionModel().clearSelection();
            setButtonsCaptureClear();
        } else {
            buttonSelectAll.setText("Select all ->");
            buttonCopy.setText("<- Copy to");
            buttonMove.setText("<- Move to");
            buttonDelete.setText("Delete ->");
            buttonRename.setText("Rename ->");
            selected = Selected.RIGHT;
        }
    }

    @FXML
    public void mouseClickedListsBytes(MouseEvent mouseEvent) {
        setListsViewClear();
    }

    private void setListsViewClear() {
        listClientFiles.getSelectionModel().clearSelection();
        listServerFiles.getSelectionModel().clearSelection();
        setButtonsCaptureClear();
    }

    private void setButtonsCaptureClear() {
        buttonSelectAll.setText("Select all");
        buttonCopy.setText("Copy to");
        buttonMove.setText("Move to");
        buttonDelete.setText("Delete");
        buttonRename.setText("Rename");
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
    public void pressButtonSignInAdd(ActionEvent actionEvent) {
        signUpPanel.setVisible(false);
        authPanel.setVisible(true);
    }

    @FXML
    public void pressButtonSignUpAdd(ActionEvent actionEvent) {
        String login = loginFieldAdd.getText();
        String password = passFieldAdd.getText();
        if ((login.equals("") || password.equals(""))){
            showAlert("Error!", "Login or password is empty!", "Enter you login and password");
        } else {
            connectToServer("AddNewUser", login, password);
        }
    }

    @FXML
    public void pressButtonSignUpAuth(ActionEvent actionEvent) {
        authPanel.setVisible(false);
        signUpPanel.setVisible(true);
    }

    @FXML
    public void pressButtonSignInAuth(ActionEvent actionEvent) {
        String login = loginFieldAuth.getText();
        String password = passFieldAuth.getText();
        if ((login.equals("") || password.equals(""))){
            showAlert("Error!", "Login or password is empty!", "Enter you login and password");
        } else {
            connectToServer("Auth", login, password);
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
