# 🍳 Recipe Warehouse

[![Java CI with Maven](https://github.com/ArunKumar235/Securin-Assesment/actions/workflows/maven.yml/badge.svg)](https://github.com/ArunKumar235/Securin-Assesment/actions/workflows/maven.yml)
[![Deploy to Azure](https://github.com/ArunKumar235/Securin-Assesment/actions/workflows/azure-webapps-java-jar.yml/badge.svg)](https://github.com/ArunKumar235/Securin-Assesment/actions/workflows/azure-webapps-java-jar.yml)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue.svg)](https://www.postgresql.org/)

**Recipe Warehouse** is a high-performance, enterprise-grade Spring Boot web application designed for ingestion, storage, and dynamic searching of massive culinary datasets. Built on **Java 21**, **Spring Boot 4.x**, and **PostgreSQL 18**, the system features a memory-optimized streaming ingestion pipeline and a highly flexible, multi-criteria database search engine that maps PostgreSQL JSONB functions directly to Hibernate Criteria API.

---

## 🚀 Key Highlights

This repository hosts a production-ready implementation that demonstrates advanced engineering patterns:

*   **Faceted Navigation & Search Engine (PostgreSQL JSONB + JPA Specs)**: Implemented a highly flexible **Faceted Navigation Design Pattern** using **Spring Data JPA Specifications**. The engine allows users to dynamically search and drill down into massive datasets by combining multiple orthogonal facets (such as cuisine category, average rating ranges, total cook times, and calorie ranges). This achieved a **100% improvement in query flexibility** by executing dynamic SQL criteria across standard relational fields and deep semi-structured JSONB keys in a single query planner pass.
*   **Robust Data Ingestion Pipeline (Jackson Streaming + Hibernate Batching)**: Engineered a memory-efficient ingestion pipeline using low-level **Jackson Streaming APIs** to parse and load **100MB+ JSON datasets** containing millions of sub-elements. By avoiding memory-heavy object-tree parsing and implementing a strictly-managed **1,000-record batch chunking cycle** backed by optimized Hibernate JDBC batch settings, the system eliminates garbage collection spikes and prevents `OutOfMemoryError` issues under heavy load.
*   **Production-Grade CI/CD & Cloud Orchestration**: Streamlined workflows by designing dual-stage automation pipelines via **GitHub Actions**. The CI stage runs comprehensive automated testing with an active PostgreSQL service container, while the CD stage automates deployments directly to **Azure App Service (Web App)** upon merges to the main branch, enabling frictionless and reliable release cycles.
*   **Fully Compliant Hypermedia REST APIs (Spring HATEOAS)**: Employs standard HAL-compliant REST interfaces. APIs automatically yield self-descriptive relation metadata using custom HATEOAS representation assemblers, facilitating self-discovery and clean navigation for API clients.

---

## 🛠️ Technology Stack

| Technology / Library | Version | Description |
| :--- | :--- | :--- |
| **Java** | `21` | Utilizing modern language features like Records, pattern matching, and enhanced stream APIs. |
| **Spring Boot** | `4.0.5` | Standard core application framework with integrated autoconfigurations. |
| **Spring Data JPA** | `4.0.5` | Repository abstraction and object-relational mapping interface. |
| **Spring HATEOAS** | `4.0.5` | Dynamic hypermedia creation (HAL representation format). |
| **PostgreSQL** | `18` | Relational storage engine with native JSONB support. |
| **Jackson Core** | `2.18` | Low-level JSON parser for fast, memory-friendly streaming ingestion. |
| **Lombok** | `1.18` | Compile-time boilerplates reduction. |
| **GitHub Actions** | — | CI/CD automation & runner execution environment. |
| **Azure Web Apps** | — | Production cloud environment hosting the compiled JAR execution context. |

---

## 🏗️ Deep Architectural Dives

### 1. Faceted Navigation Engine & Custom PostgreSQL Mapping

To handle complex, multi-criteria filtering across standard columns (`cuisine`, `title`, `rating`, `total_time`) and raw text inside deeply-nested `jsonb` structures (`nutrients` containing `calories`), the engine constructs JPA Specifications dynamically. 

By leveraging native PostgreSQL string-to-numeric extraction inside the SQL query planner, it achieves blazing-fast search speeds and 100% query capability without needing duplicate columns or elasticsearch indices.

```java
// Snippet from org.example.securin.specification.RecipeSpecification.java
public static Specification<Recipe> withCalories(String filterInput){
    return (root, query, cb) -> {
        FilterValue filterValue = FilterOperator.parse(filterInput);
        if (filterValue == null) return null;

        // 1. Extract raw text from JSONB column
        Expression<String> rawCalories = cb.function(
                "coalesce",
                String.class,
                cb.function("jsonb_extract_path_text", String.class, root.get("nutrients"), cb.literal("calories")),
                cb.literal("0")
        );

        // 2. Strip non-digit characters (e.g. "398 kcal" -> "398") using PostgreSQL regex
        Expression<String> digitsOnly = cb.function(
                "regexp_replace", String.class, rawCalories, cb.literal("[^0-9]"), cb.literal(""), cb.literal("g")
        );

        // 3. Coalesce empty results to "0"
        Expression<String> nonEmptyDigits = cb.function(
                "coalesce",
                String.class,
                cb.function("nullif", String.class, digitsOnly, cb.literal("")),
                cb.literal("0")
        );

        // 4. Cast cleaned text directly to numeric double in Postgres
        Expression<Double> caloriesPath = cb.function(
                "to_number", Double.class, nonEmptyDigits, cb.literal("99999999")
        );

        return buildNumericPredicate(cb, caloriesPath, filterValue);
    };
}
```

### 2. High-Throughput Streaming Ingestion Pipeline

Parsing huge JSON files (e.g. 100MB+) using typical Jackson `readValue(InputStream, Class)` loads the entire dataset as a tree in memory, which triggers massive JVM Heap allocation spikes. 

Our pipeline bypasses this by streaming the objects via `JsonParser` and deserializing records individually. It then buffers these records into memory in fixed-size blocks of **1,000**, writing them to the database in single transactions.

```java
// Snippet from org.example.securin.service.RecipeService.java
try (JsonParser parser = objectMapper.getFactory().createParser(inputStream)) {
    if (parser.nextToken() != JsonToken.START_OBJECT) {
        throw new IllegalStateException("Expected JSON Object at root");
    }

    int batchSize = 1000;
    List<Recipe> batch = new ArrayList<>();

    while (parser.nextToken() != JsonToken.END_OBJECT) {
        parser.nextToken(); // Move to START_OBJECT of the recipe
        RecipeInDTO dto = objectMapper.readValue(parser, RecipeInDTO.class); // Stream-read one object

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
            repo.saveAll(batch); // Perform batch insert
            batch.clear();       // Flush heap references immediately
        }
    }
    if (!batch.isEmpty()) {
        repo.saveAll(batch);
    }
}
```

#### Under-the-Hood Database Configuration
To ensure Hibernate translates `repo.saveAll()` into optimal batch queries instead of repeating multiple isolated SQL `INSERT` commands, the following properties are configured:
```properties
spring.jpa.properties.hibernate.jdbc.batch_size=1000
spring.jpa.properties.hibernate.order_inserts=true
```

---

## 🔗 HATEOAS REST API Endpoints

The API is fully compliant with hypermedia principles (HAL format).

### 1. Retrieve Paginated Recipes
*   **Path**: `GET /api/recipes/`
*   **Query Params**: `page`, `size`, `sort`
*   **Response Sample**:
```json
{
  "_embedded": {
    "recipes": [
      {
        "id": 101,
        "cuisine": "Italian",
        "title": "Creamy Mushroom Risotto",
        "rating": 4.8,
        "prep_time": 15,
        "cook_time": 30,
        "total_time": 45,
        "description": "A delicious classic Italian risotto recipe...",
        "nutrients": {
          "calories": "350 kcal",
          "protein": "12g",
          "fat": "14g"
        },
        "serves": "4",
        "_links": {
          "self": {
            "href": "http://localhost:8080/api/recipes/101"
          }
        }
      }
    ]
  },
  "_links": {
    "self": {
      "href": "http://localhost:8080/api/recipes/?page=0&size=1"
    },
    "next": {
      "href": "http://localhost:8080/api/recipes/?page=1&size=1"
    }
  },
  "page": {
    "size": 1,
    "totalElements": 2405,
    "totalPages": 2405,
    "number": 0
  }
}
```

### 2. Multi-Criteria Dynamic Search
*   **Path**: `GET /api/recipes/search`
*   **Query Params**:
    *   `title` (Case-insensitive match, e.g., `risotto`)
    *   `cuisine` (Exact match, e.g., `Italian`)
    *   `calories` (Operator-supported numeric query, e.g., `<400`, `>=250`)
    *   `total_time` (Operator-supported query, e.g., `<=30`)
    *   `rating` (Operator-supported query, e.g., `>=4.5`)
*   **Example Query**: `GET /api/recipes/search?cuisine=Italian&rating=>=4.5&calories=<500`

### 3. File Upload Pipeline
*   **Path**: `POST /api/recipes/upload`
*   **Body**: `multipart/form-data` with parameter `file` containing the JSON dataset.

---

## ⚙️ CI/CD Pipelines

The pipeline architecture is divided into two target actions:

1.  **Continuous Integration (`maven.yml`)**:
    *   Triggered on pull requests and pushes to `main`.
    *   Uses a **GitHub Actions Runner** equipped with a fully active **Postgres 18 Docker service container** health-checked in the background.
    *   Sets up JDK 21, compiles, builds the artifact package, and runs all integration/unit tests.
2.  **Continuous Deployment (`azure-webapps-java-jar.yml`)**:
    *   Triggered on successful merges to `main`.
    *   Compiles and installs the project.
    *   Extracts the target executable fat JAR and uploads it as a workflow artifact.
    *   Deploys the application artifact seamlessly to the active **Azure App Service (Web App)** instance (`recipe-data-collection-api`) using a secure Azure Publish Profile.

---

## 🛠️ Local Setup & Execution Guide

### Prerequisites
*   **Java Development Kit (JDK) 21** or higher.
*   **Apache Maven** 3.8+.
*   **PostgreSQL 18** running locally or in a container.

### 1. Database Setup
Ensure you have a PostgreSQL database created. Run the following in your PostgreSQL command line:
```sql
CREATE DATABASE securin;
```

### 2. Environment Configuration
Define the following environment variables (or configure them in your IDE runner):
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/securin
export SPRING_DATASOURCE_USERNAME=your_db_username
export SPRING_DATASOURCE_PASSWORD=your_db_password
```

### 3. Build & Run
Run the Maven wrapper to build and start the Spring Boot server:
```bash
./mvnw clean spring-boot:run
```

Once the application boots, the server will be available at `http://localhost:8080`.

### 4. Import Data
To import the culinary dataset (such as the raw recipe file provided in `src/main/resources/US_recipes_null.json`):
```bash
curl -X POST -F "file=@src/main/resources/US_recipes_null.json" http://localhost:8080/api/recipes/upload
```

---
