<!--suppress HtmlDeprecatedAttribute -->
<img align=right alt="FXR logo" src="fxr.png"/><br clear="right">
## JFXR

---
JFXR is a JavaFX downloader/bundler library for Java 17. It gets rid of all the hassle of bundling JavaFX natives with your jarfiles -- instead, you add JavaFX as a compile-time only dependency and let JFXR deal with downloading everything on the first run.

### Usage 
Using JFXR is as simple as adding it into your JAR in whatever way best works for your JAR -- whether you're Gradle, Maven, or just IDEA taking care of your artifacts, JFXR will work for you.

With JFXR installed, you simply build a new instance with `JFXR.Builder` and invoke its `call` method:
```java
JFXR.builder("18-ea+3") // JavaFX version
        .modules("graphics", "base", "fxml", "controls")
        .classifiers(Classifier.DETECT) // valid values are DETECT, WIN, LINUX, MAX, ALL
        .callback(() -> // the callback to run when JFXR is done, should launch your JavaFX app
            JFXApplication.launch(JFXApplication.class
        ).build().call(); // build and call the JFXR
```

JFXR is a sub-class of `Callable<CompletableFuture<Void>>`, so the call() method returns a CompletableFuture that'll complete when the callback has been invoked.