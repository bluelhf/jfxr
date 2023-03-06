package blue.lhf.jfxr;

import blue.lhf.jfxr.util.*;
import io.github.bluelhf.tasks.Task;
import org.apache.openjpa.enhance.InstrumentationFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarFile;

@SuppressWarnings({"unused"})
public class JFXR implements Callable<CompletableFuture<Void>> {
    // Instrumentation is used to add JARs to the System Class Loader at runtime
    protected static Instrumentation instrumentation = InstrumentationFactory.getInstrumentation();
    protected final URI repository;
    protected final String group;
    protected final String project;
    protected final String version;
    protected final Set<String> modules;
    protected final Set<Classifier> classifiers;
    protected final ThrowingRunnable runnable;
    protected final Path outDir;

    protected JFXR(URI repository, String group, String project, String version,
                   Set<String> modules, Set<Classifier> classifiers,
                   ThrowingRunnable runnable, Path outDir) {
        this.repository = repository;
        this.group = group;
        this.project = project;
        this.version = version;
        this.modules = modules;
        this.classifiers = classifiers;
        this.runnable = runnable;
        this.outDir = outDir;
    }

    public static JFXR.Builder builder(String javaFxVersion) {
        return new JFXR.Builder(javaFxVersion);
    }

    protected TargetData getData(String module, Classifier classifier) {
        URI groupURI = repository.resolve(group.replace(".", "/") + "/");
        String subproject = project + (module.isBlank() ? "" : "-" + module);
        URI projectURI = groupURI.resolve(subproject + "/");
        URI versionURI = projectURI.resolve(version + "/");
        return new TargetData(
                groupURI,
                projectURI,
                versionURI,
                versionURI.resolve(subproject + "-" + version + "-" + classifier.get() + ".jar")
        );
    }

    @Override
    public CompletableFuture<Void> call() throws IOException {
        URI groupURI = repository.resolve(group.replace(".", "/") + "/");
        List<Task<?, ?>> tasks = new ArrayList<>();

        for (String module : modules) {
            for (Classifier classifier : classifiers) {
                TargetData data = getData(module, classifier);
                Path file = outDir.resolve(data.version()
                        .relativize(data.jar())
                        .getPath()
                );

                if (Files.notExists(outDir))
                    Files.createDirectories(outDir);

                if (Files.isReadable(file)) {
                    try {
                        add(file);
                        continue;
                    } catch (Exception ignored) {
                        // jar may be corrupt, try re-downloading it
                    }
                }
                OutputStream stream = Files.newOutputStream(file);
                try {
                    tasks.add(Downloader.download(
                        data.jar().toURL(),
                        stream
                    ).onResult((ThrowingConsumer<Void>) (unused) -> {
                        stream.close();
                        add(file);
                    }));
                } catch (IOException e) {
                    stream.close();
                    throw e;
                }
            }
        }

        return CompletableFuture.allOf(
                tasks.stream().map(Task::getBackingFuture).toArray(CompletableFuture[]::new)
        ).thenRun(() -> {
            if (runnable != null) runnable.run();
        });
    }

    private void add(Path file) throws IOException {
        JarFile jarFile = new JarFile(file.toFile());
        instrumentation.appendToSystemClassLoaderSearch(jarFile);
        jarFile.close();

        System.setProperty("java.class.path", System.getProperty("java.class.path", "")
                + File.pathSeparator
                + file.toAbsolutePath());
    }

    record TargetData(URI group, URI project, URI version, URI jar) {
    }

    public static class Builder {
        protected boolean done = false;
        protected Set<String> modules = new HashSet<>();
        protected String repository = "https://repo.maven.apache.org/maven2/";
        protected String baseProject = "javafx";
        protected String group = "org.openjfx";
        protected String version;
        protected Path outDir = Path.of(System.getProperty("java.io.tmpdir"), "jfxr");
        protected Set<Classifier> classifiers = new HashSet<>();
        protected ThrowingRunnable runnable;

        {
            classifiers.add(Classifier.DETECT);
        }

        private Builder() {
        }

        public Builder(String version) {
            this.version = version;
        }

        protected void checkDone() {
            if (done) throw new IllegalStateException("Cannot use builder after it has been built");
        }

        public Builder callback(ThrowingRunnable runnable) {
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
            this.baseProject = project;
            return this;
        }

        public Builder group(String group) {
            checkDone();
            this.group = group;
            return this;
        }

        public Builder repository(String uri) {
            checkDone();
            repository = uri;
            return this;
        }

        public Builder module(String name) {
            checkDone();
            modules.add(name);
            return this;
        }

        public Builder modules(String... names) {
            checkDone();
            modules.addAll(Arrays.asList(names));
            return this;
        }

        public Builder modules(Collection<String> names) {
            checkDone();
            modules.addAll(names);
            return this;
        }

        public Builder dontDetect() {
            checkDone();
            classifiers.remove(Classifier.DETECT);
            return this;
        }

        public Builder classifier(Classifier name) {
            checkDone();
            classifiers.add(name);
            return this;
        }

        public Builder classifiers(Classifier... names) {
            checkDone();
            classifiers.addAll(Arrays.asList(names));
            return this;
        }

        public Builder classifiers(Collection<Classifier> names) {
            checkDone();
            classifiers.addAll(names);
            return this;
        }

        public JFXR build() {
            Set<Classifier> actualClassifiers = new HashSet<>(classifiers);
            Set<String> actualModules = new HashSet<>(modules);

            JFXR jfxr = new JFXR(
                    URI.create(repository), group,
                    baseProject, version, actualModules,
                    actualClassifiers, runnable, outDir
            );

            done = true;
            return jfxr;
        }
    }
}
