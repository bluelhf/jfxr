package blue.lhf.jfxr;

import blue.lhf.jfxr.util.Downloader;
import io.github.bluelhf.tasks.Task;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class JFXR implements Callable<CompletableFuture<Void>> {
    protected final URI repository;
    protected final String group;
    protected final String project;
    protected final List<String> modules;
    protected final List<String> classifiers;
    protected final Runnable runnable;
    protected final Path outDir;
    protected final String version;
    protected static Instrumentation instrumentation = ByteBuddyAgent.install();

    protected static final Logger log = Logger.getLogger("JFXR");

    static {
        try {
            LogManager.getLogManager().readConfiguration(JFXR.class.getResourceAsStream("/logging.properties"));
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) {
        JFXR.builder()
                .modules("base", "fxml", "controls")
                .build().call().join();
    }

    protected JFXR(URI repository, String group, String project, String version, List<String> modules, List<String> classifiers, Runnable runnable, Path outDir) {
        log.setLevel(Level.ALL);
        this.repository = repository;
        this.group = group;
        this.project = project;
        this.version = version;
        this.modules = modules;
        this.classifiers = classifiers;
        this.runnable = runnable;
        this.outDir = outDir;
        Arrays.stream(String.format("""
                        Initialised JFXR!
                        ========================
                          %s:%s:%s @ %s
                          Modules: %s
                          Classifiers: %s
                          Output: %s
                        ========================
                        """, group, project, version, repository, modules, classifiers, outDir.toAbsolutePath())
                .split("\n")).forEachOrdered(log::info);
    }

    public static JFXR.Builder builder() {
        return new JFXR.Builder();
    }

    @Override
    public CompletableFuture<Void> call() {
        log.fine("Running JFXR...");
        URI groupURI = repository.resolve(group.replace(".", "/") + "/");
        List<Task<?, ?>> tasks = new ArrayList<>();

        for (String module : modules) {
            log.finest("  Considering module javafx-" + module);
            for (String classifier : classifiers) {
                log.finest("    Considering classifier " + classifier);
                String subproject = project + (module.isBlank() ? "" : "-" + module);
                URI projectURI = groupURI.resolve(subproject + "/");
                URI versionURI = projectURI.resolve(version + "/");
                URI jarURI = versionURI.resolve(subproject + "-" + version + "-" + classifier + ".jar");
                log.finest("    Got JAR URL " + jarURI);
                try {
                    Path file = outDir.resolve(versionURI.relativize(jarURI).getPath());
                    if (Files.notExists(outDir)) {
                        log.finest("    Creating missing directory " + outDir.toAbsolutePath());
                        Files.createDirectories(outDir);
                    }

                    String target = subproject + ":" + version + "-" + classifier;
                    log.fine("  Download of " + target + " started");
                    tasks.add(Downloader.download(
                            jarURI.toURL(),
                            Files.newOutputStream(file)
                    ).onResult((unused) -> {
                        try {
                            instrumentation.appendToSystemClassLoaderSearch(new JarFile(file.toFile()));
                            System.setProperty("java.class.path", System.getProperty("java.class.path", "")
                                            + File.pathSeparator
                                            + file.toAbsolutePath());
                            log.fine("  Added " + target + " to the classpath");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return CompletableFuture.allOf(
                tasks.stream().map(Task::getBackingFuture).toArray(CompletableFuture[]::new)
        ).thenRun(() -> {
            log.fine("JFXR complete!");
            if (runnable != null) runnable.run();
        });
    }

    public static class Builder {
        protected boolean done = false;
        protected List<String> modules = new ArrayList<>();

        protected String repository = "https://repo.maven.apache.org/maven2/";
        protected String version = "18-ea+3";
        protected String baseProject = "javafx";
        protected String group = "org.openjfx";

        protected Path outDir = Path.of(System.getProperty("java.io.tmpdir"), "jfxr");

        protected boolean autoClassify = true;
        protected List<String> classifiers = new ArrayList<>();

        protected Runnable runnable;

        protected void checkDone() {
            if (done) throw new IllegalStateException("Cannot use builder after it has been built");
        }

        public Builder callback(Runnable runnable) {
            checkDone();
            this.runnable = runnable;
            return this;
        }

        public Builder output(Path outDir) {
            checkDone();
            this.outDir = outDir;
            return this;
        }

        public Builder project(String project) {
            checkDone();
            this.baseProject = project; return this;
        }

        public Builder group(String group) {
            checkDone();
            this.group = group; return this;
        }

        public Builder version(String version) {
            checkDone();
            this.version = version; return this;
        }

        public Builder repository(String uri) {
            checkDone();
            repository = uri; return this;
        }

        public Builder module(String name) {
            checkDone();
            modules.add(name); return this;
        }

        public Builder modules(String... names) {
            checkDone();
            modules.addAll(Arrays.asList(names));
            return this;
        }

        public Builder modules(Collection<String> names) {
            checkDone();
            modules.addAll(names); return this;
        }

        public Builder excludeOS() {
            checkDone();
            autoClassify = false;
            return this;
        }


        public Builder classifier(String name) {
            checkDone();
            classifiers.add(name); return this;
        }

        public Builder classifiers(String... names) {
            checkDone();
            classifiers.addAll(Arrays.asList(names));
            return this;
        }

        public Builder classifiers(Collection<String> names) {
            checkDone();
            classifiers.addAll(names); return this;
        }

        public JFXR build() {
            List<String> actualClassifiers = new ArrayList<>(classifiers);
            List<String> actualModules = new ArrayList<>(modules);
            if (autoClassify) {
                String os = Platform.getPlatform().name().toLowerCase(Locale.ROOT);
                if (os.equalsIgnoreCase("windows")) os = "win";
                actualClassifiers.add(os);
            }
            JFXR jfxr = new JFXR(URI.create(repository), group, baseProject, version, actualModules, actualClassifiers, runnable, outDir);
            done = true;
            return jfxr;
        }
    }
}
