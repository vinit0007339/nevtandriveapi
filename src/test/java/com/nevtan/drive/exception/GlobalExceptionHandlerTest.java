package com.nevtan.drive.exception;

import com.nevtan.drive.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void uploadSizeExceededReturnsPayloadTooLarge() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/drive/upload");
        UploadSizeExceededException exception = new UploadSizeExceededException(6L, 5L);

        ResponseEntity<ErrorResponse> response = handler.handleStorageLimit(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("exceeds maximum");
        assertThat(response.getBody().path()).isEqualTo("/api/drive/upload");
    }

    @Test
    void storageLimitExceededReturnsPayloadTooLarge() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/drive/upload");
        StorageLimitExceededException exception = new StorageLimitExceededException(9L, 6L, 10L);

        ResponseEntity<ErrorResponse> response = handler.handleStorageLimit(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Storage limit exceeded");
    }
}
