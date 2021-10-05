package blue.lhf.jfxr;

import java.util.Objects;
import java.util.function.Supplier;

public class Classifier {
    public static final Classifier MAC = new Classifier("mac"),
            WINDOWS = new Classifier("win"),
            LINUX = new Classifier("linux"),
            DETECT = new Classifier(() -> Platform.getPlatform().name().toLowerCase());

    public static final Classifier[] ALL = new Classifier[]{
            MAC, WINDOWS, LINUX
    };

    private final Supplier<String> supplier;

    private Classifier(String text) {
        this(() -> text);
    }

    private Classifier(Supplier<String> supplier) {
        this.supplier = supplier;
    }

    public String get() {
        return supplier.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Classifier that = (Classifier) o;
        return get().equals(that.get());
    }

    @Override
    public int hashCode() {
        return Objects.hash(supplier);
    }
}
