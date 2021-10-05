package blue.lhf.jfxr.util;

import java.util.Objects;
import java.util.function.Consumer;


@FunctionalInterface
public interface ThrowingConsumer<T> extends Consumer<T> {

    /**
     * Performs this operation on the given argument.
     *
     * @param t the input argument
     */
    default void accept(T t) {
        try {
            accept0(t);
        } catch (Throwable e) {
            if (e instanceof RuntimeException re) throw re;
            if (e instanceof Error err) throw err;
            throw new RuntimeException(e);
        }
    }

    void accept0(T t) throws Throwable;

    /**
     * Returns a composed {@code Consumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation.  If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code Consumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default ThrowingConsumer<T> andThen(ThrowingConsumer<? super T> after) {
        Objects.requireNonNull(after);
        return (T t) -> { accept(t); after.accept(t); };
    }
}
