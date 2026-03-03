package io.github.kdroidfilter.seforimapp.graalvm;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import org.apache.lucene.util.Attribute;
import org.apache.lucene.util.AttributeFactory;
import org.apache.lucene.util.AttributeImpl;

import java.lang.reflect.Constructor;

/**
 * GraalVM native-image substitution for Lucene's {@code DefaultAttributeFactory}.
 * <p>
 * The original uses {@code ClassValue<MethodHandle>} and {@code invokeExact()} to instantiate
 * {@link AttributeImpl} subclasses. {@code MethodHandle.invokeExact()} is not supported by
 * GraalVM native image. This substitution replaces it with plain reflection
 * ({@code Constructor.newInstance()}).
 * <p>
 * All concrete {@code AttributeImpl} classes used at runtime must be registered in
 * {@code reachability-metadata.json} with a no-arg constructor entry.
 */
@TargetClass(className = "org.apache.lucene.util.AttributeFactory$DefaultAttributeFactory")
final class Target_DefaultAttributeFactory {

    @Substitute
    public AttributeImpl createAttributeInstance(Class<? extends Attribute> attClass) {
        try {
            // Lucene convention: implementation class name = interface name + "Impl"
            String implClassName = attClass.getName() + "Impl";
            Class<? extends AttributeImpl> implClass =
                    Class.forName(implClassName, true, attClass.getClassLoader())
                            .asSubclass(AttributeImpl.class);
            Constructor<? extends AttributeImpl> ctor = implClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException(
                    "Cannot find implementing class for: " + attClass.getName(), cnfe);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Cannot create attribute instance for: " + attClass.getName(), e);
        }
    }
}

/**
 * GraalVM native-image substitution for Lucene's {@code StaticImplementationAttributeFactory}.
 * <p>
 * This is the factory used by ALL standard tokenizers via
 * {@code TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY}. The factory is created by
 * {@code AttributeFactory.getStaticImplementation()}, which generates an anonymous subclass
 * whose {@code createInstance()} method calls {@code MethodHandle.invokeExact()}.
 * <p>
 * This substitution replaces {@code createAttributeInstance()} to use plain reflection,
 * bypassing the {@code createInstance()} → {@code invokeExact()} code path entirely.
 */
@TargetClass(className = "org.apache.lucene.util.AttributeFactory$StaticImplementationAttributeFactory")
final class Target_StaticImplementationAttributeFactory {

    @Alias private AttributeFactory delegate;
    @Alias private Class<?> clazz;

    @Substitute
    public final AttributeImpl createAttributeInstance(Class<? extends Attribute> attClass) {
        if (attClass.isAssignableFrom(clazz)) {
            try {
                Constructor<?> ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                return (AttributeImpl) ctor.newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Cannot create attribute instance of type " + clazz.getName(), e);
            }
        }
        return delegate.createAttributeInstance(attClass);
    }
}
