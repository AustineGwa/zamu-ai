package ai.zamu.shared.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The single place where page errors become friendly pages (spec: Web routes, errors). Webhook controllers
 * (from Milestone 2) return the status the provider expects and handle their own errors.
 */
@ControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Let Spring Security turn access errors into a login redirect or a 403 page. */
    @ExceptionHandler(AccessDeniedException.class)
    void accessDenied(AccessDeniedException e) {
        throw e;
    }

    /** Unknown IDs, including malformed UUIDs in the path, are plain 404s. */
    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound() {
        return "error/404";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    String unexpected(Exception e, HttpServletRequest request, Model model) {
        Object requestId = request.getAttribute("zamu.requestId");
        // The stack trace goes to the log; nothing about the error reaches the page except a reference.
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), e);
        model.addAttribute("reference", requestId);
        return "error/500";
    }
}
