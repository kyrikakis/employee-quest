package com.reliaquest.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Employee {

    @ApiModelProperty(notes = "The unique identifier of the employee")
    private String id;

    @ApiModelProperty(notes = "The employee name")
    private String name;

    @ApiModelProperty(notes = "The employee name")
    private Integer salary;

    private Integer age;

    private String title;

    private String email;
}
