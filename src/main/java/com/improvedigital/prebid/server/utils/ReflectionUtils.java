package com.improvedigital.prebid.server.utils;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.lang.reflect.Field;

public class ReflectionUtils {

    private ReflectionUtils() {
    }

    private static final Logger logger = LoggerFactory.getLogger(ReflectionUtils.class);

    public static <T, R> R getPrivateProperty(
            String propertyName, T instance, Class<T> clazz
    ) throws NoSuchFieldException, IllegalAccessException {
        return getPrivateProperty(propertyName, instance, clazz, false);
    }

    public static <T, R> R getPrivateProperty(
            String propertyName, T instance, Class<T> clazz, boolean resolveBeanInstance
    ) throws NoSuchFieldException, IllegalAccessException {
        if (resolveBeanInstance) {
            instance = resolveActualInstanceWrappedInBean(instance, clazz);
        }

        if (instance == null) {
            return null;
        }
        return castValue(getDeclaredFieldValue(instance, propertyName));
    }

    public static <T> T resolveActualInstanceWrappedInBean(T instance, Class<T> clazz) {
        if (instance == null) {
            return null;
        }

        final String className = clazz.getName();
        final String instanceClassName = instance.getClass().getName();

        // If the instance is CgLib proxy object created by spring, we'll
        // try to find the actual instance in a different way.
        if (isEnhancedBySpringCGLIB(className, instanceClassName)) {
            T wrappedInstance = findWrappedInstanceInSpringBean(instance, clazz);
            if (wrappedInstance != null) {
                instance = wrappedInstance;
            }
        }

        return instance;
    }

    private static <R> R castValue(Object value) {
        @SuppressWarnings("unchecked")
        R castedValue = (R) value;
        return castedValue;
    }

    private static boolean isEnhancedBySpringCGLIB(String className, String instanceClassName) {
        return instanceClassName.startsWith(className + "$$EnhancerBySpringCGLIB");
    }

    private static <T> T findWrappedInstanceInSpringBean(Object bean, Class<T> beanClass) {
        // These are the classes of CglibAopProxy callback properties in the wrapper instance
        // that contains actual bean object as "target" attribute. We'll try
        // to find callback object using each class sequentially until one is found.
        String[] aopProxyClasses = new String[] {
                "StaticDispatcher", "StaticUnadvisedInterceptor", "StaticUnadvisedExposedInterceptor"
        };

        for (String className: aopProxyClasses) {
            Object proxyObject = findCglibAopProxyCallBackObject(bean, className);
            if (proxyObject != null) {
                // This target object should be the actual bean object wrapped in Spring Enhanced object.
                // Still we'll match the type and cast it to beanClass when matched.
                try {
                    Object target = getDeclaredFieldValue(proxyObject, "target");
                    if (beanClass.isInstance(target)) {
                        return beanClass.cast(target);
                    }
                    logger.debug("Skipping target as it is not an instance of " + beanClass.getName());
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    logger.debug("Unable to extract target property from wrapped proxy object: " + e.getMessage());
                }
            }
        }
        return null;
    }

    private static Object findCglibAopProxyCallBackObject(Object bean, String className) {
        final String aopClassName = "CglibAopProxy$" + className;
        // there are 7 fields with name pattern "CGLIB$CALLBACK_" + index
        // in the proxy object that we're interested in, where index = 0 to 6
        for (int index = 0; index <= 6; index++) {
            String fieldName = "CGLIB$CALLBACK_" + index;
            try {
                Object cgLibCallback = getDeclaredFieldValue(bean, fieldName);
                if (cgLibCallback != null && cgLibCallback.getClass().getName().endsWith(aopClassName)) {
                    logger.debug(fieldName + " property found with class: " + aopClassName);
                    return cgLibCallback;
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // we'll ignore both exceptions so that we can look into
                // fields with next indices
            }
        }
        logger.debug("No CGLIB$CALLBACK property found with class: " + aopClassName);
        return null;
    }

    public static Object getDeclaredFieldValue(Object target, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        if (target == null) {
            return null;
        }
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
