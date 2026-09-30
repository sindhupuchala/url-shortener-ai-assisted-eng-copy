package com.urlshortener.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortCodeEncoderTest {

    private final ShortCodeEncoder encoder = new ShortCodeEncoder();

    @Test
    void encodesKnownValues() {
        assertThat(encoder.encode(1)).isEqualTo("1");
        assertThat(encoder.encode(61)).isEqualTo("Z");
        assertThat(encoder.encode(62)).isEqualTo("10");
        assertThat(encoder.encode(3843)).isEqualTo("ZZ");
    }

    @Test
    void isDeterministicAndCollisionFreeForDistinctIds() {
        String a = encoder.encode(12345);
        String b = encoder.encode(12345);
        String c = encoder.encode(12346);

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void rejectsNonPositiveIds() {
        assertThatThrownBy(() -> encoder.encode(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encoder.encode(-5)).isInstanceOf(IllegalArgumentException.class);
    }
}
