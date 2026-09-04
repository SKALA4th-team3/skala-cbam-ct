package com.skala.cbam.task.controller;

import com.skala.cbam.task.error.TaskErrorCode;
import com.skala.cbam.task.error.TaskErrorResponse;
import com.skala.cbam.task.error.TaskException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** {@code FeedbackApiExceptionHandling} 과 같은 이유로 {@code @ControllerAdvice} 대신 상속으로 처리한다. */
abstract class TaskApiExceptionHandling {

    @ExceptionHandler(TaskException.class)
    ResponseEntity<TaskErrorResponse> handleTaskException(TaskException e, HttpServletRequest request) {
        TaskErrorCode code = e.errorCode();
        return ResponseEntity.status(code.status())
                .body(TaskErrorResponse.of(code, request.getRequestURI()));
    }
}
