package org.example.securin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecipeInDTO(
                String cuisine,
                String title,
                Object rating,
                Object prep_time,
                Object cook_time,
                Object total_time,
                String description,
                Map<String, Object> nutrients,
                String serves) {
}
