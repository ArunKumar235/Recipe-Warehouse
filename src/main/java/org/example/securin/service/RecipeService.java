package org.example.securin.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.securin.dto.RecipeInDTO;
import org.example.securin.model.Recipe;
import org.example.securin.repo.RecipeRepository;
import org.example.securin.utility.DataCleaner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository repo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void loadRecipes(InputStream inputStream) {
        // Non memory efficient way to load the entire file into memory at once, which
        // can cause OutOfMemoryError for large files.
        // try {
        //
        // Map<String, RecipeDTO> map = new ObjectMapper().readValue(
        // inputStream,
        // new TypeReference<Map<String, RecipeDTO>>() {}
        // );
        // RecipeDTO[] dtos = map.values().toArray(new RecipeDTO[0]);
        //
        // int batchSize = 1000;
        // List<Recipe> batch = new ArrayList<>();
        // for (RecipeDTO dto : dtos) {

        // Use Jackson streaming to handle large files
        try (JsonParser parser = objectMapper.getFactory().createParser(inputStream)) {
            // In Jackson, nextToken() returns the token it just landed on, and the
            // "pointer" stays on that token.
            // It doesn't move past it until the next time you call the method.
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalStateException("Expected JSON Object at root");
            }

            int batchSize = 1000;
            List<Recipe> batch = new ArrayList<>();

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                // Current token is FIELD_NAME (e.g., "0")

                parser.nextToken(); // Returns { and points to the start of the recipe object
                RecipeInDTO dto = objectMapper.readValue(parser, RecipeInDTO.class); // readValue will consume the
                                                                                     // entire object ( '{' to '}' ) for
                                                                                     // this recipe, until it reaches
                                                                                     // the next END_OBJECT
                // readValue will load the entire object into memory, but since we're processing
                // one recipe at a time, it should be manageable even for large files.

                Recipe recipe = Recipe.builder()
                        .cuisine(dto.cuisine())
                        .title(dto.title())
                        .rating(DataCleaner.cleanFloat(dto.rating()))
                        .prep_time(DataCleaner.cleanInteger(dto.prep_time()))
                        .cook_time(DataCleaner.cleanInteger(dto.cook_time()))
                        .total_time(DataCleaner.cleanInteger(dto.total_time()))
                        .description(dto.description())
                        .nutrients(dto.nutrients())
                        .serves(dto.serves())
                        .build();
                batch.add(recipe);
                if (batch.size() >= batchSize) {
                    repo.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                repo.saveAll(batch);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load recipes: " + e.getMessage(), e);
        }
    }

    public Page<Recipe> getAllRecipes(Pageable pageable) {
        return repo.findAll(makeNullLast(pageable));
    }

    // public Page<Recipe> getAllRecipes(int page, int size, String sortBy, String
    // sortOrder) {
    // Sort sort = Sort.by(
    // new Sort.Order(sortOrder.equalsIgnoreCase("desc") ? Sort.Direction.DESC :
    // Sort.Direction.ASC, sortBy).nullsLast()
    // ).and(Sort.by(new Sort.Order(Sort.Direction.ASC, "id")));
    //
    // Pageable pageable = PageRequest.of(page, size, sort);
    // return repo.findAll(pageable);
    // }

    public Page<Recipe> searchRecipes(Specification<Recipe> specification, Pageable pageable) {
        return repo.findAll(specification, makeNullLast(pageable));
    }

    private static Pageable makeNullLast(Pageable pageable) {
        Sort baseSort = pageable.getSort().isSorted()
                ? Sort.by(
                        pageable.getSort().stream()
                                .map(o -> new Sort.Order(o.getDirection(), o.getProperty()).nullsLast())
                                .toList())
                : Sort.unsorted();

        boolean hasIdSort = baseSort.stream().anyMatch(o -> "id".equalsIgnoreCase(o.getProperty()));
        Sort finalSort = hasIdSort
                ? baseSort
                : baseSort.and(Sort.by(new Sort.Order(Sort.Direction.ASC, "id").nullsLast()));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), finalSort);
    }
}
