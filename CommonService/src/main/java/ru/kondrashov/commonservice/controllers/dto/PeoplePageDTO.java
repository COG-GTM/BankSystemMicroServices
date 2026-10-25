package ru.kondrashov.commonservice.controllers.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class PeoplePageDTO {

    private final List<PersonRequestDTO> people;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return page < totalPages - 1;
    }
}
