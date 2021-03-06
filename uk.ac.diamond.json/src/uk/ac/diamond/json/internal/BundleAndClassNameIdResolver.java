package uk.ac.diamond.json.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.ClassUtil;

/**
 * {@link TypeIdResolver} implementation which converts between JSON strings and the information necessary to load a class in OSGi, that is,
 * the fully-qualified class name, bundle symbolic name and bundle version.
 * <p>
 * Generic types are currently not handled. It might be possible to do this (perhaps by delegating to ClassNameIdResolver) but all calls made
 * to ClassUtils would need overriding to use correct bundle classloaders.
 * <p>
 * Also, non-static inner types will probably fail with the current implementation, but this has not been tested.
 *
 * @author Colin Palmer
 *
 */
public class BundleAndClassNameIdResolver extends TypeIdResolverBase {

	private static final Logger logger = LoggerFactory.getLogger(BundleAndClassNameIdResolver.class);
	private static final Map<BundleAndClassInfo, Bundle> cachedBundles = new ConcurrentHashMap<BundleAndClassInfo, Bundle>();

	/**
	 * Clear the bundle cache. Intended only for use in testing.
	 */
	public static void clearCache() {
		cachedBundles.clear();
	}

	private final BundleProvider bundleProvider;
	private final ClassNameIdResolver classNameIdResolver;

	public BundleAndClassNameIdResolver(JavaType baseType, TypeFactory typeFactory, BundleProvider bundleProvider) {
		super(baseType, typeFactory);
		this.bundleProvider = bundleProvider;

		// Create a ClassNameIdResolver to delegate to when handling class names (i.e. after we have handled the bundle information)
		this.classNameIdResolver = new ClassNameIdResolver(baseType, typeFactory);
	}

	@Override
	public Id getMechanism() { return Id.CUSTOM; }

	@Override
	public String idFromValue(Object value) {
		return idFromValueAndType(value, value.getClass());
	}

	@Override
	public String idFromValueAndType(Object value, Class<?> clazz) {
		Bundle bundle = bundleProvider.getBundle(clazz);
		String className = classNameIdResolver.idFromValueAndType(value, clazz);
		BundleAndClassInfo info = BundleAndClassInfo.from(bundle, className);
		return info.toString();
	}

	@Override
	public JavaType typeFromId(String id) {
		try {
			Class<?> clazz = getClass(id);
			// This probably doesn't handle generics, except for arrays and collections
			// see ClassNameIdResolver#typeFromId() for more on this
			JavaType type = _typeFactory.constructSpecializedType(_baseType, clazz);
			return type;
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("Class " + id + " not found", e);
		}
	}

	private Class<?> getClass(String id) throws ClassNotFoundException {
		BundleAndClassInfo info = BundleAndClassInfo.from(id);

		// If there is no bundle name, try loading the class using the standard Jackson utility method
		// See ClassNameIdResolver for complexity regarding generics here - not supported for now
		if (info.getBundleSymbolicName().length() == 0) {
			return ClassUtil.findClass(info.getClassName());
		}

		Bundle bundleToUse = getBundle(info);
		if (bundleToUse != null) {
			try {
				return bundleToUse.loadClass(info.getClassName());
			} catch (ClassNotFoundException | IllegalStateException ex) {
				// the bundle cannot find the required class, so we log the exception and fall back to just finding the class by name
				logger.warn("Class {} could not be loaded by bundle {}", info.getClassName(), info.getBundleSymbolicName(), ex);
			}
		} else {
			logger.warn("Bundle {} (version {}) not found when trying to load class {}", info.getBundleSymbolicName(), info.getBundleVersion(), info.getClassName());
		}
		// If the bundle is not found, or cannot load the required class, fall back and try to load the class with ClassUtil
		return ClassUtil.findClass(info.getClassName());
	}

	private Bundle getBundle(BundleAndClassInfo info) {
		Bundle cachedBundle = cachedBundles.get(info);
		if (cachedBundle != null) {
			if (cachedBundle.getState() != Bundle.UNINSTALLED) {
				return cachedBundle;
			} else {
				cachedBundles.remove(info);
			}
		}
		Bundle[] bundles = bundleProvider.getBundles();
		Bundle bundleToUse = null;
		for (Bundle bundle : bundles) {
			if (info.getBundleSymbolicName().equals(bundle.getSymbolicName())
					&& bundle.getVersion().toString().equals(info.getBundleVersion())) {
				bundleToUse = bundle;
				break;
				// TODO cache bundles with incorrect version and try them if correct version is not found?
			}
		}
		if (bundleToUse != null) {
			cachedBundles.put(info, bundleToUse);
		}
		return bundleToUse;
	}
}
