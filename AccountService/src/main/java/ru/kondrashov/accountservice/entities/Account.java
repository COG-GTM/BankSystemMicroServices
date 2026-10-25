package ru.kondrashov.accountservice.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.Hibernate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

import static javax.persistence.TemporalType.TIMESTAMP;

@Entity
@Getter
@Setter
@ToString
@Table(name = "accounts", schema = "public")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotEmpty (message = "Name shouldn't be empty")
    @NotNull(message = "Name shouldn't be null")
    private String name;

    @CreatedDate
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Creation date shouldn't be null")
    private LocalDate creationDate;

    @Column(name="person_id")
    @NotNull(message = "Person Id shouldn't be null")
    private UUID personId;

    public Account(String name, LocalDate creationDate, UUID personId) {
        this.name = name;
        this.creationDate = creationDate;
        this.personId = personId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Account other = (Account) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
