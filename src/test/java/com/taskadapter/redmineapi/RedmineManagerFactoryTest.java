package com.taskadapter.redmineapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class RedmineManagerFactoryTest {

    @SuppressWarnings("unused")
    @Test
    public void testNULLHostParameter() {
        assertThrows(IllegalArgumentException.class, () -> RedmineManagerFactory.createUnauthenticated(null));
    }

    @Test
    @SuppressWarnings("unused")
    public void testEmptyHostParameter() throws RuntimeException {
        assertThrows(IllegalArgumentException.class, () -> RedmineManagerFactory.createUnauthenticated(""));
    }

}
