package com.skala.cbam.task.error;

public class TaskException extends RuntimeException {

    private final transient TaskErrorCode errorCode;

    public TaskException(TaskErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public TaskErrorCode errorCode() {
        return errorCode;
    }
}
