package com.comet.opik.api.resources.utils;

import lombok.Builder;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractCollectionAssert;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Builder
public class FieldValidator {

    private final boolean allowNullContainers;
    private final boolean allowEmptyLists;

    public void validateFields(Object actual, String... fieldPaths) {
        if (fieldPaths == null || fieldPaths.length == 0) {
            return;
        }

        Arrays.stream(fieldPaths).forEach(path -> validateField(actual, path));
    }

    private void validateField(Object actual, String fieldPath) {
        if (fieldPath.contains(".")) {
            validateNestedField(actual, fieldPath);
        } else {
            validateTopLevelField(actual, fieldPath);
        }
    }

    private void validateTopLevelField(Object actual, String fieldName) {
        if (actual instanceof Collection<?> collection) {

            if (allowNullContainers) {
                collection = collection.stream()
                        .filter(Objects::nonNull)
                        .toList();
            }

            if (allowEmptyLists && collection.isEmpty()) {
                return;
            }

            AbstractCollectionAssert<?, ?, ?, ?> assertion = getCollectionAssert(collection);

            assertion
                    .extracting(fieldName)
                    .doesNotContainNull();
        }else if (actual instanceof AbstractAssert<?, ?> source) {
            validateField(source.actual(), fieldName);
        } else {
            assertThat(actual)
                    .extracting(fieldName)
                    .isNotNull();
        }
    }

    private AbstractCollectionAssert<?, ?, ?, ?> getCollectionAssert(Collection<?> collection) {
        AbstractCollectionAssert<?, ?, ?, ?> assertion = assertThat(collection);

        if (!collection.isEmpty() && collection.stream().anyMatch(Collection.class::isInstance)) {
            assertion = assertion.flatMap((Object list) -> ((Collection<?>) list));
        }

        return assertion;
    }

    private void validateNestedField(Object actual, String fieldPath) {
        String[] pathParts = fieldPath.split("\\.", 2);

        if (pathParts.length == 0) {
            return;
        }

        String containerField = pathParts[0];
        String nestedField = pathParts[1];

        Object containerValue = extractField(actual, containerField);

        validateField(containerValue, nestedField);
    }

    private AbstractAssert<?, ?> extractField(Object source, String fieldName) {
        try {
            return switch (source) {
                case Collection<?> value -> assertThat(value).extracting(fieldName)
                        .as("Failed to extract field '%s'", fieldName);
                default -> assertThat(source).extracting(fieldName)
                        .as("Failed to extract field '%s'", fieldName);
            };
        } catch (Exception e) {
            throw new AssertionError(String.format("Could not extract field '%s' from object", fieldName), e);
        }
    }
}
