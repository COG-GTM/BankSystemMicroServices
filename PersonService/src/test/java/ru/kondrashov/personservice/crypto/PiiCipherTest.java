package ru.kondrashov.personservice.crypto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiiCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString(filled((byte) 7));

    private final PiiCipher cipher = new PiiCipher(KEY);

    @Test
    void roundTripsAndDoesNotStorePlaintext() {
        String email = "miketyson@mail.com";

        String stored = cipher.encrypt(email);

        assertThat(stored).startsWith(PiiCipher.VERSION_PREFIX).doesNotContain(email);
        assertThat(cipher.decrypt(stored)).isEqualTo(email);
    }

    @Test
    void usesFreshIvPerEncryption() {
        assertThat(cipher.encrypt("+7 1234859520")).isNotEqualTo(cipher.encrypt("+7 1234859520"));
    }

    @Test
    void roundTripsNonAsciiValues() {
        String name = "Михаил";
        assertThat(cipher.decrypt(cipher.encrypt(name))).isEqualTo(name);
    }

    @Test
    void passesNullThrough() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void rejectsTamperedCiphertext() {
        String stored = cipher.encrypt("JohnJones@mail.com");
        byte[] payload = Base64.getDecoder().decode(stored.substring(PiiCipher.VERSION_PREFIX.length()));
        payload[payload.length - 1] ^= 1;
        String tampered = PiiCipher.VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(PiiEncryptionException.class);
    }

    @Test
    void rejectsDecryptionWithWrongKey() {
        String stored = cipher.encrypt("JohnJones@mail.com");

        assertThatThrownBy(() -> new PiiCipher(OTHER_KEY).decrypt(stored)).isInstanceOf(PiiEncryptionException.class);
    }

    @Test
    void rejectsUnencryptedOrMalformedValues() {
        assertThatThrownBy(() -> cipher.decrypt("miketyson@mail.com")).isInstanceOf(PiiEncryptionException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1:not base64!")).isInstanceOf(PiiEncryptionException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1:AAAA")).isInstanceOf(PiiEncryptionException.class);
    }

    @Test
    void failsClosedWithoutValidKey() {
        assertThatThrownBy(() -> new PiiCipher("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PiiCipher(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PiiCipher("%%%")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PiiCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void convertersRoundTrip() {
        EncryptedStringConverter strings = new EncryptedStringConverter(cipher);
        EncryptedLocalDateConverter dates = new EncryptedLocalDateConverter(cipher);
        LocalDate birthdate = LocalDate.of(1966, 6, 30);

        String storedDate = dates.convertToDatabaseColumn(birthdate);

        assertThat(storedDate).doesNotContain("1966");
        assertThat(dates.convertToEntityAttribute(storedDate)).isEqualTo(birthdate);
        assertThat(strings.convertToEntityAttribute(strings.convertToDatabaseColumn("Tyson"))).isEqualTo("Tyson");
        assertThat(dates.convertToDatabaseColumn(null)).isNull();
        assertThat(dates.convertToEntityAttribute(null)).isNull();
    }

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
