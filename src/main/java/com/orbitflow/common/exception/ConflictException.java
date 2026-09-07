package com.orbitflow.common.exception;

public class ConflictException extends RuntimeException {
    private final Object currentState;
    public ConflictException(String message) { super(message); this.currentState = null; }
    public ConflictException(String message, Object currentState) { super(message); this.currentState = currentState; }
    public Object getCurrentState() { return currentState; }
}
