package interview.java.coding.Exception;

import java.io.IOException;
import java.sql.SQLException;

public class ExceptionTranslator {
    public static <T> T translateException(ExceptionSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            throw new BusinessException("File processing error", e);
        } catch (SQLException e) {
            throw new DatabaseException("Database operation failed", e);
        } catch (Exception e) {
            throw new GenericBusinessException("Unexpected error", e);
        }
    }

    @FunctionalInterface
    public interface ExceptionSupplier<T> {
        T get() throws Exception;
    }

    // Custom exception classes
    public static class BusinessException extends RuntimeException {
        public BusinessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class DatabaseException extends RuntimeException {
        public DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class GenericBusinessException extends RuntimeException {
        public GenericBusinessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}