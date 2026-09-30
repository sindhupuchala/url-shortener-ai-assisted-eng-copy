package com.urlshortener.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ValidUrlValidatorTest {

    private final ValidUrlValidator validator = new ValidUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?query=1",
            "https://sub.example.co.uk:8080/a/b/c"
    })
    void acceptsWellFormedAbsoluteUrls(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "'', blank",
            "not-a-url, no scheme/host",
            "'/relative/path', relative path"
    })
    void rejectsMalformedInput(String value, String reason) {
        assertThat(validator.isValid(value, null)).as(reason).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "file:///etc/passwd",
            "ftp://example.com/file"
    })
    void rejectsNonHttpSchemes(String value) {
        assertThat(validator.isValid(value, null)).as(value).isFalse();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    void rejectsNull(String value) {
        assertThat(validator.isValid(value, null)).isFalse();
    }
}
