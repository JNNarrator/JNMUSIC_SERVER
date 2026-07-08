package com.jn.music.lanzou;

public class LanzouSessionException extends RuntimeException {
    public LanzouSessionException(String message) {
        super(message);
    }

    public LanzouSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
