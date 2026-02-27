package io.github.kdroidfilter.seforimapp.graalvm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import java.lang.reflect.Constructor;

/**
 * GraalVM native-image substitution for {@code MMapDirectory.lookupProvider()}.
 * <p>
 * In Lucene 10.3.2, the original uses {@code MethodHandles.lookup().findClass()} and
 * {@code findConstructor()} to instantiate the package-private
 * {@code MemorySegmentIndexInputProvider}. These {@code MethodHandle} operations are not
 * supported by GraalVM native image. This substitution replaces them with plain reflection
 * ({@code Class.forName()} + {@code Constructor.newInstance()}).
 * <p>
 * The {@code MemorySegmentIndexInputProvider} class and its {@code (int)} constructor must
 * be registered in {@code reachability-metadata.json}.
 */
@TargetClass(className = "org.apache.lucene.store.MMapDirectory")
final class Target_MMapDirectory {

    @Substitute
    private static Target_MMapIndexInputProvider lookupProvider() {
        // Inline getSharedArenaMaxPermitsSysprop() to avoid @Alias complexity
        int maxPermits = 1024;
        try {
            String str = System.getProperty(
                    "org.apache.lucene.store.MMapDirectory.sharedArenaMaxPermits");
            if (str != null) {
                maxPermits = Integer.parseInt(str);
            }
        } catch (NumberFormatException | SecurityException ignored) {
        }

        try {
            Class<?> cls = Class.forName(
                    "org.apache.lucene.store.MemorySegmentIndexInputProvider");
            Constructor<?> ctor = cls.getDeclaredConstructor(int.class);
            ctor.setAccessible(true);
            return (Target_MMapIndexInputProvider) ctor.newInstance(maxPermits);
        } catch (ClassNotFoundException cnfe) {
            throw new LinkageError(
                    "MemorySegmentIndexInputProvider is missing in Lucene JAR file", cnfe);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new LinkageError(
                    "MemorySegmentIndexInputProvider is missing correctly typed constructor", e);
        } catch (Exception e) {
            throw new LinkageError(
                    "Failed to instantiate MemorySegmentIndexInputProvider", e);
        }
    }
}

/**
 * Alias for the package-private {@code MMapDirectory.MMapIndexInputProvider} interface.
 * Required so the substituted {@code lookupProvider()} return type matches the original
 * bytecode signature after type erasure.
 */
@TargetClass(className = "org.apache.lucene.store.MMapDirectory$MMapIndexInputProvider")
interface Target_MMapIndexInputProvider {
}
