package net.mysterria.stuff.features.dungeons;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reads no-argument public getters by name without a compile-time dependency on the owning API.
 * Each (class, getter) lookup is resolved once and cached, including misses, in a bounded map.
 */
final class ReflectiveReader {

    private static final int MAX_CACHED_LOOKUPS = 512;

    private final BoundedMap<Lookup, Optional<Method>> methods = new BoundedMap<>(MAX_CACHED_LOOKUPS);

    /**
     * @throws NoSuchMethodException when the target's class has no such public getter
     * @throws ReflectiveOperationException when the getter cannot be invoked or itself throws
     */
    Object read(Object target, String getter) throws ReflectiveOperationException {
        if (target == null) return null;
        Optional<Method> method = resolve(target.getClass(), getter);
        if (method.isEmpty()) {
            throw new NoSuchMethodException(target.getClass().getName() + "." + getter + "()");
        }
        return invoke(method.get(), target);
    }

    /** Like {@link #read} but returns null when the getter does not exist on this API version. */
    Object readOptional(Object target, String getter) throws ReflectiveOperationException {
        if (target == null) return null;
        Optional<Method> method = resolve(target.getClass(), getter);
        return method.isEmpty() ? null : invoke(method.get(), target);
    }

    /** Typed {@link #read}: null when the value is absent or not an instance of {@code type}. */
    <T> T read(Object target, String getter, Class<T> type) throws ReflectiveOperationException {
        Object value = read(target, getter);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    <T> T readOptional(Object target, String getter, Class<T> type) throws ReflectiveOperationException {
        Object value = readOptional(target, getter);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    private Optional<Method> resolve(Class<?> owner, String getter) {
        return methods.computeIfAbsent(new Lookup(owner, getter), ReflectiveReader::find);
    }

    private static Optional<Method> find(Lookup lookup) {
        try {
            Method method = lookup.owner().getMethod(lookup.getter());
            method.trySetAccessible();
            return Optional.of(method);
        } catch (NoSuchMethodException | SecurityException missing) {
            return Optional.empty();
        }
    }

    private static Object invoke(Method method, Object target) throws ReflectiveOperationException {
        try {
            return method.invoke(target);
        } catch (IllegalArgumentException mismatch) {
            throw new InvocationTargetException(mismatch);
        }
    }

    private record Lookup(Class<?> owner, String getter) {
    }
}
