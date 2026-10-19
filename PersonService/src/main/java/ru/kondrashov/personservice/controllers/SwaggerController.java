package ru.kondrashov.personservice.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import ru.kondrashov.personservice.config.SwaggerConfig;

@Controller
@Profile(SwaggerConfig.SWAGGER_PROFILE)
@Api(description = "Controller for swagger", tags = {"SWAGGER"})
public class SwaggerController {

    @GetMapping("/v1")
    @ApiOperation(value = "Get swagger page", tags = {"SWAGGER"})
    public String swaggerHtml(){
        return "redirect:swagger-ui.html";
    }
}
