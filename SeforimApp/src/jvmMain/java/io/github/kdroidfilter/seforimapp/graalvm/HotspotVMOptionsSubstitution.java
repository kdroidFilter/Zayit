package io.github.kdroidfilter.seforimapp.graalvm;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

import java.util.Optional;
import java.util.function.Function;

/**
 * GraalVM native-image substitution for {@code HotspotVMOptions}.
 * <p>
 * In Lucene 10.3.2, {@code HotspotVMOptions}' static initializer uses
 * {@code ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)} to query
 * JVM flags like {@code UseCompressedOops}. This creates a {@code HotSpotDiagnostic}
 * MXBean instance that {@code ManagementFactory} caches internally. When the class
 * initializes at build time, the cached MXBean ends up in the native image heap,
 * causing {@code UnsupportedFeatureException: Detected a PlatformManagedObject}.
 * <p>
 * This substitution replaces the {@code ACCESSOR} function (which captures the MXBean)
 * and {@code IS_HOTSPOT_VM} flag with safe defaults. Combined with
 * {@code --initialize-at-run-time=org.apache.lucene.util.HotspotVMOptions}, this
 * ensures the MXBean is never captured in the image heap.
 */
@TargetClass(className = "org.apache.lucene.util.HotspotVMOptions")
final class Target_HotspotVMOptions {

    /**
     * Replace the ACCESSOR function that captures the HotSpotDiagnostic MXBean.
     * In native image, HotSpot VM options are not available, so return empty.
     */
    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)
    private static Function<String, Optional<String>> ACCESSOR = name -> Optional.empty();

    /**
     * Native image does not run on HotSpot, so this is always false.
     */
    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)
    public static boolean IS_HOTSPOT_VM = false;
}
