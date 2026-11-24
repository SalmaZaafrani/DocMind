package com.docmind.llm;

public class LlmException extends RuntimeException {

    private final int status;

    public LlmException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
