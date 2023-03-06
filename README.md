<!--suppress HtmlDeprecatedAttribute -->
<img align=right alt="FXR logo" src="fxr.png"/>

# JFXR

<br clear="right">
JFXR is a JavaFX downloader/bundler library for Java 17. It gets rid of all the hassle of bundling JavaFX natives with your jarfiles -- instead, you add JavaFX as a compile-time only dependency and let JFXR deal with downloading everything on the first run.

### Usage 
Using JFXR is as simple as adding it into your JAR in whatever way best works for your JAR -- whether you're Gradle, Maven, or just IDEA taking care of your artifacts, JFXR will work for you.

With JFXR installed, you simply build a new instance with `JFXR.Builder` and invoke its `call` method:
```java
JFXR.builder("18-ea+3") // JavaFX version
        .modules("graphics", "base", "fxml", "controls")
        .classifiers(Classifier.DETECT) // valid values are DETECT, WIN, LINUX, MAC, ALL
        .callback(() -> // the callback to run when JFXR is done, should launch your JavaFX app
            JFXApplication.launch(JFXApplication.class)
        ).build().call(); // build and call the JFXR
```

JFXR is a sub-class of `Callable<CompletableFuture<Void>>`, so the call() method returns a CompletableFuture that'll complete when the callback has been invoked.

### Limitations

- Only works on VMs that support Java Instrumentation (All JDKs since 1.5, some JREs)
- Requires stable internet connection on the first run

### Setup for Gradle

Setting up JFXR in your project using Gradle is a simple task. All you need to do is merge this into your build.gradle:
```kotlin
/** 
 * You might already have this, but it defines
 * JavaFX as a dependency as well using https://github.com/openjfx/javafx-gradle-plugin
 */
plugins {
    id 'org.openjfx.javafxplugin' version '0.0.13'
}

javafx {
    // Should match the options used to build your JFXR instance
    version = "18-ea+3"
    modules("javafx.graphics", "javafx.controls", "javafx.fxml")
    configuration = "compileOnly"
}

/**
 * This part actually adds JFXR as a dependency.
 * If it doesn't work, check whether the artifact is accessible
 * in the repository.
 * 
 * If not, you can also build JFXR yourself and include it as
 * a local dependency using the files() method
 */
repositories {
    maven {
        url "https://maven.lhf.blue"
    }
}

dependencies {
    implementation "blue.lhf:jfxr:1.0"
}
```
