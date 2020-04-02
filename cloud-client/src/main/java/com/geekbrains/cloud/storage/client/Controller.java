package com.geekbrains.cloud.storage.client;

import com.geekbrains.cloud.storage.common.Bytes;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
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

    private static final int BYTES = 1024*32;
    private static final Logger logger = (Logger) LogManager.getLogger(Controller.class);

    private enum Selected{
        CLEAR, LEFT, RIGHT
    }
    private Selected selected = Selected.CLEAR;

    private int clientId = 0;
    private String folderClientFilesName;
    private static final String FOLDER_CLIENT_FILES_NAME = "Client Files/";

    private Socket socket;
    private DataOutputStream out;
    private Scanner in;

    Stage stage;

    @FXML private MenuBar menuBar;
    @FXML private HBox mainPanel;
    @FXML private BorderPane authPanel;
    @FXML private BorderPane signUpPanel;
    @FXML private TextField loginFieldAdd;
    @FXML private TextField loginFieldAuth;
    @FXML private PasswordField passFieldAdd;
    @FXML private PasswordField passFieldAuth;
    @FXML private Button buttonSignInAdd;
    @FXML private Button buttonSignInAuth;
    @FXML private Button buttonSignUpAdd;
    @FXML private Button buttonSignUpAuth;
    @FXML private Button buttonRefreshAll;
    @FXML private Button buttonSelectAll;
    @FXML private Button buttonCopy;
    @FXML private Button buttonMove;
    @FXML private Button buttonDelete;
    @FXML private Button buttonRename;
    @FXML private ListView<String> listViewOfClientFiles;
    @FXML private ListView<String> listViewOfServerFiles;
    @FXML private ListView<Long> listViewOfSizeClientFiles;
    @FXML private ListView<Long> listViewOfSizeServerFiles;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        MultipleSelectionModel<String> lagsSelectionModel;
        lagsSelectionModel = listViewOfClientFiles.getSelectionModel();
        lagsSelectionModel.setSelectionMode(SelectionMode.MULTIPLE);
        lagsSelectionModel = listViewOfServerFiles.getSelectionModel();
        lagsSelectionModel.setSelectionMode(SelectionMode.MULTIPLE);

        ContextMenu contextMenu = new ContextMenu();
        MenuItem refreshAll = new MenuItem("Refresh all");
        refreshAll.setOnAction(this::pressButtonRefreshAll);
        contextMenu.getItems().add(refreshAll);
        MenuItem selectAll = new MenuItem("Select all");
        selectAll.setOnAction(this::pressButtonSelectAll);
        contextMenu.getItems().add(selectAll);
        MenuItem copy = new MenuItem("Copy");
        copy.setOnAction(this::pressButtonCopy);
        contextMenu.getItems().add(copy);
        MenuItem move = new MenuItem("Move");
        move.setOnAction(this::pressButtonMove);
        contextMenu.getItems().add(move);
        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(this::pressButtonDelete);
        contextMenu.getItems().add(delete);
        MenuItem rename = new MenuItem("Rename");
        rename.setOnAction(this::pressButtonRename);
        contextMenu.getItems().add(rename);

        listViewOfClientFiles.setContextMenu(contextMenu);
        listViewOfServerFiles.setContextMenu(contextMenu);
    }

    private void connectToServer(String operation, String login, String password) {
        logger.info(String.format("Login to server with login: '%s' and password: '%s'", login, password));

        if (connectToServer()){
            logger.info("Wait connect");
            byte x = in.nextByte();
            logger.info("Network: " + x);

            if (x == Bytes.BYTE_OF_CONFIRM.toByte()) {
                if (operation.equals("Auth")){
                    logger.info("Auth");

                    if (requestToServer(Bytes.BYTE_OF_AUTH.toByte(), login, password) == Bytes.BYTE_OF_AUTH_RIGHT.toByte()){
                        logger.info("Auth OK");
                        authRight();
                        return;
                    } else {
                        logger.info("Auth wrong!");
                        authWrong();
                    }
                } else {
                    logger.info("New user");

                    if (requestToServer(Bytes.BYTE_OF_NEW_USER.toByte(), login, password) == Bytes.BYTE_OF_NEW_USER_RIGHT.toByte()){
                        logger.info("New user add OK");
                        newUserRight();
                    } else {
                        logger.info("New user add wrong!");
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

        logger.info("Wait byte...");
        int b = in.nextInt();
        logger.info("Byte: " + b);
        return b;
    }

    private void sendByteToServer(byte b, String login, String password) throws IOException {
        String loginAndPassword = login + " " + password;

        out.writeByte(b);
        logger.info("Byte: " + b);
        waitTenMs();
        out.writeInt(1);
        logger.info("Int: " + 1);
        waitTenMs();
        out.writeInt(loginAndPassword.length());
        logger.info("Int: " + loginAndPassword.length());
        waitTenMs();

        byte[] stringBytes = loginAndPassword.getBytes(StandardCharsets.UTF_8);
        out.write(stringBytes);
    }

    private void waitTenMs() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void authRight() {
        stage = (Stage) menuBar.getScene().getWindow();
        clientId = in.nextInt();
        stage.setTitle(stage.getTitle() + ": user id" + clientId);
        logger.info("ClientId: " + clientId);
        folderClientFilesName = FOLDER_CLIENT_FILES_NAME + "user" + clientId + "/";

        new Thread(this::refreshAll).start();

        authPanel.setVisible(false);
        mainPanel.setVisible(true);
    }

    private void authWrong() {
        showAlert("Error!", "Incorrect login or password!", "Check you login and password and try again");
    }

    private void newUserRight() {
        showAlert("Successful", "Add new user done", "You can login");
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
        refreshListOfClientFiles();
        refreshListOfServerFiles();

        logger.info("Refresh all done");
        logger.info("__________________________");
    }

    private void refreshListOfServerFiles() {
        listViewOfServerFiles.getItems().clear();
        listViewOfSizeServerFiles.getItems().clear();

        logger.info("Send byte refresh");
        try {
            out.writeByte(Bytes.BYTE_OF_REFRESH.toByte());
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte b = in.nextByte();
        logger.info("Byte: " + b);
        if (b == Bytes.BYTE_OF_CONFIRM.toByte()) {
            int countFiles = in.nextInt();
            logger.info("Count of files: " + countFiles);

            while (countFiles != 0){
                String str = in.next();
                listViewOfServerFiles.getItems().add(str);
                long size = in.nextLong();
                listViewOfSizeServerFiles.getItems().add(size);
                logger.info("File name: " + str);
                countFiles--;
            }
            logger.info("Refresh list servers done");
        } else {
            logger.info("Error in inner byte!!");
        }
        if (listViewOfServerFiles.getItems().size() == 0){
            listViewOfServerFiles.getItems().add("Empty");
        }

        if (Thread.currentThread().getName().equals("JavaFX Application Thread")) {
            setListsViewClear();
        }
    }

    private void refreshListOfClientFiles() {
        listViewOfClientFiles.getItems().clear();
        listViewOfSizeClientFiles.getItems().clear();

        try {
            logger.info("Path folder: " + folderClientFilesName);
            Path path = Paths.get(folderClientFilesName);
            if (Files.exists(path)){
                List<String> listOfClientFiles = Files.list(path)
                        .filter(p -> Files.isRegularFile(p))
                        .map(Path::toFile)
                        .map(File::getName)
                        .collect(Collectors.toList());
                listViewOfClientFiles.getItems().addAll(listOfClientFiles);
                listOfClientFiles.forEach(file -> {
                    long sizeFileName = 0;
                    try {
                        sizeFileName = Files.size(Paths.get(folderClientFilesName + file));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    listViewOfSizeClientFiles.getItems().add(sizeFileName);
                });
            } else {
                Files.createDirectories(path);
            }

            logger.info("Refresh list clients done");
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (listViewOfClientFiles.getItems().size() == 0){
            listViewOfClientFiles.getItems().add("Empty");
        }

        if (Thread.currentThread().getName().equals("JavaFX Application Thread")) {
            setListsViewClear();
        }
    }

    @FXML
    public void pressButtonCopy(ActionEvent actionEvent) {
        doCopyMoveDeleteRename("Copy", Bytes.BYTE_OF_COPY_FILE.toByte());
    }

    @FXML
    public void pressButtonMove(ActionEvent actionEvent) {
        doCopyMoveDeleteRename("Move", Bytes.BYTE_OF_MOVE_FILE.toByte());
    }

    @FXML
    public void pressButtonDelete(ActionEvent actionEvent) {
        doCopyMoveDeleteRename("Delete", Bytes.BYTE_OF_DELETE_FILE.toByte());
    }

    @FXML
    public void pressButtonRename(ActionEvent actionEvent) {
        doCopyMoveDeleteRename("Rename", Bytes.BYTE_OF_RENAME_FILE.toByte());
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
                listViewOfClientFiles.getSelectionModel().selectAll();
            } else {
                listViewOfServerFiles.getSelectionModel().selectAll();
            }
        }
    }

    private void getListOfSelectedAndAction(String action) {
        ObservableList<String> selectedItemsListClients = listViewOfClientFiles.getSelectionModel().getSelectedItems();
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
        sendFile(folderClientFilesName + fileName);
        logger.info("File send: " + fileName);
        if (action.equals("Move")) {
            deleteFile(fileName);
        }
    }

    private void deleteFile(String file) throws IOException {
        boolean bool = Files.deleteIfExists(Paths.get(folderClientFilesName + file));
        logger.info("File delete: " + file + "; Result: " + bool);
    }

    private void setNewFileName(String file, String newFileName) throws IOException {
        Path path = Paths.get(folderClientFilesName + file);
        Files.move(path, path.resolveSibling(newFileName));
        logger.info("File rename: " + file + " to new name: " + newFileName);
    }

    private void sendFile(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        long sizeFile = Files.size(path);

        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileName));

        out.writeByte(Bytes.BYTE_OF_SEND_FILE_FROM_CLIENT.toByte());

        int length = path.getFileName().toString().length();
        out.writeInt(length);
        logger.info("Send length: " + length);

        byte[] filenameBytes = path.getFileName().toString().getBytes(StandardCharsets.UTF_8);
        out.write(filenameBytes);
        logger.info("Send fileNameBytes: " + new String(filenameBytes, StandardCharsets.UTF_8));

        out.writeLong(sizeFile);
        logger.info("Send size of file: " + sizeFile);

        logger.info("Sending file...");
        out.write(bis.readNBytes((int) sizeFile));

        bis.close();
    }

    private void sendListToGetOrDeleteFromServer(byte byteOfOperation){
        int countNeedsFilesFromServer = listViewOfServerFiles.getSelectionModel().getSelectedItems().size();
        if (countNeedsFilesFromServer != 0) {
            if (byteOfOperation == Bytes.BYTE_OF_RENAME_FILE.toByte()) {
                if (countNeedsFilesFromServer == 1) {
                    try {
                        String fileName = listViewOfServerFiles.getSelectionModel().getSelectedItem();

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
                    logger.info("Byte: " + byteOfOperation);
                    out.writeByte(byteOfOperation);
                    logger.info("Count: " + countNeedsFilesFromServer);
                    out.writeInt(countNeedsFilesFromServer);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                listViewOfServerFiles.getSelectionModel().getSelectedItems().forEach(fileName -> {
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

                if (byteOfOperation != Bytes.BYTE_OF_DELETE_FILE.toByte()) {

                    logger.info("Byte wait");
                    byte b = in.nextByte();
                    logger.info("Byte: " + b);

                    if (b == Bytes.BYTE_OF_SEND_FILE_FROM_SERVER.toByte()) {
                        int id = 0;
                        List<Integer> ints = listViewOfServerFiles.getSelectionModel().getSelectedIndices();
                        for (String fileName : listViewOfServerFiles.getSelectionModel().getSelectedItems()) {
                            getFile(fileName, listViewOfSizeServerFiles.getItems().get(ints.get(id++)));
                        }
                    } else {
                        logger.info("Error in inner byte!!!!!!!!!!!!!!!!!!!!");
                    }

                    try {
                        in = new Scanner(socket.getInputStream());
                        out.writeByte(Bytes.BYTE_OF_CONFIRM.toByte());
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
        if (b != Bytes.BYTE_OF_CONFIRM.toByte()) {
            logger.info("Error in inner byte!!!!!!!!!!!!!!!!!!!!");
        }
    }

    private void getFile(String fileName, long sizeFileName) {
        logger.info("File name: " + fileName);
        logger.info("Length file: " + sizeFileName);
        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(folderClientFilesName + fileName));
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

                    //noinspection ResultOfMethodCallIgnored
                    bufferedInputStream.read(bytes);
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
        listViewOfServerFiles.getSelectionModel().clearSelection();
        if (listViewOfClientFiles.getItems().get(0).equals("Empty")){
            listViewOfClientFiles.getSelectionModel().clearSelection();
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
        listViewOfClientFiles.getSelectionModel().clearSelection();
        if (listViewOfServerFiles.getItems().get(0).equals("Empty")){
            listViewOfServerFiles.getSelectionModel().clearSelection();
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
        listViewOfClientFiles.getSelectionModel().clearSelection();
        listViewOfServerFiles.getSelectionModel().clearSelection();
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
        loginFieldAuth.clear();
        passFieldAuth.clear();
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
        loginFieldAdd.clear();
        passFieldAdd.clear();
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

    public void handleExitAction(ActionEvent actionEvent) throws IOException {
        close();
        Platform.exit();
    }

    @Override
    public void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }

    @FXML
    public void handleAboutAction(ActionEvent actionEvent) {
        showAlert("About", "Create by PavelNazaro", "");
    }
}
