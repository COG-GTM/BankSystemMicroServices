package ru.kondrashov.personservice.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.kondrashov.personservice.controllers.dto.PersonRequestDTO;
import ru.kondrashov.personservice.controllers.dto.PersonResponseDTO;
import ru.kondrashov.personservice.services.PeopleService;
import ru.kondrashov.personservice.utils.PeopleMapping;

import javax.validation.Valid;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;


@RestController
@RequestMapping(value = "/v1/people", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Api(description = "Controller which provides methods for operations with people", tags = {"PEOPLE"})
public class PeopleController {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;

    private final PeopleService peopleService;
    private final PeopleMapping peopleMapping;
    private final Logger logger;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation("Find people")
    public Collection<PersonResponseDTO> getPeople(@ApiParam("Zero-based page index") @RequestParam(defaultValue = "0") int page,
                                                   @ApiParam("Page size, 1.." + MAX_PAGE_SIZE) @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
                                                   @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people\""+" received a GET request from the server "+header.getHost()+" with content type "+header.getContentType());
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return peopleService.getAll(PageRequest.of(page, size, Sort.by("lastName", "firstName", "id")))
                .stream()
                .map(peopleMapping::mapToPersonResponseDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation("Find person by ID")
    public PersonResponseDTO getPerson(@ApiParam("ID of the person. Cannot to be null or empty") @PathVariable UUID id, @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{id}\""+" received a GET request from the server "+header.getHost()+" with content type "+header.getContentType());
        return peopleMapping.mapToPersonResponseDTO(peopleService.get(id));
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    @ApiOperation("Save a new person")
    public void createPerson(@ApiParam("new person to be save") @RequestBody @Valid PersonRequestDTO personRequestDTO, @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people\""+" received a POST request from the server "+header.getHost()+" with content type "+header.getContentType());
        peopleService.save(peopleMapping.mapToPerson(personRequestDTO));
    }

    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @ApiOperation("Update a person")
    public void updatePerson(@ApiParam("new person to be update") @RequestBody @Valid PersonRequestDTO personRequestDTO,
                             @ApiParam("ID of the person. Cannot to be null or empty") @PathVariable("id") UUID id, @RequestHeader HttpHeaders header) {
        logger.info("The controller "+"\"/v1/people/{id}\""+" received a PUT request from the server "+header.getHost()+" with content type "+header.getContentType());
        peopleService.update(id, peopleMapping.mapToPerson(personRequestDTO));
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiOperation("Delete a person")
    public void deletePerson(@ApiParam("ID of the person. Cannot to be null or empty") @PathVariable("id") UUID id, @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{id}\""+" received a DELETE request from the server "+header.getHost()+" with content type "+header.getContentType());
        peopleService.delete(id);

    }
}
