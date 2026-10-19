package ru.kondrashov.personservice.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import ru.kondrashov.personservice.crypto.EncryptedLocalDateConverter;
import ru.kondrashov.personservice.crypto.EncryptedStringConverter;

import javax.persistence.*;
import javax.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "people", schema = "public")
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString(of = "id")
public class Person {

    private static final String ENCRYPTED_COLUMN = "text";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotEmpty(message = "First name should not be empty")
    @Size(min = 1, max = 30)
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = ENCRYPTED_COLUMN)
    private String firstName;

    @NotEmpty(message = "Last name should not be empty")
    @Size(min = 1, max = 30)
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = ENCRYPTED_COLUMN)
    private String lastName;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Birthdate should not be empty")
    @Convert(converter = EncryptedLocalDateConverter.class)
    @Column(columnDefinition = ENCRYPTED_COLUMN)
    private LocalDate birthdate;

    @NotEmpty(message = "Phone number should not be empty")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = ENCRYPTED_COLUMN)
    private String phoneNumber;

    @NotEmpty(message = "Email should not be empty")
    @Email(message = "Email should be valid")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = ENCRYPTED_COLUMN)
    private String email;

}