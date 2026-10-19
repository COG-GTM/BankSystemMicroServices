package ru.kondrashov.personservice.controllers.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import ru.kondrashov.personservice.entities.ErasureAuditEvent;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class ErasureAuditEventDTO {

    private UUID id;

    private UUID personId;

    @NotBlank(message = "Actor should not be empty")
    private String actor;

    @NotNull(message = "Outcome should not be empty")
    private ErasureAuditEvent.Outcome outcome;

    private String detail;

    private String sourceAddress;

    private LocalDateTime occurredAt;

    public static ErasureAuditEventDTO from(ErasureAuditEvent event) {
        ErasureAuditEventDTO dto = new ErasureAuditEventDTO();
        dto.setId(event.getId());
        dto.setPersonId(event.getPersonId());
        dto.setActor(event.getActor());
        dto.setOutcome(event.getOutcome());
        dto.setDetail(event.getDetail());
        dto.setSourceAddress(event.getSourceAddress());
        dto.setOccurredAt(event.getOccurredAt());
        return dto;
    }
}
