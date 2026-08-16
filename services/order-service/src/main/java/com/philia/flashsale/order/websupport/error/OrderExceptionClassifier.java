package com.philia.flashsale.order.websupport.error;

import com.philia.flashsale.order.order.application.exception.InvalidOrderQueryException;
import com.philia.flashsale.order.order.application.exception.OrderNotFoundException;
import com.philia.flashsale.order.order.application.exception.RetryableOrderPersistenceException;
import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/** Converts framework and persistence failures into the public Order error catalog. */
public final class OrderExceptionClassifier {
    private OrderExceptionClassifier() {
    }

    public static OrderErrorCode classify(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OrderNotFoundException) {
                return OrderErrorCode.ORDER_NOT_FOUND;
            }
            if (current instanceof InvalidOrderQueryException) {
                return OrderErrorCode.VALIDATION_FAILED;
            }
            if (current instanceof OrderAuthenticationException) {
                return OrderErrorCode.AUTHENTICATION_REQUIRED;
            }
            if (current instanceof DataAccessException || current instanceof RetryableOrderPersistenceException
                    || current instanceof SQLException) {
                return OrderErrorCode.ORDER_DATABASE_UNAVAILABLE;
            }
            if (current instanceof ResponseStatusException responseStatusException) {
                return fromStatus(responseStatusException.getStatusCode());
            }
            current = current.getCause();
        }
        return OrderErrorCode.ORDER_INTERNAL_ERROR;
    }

    private static OrderErrorCode fromStatus(HttpStatusCode status) {
        if (status.value() == 400) {
            return OrderErrorCode.VALIDATION_FAILED;
        }
        if (status.value() == 401) {
            return OrderErrorCode.AUTHENTICATION_REQUIRED;
        }
        if (status.value() == 403) {
            return OrderErrorCode.ACCESS_DENIED;
        }
        if (status.value() == 404) {
            return OrderErrorCode.ORDER_NOT_FOUND;
        }
        if (status.value() == 405) {
            return OrderErrorCode.ORDER_METHOD_NOT_ALLOWED;
        }
        return OrderErrorCode.ORDER_INTERNAL_ERROR;
    }
}
