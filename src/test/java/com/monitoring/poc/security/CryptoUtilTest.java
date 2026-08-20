package com.monitoring.poc.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoUtilTest {

    private final CryptoUtil cryptoUtil = new CryptoUtil("unit-test-master-key");

    @Test
    void encryptThenDecryptRoundTripsToOriginalValue() {
        String secret = "my-super-secret-hmac-key-value";

        String encrypted = cryptoUtil.encrypt(secret);
        String decrypted = cryptoUtil.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(secret);
        assertThat(encrypted).isNotEqualTo(secret);
    }

    @Test
    void encryptingTheSamePlaintextTwiceProducesDifferentCiphertext() {
        String secret = "same-plaintext";

        String first = cryptoUtil.encrypt(secret);
        String second = cryptoUtil.encrypt(secret);

        assertThat(first).isNotEqualTo(second);
        assertThat(cryptoUtil.decrypt(first)).isEqualTo(secret);
        assertThat(cryptoUtil.decrypt(second)).isEqualTo(secret);
    }
}
