package com.portabilidade.backend.exception;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
	private static final String CODE = "code";

	@ExceptionHandler(AuthorizationRequiredException.class)
	ProblemDetail handleAuthorizationRequired(AuthorizationRequiredException e) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), "AUTHORIZATION_REQUIRED");
	}

	@ExceptionHandler(IdempotencyKeyReusedException.class)
	ProblemDetail handleIdempotencyKeyReused(IdempotencyKeyReusedException e) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), "IDEMPOTENCY_KEY_REUSED");
	}

	@ExceptionHandler(InstallmentNotEligibleException.class)
	ProblemDetail handleInstallmentNotEligible(InstallmentNotEligibleException e) {
		var body = problem(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), "INSTALLMENT_NOT_ELIGIBLE");
		body.setProperty("installmentIds", e.getInstallmentIds());
		return body;
	}

	@ExceptionHandler(InstallmentInOperationException.class)
	ProblemDetail handleInstallmentInOperation(InstallmentInOperationException e) {
		var body = problem(HttpStatus.CONFLICT, e.getMessage(), "INSTALLMENT_IN_OPERATION");
		body.setProperty("installmentIds", e.getInstallmentIds());
		return body;
	}

	@ExceptionHandler(PortabilityOperationNotFoundException.class)
	ProblemDetail handlePortabilityOperationNotFound(PortabilityOperationNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, e.getMessage(), "PORTABILITY_OPERATION_NOT_FOUND");
	}

	@ExceptionHandler(CentralConveniosUnavailableException.class)
	ProblemDetail handleCentralUnavailable(CentralConveniosUnavailableException e) {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), "CENTRAL_CONVENIOS_UNAVAILABLE");
	}

	@ExceptionHandler(CentralConveniosBadResponseException.class)
	ProblemDetail handleCentralBadResponse(CentralConveniosBadResponseException e) {
		return problem(HttpStatus.BAD_GATEWAY, e.getMessage(), "CENTRAL_CONVENIOS_BAD_RESPONSE");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception e) {
		log.error("Unexpected error", e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", "INTERNAL_ERROR");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(FieldError::getField, this::messageOf, (first, second) -> first));
		var body = problem(HttpStatus.BAD_REQUEST, "Request has invalid fields.", "VALIDATION_ERROR");
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var body = problem(HttpStatus.BAD_REQUEST, "Request body is missing or malformed.", "VALIDATION_ERROR");
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (ex instanceof MissingRequestHeaderException missing) {
			return headerProblem(ex, missing.getHeaderName(), headers, status, request);
		}
		return super.handleServletRequestBindingException(ex, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		if (ex instanceof MethodArgumentTypeMismatchException mismatch
				&& mismatch.getParameter().getParameterAnnotation(RequestHeader.class)
						instanceof RequestHeader header) {
			return headerProblem(ex, header.value(), headers, status, request);
		}
		return super.handleTypeMismatch(ex, headers, status, request);
	}

	// Gives the standard Spring MVC problems (404, 405, 415...) a machine-readable code as well.
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (body instanceof ProblemDetail problem
				&& (problem.getProperties() == null || !problem.getProperties().containsKey(CODE))) {
			problem.setProperty(CODE, HttpStatus.valueOf(statusCode.value()).name());
		}
		return super.handleExceptionInternal(ex, body, headers, statusCode, request);
	}

	// Missing or malformed required header -> 400 <HEADER>_REQUIRED, e.g. Idempotency-Key -> IDEMPOTENCY_KEY_REQUIRED.
	private ResponseEntity<Object> headerProblem(Exception ex, String headerName, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		var code = headerName.toUpperCase(Locale.ROOT).replace('-', '_') + "_REQUIRED";
		var body = problem(HttpStatus.BAD_REQUEST, "Header " + headerName + " is required and must be valid.", code);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	private String messageOf(FieldError error) {
		return error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
	}

	private static ProblemDetail problem(HttpStatus status, String detail, String code) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty(CODE, code);
		return problem;
	}

}
