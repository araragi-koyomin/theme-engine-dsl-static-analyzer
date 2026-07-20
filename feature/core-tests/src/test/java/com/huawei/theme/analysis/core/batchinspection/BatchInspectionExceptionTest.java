package com.huawei.theme.analysis.core.batchinspection;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchInspectionExceptionTest {
    @Test
    void constructorWithMessage() {
        BatchInspectionException ex = new BatchInspectionException("test error");
        assertEquals("test error", ex.getMessage());
        assertNotNull(ex);
    }
    @Test
    void constructorWithMessageAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("root cause");
        BatchInspectionException ex = new BatchInspectionException("test error", cause);
        assertEquals("test error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
    @Test
    void causeChainPreserved() {
        IOException ioCause = new IOException("disk failure");
        BatchInspectionException ex = new BatchInspectionException("file read failed", ioCause);
        assertEquals("file read failed", ex.getMessage());
        assertEquals(ioCause, ex.getCause());
        assertEquals("disk failure", ex.getCause().getMessage());
    }
    @Test
    void exceptionIsRuntimeException() {
        BatchInspectionException ex = new BatchInspectionException("msg");
        assertTrue(ex instanceof RuntimeException);
    }
    @Test
    void exceptionCanBeCaughtAsRuntimeException() {
        RuntimeException caught = null;
        try {
            throw new BatchInspectionException("test");
        } catch (RuntimeException e) {
            caught = e;
        }
        assertNotNull(caught);
        assertTrue(caught instanceof BatchInspectionException);
    }
    @Test
    void constructorWithNullMessage() {
        BatchInspectionException ex = new BatchInspectionException(null);
        assertNull(ex.getMessage());
    }
    @Test
    void constructorWithNullCause() {
        BatchInspectionException ex = new BatchInspectionException("msg", null);
        assertEquals("msg", ex.getMessage());
        assertNull(ex.getCause());
    }
    @Test
    void constructorWithNullMessageAndCause() {
        BatchInspectionException ex = new BatchInspectionException(null, null);
        assertNull(ex.getMessage());
        assertNull(ex.getCause());
    }
}
