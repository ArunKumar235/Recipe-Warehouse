package org.example.securin.dto;

import lombok.Builder;
import org.springframework.hateoas.server.core.Relation;

import java.util.Map;

// @Relation - Used to customize the _links and _embedded properties in the JSON output.
// By default, Spring HATEOAS uses the class name (in lowercase) as the relation name.
// So without this annotation, the collection would be "recipeOutDTOs" and the item would be "recipeOutDTO".
// By specifying @Relation, we can change it to "recipes" and "recipe", which is more intuitive for API consumers.
@Relation(collectionRelation = "recipes", itemRelation = "recipe")
@Builder
public record RecipeOutDTO(
                Integer id,
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
