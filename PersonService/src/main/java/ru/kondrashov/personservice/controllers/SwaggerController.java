package ru.kondrashov.personservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Tag(name = "SWAGGER", description = "Controller for swagger")
public class SwaggerController {

    @GetMapping("/v1")
    @Operation(summary = "Get swagger page")
    public String swaggerHtml(){
        return "redirect:swagger-ui.html";
    }
}
