package blue.lhf.jfxr.test;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class JFXApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        URL fxml = JFXApplication.class.getResource("HelloWorld.fxml");
        if (fxml == null) throw new AssertionError("Corrupt jarfile, fxml not found");
        Parent root = FXMLLoader.load(fxml);
        Scene scene = new Scene(root);
        stage.setTitle("Hello, world!");
        stage.setScene(scene);
        stage.show();
    }
}
