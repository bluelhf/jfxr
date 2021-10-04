package blue.lhf.jfxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class JFXR {
    public static JFXR.Builder builder() {

    }

    public static class Builder {
        List<String> modules = new ArrayList<>();

        { modules.add(""); }

        boolean autoClassify = true;
        List<String> classifiers = new ArrayList<>();

        public Builder module(String name) {
            modules.add(name); return this;
        }

        public Builder modules(String... names) {
            modules.addAll(Arrays.asList(names));
            return this;
        }

        public Builder modules(Collection<String> names) {
            modules.addAll(names); return this;
        }

        public Builder excludeOS() {
            autoClassify = false;
            return this;
        }

        public Builder classifier(String name) {
            classifiers.add(name); return this;
        }

        public Builder classifiers(String... names) {
            classifiers.addAll(Arrays.asList(names));
            return this;
        }

        public Builder classifiers(Collection<String> names) {
            classifiers.addAll(names); return this;
        }
    }
}
