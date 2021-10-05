package blue.lhf.jfxr.test;

import blue.lhf.jfxr.Classifier;
import blue.lhf.jfxr.JFXR;
//import org.junit.jupiter.api.Test;

import java.io.IOException;

public class JFXRTest {
    public static void main(String[] args) throws IOException {
        new JFXRTest().testJFXR();
    }

    //@Test
    public void testJFXR() throws IOException {
        JFXR.builder("18-ea+3")
                .modules("graphics", "base", "fxml", "controls")
                .classifiers(Classifier.DETECT)
                .callback(() -> JFXApplication.launch(JFXApplication.class)).build().call().join();
    }
}
