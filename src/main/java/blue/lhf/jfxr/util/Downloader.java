package blue.lhf.jfxr.util;

import io.github.bluelhf.tasks.Task;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Downloader {
    private static final int DEFAULT_BUFFER_SIZE = 65535;

    public static record Progress(long read, long total) {
    }
    public static Executor DOWNLOAD_POOL = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static Task<Progress, Void> download(URL url, OutputStream output) throws IOException {
        URLConnection connection = url.openConnection();
        Task<Progress, Void> task = Task.of((Task<Progress, Void>.Delegate delegate) -> {
            try (InputStream stream = connection.getInputStream()) {
                long length = connection.getContentLengthLong();

                long transferred = 0;
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int read;
                while ((read = stream.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
                    output.write(buffer, 0, read);
                    transferred += read;

                    delegate.setProgress(new Progress(transferred, length));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        task.runAsync(DOWNLOAD_POOL);
        return task;
    }
}
