package net.keksipurkki.vertx;

import static java.util.Objects.nonNull;

public class DataAccessException extends RuntimeException {

    private Throwable cause;

    DataAccessException(String message) {
        super(message);
    }

    @Override
    public String getMessage() {
        if (nonNull(cause)) {
            return String.format("%s: %s", super.getMessage(), cause.getMessage());
        } else {
            return super.getMessage();
        }
    }

    public DataAccessException withCause(Throwable cause) {
        this.cause = cause;
        initCause(this.cause);
        return this;
    }
}
