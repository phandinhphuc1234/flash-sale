package com.philia.flashsale.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CommonWebResponseContractTest {

    @Test
    void successEnvelopeUsesCanonicalDefaults() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertTrue(response.success());
        assertEquals("SUCCESS", response.code());
        assertEquals("Operation completed successfully", response.message());
        assertEquals("payload", response.data());
        assertNotNull(response.timestamp());
    }

    @Test
    void successEnvelopeNormalizesCodeAndTimestamp() {
        ApiResponse<String> response = new ApiResponse<>(false, " ", "ok", "payload", null);

        assertTrue(response.success());
        assertEquals("SUCCESS", response.code());
        assertNotNull(response.timestamp());
    }

    @Test
    void successWithoutDataKeepsNullPayload() {
        ApiResponse<Void> response = ApiResponse.successWithoutData("accepted");

        assertTrue(response.success());
        assertEquals("accepted", response.message());
        assertNull(response.data());
    }

    @Test
    void errorEnvelopeUsesSafeShapeAndCopiesViolations() {
        List<FieldViolation> violations = new ArrayList<>();
        violations.add(new FieldViolation("name", "must not be blank"));

        ApiErrorResponse response = ApiErrorResponse.of("VALIDATION_ERROR", "Request is invalid", violations);
        violations.add(new FieldViolation("sku", "must not be blank"));

        assertFalse(response.success());
        assertEquals("VALIDATION_ERROR", response.errorCode());
        assertEquals("Request is invalid", response.message());
        assertEquals(List.of(new FieldViolation("name", "must not be blank")), response.errors());
        assertNotNull(response.timestamp());
        assertThrows(UnsupportedOperationException.class,
                () -> response.errors().add(new FieldViolation("price", "must be positive")));
    }

    @Test
    void errorEnvelopeOmitsEmptyViolations() {
        assertNull(ApiErrorResponse.of("NOT_FOUND", "Resource was not found").errors());
        assertNull(ApiErrorResponse.of("VALIDATION_ERROR", "Request is invalid", List.of()).errors());
    }

    @Test
    void pageMetadataCalculatesTotalsAndHasNext() {
        PageMeta firstPage = PageMeta.of(0, 10, 21);
        PageMeta lastPage = PageMeta.of(2, 10, 21);
        PageMeta emptyPage = PageMeta.of(0, 10, 0);

        assertEquals(3, firstPage.totalPages());
        assertTrue(firstPage.hasNext());
        assertFalse(lastPage.hasNext());
        assertEquals(0, emptyPage.totalPages());
        assertFalse(emptyPage.hasNext());
    }

    @Test
    void pageMetadataRejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new PageMeta(-1, 10, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new PageMeta(0, 0, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new PageMeta(0, 10, -1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new PageMeta(0, 10, 0, -1, false));
    }

    @Test
    void pageResponseNormalizesNullsAndCopiesData() {
        List<String> data = new ArrayList<>(List.of("one"));
        PageResponse<String> response = PageResponse.of(data, 0, 10, 1);
        data.add("two");

        assertEquals(List.of("one"), response.data());
        assertEquals(PageMeta.of(0, 10, 1), response.page());
        assertThrows(UnsupportedOperationException.class, () -> response.data().add("three"));

        PageResponse<String> empty = new PageResponse<>(null, null);
        assertEquals(List.of(), empty.data());
        assertEquals(PageMeta.of(0, 10, 0), empty.page());
    }

    @Test
    void sharedTransportRecordsDoNotExposeTraceIdInJsonContract() {
        Set<String> traceIdComponents = Set.of(ApiResponse.class, ApiErrorResponse.class, FieldViolation.class,
                PageMeta.class, PageResponse.class).stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .filter("traceId"::equals)
                .collect(Collectors.toSet());

        assertTrue(traceIdComponents.isEmpty());
    }
}
