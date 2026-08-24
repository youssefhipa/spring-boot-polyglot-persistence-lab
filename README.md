# Polyglot Persistence Lab — Smart E-Commerce API

A Spring Boot service that integrates **six different data stores** behind
one API to practice picking the right database for the right access
pattern: relational, document, key-value cache, wide-column time-series,
graph, and full-text search. Built for a "massively scalable applications"
course lab on polyglot persistence.

## Tech stack

- Java, Spring Boot 3.4
- **PostgreSQL** (`spring-data-jpa`) — core product catalog
- **MongoDB** (`spring-data-mongodb`) — product documents, purchase receipts, aggregations
- **Redis** (`spring-data-redis`) — caching / fast lookups
- **Cassandra** (`spring-data-cassandra`) — time-series sensor readings
- **Neo4j** (`spring-data-neo4j`) — social graph (people, follows, purchases)
- **Elasticsearch** (`spring-data-elasticsearch`) — product search
- Maven

## What it demonstrates

- Wiring and configuring six heterogeneous datastores in a single Spring
  Boot application (`RedisConfig`, `Neo4jConfig`, plus Spring Boot
  auto-configuration for the rest).
- Repository-per-store pattern, each with data access idioms suited to
  that store (JPA repositories, Mongo aggregation pipelines, Cassandra
  partition/clustering keys, Neo4j graph traversals, Elasticsearch queries).
- A "smart product page" endpoint that composes data pulled from multiple
  stores into one response.
- Purchase flow that writes across services (relational product data +
  Mongo receipts + graph purchase edges).

## Key modules

```
controller/  ProductController, MongoProductController, SearchController,
             SensorController, SocialGraphController, PurchaseController,
             SmartProductPageController, DataSeederController, HealthController
service/     ProductService, MongoProductService, ProductSearchService,
             SensorService, SocialGraphService, PurchaseService,
             SmartProductPageService
model/       Product (JPA), and per-store models under mongo/, cassandra/,
             neo4j/, elastic/
repository/  Per-store repositories under mongo/, cassandra/, neo4j/, elastic/
```

## Running locally

Requires local instances (or containers) of PostgreSQL, MongoDB, Redis,
Cassandra, Neo4j, and Elasticsearch matching the connection settings in
`src/main/resources/application.yml`.

```bash
mvn spring-boot:run
```

## Running with Docker

```bash
docker compose up --build
```
