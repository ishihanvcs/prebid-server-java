package com.improvedigital.prebid.server.utils;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class ReflectionUtilsTest {

    private static final String PROPERTY_NAME = "testMap";

    Target instance;
    Target.$EnhancerBySpringCGLIB enhancedInstance;

    @Before
    public void setUp() {
        instance = new Target();
        enhancedInstance = new Target.$EnhancerBySpringCGLIB(instance);
    }

    @Test
    public void testGetPrivateProperty() throws Exception {
        Map<String, String> property = ReflectionUtils.getPrivateProperty(PROPERTY_NAME, instance, Target.class);
        assertThat(property).isNotNull();
        property = ReflectionUtils.getPrivateProperty(PROPERTY_NAME, instance, Target.class, false);
        assertThat(property).isNotNull();
        property = ReflectionUtils.getPrivateProperty(PROPERTY_NAME, enhancedInstance, Target.class, false);
        assertThat(property).isNull();
        property = ReflectionUtils.getPrivateProperty(PROPERTY_NAME, enhancedInstance, Target.class, true);
        assertThat(property).isNotNull();
    }

    @Test
    public void testResolveActualInstanceWrappedInBean() {
        Target result = ReflectionUtils.resolveActualInstanceWrappedInBean(instance, Target.class);
        assertThat(result).isEqualTo(instance);
        result = ReflectionUtils.resolveActualInstanceWrappedInBean(enhancedInstance, Target.class);
        assertThat(result).isEqualTo(instance);
    }

    @Test
    public void testGetDeclaredFieldValue() throws Exception {
        Object property = ReflectionUtils.getDeclaredFieldValue(instance, PROPERTY_NAME);
        assertThat(property).isNotNull();
        assertThatExceptionOfType(NoSuchFieldException.class)
                .isThrownBy(() -> ReflectionUtils.getDeclaredFieldValue(instance, "unknown"));
    }

    @SuppressWarnings("checkstyle:TypeName")
    static class CglibAopProxy$StaticDispatcher {
        private final Target target;

        CglibAopProxy$StaticDispatcher(Target target) {
            this.target = target;
        }
    }

    static class Target {
        private Map<String, String> testMap;

        Target(Map<String, String> testMap) {
            this.testMap = testMap;
        }

        Target() {
            this.testMap = new HashMap<>();
        }

        @SuppressWarnings("checkstyle:TypeName")
        static class $EnhancerBySpringCGLIB extends Target {
            @SuppressWarnings("checkstyle:MemberName")
            private CglibAopProxy$StaticDispatcher CGLIB$CALLBACK_0;
            private Map<String, String> testMap;

            $EnhancerBySpringCGLIB(Target target) {
                CGLIB$CALLBACK_0 = new CglibAopProxy$StaticDispatcher(target);
            }
        }
    }
}
