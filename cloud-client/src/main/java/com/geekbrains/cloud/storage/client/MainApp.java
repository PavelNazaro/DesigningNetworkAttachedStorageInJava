package com.geekbrains.cloud.storage.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception{
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/main.fxml"));
        Parent root = fxmlLoader.load();
        Controller controller = fxmlLoader.getController();
        primaryStage.setTitle("Cloud storage");
        primaryStage.setScene(new Scene(root, 800, 400));
        primaryStage.setMinWidth(480);
        primaryStage.setMinHeight(370);
        primaryStage.setOnCloseRequest(windowEvent -> controller.close());
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
