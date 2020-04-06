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
import java.util.*;
import java.util.stream.Collectors;

public class Controller implements Initializable, Closeable {

    private static final int BYTES = 1024*32;
    private static final Logger logger = (Logger) LogManager.getLogger(Controller.class);

    private enum Selected{
        CLEAR, LEFT, RIGHT
    }
    private Selected selected = Selected.CLEAR;

    private int clientId = 0;
    private String rootFolderClientFilesName;
    private String currentFolderClientFilesName;
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
    @FXML private ListView<String> listViewOfSizeClientFiles;
    @FXML private ListView<String> listViewOfSizeServerFiles;

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
        rootFolderClientFilesName = FOLDER_CLIENT_FILES_NAME + "user" + clientId + "/";
        currentFolderClientFilesName = rootFolderClientFilesName;

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
                String sizeFileName = in.next();
                listViewOfSizeServerFiles.getItems().add(sizeFileName);
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
            logger.info("Path folder: " + currentFolderClientFilesName);
            Path path = Paths.get(currentFolderClientFilesName);
            if (Files.exists(path)){
                Map<String, String> mapFileNameAndSize = new LinkedHashMap<>();
                List<String> listOfClientFolders = Files.list(path)
                        .filter(p -> Files.isDirectory(p))
                        .map(Path::getFileName)
                        .map(path1 -> path1.toString() + "/")
                        .collect(Collectors.toList());
                if (! rootFolderClientFilesName.equals(currentFolderClientFilesName)){
                    mapFileNameAndSize.put("./", "Level_Up");
                }
                listOfClientFolders.forEach(fileName -> mapFileNameAndSize.put(fileName, "folder"));

                List<String> listOfClientFiles = Files.list(path)
                        .filter(p -> Files.isRegularFile(p))
                        .map(Path::toFile)
                        .map(File::getName)
                        .collect(Collectors.toList());
                listOfClientFiles.forEach(fileName -> {
                    long sizeFileName = 0;
                    try {
                        sizeFileName = Files.size(Paths.get(currentFolderClientFilesName + fileName));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mapFileNameAndSize.put(fileName, String.valueOf(sizeFileName));
                });

                mapFileNameAndSize.forEach((fileName, fileSize) -> {
                    listViewOfClientFiles.getItems().add(fileName);
                    listViewOfSizeClientFiles.getItems().add(fileSize);
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
        int countSelectedItems = selectedItemsListClients.size();
        if (countSelectedItems != 0) {
            boolean incorrectChoice = false;
            for (String selectedItem : selectedItemsListClients) {
                if (selectedItem.equals("./")){
                    incorrectChoice = true;
                    break;
                }
            }
            if (incorrectChoice){
                showAlert("Error!", "You can't selected './' to operation!", "Choose again");
            } else {
                if (action.equals("Rename")) {
                    if (countSelectedItems == 1) {
                        String fileName = selectedItemsListClients.get(0);

                        Optional<String> result = setNewFileName(fileName);
                        if (result.isPresent()) {
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
                    selectedItemsListClients.forEach(fileName -> {
                        try {
                            if (action.equals("Copy") || action.equals("Move")) {
                                copyFile(currentFolderClientFilesName + fileName, fileName, action);
                            } else {
                                deleteFile(currentFolderClientFilesName + fileName);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }

                refreshAll();
            }
        }
    }

    private Optional<String> setNewFileName(String fileName) {
        TextInputDialog textInputDialog = new TextInputDialog(fileName);
        textInputDialog.setTitle("New name");
        textInputDialog.setHeaderText("Enter new name for file");
        textInputDialog.setContentText("New name:");
        return textInputDialog.showAndWait();
    }

    private void copyFile(String filePath, String fileName, String action) throws IOException {
        logger.info("File path: " + filePath);
        if (Files.isDirectory(Paths.get(filePath))) {
            logger.info("File is directory: " + fileName);
            sendCatalog(fileName);
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
            out.writeByte(Bytes.BYTE_OF_CATALOG_LEVEL_UP.toByte());
        } else {
            sendFile(filePath);
        }
        logger.info("File send: " + fileName);
        if (action.equals("Move")) {
            deleteFile(filePath);
        }
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

    private void setNewFileName(String file, String newFileName) throws IOException {
        Path path = Paths.get(currentFolderClientFilesName + file);
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

    private void sendCatalog(String fileName) throws IOException {
        out.writeByte(Bytes.BYTE_OF_SEND_CATALOG_FROM_CLIENT.toByte());
        out.writeInt(1);

        out.writeInt(fileName.length());
        logger.info("Send length: " + fileName.length());

        byte[] filenameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        out.write(filenameBytes);
        logger.info("Send fileNameBytes: " + new String(filenameBytes, StandardCharsets.UTF_8));

        waitByteOfConfirm();
    }

    private void sendListToGetOrDeleteFromServer(byte byteOfOperation){
        ObservableList<String> selectedItemsServerFiles = listViewOfServerFiles.getSelectionModel().getSelectedItems();
        int countSelectedItems = selectedItemsServerFiles.size();
        if (countSelectedItems != 0) {
            boolean incorrectChoice = false;
            for (String selectedItem : selectedItemsServerFiles) {
                if (selectedItem.equals("./")){
                    incorrectChoice = true;
                    break;
                }
            }
            if (incorrectChoice){
                showAlert("Error!", "You can't selected './' to operation!", "Choose again");
            } else {
                if (byteOfOperation == Bytes.BYTE_OF_RENAME_FILE.toByte()) {
                    if (countSelectedItems == 1) {
                        try {
                            String fileName = listViewOfServerFiles.getSelectionModel().getSelectedItem();

                            Optional<String> result = setNewFileName(fileName);
                            if (result.isPresent()) {
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
                        logger.info("Count: " + countSelectedItems);
                        out.writeInt(countSelectedItems);
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

                    if (byteOfOperation == Bytes.BYTE_OF_DELETE_FILE.toByte()) {
                        waitByteOfConfirm();
                    } else {
                        while (true) {
                            logger.info("Byte wait");
                            byte b = in.nextByte();
                            logger.info("Byte: " + b);
                            if (b == Bytes.BYTE_OF_END_OF_SEND_FROM_SERVER.toByte()) {
                                break;
                            }
                            if (b == Bytes.BYTE_OF_CATALOG_LEVEL_UP.toByte()) {
                                folderLevelUp();
                            } else if (b == Bytes.BYTE_OF_SEND_CATALOG_FROM_SERVER.toByte()) {
                                logger.info("Catalog");
                                String fileName = in.next();
                                currentFolderClientFilesName += fileName;
                                File file = new File(currentFolderClientFilesName);
                                //noinspection ResultOfMethodCallIgnored
                                file.mkdir();
                                logger.info("Byte of enter catalog");

                            } else if (b == Bytes.BYTE_OF_SEND_FILE_FROM_SERVER.toByte()) {
                                logger.info("File");
                                getFile();

                                try {
                                    in = new Scanner(socket.getInputStream());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                logger.info("Error in inner byte!!!!!!!!!!!!!!!!!!!!");
                            }
                        }
                        logger.info("Files gets from server");
                    }
                }
            }
        }
    }

    private void folderLevelUp() {
        StringBuilder stringBuilder = new StringBuilder(currentFolderClientFilesName);
        stringBuilder.delete(stringBuilder.lastIndexOf("/") - 1, stringBuilder.length());
        stringBuilder.delete(stringBuilder.lastIndexOf("/") + 1, stringBuilder.length());
        currentFolderClientFilesName = stringBuilder.toString();
    }

    private void waitByteOfConfirm() {
        logger.info("Byte wait");
        byte b = in.nextByte();
        logger.info("Byte: " + b);
        if (b != Bytes.BYTE_OF_CONFIRM.toByte()) {
            logger.info("Error in inner byte!!!!!!!!!!!!!!!!!!!!");
        }
    }

    private void getFile() {
        String fileName = in.next();
        logger.info("File name: " + fileName);
        String sizeFileName = in.next();
        long size = Long.parseLong(sizeFileName);
        logger.info("Size file: " + size);

        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(currentFolderClientFilesName + fileName));
            BufferedInputStream bufferedInputStream = new BufferedInputStream(socket.getInputStream());

            if (size != 0) {
                byte[] bytes;

                long leftBytes = 0;
                while (size > leftBytes) {
                    int available = bufferedInputStream.available();
                    if (available == 0){
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        continue;
                    }

                    if (available > size - leftBytes){
                        available = (int) (size - leftBytes);
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
    public void mouseClickedListClientFiles(MouseEvent mouseEvent) {
        if(mouseEvent.getClickCount() == 2){
            String enteredFolder = listViewOfClientFiles.getSelectionModel().getSelectedItem();
            logger.info("Entered Folder: " + enteredFolder);
            if (enteredFolder.equals("./")){
                StringBuilder stringBuilder = new StringBuilder(currentFolderClientFilesName);
                stringBuilder.delete(stringBuilder.lastIndexOf("/")-1,stringBuilder.length());
                stringBuilder.delete(stringBuilder.lastIndexOf("/")+1,stringBuilder.length());
                currentFolderClientFilesName = stringBuilder.toString();

                refreshListOfClientFiles();
            } else if (enteredFolder.endsWith("/")) {
                currentFolderClientFilesName += enteredFolder;
                refreshListOfClientFiles();
            }
        }
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
    public void mouseClickedListServerFiles(MouseEvent mouseEvent) throws IOException {
        if(mouseEvent.getClickCount() == 2){
            System.out.println("Double clicked");
            String enteredFolder = listViewOfServerFiles.getSelectionModel().getSelectedItem();
            logger.info("Entered Folder: " + enteredFolder);
            if (enteredFolder.endsWith("/")){
                out.writeByte(Bytes.BYTE_OF_ENTER_CATALOG.toByte());
                out.writeInt(1);

                out.writeInt(enteredFolder.length());
                byte[] stringBytes = enteredFolder.getBytes(StandardCharsets.UTF_8);
                out.write(stringBytes);

                logger.info("Byte wait");
                byte b = in.nextByte();
                logger.info("Byte: " + b);
                if (b == Bytes.BYTE_OF_CONFIRM.toByte()){
                    refreshListOfServerFiles();
                } else {
                    logger.info("Error in inner byte!!!!!!!!!!!!!!!!!!!!");
                }
            }
        }
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