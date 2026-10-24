package com.raxim.myscoutee.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@org.junit.jupiter.api.Tag("standard")
class BinaryRecordContractTest {
    private static Stream<String> binaryRecords() {
        return Stream.of(
                "com.raxim.myscoutee.domain.media.api.MediaStorage$StoredMediaObject",
                "com.raxim.myscoutee.domain.media.service.MediaStorageService$ImportedImage",
                "com.raxim.myscoutee.domain.integrations.service.McpEventCallbackClient$Response",
                "com.raxim.myscoutee.domain.platform.security.bootstrap.OperatorBootstrapAuthenticationService$BootstrapConfiguration",
                "com.raxim.myscoutee.domain.platform.service.DefaultOperatorFirebaseCapabilityClient$HttpFirebasePublicConfigurationOperations$BoundedResponse");
    }

    @ParameterizedTest
    @MethodSource("binaryRecords")
    void binaryValueObjectsDeduplicateByContentAndKeepPayloadsOutOfDiagnostics(String className) throws Exception {
        Class<?> type = Class.forName(className);
        var components = type.getRecordComponents();
        Constructor<?> constructor = type.getDeclaredConstructor(
                Arrays.stream(components).map(component -> component.getType()).toArray(Class<?>[]::new));
        constructor.setAccessible(true);
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            values[i] = components[i].getType() == byte[].class ? new byte[] {11, 22, 33}
                    : components[i].getType() == int.class ? 200 : "sensitive-value";
        }
        Object first = constructor.newInstance(values);
        Object[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) if (copy[i] instanceof byte[] bytes) copy[i] = bytes.clone();
        Object second = constructor.newInstance(copy);
        assertThat(new HashSet<>(java.util.List.of(first, second))).hasSize(1);
        assertThat(first).isEqualTo(first).isNotEqualTo(null).isNotEqualTo("unrelated");
        assertThat(first.toString()).doesNotContain("sensitive-value", "11", "22", "33", "[B@");
        for (int i = 0; i < copy.length; i++) {
            Object[] changed = copy.clone();
            changed[i] = changed[i] instanceof byte[] ? new byte[] {44}
                    : changed[i] instanceof Integer ? 500 : "changed";
            assertThat(first).isNotEqualTo(constructor.newInstance(changed));
            if (components[i].getType() != int.class) {
                changed[i] = null;
                assertThat(first).isNotEqualTo(constructor.newInstance(changed));
                assertThat(constructor.newInstance(changed).hashCode()).isEqualTo(constructor.newInstance(changed).hashCode());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("binaryRecords")
    void arrayAccessorsKeepTheExistingOwnershipNeededForSecretWiping(String className) throws Exception {
        Class<?> type = Class.forName(className);
        var components = type.getRecordComponents();
        Constructor<?> constructor = type.getDeclaredConstructor(
                Arrays.stream(components).map(component -> component.getType()).toArray(Class<?>[]::new));
        constructor.setAccessible(true);
        Object[] values = Arrays.stream(components).map(component -> component.getType() == byte[].class
                ? new byte[] {11, 22, 33} : component.getType() == int.class ? 200 : "metadata").toArray();
        Object record = constructor.newInstance(values);
        for (int i = 0; i < values.length; i++) if (values[i] instanceof byte[] bytes) {
            var accessor = components[i].getAccessor();
            accessor.setAccessible(true);
            assertThat(accessor.invoke(record)).isSameAs(bytes);
            Arrays.fill(bytes, (byte) 0);
            assertThat((byte[]) accessor.invoke(record)).containsOnly((byte) 0);
        }
    }
}
