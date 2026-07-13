# Aggregations and Analytics in JettraStoreEngine

JettraStoreEngine supports powerful aggregation pipelines for real-time analytics across your data. This functionality is accessible via the Java Driver, Jettra Shell, and REST API.

## Summary of Aggregation Operators

| Operator | Description |
| :--- | :--- |
| `$match` | Filters documents based on conditions. |
| `$group` | Groups documents by a specified identifier. |
| `$sum` | Calculates the sum of a numeric field. |
| `$avg` | Calculates the average of a numeric field. |
| `$min` | Finds the minimum value in a field. |
| `$max` | Finds the maximum value in a field. |
| `$count` | Counts the number of documents in a stage. |

## Usage via Java Driver

The Java Driver provide two ways to perform aggregations: using the generic `aggregate` method or specialized high-level methods.

### 1. High-Level Aggregation Methods

These methods are available in both `JettraReactiveClient` and `JettraRepository`.

#### Count
```java
// Count all documents in a collection
Long total = client.count("users").await().indefinitely();

// Count with a query
Long activeUsers = repository.count("{status: 'active'}").await().indefinitely();
```

#### Numeric Aggregations (Sum, Avg, Min, Max)
```java
// Calculate sum of sales
Double totalSales = repository.sum("amount").await().indefinitely();

// Calculate average price with a filter
Double avgPrice = repository.avg("price", "{category: 'electronics'}").await().indefinitely();

// Find min/max values
Double minAge = repository.min("age").await().indefinitely();
Double maxAge = repository.max("age").await().indefinitely();
```

### 2. Generic Aggregation Pipelines

For complex logic, you can define a full pipeline.

```java
String pipeline = "[" +
    "{\"$match\": {\"category\": \"sports\"}}," +
    "{\"$group\": {\"_id\": \"$brand\", \"totalInventory\": {\"$sum\": \"$stock\"}}}" +
"]";

List<Object> results = client.aggregate("products", pipeline).await().indefinitely();
```

## Usage via Jettra Shell

The Jettra Shell supports MongoDB-style `aggregate` syntax.

```bash
# Basic sum
mongo db.sales.aggregate([{$group: {_id: null, total: {$sum: '$amount'}}}])

# Average with match
mongo db.users.aggregate([{$match: {city: 'NY'}}, {$group: {_id: null, avgAge: {$avg: '$age'}}}])
```

## REST API Endpoint

`POST /api/v1/document/{collection}/aggregate`

**Body:**
```json
[
  {
    "$group": {
      "_id": "$category",
      "count": { "$count": {} }
    }
  }
]
```
# JettraStoreEngine Architecture Overview

JettraStoreEngine is a high-performance, multi-model, cloud-native database designed for modern workloads. It leverages Multi-Raft groups for consistency and a specialized engine architecture for diverse data types.

## Components

### 1. Placement Driver (PD)
The "brain" of the cluster. It manages:
- Node health and heartbeats.
- Raft group assignments.
- Database and collection metadata.
- Load balancing.

### 2. Jettra Store
The storage layer where data resides. Each store node contains:
- Multiple specialized engines (Document, Graph, Vector, Column).
- Local Raft state machines.
- Versioned storage (jettra-store).

### 3. Jettra Engines
Specialized processing units for different data models:
- **Document Engine**: Handles JSON documents with automatic indexing and versioning.
- **Graph Engine**: Specialized for relationship traversal and graph algorithms.
- **Vector Engine**: Performs high-speed similarity search for AI/ML embeddings.
- **Column Engine**: Optimized for analytical queries (aggregations).

### 4. Jettra Web / UI
The management console provided via a web interface, allowing:
- Monitoring node resources.
- Managing databases and collections.
- Executing SQL and Mongo queries.
- Managing users and roles.

### 5. Jettra Shell
Terminal-based interactive CLI for administrative and data operations.

### 6. Jettra Driver (Java)
Fluent and reactive Java client supporting:
- Repository patterns.
- Annotation-based entity mapping.
- Fluent Query builder.

## Architecture Diagram
![Architecture Diagram](resources/architecture.png)

## Data Replication (Multi-Raft)
JettraStoreEngine shunts data into "groups", each managed by its own Raft instance. This allows horizontal scaling of both reads and writes.
![Raft Groups](resources/raft_groups.png)
# Global Auditing System

JettraStoreEngine includes a built-in auditing system to track all critical operations, ensuring transparency and security in distributed transactions.

## Overview
The `AuditService` provides a centralized or sharded log of all modifications. When a transaction is processed by the `TransactionCoordinator`, every state change (Prepare, Commit, Abort) is automatically logged.

## Features
- **Immutability**: Designed to be stored in specialized append-only Raft groups.
- **Traceability**: Every log entry includes a Transaction ID (`txId`), a unique Log ID, a high-resolution timestamp, and details of the participants.
- **Compliance**: Helps meet regulatory requirements for financial and sensitive industrial data.

## Process Workflow
1. **TX Begin**: Audit logs the initiation of the transaction.
2. **Phase 1 (Prepare)**: Logs the list of participants (Raft Group IDs) that have successfully locked the resources.
3. **Phase 2 (Commit/Abort)**: Logs the final outcome. In case of failure, it logs the reason for the rollback.

## Monitoring Audit Logs
Currently, audit logs are output to the standard log management system (e.g., Graylog, ELK, or CloudWatch via Quarkus Logging).

```bash
# View real-time audit logs in the console
docker logs -f jettra-tx
```

## Java API Integration
The `AuditService` is automatically called by the `TransactionCoordinator`. Developers can also manually log custom business actions:

```java
@Inject AuditService auditor;

// Log a custom security event
auditor.log("N/A", "SECURITY_ALERT_IP_BLOCKED", "IP: 192.168.1.50 Attempted unauthorized access")
       .subscribe().with(v -> {});
```
# Consensus: Multi-Raft Groups

Traditional Raft uses a single cluster for the entire database. JettraStoreEngine uses **Multi-Raft**, which scales by partitioning data into multiple Raft groups.

## How it works

1. **Sharding:** Data is split into ranges (shards).
2. **Raft Groups:** Each shard is managed by a separate Raft group.
3. **Nodes:** A single physical node can participate in hundreds of Raft groups.
4. **Roles:** A node can be a Leader for Group A but a Follower for Group B.

## Advantages

- **Horizontal Scalability:** Add nodes and move Raft groups to balance load.
- **Improved Availability:** Failure of one node only affects the Raft groups where it was a leader.
- **Parallelism:** Commit operations in different groups happen in parallel.

## Implementation Details

The `jettra-consensus` module uses gRPC for inter-node communication. Each message includes a `group_id` to route it to the correct state machine.

```java
// Example of proposing a change to a specific group
raftService.propose(ProposeRequest.newBuilder()
    .setGroupId(targetShardId)
    .setData(payload)
    .build());
```
# Ejemplos de uso con cURL para JettraStoreEngine

Esta guía proporciona ejemplos de comandos `curl` para interactuar con la API de JettraStoreEngine, específicamente para la monitorización, autenticación y administración de bases de datos.

## Autenticación (Requerido)

Todas las peticiones a la API requieren un token JWT válido. Primero debes autenticarte para obtener el token que usarás en las cabeceras `Authorization`.

```bash
# 1. Login para obtener el token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"super-user","password":"superuser-jettra"}' | jq -r .token)

echo "Token: $TOKEN"
```

## Monitorización de Nodos y Recursos

Para obtener la lista de nodos registrados y su consumo de recursos actual (CPU y Memoria), consulta el endpoint `/api/monitor/nodes` en el puerto 8081.

```bash
curl -s http://localhost:8081/api/monitor/nodes \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Respuesta esperada (JSON):**

```json
[
  {
    "id": "jettra-store-1",
    "address": "172.18.0.3:8080",
    "role": "STORAGE",
    "status": "ONLINE",
    "raftRole": "LEADER",
    "lastSeen": 1709923456789,
    "cpuUsage": 15.4,
    "memoryUsage": 245678912,
    "memoryMax": 1073741824
  },
  {
    "id": "jettra-store-2",
    "address": "172.18.0.4:8080",
    "role": "STORAGE",
    "status": "ONLINE",
    "raftRole": "FOLLOWER",
    "lastSeen": 1709923456790,
    "cpuUsage": 8.2,
    "memoryUsage": 198234567,
    "memoryMax": 1073741824
  }
]
```


## Administración de Bases de Datos

Gestión de bases de datos indicando el nombre y el tipo de almacenamiento (`storage`). Por defecto, todas las bases de datos son **Multi-modelo**.
### Change Password
```bash
curl -X POST http://localhost:8080/api/auth/change-password \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "bob",
    "oldPassword": "password123",
    "newPassword": "newPassword456"
  }'
```

### Listar Bases de Datos
```bash
curl -s http://localhost:8081/api/db \
  -H "Authorization: Bearer $TOKEN"
```

### Crear Base de Datos (Persistent Multi-Model)
```bash
curl -X POST http://localhost:8081/api/db \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "sales_db", "storage": "STORE"}'
```

### Crear Colección (Document)
```bash
curl -X POST http://localhost:8081/api/db/sales_db/collections/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"engine": "Document"}'
```

### Crear Base de Datos (In-Memory Multi-Model)
```bash
curl -X POST http://localhost:8081/api/db \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "user_graph", "storage": "MEMORY"}'
```

### Multi-Raft Groups Information (Nuevo) ⭐
To view information about the Multi-Raft groups in the cluster:

```bash
curl -s http://localhost:8081/api/internal/pd/groups \
  -H "Authorization: Bearer $TOKEN"
```

### Renombrar Base de Datos
```bash
curl -X PUT http://localhost:8081/api/db/sales_db \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "sales_v2"}'
```

### Eliminar Base de Datos
```bash
curl -X DELETE http://localhost:8081/api/db/sales_db \
  -H "Authorization: Bearer $TOKEN"
```

### Consultar Información de la Base de Datos
Obtiene los metadatos y la configuración de una base de datos específica.

```bash
curl -s http://localhost:8081/api/db/sales_db \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

## Gestión de Colecciones
Operaciones para gestionar colecciones dentro de una base de datos específica.

### Listar Colecciones
```bash
curl -s http://localhost:8081/api/db/sales_db/collections \
  -H "Authorization: Bearer $TOKEN"
```

### Añadir Colección
```bash
curl -X POST http://localhost:8081/api/db/sales_db/collections/users \
  -H "Authorization: Bearer $TOKEN"
```

### Renombrar Colección
```bash
curl -X PUT http://localhost:8081/api/db/sales_db/collections/users/customers \
  -H "Authorization: Bearer $TOKEN"
```

### Eliminar Colección
```bash
curl -X DELETE http://localhost:8081/api/db/sales_db/collections/customers \
  -H "Authorization: Bearer $TOKEN"
```

## Registro Manual de Nodos (Interno)

Para registrar manualmente un nodo con información de recursos inicial:

**Endpoint:** `POST http://localhost:8080/api/internal/pd/register`

```bash
curl -X POST http://localhost:8080/api/internal/pd/register \
  -H "Content-Type: application/json" \
  -d '{
    "id": "manual-node-1",
    "address": "192.168.1.50:8080",
    "role": "STORAGE",
    "status": "ONLINE",
    "lastSeen": 0,
    "cpuUsage": 0.0,
    "memoryUsage": 0,
    "memoryMax": 0
  }'
```

### Index Management 🔍

#### Create Index
```bash
curl -X POST http://localhost:8080/api/internal/pd/databases/myDB/collections/myCollection/indexes \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "field": "name",
    "type": "text"
  }'
```

#### List Indexes
```bash
curl -G http://localhost:8080/api/internal/pd/databases/myDB/collections/myCollection/indexes \
  -H "Authorization: Bearer $TOKEN"
```

#### Delete Index
```bash
curl -X DELETE http://localhost:8080/api/internal/pd/databases/myDB/collections/myCollection/indexes/myCollection_name_text \
  -H "Authorization: Bearer $TOKEN"
```

### Aggregations (Analytics) 📊

You can perform aggregations using SQL or the internal analytics engine endpoints.

```bash
# Example: Sum of amount
curl -X POST http://localhost:8081/api/v1/sql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT SUM(amount) FROM sales_db.orders"}'
```
Wait, the user asked for Mongo-like aggregation support. The shell translates it. Here we can document how to use the SQL endpoint or similar.
Actually, let's add a "Mongo-like Operations via cURL" section below.

## Monitoring Node Resources

## Monitoring Node Resources

You can monitor the resource usage (CPU, Memory) of all registered nodes:

```bash
# 1. Login to get token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin", "password":"superuser-jettra"}' | jq -r .token)

# 2. Get Node Metrics (Request to Web Dashboard on port 8081)
curl -s http://localhost:8081/api/monitor/nodes \
  -H "Authorization: Bearer $TOKEN" | jq .
```
Response:
```json
[
  {
    "id": "jettra-store-1",
    "address": "jettra-store-1:8080",
    "role": "STORAGE",top
    "status": "ONLINE",
    "lastSeen": 1704321000,
    "cpuUsage": 12.5,
    "memoryUsage": 104857600,
    "memoryMax": 4294967296
  }
]
```

> [!IMPORTANT]
> La detención de nodos está restringida a usuarios con rol **admin**.

## Detener un Nodo

Para detener un nodo de forma segura, puedes usar el proxy de monitorización (puerto 8081) o directamente el PD (puerto 8080).

### Vía Proxy (Recomendado)
```bash
curl -X POST http://localhost:8081/api/monitor/nodes/jettra-store-3/stop \
  -H "Authorization: Bearer $TOKEN"
```

### Vía Placement Driver (Directo)
```bash
curl -X POST http://localhost:8080/api/internal/pd/nodes/jettra-store-3/stop \
  -H "Authorization: Bearer $TOKEN"
```

### Invocación Directa al Nodo (Nuevo) ⭐
Ahora cada nodo (PD, Store, Web) expone un endpoint `/stop` directo en la raíz para facilitar su detención.

```bash
# Detener Placement Driver
curl -X POST http://localhost:8080/stop -H "Authorization: Bearer $TOKEN"

# Detener Jettra Store
curl -X POST http://localhost:8082/stop -H "Authorization: Bearer $TOKEN"

# Detener Jettra Web
curl -X POST http://localhost:8081/stop -H "Authorization: Bearer $TOKEN"
```

## Gestión de Usuarios y Roles ⭐

> [!IMPORTANT]
> La gestión de usuarios y roles está restringida exclusivamente a usuarios con rol **admin**.

### 1. Crear un Rol
Define permisos para una base de datos específica o para todas (`_all`).
**Nota:** Se recomienda seguir la convención de nombres: `<tipo>_<base_de_datos>` (ej: `read_sales_db`, `read-write_logs`).

```bash
curl -X POST http://localhost:8081/api/web-auth/roles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "read_sales_db",
    "database": "sales_db",
    "privileges": ["READ"]
  }'
```

### 2. Crear un Usuario
Asigna uno o varios roles al usuario.
JettraStoreEngine soporta 4 tipos de roles principales:
1. **super-user**: Solo para `admin`.
2. **admin**: Administrador de DB.
3. **read**: Solo lectura.
4. **read-write**: Lectura y Escritura.

```bash
curl -X POST http://localhost:8081/api/web-auth/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "bob",
    "password": "password123",
    "roles": ["read_sales_db", "read_all"],
    "forcePasswordChange": false
  }'
```

### 3. Editar un Usuario
Actualiza los roles o la contraseña de un usuario existente.

```bash
curl -X PUT http://localhost:8081/api/web-auth/users/bob \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "bob",
    "password": "newpassword456",
    "roles": ["admin_all"]
  }'
```

### 4. Listar Usuarios y Roles
```bash
# Listar todos los usuarios
curl -s http://localhost:8081/api/web-auth/users -H "Authorization: Bearer $TOKEN"

# Listar todos los roles
curl -s http://localhost:8081/api/web-auth/roles -H "Authorization: Bearer $TOKEN"
```

## Ejemplo Completo: Base de Datos y Colección (Document)
Este ejemplo muestra el flujo completo para crear una base de datos persistente y una colección usando el motor **Document** vía cURL.

```bash
# 1. Crear la base de datos (Persistent Multi-Model)
curl -X POST http://localhost:8081/api/db \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "mi_base_datos", "storage": "STORE"}'

# 2. Crear la colección con el motor Document
curl -X POST http://localhost:8081/api/db/mi_base_datos/collections/usuarios \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"engine": "Document"}'

## Operaciones de Documentos (Document Engine) ⭐

Una vez creada la colección con motor `Document`, puedes interactuar directamente con los documentos.

### 1. Guardar un Documento (Save / Upsert)
Si no se proporciona `jettraID`, el sistema lo generará automáticamente.
Nota: No se permiten documentos vacíos ni JSON vacíos `{}`.

```bash
# Se requiere hablar directamente con un nodo STORAGE (ej: puerto 8082)
curl -X POST http://localhost:8082/api/v1/document/usuarios \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nombre": "Juan", "email": "juan@example.com", "_tags": ["premium"]}'
```

### 2. Recuperar un Documento por ID
```bash
# Reemplazar {jettraID} con el ID generado (ej: node1/default#uuid)
curl -s http://localhost:8082/api/v1/document/usuarios/{jettraID} \
  -H "Authorization: Bearer $TOKEN"
```

### 3. Ver Historial de Versiones
Para ver todas las versiones guardadas de un documento:

```bash
curl -s http://localhost:8082/api/v1/document/usuarios/{jettraID}/versions \
  -H "Authorization: Bearer $TOKEN"
```

### 4. Búsqueda por Etiquetas (Tags)
```bash
curl -s "http://localhost:8082/api/v1/document/usuarios/search/tag?tag=premium" \
  -H "Authorization: Bearer $TOKEN"
```

  -H "Authorization: Bearer $TOKEN"
```

## Mongo-like Operations (New) 🍃

Perform operations using MongoDB-like syntax. Note that currently `curl` interacts with the underlying HTTP APIs, so we map these operations to the Document API.

### insertOne / insertMany
To insert documents, perform a POST to the document endpoint.
```bash
# insertOne
curl -X POST http://localhost:8082/api/v1/document/usuarios \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice"}'

# insertMany (Batch insert is supported by sending a JSON Array)
curl -X POST http://localhost:8082/api/v1/document/usuarios/batch \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '[{"name": "Bob"}, {"name": "Charlie"}]'
```

### replaceOne / replaceMany
To replace (update) documents.
```bash
# replaceOne (Update by ID)
curl -X POST http://localhost:8082/api/v1/document/usuarios/{id} \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice Updated"}'
```

### deleteOne / deleteMany
```bash
# deleteOne (Delete by ID)
curl -X DELETE http://localhost:8082/api/v1/document/usuarios/{id} \
  -H "Authorization: Bearer $TOKEN"
```

### Aggregations (Mongo-Style) 📊
JettraStoreEngine supports complex aggregation pipelines via the Document API.

```bash
# General Aggregation Pipeline
curl -X POST http://localhost:8082/api/v1/document/usuarios/aggregate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '[
     {"$match": {"age": {"$gt": 20}}},
     {"$group": {"_id": "$city", "total": {"$sum": "$age"}}}
  ]'
```

#### Analytical Shortcuts (via SQL)
For high-level analytic functions, use the SQL endpoint:
```bash
curl -X POST http://localhost:8081/api/v1/sql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT AVG(age) FROM usuarios"}'
```

## Query Builder (Manual Guide) 🛠️
To construct a complex query via curl without an interactive tool:

1. **Identify Entpoint**: Decide if you are querying Documents (GET /api/v1/document) or using SQL (POST /api/v1/sql).
2. **Build JSON**:
   - For SQL: `{"sql": "SELECT * FROM col WHERE field='value'"}`
   - For Document Search: Use query params `?tag=label` or `?id=...`
3. **Execute**:
   ```bash
   curl -X POST http://localhost:8081/api/v1/sql -d '{"sql": "..."}' ...
   ```

## Consultas SQL (Nuevo) ⭐

JettraStoreEngine ahora soporta un subconjunto del lenguaje SQL para operar sobre los diversos motores. Las consultas se envían al Placement Driver (puerto 8081).

### Ejecutar una consulta SQL
Soporta `SELECT`, `INSERT`, `UPDATE` y `DELETE`.

```bash
# 1. SELECT (Obtener todos los documentos de una colección)
curl -X POST http://localhost:8081/api/v1/sql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT * FROM sales_db.orders"}'

# 2. INSERT (Insertar un nuevo documento)
curl -X POST http://localhost:8081/api/v1/sql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sql": "INSERT INTO sales_db.orders VALUES ('order123', 'Laptop', 1200)"}'

# 3. UPDATE (Actualizar un documento por su ID)
curl -X POST http://localhost:8081/api/v1/sql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sql": "UPDATE sales_db.orders SET precio=1300 WHERE id='order123'"}'

# 4. DELETE (Eliminar un documento por su ID)
curl -X POST http://localhost:8081/api/v1/sql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sql": "DELETE FROM sales_db.orders WHERE id='order123'"}'
```

## Llaves Secuenciales (Sequences) ⭐

Permite crear y gestionar contadores persistentes en el cluster.

```bash
# 1. Crear secuencia
curl -X POST http://localhost:8081/api/v1/sequence \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "test_seq", "database": "db1", "startValue": 100, "increment": 1}'

# 2. Obtener siguiente valor
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/sequence/test_seq/next

# 3. Obtener valor actual
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/sequence/test_seq/current

# 4. Reiniciar secuencia
curl -X POST http://localhost:8081/api/v1/sequence/test_seq/reset \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"newValue": 500}'

# 5. Eliminar secuencia
curl -X DELETE http://localhost:8081/api/v1/sequence/test_seq -H "Authorization: Bearer $TOKEN"
```
## Resolución de Referencias (Resolve References) ⭐

Esta característica permite que JettraStoreEngine resuelva automáticamente las referencias entre documentos basadas en `jettraID` en una sola operación de lectura. Utiliza **acceso directo a la memoria** donde se encuentra el registro referenciado, evitando el uso de JOINS costosos y haciendo la búsqueda mucho más eficiente. Devolviendo el objeto completo en lugar de solo el ID.

### 1. Vía API de Documentos
Agrega el parámetro `resolveRefs=true` a la URL de consulta.

```bash
# Obtener un documento resolviendo sus referencias internas
curl -s "http://localhost:8082/api/v1/document/usuarios/{jettraID}?resolveRefs=true" \
  -H "Authorization: Bearer $TOKEN"

# Listar documentos con resolución
curl -s "http://localhost:8082/api/v1/document/usuarios?resolveRefs=true" \
  -H "Authorization: Bearer $TOKEN"

# Respuesta esperada (Ejemplo):
# {
#   "id": "node1/def#uuid1",
#   "name": "Aris",
#   "pais": {
#     "jettraID": "node1/def#uuid2",
#     "name": "Panama",
#     "codigo": "PA"
#   }
# }

```

### 2. Vía API SQL
Agrega el campo `resolveRefs: true` en el cuerpo del JSON.

```bash
curl -X POST http://localhost:8081/api/v1/sql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT * FROM sales_db.orders",
    "resolveRefs": true
  }'
```

---
Este manual se actualiza periódicamente con las nuevas funciones del núcleo de JettraStoreEngine.
# Cloud Native Deployment

JettraStoreEngine is designed for modern cloud environments. This guide explains how to deploy a full cluster using Docker and Kubernetes.

## Docker Compose Deployment

The root of the project contains a `docker-compose.yaml` file that sets up:
1.  **Placement Driver (PD)**: The central coordinator.
2.  **3 Storage Nodes**: Form the base for Multi-Raft groups.
3.  **Web Dashboard**: Visual interface for management.

### Prerequisites
- Docker and Docker Compose installed.
- Maven (to build the artifacts).

### Step 1: Build the Project
Before running Docker, you must build the JAR files:
```bash
mvn clean package -DskipTests
```

### Step 2: Launch the Cluster
```bash
docker-compose up --build
```

### Step 3: Access the UI
Open your browser at `http://localhost:8081` to see the JettraStoreEngine Dashboard.

## Scaling the Cluster
To add more storage capacity or increase Raft group parallelism, you can scale the store service:

```bash
docker-compose up -d --scale jettra-store=5
```

## Kubernetes Deployment

In a production environment, JettraStoreEngine should be deployed using Kubernetes to ensure high availability and scalability. You can use StatefulSets to maintain consistent identity and persistent volumes for the nodes.

### Guía Paso a Paso: Instalación de Kubernetes en Ubuntu 26.04

Esta guía muestra cómo preparar un clúster Kubernetes ligero usando `k3s` (ideal para JettraStoreEngine en Edge o Cloud) en Ubuntu 26.04.

#### Paso 1: Actualizar el Sistema
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install curl -y
```

#### Paso 2: Instalar K3s (Master Node)
K3s es una distribución certificada y ultraligera de Kubernetes.
```bash
curl -sfL https://get.k3s.io | sh -
```

#### Paso 3: Verificar el Clúster
Espera unos segundos a que el nodo esté listo y verifica con:
```bash
sudo k3s kubectl get nodes
```

#### Paso 4: Configurar kubeconfig para el usuario actual
Para poder usar `kubectl` sin `sudo`:
```bash
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $(id -u):$(id -g) ~/.kube/config
echo "export KUBECONFIG=~/.kube/config" >> ~/.bashrc
source ~/.bashrc
```

#### Paso 5: Desplegar JettraStoreEngine (Ejemplo con StatefulSet)
Crea un archivo `jettra-statefulset.yaml`:
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: jettra-node
spec:
  serviceName: "jettra-service"
  replicas: 3
  selector:
    matchLabels:
      app: jettra-store
  template:
    metadata:
      labels:
        app: jettra-store
    spec:
      containers:
      - name: jettra-engine
        image: jettradb/jettra-store-engine:latest
        ports:
        - containerPort: 8080
          name: rest
        - containerPort: 50051
          name: grpc
        volumeMounts:
        - name: data
          mountPath: /data
        - name: config
          mountPath: /app/jettrastoreengine.properties
          subPath: jettrastoreengine.properties
      volumes:
      - name: config
        configMap:
          name: jettra-config
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 10Gi
```
Aplica el archivo:
```bash
kubectl apply -f jettra-statefulset.yaml
```

Con esto tendrás 3 nodos corriendo en Kubernetes sincronizándose mediante Raft a través de gRPC en el puerto 50051.

## Resource Optimization
- **Memory**: Tuning the Java heap and ZGC sizes via `JAVA_OPTS`.
- **Network**: Using gRPC (HTTP/2) for inter-node communication minimizes latency in cloud VPCs.
# JettraStoreEngine Docker Deployment Guide

This guide explains how to deploy the JettraStoreEngine stack using Docker and Docker Compose. 

## Core Components

The JettraStoreEngine architecture consists of a symmetric cluster where nodes communicate via gRPC for consensus (Raft).

## Docker Compose Walkthrough

The following `docker-compose.yaml` defines a cluster with three nodes. We mount local `properties` files instead of using environment variables to configure the nodes.

```yaml
version: '3.8'

services:
  jettra-node1:
    build: .
    container_name: jettra_node1
    volumes:
      - ./config/node1.properties:/app/jettrastoreengine.properties
      - jettra_data1:/data
    ports:
      - "8080:8080"
      - "50051:50051"
    networks:
      - jettra_net
    restart: unless-stopped

  jettra-node2:
    build: .
    container_name: jettra_node2
    volumes:
      - ./config/node2.properties:/app/jettrastoreengine.properties
      - jettra_data2:/data
    ports:
      - "8081:8081"
      - "50052:50052"
    networks:
      - jettra_net
    restart: unless-stopped

  jettra-node3:
    build: .
    container_name: jettra_node3
    volumes:
      - ./config/node3.properties:/app/jettrastoreengine.properties
      - jettra_data3:/data
    ports:
      - "8082:8082"
      - "50053:50053"
    networks:
      - jettra_net
    restart: unless-stopped

volumes:
  jettra_data1:
  jettra_data2:
  jettra_data3:

networks:
  jettra_net:
    driver: bridge
```

### Network Configuration
All components communicate over an internal bridge network named `jettra_net`.

## Troubleshooting

- **Connection Errors**: Ensure all nodes are on the same Docker network.
- **Port Conflicts**: Use `docker ps` to verify no other services are using the assigned ports.
- **Logs**: Use `docker logs -f <container_name>` to debug startup issues.
# JettraStoreEngine Java Driver Guide

The JettraStoreEngine Java Driver allows seamless integration with JettraStoreEngine clusters using reactive programming principles (Mutiny).

## Maven Dependency

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.jettra</groupId>
    <artifactId>jettra-driver-java</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Usage

### 1. Initialization
Initialize the client with the Placement Driver (PD) address and your Authentication Token.

```java
import io.jettra.driver.JettraReactiveClient;

// ...
String pdAddress = "localhost:8081"; 
String authToken = "eyJh... (your JWT token)"; // Obtain via Auth API

JettraReactiveClient client = new JettraReactiveClient(pdAddress, authToken);
```

### 2. Authentication Flow
To obtain a token programmatically, you can use the Auth API:

```java
// Example: Obtaining token via curl or dedicated AuthClient (if available)
// Use this token for all JettraReactiveClient operations.
```

### 3. Database Management
Create and manage multi-model databases, specifying the **Engine** and **Storage** style.

```java
// Create a new persistent Multi-Model database
client.createDatabase("sales_db", "STORE").await().indefinitely();

// Create a new in-memory Multi-Model database 
client.createDatabase("social_net", "MEMORY").await().indefinitely();

// Rename a database
client.renameDatabase("sales_db", "sales_v2").await().indefinitely();

// List databases
List<String> dbs = client.listDatabases().await().indefinitely();
System.out.println("Databases: " + dbs);

// Delete a database
client.deleteDatabase("sales_v2").await().indefinitely();

// Get detailed database metadata (returns JSON String)
String info = client.getDatabaseInfo("sales_v2").await().indefinitely();
System.out.println("Metadata: " + info);
```

### 4. Collection Management
Manage collections within a database.

```java
// List collections in a database
List<String> collections = client.listCollections("retail_db").await().indefinitely();

// Add a new collection (Engine: Document)
client.addCollection("retail_db", "orders", "Document").await().indefinitely();

// Add a new Graph collection
client.addCollection("retail_db", "friends", "Graph").await().indefinitely();

// Add a new Vector collection
client.addCollection("retail_db", "embeddings", "Vector").await().indefinitely();

// Rename a collection
client.renameCollection("retail_db", "orders", "customer_orders").await().indefinitely();

// Remove a collection
client.removeCollection("retail_db", "customer_orders").await().indefinitely();
```

### 5. Cluster Monitoring ⭐
Monitor node resource consumption (CPU, Memory) directly via the driver.

```java
import io.jettra.driver.NodeInfo;

// 1. List all cluster nodes
Uni<List<NodeInfo>> nodesUni = client.listNodes();
List<NodeInfo> nodes = nodesUni.await().indefinitely();

// 2. Iterate and display resource metrics
for (NodeInfo node : nodes) {
    double memUsedMB = node.memoryUsage() / 1024.0 / 1024.0;
    double memMaxMB = node.memoryMax() / 1024.0 / 1024.0;
    
    System.out.println("Node ID: " + node.id());
    System.out.println(" - Role: " + node.role() + " (" + node.raftRole() + ")");
    System.out.println(" - Status: " + node.status());
    System.out.println(" - CPU Usage: " + String.format("%.1f %%", node.cpuUsage()));
    System.out.println(" - Memory: " + String.format("%.1f / %.1f MB", memUsedMB, memMaxMB));
    System.out.println("-----------------------------------");
}

// 3. Stop a specific node (Sends a remote stop request via PD)
// NOTE: Only allowed for users with 'admin' role.
client.stopNode("jettra-store-2").await().indefinitely();

// Check Connection Info
System.out.println(client.connectionInfo());
```


### 6. User & Role Management ⭐
Manage cluster access control. 

> [!IMPORTANT]
> These methods are restricted to users with the **admin** role.

```java
// Create a role for a specific database
// Create a role for a specific database using standard prefixes
client.createRole("read_db1", "db1", Set.of("READ"))
      .await().indefinitely();

// Create a user
client.createUser("bob", "password123", "bob@example.com", Set.of("reader")).await().indefinitely();

// Update a user
client.updateUser("bob", "newpass", "bob_new@example.com", Set.of("writer")).await().indefinitely();

// Change password
client.changePassword("bob", "oldpass", "newpass").await().indefinitely();

// List users
List<User> users = client.listUsers().await().indefinitely();

// Delete a user
client.deleteUser("bob").await().indefinitely();
```

### 7. Document Operations (Document Engine) ⭐

Operaciones específicas para el motor de documentos, incluyendo versionamiento y gestión de IDs físicos.

```java
// 1. Generar un jettraID basado en la ubicación física (bucket)
// Formato: nodeId/bucketName#uuid
String jettraId = client.generateJettraId("node1/main-bucket").await().indefinitely();

// 2. Guardar un documento (JSON String o POJO)
// Si el documento ya existe, se crea una nueva versión automáticamente.
// Nota: No se permiten documentos vacíos ni JSON vacíos "{}".
String json = "{\"nombre\": \"Alice\", \"_tags\": [\"vip\", \"2024\"]}";
client.save("usuarios", jettraId, json).await().indefinitely();

// 3. Recuperar la versión actual por jettraID
Object doc = client.findById("usuarios", jettraId).await().indefinitely();
System.out.println("Documento: " + doc);

// 4. Listar historial de versiones
List<String> versiones = client.getDocumentVersions("usuarios", jettraId).await().indefinitely();
System.out.println("Versiones: " + versiones);

// 5. Resolver una referencia (Document Linking)
// Útil para navegar entre documentos vinculados físicamente.
Object related = client.resolveReference("pedidos", "node2/orders#ref123").await().indefinitely();

// 6. Restaurar una versión
client.restoreVersion("usuarios", jettraId, "1").await().indefinitely();

// 7. Eliminar un documento
client.delete("usuarios", jettraId).await().indefinitely();
```

### 8. Consultas SQL (Nuevo) ⭐

Soporta la ejecución de sentencias SQL (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) directamente. Las consultas son procesadas por el Placement Driver y enrutadas al motor correspondiente.

```java
// Ejecutar una consulta SQL
String result = client.executeSql("SELECT * FROM sales_db.orders").await().indefinitely();
System.out.println("Resultados: " + result);

// Insertar vía SQL
client.executeSql("INSERT INTO sales_db.orders VALUES ('order123', 'Laptop', 1200)").await().indefinitely();

// Actualizar vía SQL
client.executeSql("UPDATE sales_db.orders SET precio=1300 WHERE id='order123'").await().indefinitely();

// Eliminar vía SQL
client.executeSql("DELETE FROM sales_db.orders WHERE id='order123'").await().indefinitely();
```

### 9. Llaves Secuenciales (Sequences) ⭐

Gestión de contadores persistentes y secuenciales.

```java
// 1. Crear secuencia
client.createSequence("user_id_seq", "sales_db", 1000, 1).await().indefinitely();

// 2. Obtener siguiente valor
long nextId = client.nextSequenceValue("user_id_seq").await().indefinitely();

// 3. Obtener valor actual
long currentId = client.currentSequenceValue("user_id_seq").await().indefinitely();

// 4. Listar todas las secuencias de una base de datos
List<String> names = client.listSequences("sales_db").await().indefinitely();

// 5. Reiniciar secuencia
client.resetSequence("user_id_seq", 2000).await().indefinitely();

// 6. Eliminar secuencia
client.deleteSequence("user_id_seq").await().indefinitely();
```

### 10. Resolución de Referencias (Resolve References) ⭐

Permite obtener objetos referenciados completos de manera automática.

```java
// 1. Usando findById con resolución activa
// Devuelve el documento con todos sus JettraID internos resueltos a objetos completos
String jsonResult = (String) client.findById("usuarios", "node1/def#uuid1", true).await().indefinitely();

// 2. Usando SQL con resolución activa 
String sqlResult = client.executeSql("SELECT * FROM users", true).await().indefinitely();
```

### MongoDB-like Operations (Experimental)

```java
// Insert One
client.insertOne("myCollection", "{\"name\": \"Jettra\"}").await().indefinitely();

// Insert Many
client.insertMany("myCollection", List.of("{\"id\": 1}", "{\"id\": 2}")).await().indefinitely();

// Delete One
client.deleteOne("myCollection", "{\"id\": 1}").await().indefinitely();

// Delete Many
client.deleteMany("myCollection", "{\"status\": \"old\"}").await().indefinitely();

// Replace One
client.replaceOne("myCollection", "{\"id\": 1}", "{\"id\": 1, \"new\": true}").await().indefinitely();

### Aggregations & Analytics ⭐

Support for MongoDB-style aggregation pipelines and high-level analytical functions. See the [Aggregations Guide](aggregations.md) for more details.

```java
// 1. High-level methods
Long total = client.count("sales").await().indefinitely();
Double totalAmount = client.sum("sales", "amount", "{status: 'paid'}").await().indefinitely();
Double avgAge = client.avg("users", "age", "{}").await().indefinitely();

// 2. Generic Pipelines
String pipeline = "[{\"$match\": {status: 'active'}}, {\"$group\": {_id: '$city', count: {\"$count\": {}}}}]";
List<Object> results = client.aggregate("users", pipeline).await().indefinitely();
```
```

### Index Management

```java
// Create Index
client.createIndex("myDB", "myCollection", "fieldName", "text").await().indefinitely();

// List Indexes
List<IndexMetadata> indexes = client.listIndexes("myDB", "myCollection").await().indefinitely();

// Delete Index
client.deleteIndex("myDB", "myCollection", "indexName").await().indefinitely();
```

### QueryBuilder

The `QueryBuilder` provides a fluent API for constructing complex queries.

```java
String query = QueryBuilder.start()
    .eq("status", "active")
    .gt("age", 25)
    .build();
```

---
The driver uses `Mutiny` (Uni/Multi) for non-blocking I/O.

## Ejemplo Completo: Base de Datos y Colección (Document)
Este ejemplo muestra el flujo completo para crear una base de datos persistente y una colección usando el motor **Document**.

```java
import io.jettra.driver.JettraReactiveClient;
import java.util.List;

public class DocumentExample {
    public static void main(String[] args) {
        String pdAddress = "localhost:8081";
        String token = "YOUR_TOKEN";
        
        JettraReactiveClient client = new JettraReactiveClient(pdAddress, token);

        // 1. Crear una base de datos persistente Multi-Modelo
        client.createDatabase("mi_base_datos", "STORE")
              .await().indefinitely();

        // 2. Añadir una colección con engine = "Document"
        client.addCollection("mi_base_datos", "usuarios", "Document")
              .await().indefinitely();

        System.out.println("Base de datos y Colección (Document) creadas con éxito!");
    }
}
```
# Column Engine: Analytics at Scale

El motor columnar de JettraStoreEngine está optimizado para cargas de trabajo analíticas (OLAP). Almacena los datos por columnas en lugar de por filas, lo que permite escaneos extremadamente rápidos y un uso eficiente de la caché de la CPU.

## Especificaciones Técnicas
- **Clase Principal**: `io.jettra.engine.column.ColumnEngine`
- **Optimizaciones**: Proyección de columnas (sólo se leen los campos necesarios) y agregaciones vectorizadas.
- **Consumo**: Minimiza el I/O al ignorar columnas que no forman parte de la consulta.

## Operaciones de Alta Performance

### 1. Inserción de Filas
Aunque el almacenamiento es columnar, la API permite la inserción de filas completas de forma atómica.

```java
Map<String, Object> telemetry = Map.of(
    "temp", 22.5,
    "humidity", 60,
    "site", "Madrid-01"
);
engine.insert(telemetry).subscribe().with(v -> {});
```

### 2. Agregaciones Vectorizadas (SUM)
Ideal para dashboards financieros o de monitoreo IoT.

```java
engine.sum("temp")
      .subscribe().with(avg -> System.out.println("Temperatura Total: " + avg));
```

### 3. Proyección de Columnas (Select)
Recupera sólo los datos necesarios para reducir el tráfico de red y memoria.

```java
engine.project(List.of("site", "temp"))
      .subscribe().with(results -> results.forEach(System.out::println));
```

## Arquitectura Interna
Los datos se organizan en bloques columnares en memoria (`StorageMap`) y se persisten de forma asíncrona mediante el motor LSM de `jettra-store`, asegurando durabilidad sin sacrificar la velocidad de análisis.
# Document Engine: Optimized Persistence & Indexing

El motor de documentos de JettraStoreEngine está diseñado para manejar esquemas flexibles con un rendimiento de lectura cercano a la memoria gracias a su caché integrada y su sistema de índices optimizado.

## Especificaciones Técnicas
- **Clase Principal**: `io.jettra.engine.document.DocumentEngine`
- **Consumo de Memoria**: Eficiente mediante el uso de `ConcurrentHashMap` para MemTables y gestión de punteros.
- **Escalabilidad**: Particionado automático mediante Multi-Raft Groups.

## Especificaciones Avanzados
- **jettraID**: Identificador único que incluye la dirección física del bucket (`nodeId/bucketName#uuid`). Las referencias entre documentos utilizan esta dirección física.
- **Versiones**: Gestión automática de cambios. Cada actualización archiva la versión anterior permitiendo trazabilidad total.
- **Enriquecimiento JSON**: Soporte nativo para etiquetas (tags) dentro del campo `_tags` para búsquedas rápidas.
- **Documentos Embebidos y Referenciados**: Soporta nativamente objetos anidados y referencias cruzadas mediante etiquetas y `jettraID`.

## Operaciones de Alta Performance

### 1. Inserción con Versionamiento
Las escrituras generan automáticamente una nueva versión si el documento ya existe.

```java
// El motor asigna un jettraID basado en el bucket físico
String jettraId = engine.generateJettraId("node1/bucketA");
engine.save("users", jettraId, "{\"name\":\"Alice\",\"_tags\":[\"vip\"]}");
```

### 2. Resolución de Referencias
Permite obtener documentos vinculados mediante su `jettraID` físico.

```java
engine.resolveReference("orders", "node1/bucketB#order123")
      .subscribe().with(order -> LOG.info("Pedido recuperado"));
```

### 3. Consultas por Etiquetas
Búsqueda optimizada sobre el JSON enriquecido.

```java
engine.findByTag("users", "vip")
      .subscribe().with(doc -> System.out.println("VIP: " + doc));
```

## Optimización Multi-Model
Al estar integrado en `jettra-engine`, los documentos pueden contener referencias (`jettraID`) a objetos en otros motores, manteniendo la consistencia de ubicación incluso si las direcciones IP cambian, ya que los buckets permanecen constantes.


## Integración y Acceso

El motor de documentos se expone a través de múltiples interfaces en el stack JettraStoreEngine.

### 1. API REST (Store API)
Los nodos de almacenamiento exponen endpoints específicos para el motor de documentos:
- `POST /api/v1/document/{collection}`: Guardar/Upsert.
- `GET /api/v1/document/{collection}/{jettraId}`: Recuperar por ID.
- `GET /api/v1/document/{collection}/{jettraId}/versions`: Historial de versiones.
- `GET /api/v1/document/{collection}/search/tag?tag={val}`: Búsqueda por tag.

### 2. Jettra Shell (MongoDB Integration)
Puedes usar comandos estilo MongoDB que se traducen automáticamente a operaciones del Document Engine:
```bash
mongo db.usuarios.insert({nombre: "Alice", _tags: ["beta"]})
mongo db.usuarios.find("node1/main#uuid123")
```

### 3. Jettra Web Dashboard
El explorador de documentos permite:
- **Navegación Visual**: Selección de colecciones desde el árbol multi-modelo.
- **Inserción de Documentos**: Interfaz de edición JSON con soporte para etiquetas automáticas.
- **Monitoreo de Ubicación**: Visualización del `jettraID` para verificar la ubicación física (node/bucket) de los datos.

## Ejemplo de Documento Enriquecido
Internamente, el motor añade metadatos de control para garantizar consistencia y auditoría:

```json
{
  "nombre": "Alice Cooper",
  "empresa": "JettraCorp",
  "_tags": ["vip"],
  "jettraID": "node1/clientes-premium#389252...",
  "_version": 2,
  "_lastModified": 1705786400231
}
```
# Graph Engine: Native Relationships

El motor de grafos de JettraStoreEngine permite modelar y consultar relaciones complejas de forma nativa. A diferencia de las bases de datos relacionales, el Graph Engine utiliza punteros directos entre nodos, eliminando la necesidad de costosos `JOINs`.

## Especificaciones Técnicas
- **Clase Principal**: `io.jettra.engine.graph.GraphEngine`
- **Modelo**: Labeled Property Graph (LPG).
- **Algoritmos**: Traversal BFS distribuido con soporte para Multi-Raft.

## Operaciones de Alta Performance

### 1. Creación de Vértices y Aristas
Define nodos y las conexiones que los unen de forma reactiva.

```java
// Vértice
engine.addVertex(new Vertex("v1", "Person", Map.of("name", "Alice"))).subscribe().with(v -> {});

// Arista (Relación)
engine.addEdge(new Edge("v1", "v2", "FOLLOWS")).subscribe().with(e -> {});
```

### 2. Recorridos de K-Pasos (Traversals)
Encuentra conexiones a profundidades específicas de forma eficiente.

```java
// Busca conexiones hasta nivel 3 desde el nodo "v1"
engine.traverse("v1", 3)
      .subscribe().with(node -> System.out.println("Relacionado: " + node.id()));
```

## Casos de Uso
- **Redes Sociales**: Detección de círculos de amigos o influenciadores.
- **Detección de Fraude**: Identificación de patrones circulares en transacciones financieras.
- **Sistemas de Recomendación**: Recomendaciones basadas en "quien compró esto también compró...".

## Persistencia
Las aristas se almacenan en una lista de adyacencia optimizada localmente y se replican a través del grupo Raft para garantizar que el grafo sea consistente en todo el cluster.
# Key-Value Engine

The Key-Value engine is the fastest way to store and retrieve data in JettraStoreEngine.

## Configuration
- **Module:** `jettra-engine-key-value`
- **Class:** `io.jettra.engine.kv.KvEngine`

## Features
- **Ultra-low Latency:** Designed for sub-millisecond response times.
- **Persistence:** Unlike Redis, data is persisted via Multi-Raft to SSD.
- **Atomicity:** Key operations are atomic within a group.

## Usage Example (Java)

```java
@Inject KvEngine kv;

// Set a value
kv.put("session:123", "logged-in").subscribe().with(v -> {});

// Get a value
kv.get("session:123")
    .subscribe().with(opt -> opt.ifPresent(System.out::println));
```
# Object Engine

High-performance binary object storage.

## Configuration
- **Module:** `jettra-engine-object`
- **Class:** `io.jettra.engine.object.ObjectEngine`

## Features
- **BLOB Support:** Optimized for streaming large files.
- **Bucket Organization:** Hierarchical storage structure.
- **Cloud-Native Compatibility:** Designed to behave like S3-ready storage.

## Usage Example (Java)

```java
@Inject ObjectEngine objects;

byte[] fileBytes = // ... read from disk
objects.stash("uploads", "image.jpg", fileBytes)
    .subscribe().with(v -> System.out.println("Stored!"));

objects.retrieve("uploads", "image.jpg")
    .subscribe().with(opt -> opt.ifPresent(bytes -> System.out.println("Size: " + bytes.length)));
```
# Multi-Model Engine

JettraStoreEngine is a truly multi-model database engine. Unlike other databases that add "wrappers", JettraStoreEngine's engines are optimized for each data model while sharing the same underlying Multi-Raft and LSM-Store layers. 

**Note:** Starting from version 1.0, JettraStoreEngine databases are **Multi-Model by default**. You don't need to specify an engine during database creation; the database will automatically support all the models listed below integrated into a single instance.

## Supported Engines

### 1. Document Engine
- **Module:** `jettra-engine-document`
- **Format:** JSON/BSON
- **Features:** Rich indexing, sub-document queries.

### 2. Column Engine (OLAP)
- **Module:** `jettra-engine-column`
- **Use Case:** Analytics, big data.
- **Optimization:** Columnar storage for fast aggregations.

### 3. Key-Value Engine
- **Module:** `jettra-engine-key-value`
- **Use Case:** Caching, session management.
- **Latency:** Sub-millisecond.

### 4. Graph Engine
- **Module:** `jettra-engine-graph`
- **Format:** Labeled Property Graph (LPG).
- **Optimization:** Native vertex/edge storage with fast traversals.
- **Algorithms:** Built-in BFS (Breadth-First Search) and DFS for relationship traversal. Optimized for deep path queries.

### 5. Vector Engine (AI)
- **Module:** `jettra-engine-vector`
- **Use Case:** LLM context, similarity search, recommendation systems.
- **Algorithms:** Optimized Cosine Similarity search. Designed for high-dimensional data (1536+ dimensions).
- **Indexing:** Supports tiered indexing for low-latency retrieval.

### 6. Time-Series Engine
- **Module:** `jettra-engine-time-series`
- **Use Case:** IoT, monitoring.
- **Optimization:** Compression for time-series data.

### 7. Geospatial Engine
- **Module:** `jettra-engine-geospatial`
- **Use Case:** GIS, location-based services, spatial queries.
- **Indexes:** QuadTree, R-Tree.

### 8. Object Engine
- **Module:** `jettra-engine-object`
- **Use Case:** Cloud-native object storage.
- **API:** Compatible with major object storage protocols.

### 9. Files Engine
- **Module:** `jettra-engine-files`
- **Use Case:** File management as databases.
- **Features:** Treat file systems as queryable databases.
# Time-Series Engine

Optimized for high-frequency time-stamped data.

## Configuration
- **Module:** `jettra-engine-time-series`
- **Class:** `io.jettra.engine.ts.TimeSeriesEngine`

## Features
- **Range Queries:** Optimized for "last X hours" queries.
- **Efficient Storage:** Time-ordered indexing reduces seek times.
- **Automatic Compression:** Data is compressed as it ages.

## Usage Example (Java)

```java
@Inject TimeSeriesEngine ts;

// Log a metric
ts.insert("cpu_load", new DataPoint(Instant.now(), 45.3, Map.of("node", "srv-1")))
    .subscribe().with(v -> {});

// Query range
Instant start = Instant.now().minus(Duration.ofHours(1));
ts.queryRange("cpu_load", start, Instant.now())
    .subscribe().with(points -> System.out.println("Points found: " + points.size()));
```
# Vector Engine

Built for AI and Machine Learning workloads.

## Configuration
- **Module:** `jettra-engine-vector`
- **Class:** `io.jettra.engine.vector.VectorEngine`

## Features
- **Similarity Search:** Search using Cosine Similarity or Euclidean distance.
- **RAG Ready:** Perfect for storage of embeddings from OpenAI, HuggingFace, etc.
- **High Performance:** Designed for high-dimensional vector spaces.

## Usage Example (Java)

```java
@Inject VectorEngine vectorEngine;

float[] embedding = new float[]{0.1f, 0.9f, -0.3f};
vectorEngine.addVector(new VectorRecord("doc-1", embedding, "Sample metadata"))
    .subscribe().with(v -> {});

// Search for similar vectors
vectorEngine.searchSimilarity(new float[]{0.1f, 0.85f, -0.2f}, 5)
    .subscribe().with(results -> {
        results.forEach(res -> System.out.println("Match: " + res.id()));
    });
```
# JettraStoreEngine Usage Examples

## 1. Document Storage (Java Reactive)

```java
import io.jettra.driver.JettraClient;
import io.jettra.driver.DocumentEngine;

JettraClient client = JettraClient.create("pd-address:9000");
DocumentEngine docEngine = client.getDocumentEngine();

// Persistent reactive save
docEngine.save("sensors", "sensor-01", "{\"temp\": 22.5, \"status\": \"OK\"}")
    .onItem().transform(v -> "Saved successfully!")
    .subscribe().with(System.out::println);
```

## 2. Key-Value Operations

```java
JettraClient client = JettraClient.create("pd-address:9000");
KvEngine kv = client.getKvEngine();

kv.put("config:max_retries", "5")
    .chain(() -> kv.get("config:max_retries"))
    .subscribe().with(val -> System.out.println("Max Retries: " + val));
```

## 3. Shell Interaction

```bash
# Connect to cluster
jettra-shell connect localhost:9000

# Insert a document
jettra-shell query "INSERT INTO users {name: 'Alice', role: 'admin'}"

# List nodes
jettra-shell query "SHOW NODES"
```

## 4. Vector Search (AI)

```java
VectorEngine vector = client.getVectorEngine();
float[] embedding = {0.1f, 0.5f, -0.2f};

vector.search("profiles", embedding, 10)
    .subscribe().with(results -> {
        results.forEach(res -> System.out.println("Found match: " + res.getId()));
    });
```

## 5. Load Testing & Benchmarking

We have provided scripts to test the cluster performance under high load in the `sh/` directory:

### Python Benchmark
Uses thread pools to perform simultaneous REST insertions.
```bash
bash sh/run_python_test.sh
```

### Java Reactive Benchmark
Uses the native driver with Mutiny to perform non-blocking high-throughput inserts.
```bash
bash sh/run_java_test.sh
```
# Getting Started with JettraStoreEngine

JettraStoreEngine is a high-performance, multi-model distributed database designed for modern cloud-native applications. It features a Multi-Raft consensus algorithm across multiple node groups, providing high availability and strong consistency.

## Installation

### Maven
Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.jettra</groupId>
    <artifactId>jettra-driver-java</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Core Guides
- [Architecture & Design](architecture.md)
- [Multi-Raft Consensus](consensus.md)
- [Engines & Multi-Model Support](engines.md)
- [Java Reactive Driver](driver.md)
- [Vector and Graph Search](engines.md)
- [Distributed Transactions (2PC)](transactions.md)
- [Global Auditing System](auditing.md)
- [Repository Pattern](repository.md)
- [Predictive Alerts & Monitoring](monitoring.md)
- [Web Management Interface](web.md)
- [LSM Storage & Persistence](storage.md)
- [Usage Examples](examples.md)

## Starting with Docker
(Easiest)

```bash
docker-compose up -d
```

## Running Locally

To run JettraStoreEngine locally, you can use the provided startup scripts:

```bash
./sh/start-cluster.sh
```

## Dashboard
Once the cluster is running, the management dashboard is available at:
`http://localhost:8080`
# JettraStoreEngine Geospatial Engine

The **JettraStoreEngine Geospatial Engine** provides specialized support for storing, indexing, and querying geospatial data. It is designed to handle location-based services, efficient spatial queries, and GeoJSON data structures within the multi-model ecosystem of JettraStoreEngine.

## Features

- **GeoJSON Support**: Store Point, LineString, Polygon, MultiPoint, MultiLineString, MultiPolygon, and GeometryCollection.
- **Spatial Indexing**: Utilizes QuadTrees (and planned R-Trees) for efficient spatial lookups.
- **Spatial Queries**:
    - `NEAR`: Find points within a certain radius.
    - `WITHIN`: Find geometries entirely inside a polygon.
    - `INTERSECTS`: Find geometries that intersect with a given shape.

## Usage in Jettra Shell

You can interact with the Geospatial Engine using the unified `sql` interface or direct engine commands.

### Creating a Geospatial Database

```bash
# Create a dedicated geospatial database
db create city_maps --engine Geospatial --storage STORE
```

### Storing Data (GeoJSON)

```sql
-- Insert a location (Point)
INSERT INTO landmarks VALUES ('statue_liberty', {
    "type": "Point",
    "coordinates": [-74.0445, 40.6892],
    "properties": {"name": "Statue of Liberty"}
});

-- Insert a zone (Polygon)
INSERT INTO zones VALUES ('central_park', {
    "type": "Polygon",
    "coordinates": [[
        [-73.981, 40.768],
        [-73.958, 40.800],
        [-73.949, 40.796],
        [-73.973, 40.764],
        [-73.981, 40.768]
    ]]
});
```

### Querying Data

#### Find Nearby Locations

```bash
# Find landmarks within 5km of a point (lat, lon)
sql SELECT * FROM landmarks WHERE NEAR(-74.006, 40.7128, 5000)
```

#### Find Points Within a Zone

```bash
# Find all landmarks inside Central Park
sql SELECT * FROM landmarks WHERE WITHIN('central_park')
```

## Java Driver Example

```java
import io.jettra.driver.JettraDriver;
import io.jettra.driver.JettraClient;

public class MapsExample {
    public static void main(String[] args) {
        JettraClient client = JettraDriver.connect("localhost:8081");
        
        // Store a location
        String json = """
            {
                "type": "Point",
                "coordinates": [-122.4194, 37.7749],
                "name": "San Francisco"
            }
        """;
        client.database("maps_db").collection("cities").insert(json);
        
        System.out.println("City stored.");
    }
}
```
# JettraStoreEngine Graph Engine Guide

JettraStoreEngine provides a powerful Native Graph Engine that allows you to manage and query complex relationships via direct node pointers without the need for expensive JOINs. This guide covers how to use the Graph Engine through the Java Driver (`jettra-driver-java`).

## High-Performance Graph Operations

The Graph Engine supports Labeled Property Graphs (LPG). Nodes are represented as Vertices, and relationships as Edges.

### 1. Creating Vertices

A vertex represents an entity in the graph. It requires an ID, a Label (type), and a Map of properties.

```java
import io.jettra.driver.JettraClient;
import java.util.Map;

// Obtain the client
JettraClient client = ...;

// Create a Person vertex
client.addVertex(
    "person:alice", 
    "Person", 
    Map.of("name", "Alice", "age", 30)
).subscribe().with(v -> System.out.println("Vertex created!"));

// Create another Person vertex
client.addVertex(
    "person:bob", 
    "Person", 
    Map.of("name", "Bob", "age", 32)
).subscribe().with(v -> System.out.println("Vertex created!"));
```

### 2. Creating Edges (Relationships)

An edge defines a directed relationship between two vertices.

```java
// Alice follows Bob
client.addEdge(
    "person:alice", 
    "person:bob", 
    "FOLLOWS"
).subscribe().with(e -> System.out.println("Edge created!"));
```

### 3. Graph Traversals (K-Steps BFS)

You can explore connections up to a specific depth using high-performance Breadth-First Search (BFS).

```java
// Find connections up to depth 3 from Alice
client.traverseGraph("person:alice", 3)
    .subscribe().with(vertexList -> {
        System.out.println("Traversed Vertices:");
        vertexList.forEach(System.out::println);
    });
```

## Internal Engine Representation

Under the hood, the Graph Operations translate to the engine module `jettra-engine-graph`.

- **Vertices**: Stored using the `graph:v:{id}` key pattern with properties serialized effectively.
- **Edges**: Stored using the `graph:e:{fromId}:{toId}` pattern. An optimized adjacency list updates concurrently for rapid traversal.
- **Persistence**: Vertices and relationships replicate robustly across the Raft consensus group.

## Use Cases

- **Social Networks**: Fast friend-of-friend discoveries.
- **Fraud Detection**: Spotting cyclical transfer patterns in finance.
- **Recommendations**: Content and product discovery (e.g. users who bought x also bought y).

## JettraShell Examples

You can interact with the Graph Engine directly from the interactive `jettra-shell`.

1. **Connect and Login**:
   ```bash
   jettra> connect localhost:8081
   jettra> login admin
   ```

2. **Add Vertices**:
   ```bash
   jettra> graph add-vertex "person:alice" "Person" "{\"name\":\"Alice\",\"age\":30}"
   Vertex 'person:alice' added successfully.
   
   jettra> graph add-vertex "person:bob" "Person" "{\"name\":\"Bob\",\"age\":32}"
   Vertex 'person:bob' added successfully.
   ```

3. **Add Edges**:
   ```bash
   jettra> graph add-edge "person:alice" "person:bob" "FOLLOWS"
   Edge from 'person:alice' to 'person:bob' added successfully.
   ```

4. **Traverse Graph**:
   Traverse from Alice up to a depth of 3:
   ```bash
   jettra> graph traverse "person:alice" 3
   Traversal results:
   [{"id":"person:alice","label":"Person","properties":{"name":"Alice","age":30}},{"id":"person:bob","label":"Person","properties":{"name":"Bob","age":32}}]
   ```

## REST API (cURL) Examples

You can also use standard HTTP tools like `curl` to interact with the Graph API. Ensure you have obtained a valid Bearer token from the `/api/web-auth/login` endpoint.

**Add a Vertex**:
```bash
curl -X POST http://localhost:8081/api/v1/graph/vertex \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"id": "person:charlie", "label": "Person", "properties": {"name": "Charlie", "age": 28}}'
```

**Add an Edge**:
```bash
curl -X POST http://localhost:8081/api/v1/graph/edge \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fromId": "person:alice", "toId": "person:charlie", "label": "KNOWS"}'
```

**Traverse Graph**:
```bash
curl -X GET "http://localhost:8081/api/v1/graph/traverse/person:alice?depth=2" \
  -H "Authorization: Bearer YOUR_TOKEN"
```
# Jettra UI Framework Guide

Jettra UI is a Java API designed to generate modern, responsive web interfaces programmatically. It abstracts away HTML and CSS (specifically Flowbite/TailwindCSS), allowing developers to build rich UIs using pure Java objects.

## Core Concepts

Jettra UI revolves around a few key concepts:
1.  **Components**: The building blocks of the UI (e.g., Buttons, Labels, Inputs).
2.  **Containers**: Components that can hold other components (e.g., Div, Form).
3.  **Templates**: Pre-defined layouts to structure your application pages.
4.  **Events & Validation**: Mechanisms to handle user interaction and data integrity.

## Getting Started

To use Jettra UI, ensure the module is included in your project's `pom.xml`.

```xml
<dependency>
    <groupId>io.jettra</groupId>
    <artifactId>jettra-ui</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Basic Component Usage

All UI elements inherit from the base `Component` class. You can set IDs, add custom attributes, and add event listeners.

### Creating a Button

```java
import io.jettra.ui.component.Button;

// Create a button with ID "save-btn" and label "Save Changes"
Button saveBtn = new Button("save-btn", "Save Changes");

// The component comes pre-styled with optimized Flowbite CSS classes
```

### Creating Inputs and Labels

```java
import io.jettra.ui.component.InputText;
import io.jettra.ui.component.Label;

// Create a label
Label nameLabel = new Label("lbl-name", "Full Name");

// Create an input field
InputText nameInput = new InputText("inp-name");
nameInput.setPlaceholder("Enter your name");

// Create a password field manually
InputText passInput = new InputText("inp-pass");
passInput.setType("password");
```

### Using Containers

Containers like `Div` and `Form` are used to group components.

```java
import io.jettra.ui.component.Div;
import io.jettra.ui.component.Form;

Form form = new Form("registration-form");

// Add components to the form
form.addComponent(nameLabel);
form.addComponent(nameInput);
form.addComponent(saveBtn);
```

### Login Component

Jettra UI provides a high-level `Login` component to simplify the creation of login forms.

**Basic Login:**

```java
import io.jettra.ui.component.*;

InputText username = new InputText("username");
Password password = new Password("password");
Button loginBtn = new Button("btn-login", "Sign In");

// Add HTMX properties for AJAX login
loginBtn.setHxPost("/auth/login");
loginBtn.setHxTarget("body");

Login login = new Login("login-comp", username, password, loginBtn);
login.setTitle("Welcome Back");

// Render
String html = login.render();
```

**Login with Role Selection:**

```java
SelectOne roleInfo = new SelectOne("sel-role");
roleInfo.addOption("admin", "Administrator");
roleInfo.addOption("user", "User");

Login loginWithRole = new Login("login-role", username, password, loginBtn, roleInfo);
```

### Complex Components

#### SelectOne (Dropdown)

```java
import io.jettra.ui.component.SelectOne;

SelectOne roleSelect = new SelectOne("role-select");
roleSelect.addOption("USER", "Standard User");
roleSelect.addOption("ADMIN", "Administrator");

form.addComponent(roleSelect);
```

#### Data Table

```java
import io.jettra.ui.component.Table;
import java.util.Arrays;

Table userTable = new Table("users-table");

// Set headers
userTable.addHeader("ID");
userTable.addHeader("Username");
userTable.addHeader("Role");

// Add rows
userTable.addRow(Arrays.asList("1", "jdoe", "USER"));
userTable.addRow(Arrays.asList("2", "admin", "ADMIN"));
```

#### Tree (Data Explorer)

```java
import io.jettra.ui.component.Tree;

Tree tree = new Tree("explorer");
Tree.TreeNode root = new Tree.TreeNode("Databases", "📁");
root.addChild(new Tree.TreeNode("Sales", "📄"));
tree.addNode(root);
```

#### Navbar and Sidebar

```java
Navbar navbar = new Navbar("top-nav");
navbar.setBrandName("Jettra Manager");

Sidebar sidebar = new Sidebar("side-nav");
sidebar.addItem(new Sidebar.SidebarItem("home", "Home", "🏠"));
```

## Layouts and Templates

Jettra UI provides a powerful `Template` class to create responsive application layouts.

```java
Template appTemplate = new Template();
appTemplate.setTop(navbar);
appTemplate.setLeft(sidebar);
appTemplate.setCenter(content);
```

## HTMX Support

Jettra UI includes first-class support for HTMX to enable dynamic interactions with minimal JavaScript. 
The `Component` class provides helper methods to easily configure HTMX attributes.

### Example: AJAX Form Submission

```java
Button saveBtn = new Button("btn-save", "Save User");

// Configure HTMX properties
saveBtn.setHxPost("/api/users");      // URL to POST to
saveBtn.setHxTarget("#user-table");   // Element to update with the response
saveBtn.setHxSwap("outerHTML");       // Strategy for swapping content

form.addComponent(saveBtn);
```

### Available HTMX Methods

Every component supports the following methods:

- `setHxGet(String url)`
- `setHxPost(String url)`
- `setHxPut(String url)`
- `setHxDelete(String url)`
- `setHxTarget(String selector)`
- `setHxSwap(String swapStrategy)`
- `setHxTrigger(String triggerEvent)`
- `setHxConfirm(String message)`
- `setHxInclude(String selector)`

These methods automatically render the corresponding `hx-*` attributes in the generated HTML.

## Event Handling

You can attach event listeners to handle user interactions programmatically.

```java
import io.jettra.ui.event.JettraEvent;

Button actionBtn = new Button("btn-action", "Click Me");

actionBtn.addEventListener((JettraEvent event) -> {
    System.out.println("Button clicked! Source: " + event.getSource().getId());
    // Handle the event logic here
});
```

*Note: In the current snapshot, Java event listeners are logical constructs. For client-server interaction in a web environment, we recommend using the HTMX support described above or integrating with a suitable backend adapter.*
# Monitoreo y Alertas Predictivas

JettraStoreEngine no solo monitoriza el estado actual del cluster, sino que utiliza algoritmos de tendencia para predecir posibles fallos o cuellos de botella antes de que ocurran.

## Monitoreo de Recursos en Tiempo Real ⭐
El sistema ahora permite una inspección profunda y granular de cada componente del cluster. A través del Dashboard Web, los administradores pueden visualizar el consumo exacto de recursos:

1.  **Navegación**: Dirígete a la sección **Nodes** en el menú lateral.
2.  **Inspección**: Haz clic en el botón **🔍 View Resources** dentro de la tarjeta de cualquier nodo (Storage, Memory, etc.).
3.  **Métricas Detalladas**:
    -   **CPU Usage**: Visualización mediante barra de progreso del porcentaje de carga de CPU actual del proceso.
    -   **RAM Usage**: Consumo de memoria RAM en Megabytes vs el límite máximo configurado.
    -   **Last Heartbeat**: Monitoreo de la frescura de la señal del nodo para detectar "zombie nodes".

## Centro de Alertas
En la interfaz web, la sección **Alertas & Métricas** centraliza todas las notificaciones críticas. El sistema clasifica las alertas en tres niveles de severidad:

### Niveles de Severidad
- 🔴 **CRITICAL (Alta)**: Requiere acción inmediata (ej. Nodo con >85% de disco). El sistema podría comenzar a rechazar escrituras pronto.
- 🟡 **WARNING (Media)**: Desviación detectada (ej. Latencia de replicación Raft >100ms).
- 🔵 **PREDICTIVE (Predictiva)**: Basada en tendencias de carga. Te avisa con antelación si el CPU o la Memoria excederán los umbrales en las próximas horas.

## Métricas Clave
El dashboard visualiza tendencias de salud del cluster:
1.  **Predicted Disk Usage**: Proyección del uso de almacenamiento para las próximas 24-48 horas basada en el ritmo de ingestión actual.
2.  **Throughput Trend**: Comparativa del rendimiento (RPS - Requests Per Second) respecto a la última hora.

## Cómo Responder a una Alerta
- **Alerta de Almacenamiento**: Considera añadir nuevos nodos de almacenamiento al cluster usando Docker Compose y deja que el Placement Driver reequilibre los datos.
- **Alerta de Latencia**: Revisa la conectividad de red entre los nodos del grupo Raft afectado.
- **Alerta Predictiva de CPU**: Es el momento ideal para escalar horizontalmente la capa de motores (Engines).

### 🐚 Vía Shell
Ejecuta el comando `node list` para ver una tabla comparativa de recursos en tiempo real. Este comando extrae datos directamente del Placement Driver, mostrando la carga de CPU y el consumo de memoria JVM de cada nodo.

```bash
node list
```

**Ejemplo de Salida:**
```text
Node Resources Monitoring:
--------------------------------------------------------------------------------------------------------------------------
ID              | Address            | Role       | Raft Role  | Status   | CPU%   | Memory Usage    / Max Memory     
--------------------------------------------------------------------------------------------------------------------------
jettra-store-1  | 172.18.0.3:8080    | STORAGE    | LEADER     | ONLINE   | 4.5    | 156.2 MB        / 4096.0 MB      
jettra-store-2  | 172.18.0.4:8080    | STORAGE    | FOLLOWER   | ONLINE   | 2.1    | 120.8 MB        / 4096.0 MB      
jettra-store-3  | 172.18.0.5:8080    | STORAGE    | FOLLOWER   | ONLINE   | 1.8    | 115.5 MB        / 4096.0 MB      
--------------------------------------------------------------------------------------------------------------------------
```

### 🌐 Vía cURL (API REST)
Consulta el endpoint de monitorización unificado en el puerto del Dashboard Web (8081). Este endpoint devuelve un JSON con las métricas crudas de todos los nodos.

```bash
# 1. Obtener Token
TOKEN=$(curl -s -X POST http://localhost:8081/api/web-auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"super-user","password":"superuser-jettra"}' | jq -r .token)

# 2. Consultar Recursos
curl -s http://localhost:8081/api/monitor/nodes \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### ☕ Vía Java Driver
Utiliza el método `listNodes()` de `JettraClient` para obtener objetos `NodeInfo` que contienen todas las métricas de recursos.

```java
JettraReactiveClient client = new JettraReactiveClient("localhost:8081", token);

client.listNodes().subscribe().with(nodes -> {
    for (NodeInfo node : nodes) {
        System.out.printf("Node: %s | CPU: %.1f%% | Mem: %.1f MB / %.1f MB\n",
            node.id(), 
            node.cpuUsage(), 
            node.memoryUsage() / 1024.0 / 1024.0, 
            node.memoryMax() / 1024.0 / 1024.0);
    }
});
```

## Ejemplo: Escalamiento Dinámico
Si detectas que los nodos de almacenamiento están llegando a su límite de recursos, puedes escalar horizontalmente el cluster:

```bash
docker-compose up -d --scale jettra-store=5
```

## Configuración de Umbrales
Los umbrales de alerta se pueden configurar en el archivo `application.properties` o `config.json` del Placement Driver (PD), permitiendo personalizar la sensibilidad del sistema predictivo según el entorno (Dev, Stage, Prod).

# Guía de Optimización de JettraStoreEngine

Esta guía detalla las políticas y reglas de optimización implementadas en los proyectos de JettraStoreEngine, enfocadas en minimizar la latencia y maximizar la eficiencia de memoria utilizando las características más avanzadas de la JVM (Java 25).

## Configuración de la JVM

Para entornos de producción y pruebas de alto rendimiento, utilizamos una configuración optimizada del Garbage Collector y la gestión de memoria.

### Banderas de Optimización ("Best Practices")

La configuración estándar recomendada para los nodos de JettraStoreEngine (Store y PD) es:

```bash
java -Xmx8g -Xms8g \
     -XX:+UseZGC \
     -XX:+UseCompactObjectHeaders \
     -jar quarkus-run.jar
```

### Explicación de las Banderas

1.  **`-XX:+UseZGC`**: Activa el Z Garbage Collector. ZGC está diseñado para tiempos de pausa extremadamente bajos (sub-milisegundos), lo cual es crítico para una base de datos distribuida como JettraStoreEngine para evitar *hiccups* en el consenso Raft y las lecturas/escrituras.
2.  **`-XX:+UseZGC`**: Habilita el Z Garbage Collector, diseñado para latencias extremadamente bajas (menores a 1ms).
3.  **`-XX:+UseCompactObjectHeaders`**: Una optimización clave de las versiones modernas de Java (Project Lilliput). Reduce el tamaño de la cabecera de los objetos instanciados en el heap. En aplicaciones con millones de objetos pequeños (como nodos de documentos o entradas de índice), esto reduce significativamente el consumo de memoria y mejora la localidad de caché.
4.  **` AlwaysPreTouch` (Deshabilitado por defecto)**: *Ver sección de "Optimizaciones Futuras"*.

> **Nota Crítica sobre Versiones de Java:**
> La opción `-XX:+UseCompactObjectHeaders` es una característica experimental/avanzada (Project Lilliput) que requiere versiones muy recientes de la JVM (JDK 24/25+). **Actualmente está activa en la configuración por defecto**, por lo que es necesario asegurar que el entorno de ejecución (Local y Docker) utilice una versión de Java compatible.

## Configuración en los Proyectos (Estado Actual)

### 1. Desarrollo y Test (`application.properties`)

```properties
quarkus.jvm.args=-XX:+UseZGC -XX:+UseCompactObjectHeaders
```

### 2. Contenedores Docker (`docker-compose.yaml`)

```yaml
environment:
  - JAVA_OPTS=-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -XX:+UseZGC -XX:+UseCompactObjectHeaders
```

## Optimizaciones Futuras y Producción Avanzada

### Uso de `-XX:+AlwaysPreTouch`

Actualmente, **hemos deshabilitado** esta bandera en la configuración de desarrollo y Docker por defecto debido a que incrementa notablemente el tiempo de inicio de los contenedores y servicios (la JVM debe poner a cero toda la memoria asignada antes de arrancar).

**¿Cuándo habilitarla?**
Se recomienda encarecidamente habilitarla **solo en entornos de Producción reales** (no en desarrollo local ni CI rápido) donde la estabilidad de latencia a largo plazo es más importante que un reinicio rápido.

**Beneficios:**
1.  **Elimina "Hiccups":** Evita que el sistema operativo pause la base de datos aleatoriamente para asignar páginas de memoria física bajo carga.
2.  **Validación de RAM:** Fuerza un fallo inmediato al arranque si no hay RAM suficiente, evitando muertes súbitas (OOM Killer) posteriores.

**Cómo habilitarla:**
Añade la bandera `-XX:+AlwaysPreTouch` a la variable `JAVA_OPTS` o `quarkus.jvm.args`.

*Ejemplo para Producción:*
```bash
java -Xmx32g -Xms32g -XX:+UseZGC -XX:+AlwaysPreTouch -jar quarkus-run.jar
```

## Recomendaciones de Hardware

*   **Memoria:** Se recomienda un mínimo de **8GB** de Heap (`-Xmx8g`) para nodos de almacenamiento en producción para aprovechar al máximo ZGC Generacional.
*   **CPU:** ZGC se beneficia de múltiples núcleos para los hilos de marcado concurrente.
# Gestión de Perfiles y Roles en JettraStoreEngine

JettraStoreEngine implementa un sistema robusto de Control de Acceso Basado en Roles (RBAC) que distingue entre privilegios a nivel de aplicación (**Perfiles**) y privilegios a nivel de base de datos (**Roles**).

## 1. Perfiles de Aplicación (Globales)

Los perfiles determinan qué acciones puede realizar un usuario en la interfaz administrativa (Web, Shell, cURL, Driver) y su visibilidad global de los recursos del cluster.

| Perfil | Descripción | Restricciones de UI |
| :--- | :--- | :--- |
| **super-user** | Acceso total y absoluto al sistema. Ve todos los nodos, grupos y todas las bases de datos creadas en el sistema sin excepción. | Ninguna |
| **management** | Acceso administrativo global. Puede gestionar usuarios y configuraciones generales del cluster. | **No puede detener (Stop)** nodos del cluster. |
| **end-user** | Perfil estándar para usuarios finales. Solo tiene visibilidad de las bases de datos donde se le asigne un rol específico o las que él mismo cree. | No puede gestionar usuarios globales ni detener nodos. |

> **Seguridad Crítica:** El usuario `super-user` es una cuenta de sistema protegida. No puede ser eliminado ni su perfil puede ser alterado a través de las APIs estándar para prevenir bloqueos accidentales.

## 2. Roles a Nivel de Base de Datos

Cada base de datos creada en JettraStoreEngine tiene su propio conjunto de roles. Los roles siguen la convención de nomenclatura `{tipo-rol}_{nombre-db}`.

### Tipos de Roles Disponibles:

1.  **super-user**: Permiso total dentro de la base de datos.
    - Se asigna automáticamente al usuario `super-user` en cada base de datos nueva.
    - No puede ser removido de esta base de datos.
2.  **admin**: Permite administrar la base de datos, incluyendo la gestión de permisos para otros usuarios en esa base de datos específica.
    - El **creador** de una base de datos recibe automáticamente este rol para dicha base de datos.
3.  **read-write**: Permite realizar operaciones de lectura y escritura (Insert, Update, Delete) sobre los datos.
4.  **read**: Solo permite realizar consultas (Select/Find) de datos.
5.  **denied**: Deniega explícitamente el acceso. Una base de datos con este rol para un usuario será invisible para él en la interfaz.

## 3. Comportamiento del Sistema

### Creación de Bases de Datos
Todos los usuarios, independientemente de su perfil, tienen permiso para **crear nuevas bases de datos**. Al crear una base de datos:
1.  Se registra la base de datos en el Placement Driver.
2.  Se crea el rol `super-user_{db}` y se asigna al usuario `super-user`.
3.  Se crea el rol `admin_{db}` y se asigna al usuario que realizó la creación.

### Visibilidad en el Dashboard
- El usuario con perfil `super-user` visualiza el árbol completo de bases de datos del sistema.
- Los usuarios con perfiles `management` o `end-user` solo visualizan aquellas bases de datos en las que poseen un rol asignado (que no sea `denied`).

### Gestión de Usuarios
La administración de usuarios y la asignación de perfiles globales está reservada para los perfiles `super-user` y `management`. Sin embargo, un `end-user` con rol `admin` en una base de datos específica puede gestionar los permisos de otros usuarios dentro de los límites de esa base de datos.
# JettraStoreEngine Repository Pattern

JettraStoreEngine provides a developer-friendly Repository Pattern inspired by **Jakarta NoSQL**, **Jakarta Data**, and **Jakarta Query**. This pattern abstracts the low-level client operations and allows you to work with **Java Records** or POJOs using annotations.

## Annotations

### Entity Mapping
- `@Entity(collection = "...")`: Marks a class/record as a JettraStoreEngine entity.
- `@Id(sequential = true)`: Marks the primary key. If `sequential` is true, JettraStoreEngine will handle automatic ID generation.
- `@Column(name = "...")`: Maps a field/component to a specific database field.

### Repository Methods (Jakarta Style)
- `@Save`: Upserts the entity.
- `@Update`: Explicit update operation.
- `@Delete`: Deletes the record.
- `@Find`: Finds a record (usually by ID or complex query).
- `@FindAll`: Retrieves all records in the collection.

## Using Java Records as Entities

Java Records are the preferred way to define entities in JettraStoreEngine due to their immutability and conciseness.

```java
import io.jettra.driver.annotations.Column;
import io.jettra.driver.annotations.Entity;
import io.jettra.driver.annotations.Id;

@Entity(collection = "products")
public record Product(
    @Id(sequential = true)
    Long id,
    
    @Column(name = "product_name")
    String name,
    
    Double price
) {}
```

## Fluent Repository Interface

You can define repository interfaces that follow the Jakarta Data specification.

```java
import io.jettra.driver.repository.Repository;
import io.jettra.driver.annotations.*;
import io.smallrye.mutiny.Uni;
import java.util.List;

public interface ProductRepository extends Repository<Product, Long> {
    
    @Save
    Product save(Product product);
    
    @Find
    Optional<Product> findById(Long id);
    
    @FindAll
    List<Product> findAll();
    
    @Delete
    void deleteById(Long id);
}
```

### Aggregations in Repositories ⭐

The repository pattern now supports high-level analytic functions:

```java
var productRepo = new JettraRepositoryImpl<>(client, Product.class);

// Count documents
Uni<Long> total = productRepo.count();

// Sum of a field
Uni<Double> totalSales = productRepo.sum("price");

// Average value
Uni<Double> avgPrice = productRepo.avg("price");
```

## Base Implementation: `JettraRepository`

The `JettraRepository<T, K>` class provides a base implementation that uses reflection to handle common CRUD operations automatically, even for Java Records.

### Example Usage

```java
JettraClient client = ...;
var productRepo = new JettraRepository<>(client, Product.class);

// Saving a Record (ID will be generated if sequential=true)
Product newProduct = new Product(null, "Laptop", 1200.0);
productRepo.save(newProduct).subscribe().with(p -> System.out.println("Saved ID: " + p.id()));
```

## Summary of Features
- **Reactive by Default**: All operations return `Uni` or `Multi`.
- **Automatic ID Generation**: Enabled via `@Id(sequential = true)`.
- **Multi-Engine Support**: Repositories work seamlessly across Document, Key-Value, and Graph engines.
- **Jakarta Standards**: Compatible with Jakarta EE dependency injection and programming models.
# Sequential Keys (Sequences) in JettraStoreEngine

JettraStoreEngine supports persistent, monotonically increasing internal counters called **Sequences**. These are useful for generating unique non-random IDs, order numbers, or any value that requires gaps-free incrementing logic.

## Key Features
- **Auto-incrementing**: Each call to `next` increment the value by a configurable amount.
- **Persistent**: Sequences are managed by the Placement Driver (PD) and shared across the cluster.
- **Multi-tenant**: Sequences are bound to a specific database context.

## Core Concepts
- **Name**: Unique identifier for the sequence within a database.
- **Start Value**: The initial value of the sequence.
- **Increment**: The amount to add on each `next()` call (default is 1).
- **Current Value**: The last generated value.

## Usage Guide

### 1. via cURL (API)
The Placement Driver (PD) exposes the sequence API on port 8081 (default).

```bash
# Create a sequence
curl -X POST http://localhost:8081/api/v1/sequence \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "test_seq", "database": "db1", "startValue": 100, "increment": 1}'

# Get next value
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/sequence/test_seq/next
# Response: {"value": 101}

# Get current value
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/sequence/test_seq/current

# Reset sequence
curl -X POST http://localhost:8081/api/v1/sequence/test_seq/reset \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"newValue": 500}'

# Delete sequence
curl -X DELETE http://localhost:8081/api/v1/sequence/test_seq -H "Authorization: Bearer $TOKEN"
```

### 2. via Java Driver
The `JettraClient` provides native methods to manage sequences.

```java
// Create
client.createSequence("user_ids", "sales_db", 1, 1).await().indefinitely();

// Next value
long nextId = client.nextSequenceValue("user_ids").await().indefinitely();

// Current value
long currentId = client.currentSequenceValue("user_ids").await().indefinitely();

// List sequences
List<String> names = client.listSequences("sales_db").await().indefinitely();

// Reset/Delete
client.resetSequence("user_ids", 0).await().indefinitely();
client.deleteSequence("user_ids").await().indefinitely();
```

### 3. via JettraStoreEngine Shell
Interactive commands for quick management.

```bash
# Create
sequence create user_id_seq --start 1000 --inc 5

# Get values
sequence next user_id_seq
sequence current user_id_seq

# Management
sequence list
sequence reset user_id_seq 0
sequence delete user_id_seq
```

### 4. via Web Dashboard
Navigate to the **Sequences** section in the main menu. 
- Click **Create Sequence** to provision a new counter.
- Use the **NEXT** button in the table to test incrementing logic.
- Manage/Delete existing sequences directly from the list.
# JettraStoreEngine Interactive Shell


For specific engine details, see:
- [Geospatial Engine Guide](jettra-engine-geospatial.md)


## Navigation
To start the shell in interactive mode, navigate to the `jettra-shell` directory and run:

```bash
mvn clean package
```

Para ejecutar el shell

```bash
 java -jar target/jettra-shell-1.0.0-SNAPSHOT.jar   

```

**Note:** `jettra-shell` is a pure console application and does not open any network ports for its execution.

You will enter the JettraStoreEngine REPL where you can type commands directly.

## Basic Commands

### 1.# Connect to remote cluster
### Finding Connection Details
To know which IP and port to connect to, check your Docker configuration:

1. List running containers:
   ```bash
   docker ps
   ```
2. Look for the `jettra-web` container and its port mapping (e.g., `0.0.0.0:8081->8080/tcp`).
3. Connect using that address (e.g., `localhost:8081`).

```bash
connect http://localhost:8081
```

Once connected, you typically need to login:
```bash
login super-user -p
# Enter password when prompted

Password: superuser-jettra
```
Password: superuser-jettra


# Show Connection Info (View current PD address and Auth status)
```bash
connect info
# Output:
# Current Connection Info:
#   PD/Web Address: localhost:8081
#   Auth Token: None (Logged Out)
```
The first step is always connecting to the Placement Driver (PD).

```bash
connect localhost:9000
```

### 2. Authentication
Before performing operations, you must log in:

```bash
login super-user
# Password prompt will appear
```

### 3. Database Management
Manage your databases directly from the shell, specifying the storage type. All databases are Multi-Model containers:

```bash
# Create a new persistent Multi-Model database (Default)
db create sales_db

# Create a new Multi-Model database in memory
db create social_db --storage MEMORY

# Rename a database
db rename sales_db retail_db

# Options for --storage:
# STORE (Persistent), MEMORY (In-Memory)

# List all databases
db list # legacy
show dbs # improved

# Delete a database
db delete retail_db

# Show detailed information for a database (aliases: info, database info)
db info retail_db
```

### 4. Navigation & Context
JettraStoreEngine shell now supports context switching and detailed metadata inspection:

```bash
# List all available databases
show dbs

# Switch to a target database context
use retail_db

# Show collections in the current database
show collections

# Show detailed information for a database
info retail_db
```

### 5. Collection Management
Manage collections within your current database context, specifying the specialized engine:

```bash
# Add a new Document collection (Default)
collection add users

# Add a new Graph collection
collection add friends --engine Graph

# Add a new Vector collection for search
collection add product_vectors --engine Vector

# New MongoDB-style methods
- `db.<collection>.find(<query>, [--resolve-refs])`: Query documents.
- `db.<collection>.insertOne(<document>)`: Insert a single document.
- `db.<collection>.insertMany([<doc1>, <doc2>, ...])`: Insert multiple documents.
- `db.<collection>.replaceOne(<query>, <document>)`: Replace a document.
- `db.<collection>.replaceMany(<query>, <document>)`: Replace multiple documents (iterative).
- `db.<collection>.deleteOne(<query>)`: Delete a single document matching the query.
- `db.<collection>.deleteMany(<query>)`: Delete all documents matching the query.
- `db.<collection>.aggregate(<pipeline>)`: Run complex aggregation pipelines.
- `db.<collection>.count([query])`: Count documents.
- `db.<collection>.createIndex({field: 1})`: Create an index on a field.
- `db.<collection>.dropIndex("indexName")`: Drop an index.
- `db.<collection>.getIndexes()`: List indexes.

# Rename a collection
collection rename users customers

# Delete a collection
collection delete logs
```

### 6. Querying Data (Legacy)
The `query` command provides low-level access to engine primitives:

```bash
query "INSERT DOCUMENT INTO users {name: 'John', age: 30}"
query "SET config 'max_connections' '500'"
```

### 7. Multi-Engine SQL Support 🚀
JettraStoreEngine now supports a unified SQL interface that automatically routes operations to the specialized engines:

```bash
# Document Engine
sql SELECT * FROM users_collection WHERE age > 21

# Graph Engine (Traversals internally)
sql SELECT * FROM friends_graph WHERE name = 'Alice'

# Vector Engine (Similarity search internally)
sql SELECT * FROM image_vector_index LIMIT 5

# Persistence
sql INSERT INTO users VALUES ('id_1', 'John Doe')
sql UPDATE analytics SET processed=true WHERE id='node_2'
sql DELETE FROM logs WHERE date < '2023-01-01'
```

### 8. MongoDB-style Support (REAL) 🍃
JettraStoreEngine entiende la sintaxis de MongoDB y ahora la traduce internamente a llamadas al **Document Engine** para una persistencia real con versionamiento.

```bash
# Inserción (Genera automáticamente jettraID y Versión 1)
# Nota: No se permiten documentos vacíos ni JSON vacíos '{}'.
mongo db.usuarios.insert({nombre: 'Alice', _tags: ['vip']})

# Consulta por ID (Simplificado)
mongo db.usuarios.find('node1/default#uuid123')

# Consulta por Filtro ID
mongo db.usuarios.find({id: 'node1/default#uuid123'})

# Consulta con Paginación
sql SELECT * FROM usuarios --offset 0 --limit 10

# Actualización (Incrementa el número de versión)
mongo db.usuarios.update({id: 'node1/default#uuid123'}, {nombre: 'Alice Cooper'})

# Eliminación
mongo db.usuarios.remove({id: 'node1/default#uuid123'})

# Restaurar Versión
restore usuarios node1/default#uuid123 1

### 8.1 Aggregations & Analytics 📊
The shell supports MongoDB-style aggregation pipelines translated to Jettra analytics.

```bash
# General pipeline
mongo db.users.aggregate([{$match: {age: {$gt: 25}}}, {$group: {_id: '$city', count: {$sum: 1}}}])

# Simple shortcuts (Translated via SQL)
mongo db.sales.aggregate([{$group: {_id: null, total: {$sum: '$amount'}}}])
```
```

### 9. Cluster Administration & Monitoring ⭐
Monitor your cluster health and resource usage directly from the shell. This is critical for maintaining high availability and identifying performance bottlenecks.

```bash
# List all nodes with CPU and Memory consumption
node list

# Output Example:
# Node Resources Monitoring:
# --------------------------------------------------------------------------------------------------------------------------
# ID              | Address            | Role       | Raft Role  | Status   | CPU%   | Memory Usage    / Max Memory     
# --------------------------------------------------------------------------------------------------------------------------
# jettra-store-1  | 172.18.0.3:8080    | STORAGE    | LEADER     | ONLINE   | 4.5    | 156.2 MB        / 4096.0 MB      
# jettra-store-2  | 172.18.0.4:8080    | STORAGE    | FOLLOWER   | ONLINE   | 2.1    | 120.8 MB        / 4096.0 MB      
# jettra-store-3  | 172.18.0.5:8080    | STORAGE    | FOLLOWER   | ONLINE   | 1.8    | 115.5 MB        / 4096.0 MB      
# --------------------------------------------------------------------------------------------------------------------------
```
*   **CPU%**: Porcentaje de uso de CPU del proceso del nodo.
*   **Memory Usage**: Memoria RAM actualmente utilizada por la JVM del nodo.
*   **Status**: `ONLINE` o `OFFLINE` basado en los heartbeats recibidos por el Placement Driver.

**Control de Nodos:**
*   `node list`: Muestra CPU/Memoria y Raft Role de cada nodo.
*   `node raft`: Muestra información detallada sobre los grupos Multi-Raft encontrados.
*   `node stop <node-id>`: Apaga de forma segura el nodo especificado.
*   `node <node-id> stop`: Sintaxis alternativa para el apagado.

- `database list`: List all multi-model databases.

```bash
# List all nodes in the cluster (SQL legacy)
query "SHOW NODES"

# List Raft Groups
query "SHOW GROUPS"
```

### 10. User & Role Management ⭐
Manage users and their permissions across the cluster. 

> [!IMPORTANT]
> User and Role management commands are restricted to users with the **admin** role.

```bash
# Create a new role for a specific database
role create reader_db1 db1 READ

# Create a new role with multiple privileges
role create writer_db1 db1 READ,WRITE

# List all existing roles
role list

#### User Administration
```bash
# Create user
user create <username> <password> <email> [role1,role2]

# Edit user
user edit <username> <new_password> <new_email> [new_role1,new_role2]

# Delete user
user delete <username>

# List users
user list

# Change password
user change-password <username> <old_password> <new_password>
```

**Tipos de Roles Permitidos:**
JettraStoreEngine implementa una jerarquía estricta de 4 roles principales:
- `super-user`: Rol único del usuario `admin`. Acceso total global. Inmutable.
- `admin`: Administrador de base de datos (CREATE, DROP, GRANT).
- `read`: Solo lectura (SELECT/GET).
- `read-write`: Lectura y Escritura (INSERT, UPDATE, DELETE), sin capacidades administrativas.

### 11. Sequential Keys (Sequences) 🔑

Manage persistent counters for generating sequential IDs at the database level. Sequences are cluster-wide but can be filtered by database context.

```bash
# Context Awareness: Use 'use' to target a database for sequence creation and listing
use sales_db

# Create a sequence (auto-associates with 'sales_db')
sequence create user_id_seq --start 1000 --inc 1

# Get next/current value
sequence next user_id_seq
sequence current user_id_seq

# Management
sequence list                 # Lists sequences for the current database context
sequence list --database db2  # Override database filtering
sequence reset user_id_seq 0
sequence delete user_id_seq
```

#### Associating Sequences with Collections
While sequences are database-level objects, they are primarily used to provide unique IDs for Document collections. 

**Best Practice:** Use a naming convention that includes the collection name (e.g., `orders_seq` for an `orders` collection).

**Example Workflow:**
1. Generate the next sequential ID:
   ```bash
   sequence next user_id_seq
   # Output: 1001
   ```
2. Insert a document using that ID:
   ```bash
   mongo db.users.insert({id: 1001, name: 'John Doe'})
   ```

   **Note on Association**: JettraStoreEngine sequences are standalone objects. "Associating" them with a collection is a logical convention maintained by your application logic (e.g., always using `orders_seq` for the `orders` collection). The database does not enforce this relationship.

### 12. Resolve References (Direct Memory Access) 🚀

JettraStoreEngine allows you to automatically resolve references between documents without using JOINs. When a field contains a `jettraID`, JettraStoreEngine can fetch the full object in a single operation.

```bash
# Using SQL with resolution
sql SELECT * FROM users --resolve-refs

# Output Example:
# {
#   "id": "node1/def#uuid1",
#   "name": "Jane",
#   "profile": {
#      "jettraID": "node1/def#profile1",
#      "bio": "Software Engineer"
#   }
# }


# Using MongoDB syntax with resolution
mongo db.users.find('node1/default#u123') --resolve-refs
```

This feature uses the internal `idToCollection` index to perform direct memory access to the referenced objects, making it extremely efficient for normalized data models.

### 13. Query Builder (Interactive) 🛠️
The shell provides an interactive tool to construct complex SQL or MongoDB queries without memorizing the syntax.

```bash
query-builder
```
Follow the interactive prompts to select the engine, collection, and define multiple conditions. The tool will generate the query and offer to execute it immediately.

### 14. Backup & Restore (New) 💾
JettraStoreEngine supports full database backups and restores via the shell.

```bash
# Generate Backup
backup sales_db --format json

# Restore Backup
restore sales_db backup_20240101.json --format json
```

### 15. Java 25 Support ☕
JettraStoreEngine is fully optimized for Java 25. To enable compact object headers for reduced memory footprint, use:
```bash
java -XX:+UseCompactObjectHeaders -jar jettra-shell.jar
```


### 14. Integrated Help System 📖

## Advanced Features

### Auto-completion
The shell supports Tab-completion for commands and collection names.

### Reactivity
The shell is reactive. If a leader changes during your session, the shell automatically reconnects to the new leader via the PD metadata.

## Scripting Mode
You can pass a file with commands to the shell for automation:

## Monitoring via API (CURL)

You can also monitor the cluster status using standard HTTP tools like `curl`. This is useful for integration with external monitoring systems.

### 1. List Nodes
```bash
curl -H "Authorization: Bearer <your-token>" \
     http://localhost:8081/api/monitor/nodes
```

### 2. List Raft Groups
```bash
curl -H "Authorization: Bearer <your-token>" \
     http://localhost:8081/api/monitor/groups
```bash
curl -X GET http://localhost:8080/api/monitor/groups -H "Authorization: Bearer <token>"
```

### Stopping a Node
To gracefully shut down a node via API Proxy (Recommended):

```bash
curl -X POST http://localhost:8081/api/monitor/nodes/<node-id>/stop -H "Authorization: Bearer <token>"
```

Or directly via the new root endpoint on any node:

```bash
curl -X POST http://localhost:<port>/stop -H "Authorization: Bearer <token>"
```

Or directly via PD (legacy):

```bash
curl -X POST http://localhost:8080/api/internal/pd/nodes/<node-id>/stop -H "Authorization: Bearer <token>"
```

### 3. Health Check
```bash
curl http://localhost:8080/api/internal/pd/health
```
# Storage: Optimized LSM Engine

The `jettra-store` module implements an optimized Object Storage layer using Log-Structured Merge-tree (LSM) principles.

## Structure

1. **MemTable:** In-memory buffer for the latest writes. Sorted for fast access.
2. **Commit Log (WAL):** Persistent record of writes for crash recovery.
3. **SSTables:** Immutable on-disk files containing sorted rows.
4. **Compaction:** Background process that merges SSTables to reclaim space and maintain read performance.

## Object Storage Optimization

JettraStoreEngine treats every data point as an object. This allows:
- **Variable Key Sizes:** Flexible indexing.
- **Versioning:** Built-in support for historical data.
- **Compression:** High-ratio compression for cloud-native cost efficiency.

## Interaction with Raft

The storage layer is the "State Machine" in the Raft consensus. When Raft commits an entry, it is applied to the `jettra-store`.

```java
public class JettraStateMachine implements StateMachine {
    @Inject ObjectStorage storage;

    @Override
    public void apply(byte[] data) {
        // Parse and apply to LSM store
        storage.put(key, data);
    }
}
```
# Distributed Transactions in JettraStoreEngine

Ensuring consistency across multiple shards (Raft Groups) using the Two-Phase Commit (2PC) protocol.

## Introduction
In a distributed database like JettraStoreEngine, a single operation (like a bank transfer) might involve data residing in different Raft physical groups. To ensure **Atomicity**, we implement a Transaction Coordinator (TC) that manages the lifecycle of these operations.

## Architecture
- **Coordinator**: Managed by the Placement Driver (PD) or a dedicated `jettra-tx` node.
- **Participants**: Individual Raft Group Leaders.
- **Protocol**: Two-Phase Commit (2PC).

## Transaction Lifecycle (2PC)

### Phase 1: Prepare
1. The client starts a transaction and receives a `txId`.
2. The client performs operations (Save, Update, etc.).
3. The Coordinator sends a **PREPARE** message to all involved Raft groups.
4. Each group locks the records locally and logs a "Prepared" intent in its Raft Log.
5. Participants respond with a vote: `COMMIT` or `ABORT`.

### Phase 2: Commit / Abort
1. If **ALL** participants voted `COMMIT`, the Coordinator sends a **GLOBAL_COMMIT**.
2. If **ANY** participant voted `ABORT` (or timed out), the Coordinator sends a **GLOBAL_ABORT** (Rollback).
3. Participants finalize the changes or release the locks.

## Usage Example (Java API)

```java
@Inject TransactionCoordinator tc;

public Uni<Void> transferMoney(String from, String to, double amount) {
    return tc.begin().chain(txId -> {
        return engine.saveDocument("accounts", from, "{...}")
            .chain(() -> engine.saveDocument("accounts", to, "{...}"))
            // Execute 2PC
            .chain(() -> tc.prepare(txId, List.of(groupA, groupB)))
            .chain(success -> success ? tc.commit(txId) : tc.abort(txId));
    });
}
```

## Saga Pattern (Alternative)
For very long-running transactions where holding locks is not feasible, JettraStoreEngine supports **Sagas** via compensating actions. 

- **Success path**: Op A -> Op B -> Op C.
- **Failure path**: If B fails, execute Undo A.

## Guarantees
- **Acid Compliance**: JettraStoreEngine transactions provide ACID guarantees even in a sharded environment.
- **Fault Tolerance**: If the Coordinator fails during Phase 2, the new Coordinator can reconstruct the state from the participants' status.
# Interfaz Web de JettraStoreEngine

El Dashboard de JettraStoreEngine es una aplicación de una sola página (SPA) moderna, elegante y reactiva, diseñada para ofrecer una visibilidad total sobre el cluster cloud-native y permitir la interacción directa con los diversos motores de base de datos.

## Acceso al Dashboard
La interfaz web se despliega automáticamente con el componente `jettra-web`.
- **URL**: `http://localhost:8081` (Puerto por defecto del componente web)
- **Credenciales**: **Requerido**. El acceso está protegido por JWT.
  - Usuario por defecto: `super-user`
  - Contraseña por defecto: `superuser`

## Jettra Web Vaadin ⭐
JettraStoreEngine ahora incluye un panel de administración moderno construido con **Vaadin**, ofreciendo una una experiencia de usuario enriquecida y componentes interactivos avanzados.

- **URL**: `http://localhost:8082`
- **Tecnología**: Java + Vaadin 24 + Quarkus.
- **Funcionalidades**:
    - **Explorador de Base de Datos**: Vista de árbol para navegar por bases de datos y colecciones.
    - **Monitoreo**: Gráficos y tablas para visualizar el estado del cluster.
    - **Administración**: Gestión de configuraciones y seguridad.

## Secciones y Funcionalidades

### 1. Panel de Control (Overview)
Es la vista general del sistema donde se muestran indicadores clave de rendimiento (KPIs):
- **Nodos del Cluster**: Conteo de nodos activos gestionados por el Placement Driver.
- **Grupos Raft**: Estado de salud de los grupos de consenso y elección de líderes.

### 2. Nodos del Cluster (Nodes)
Muestra una topología detallada de la red JettraStoreEngine en tiempo real:
- **Descubrimiento Automático**: Los nodos se registran automáticamente con el Placement Driver al iniciarse.
- **Estado en Vivo**: Indicadores visuales (Verde/Rojo) para el estado ONLINE/OFFLINE de cada nodo.
- **Roles**: Identificación de nodos **Storage** y su dirección de red.
- **Monitorización de Recursos** ⭐: Al dar clic en el botón de búsqueda/lupa del nodo, se abre un diálogo modal que muestra:
    - **Uso de CPU**: Porcentaje de carga del procesador del nodo.
    - **Uso de Memoria**: Memoria RAM consumida vs Memoria RAM disponible.
    - **Latencia de Señal**: Tiempo transcurrido desde el último latido (heartbeat).
    - **Detener Nodo** 🛑: Botón para enviar una petición remota de parada al nodo (vía PD o directamente al endpoint `/stop`), lo que lo marcará como OFFLINE de forma segura.

### 3. Administración de Bases de Datos (Database Management)
Gestión completa del ciclo de vida de las bases de datos:
- **Explorador de Datos (Sidebar Tree)** ⭐: Un nuevo árbol interactivo en el menú izquierdo que permite:
    - **Navegación Visual**: Listado en tiempo real de todas las bases de datos.
    - **Creación Rápida**: Botón "+" directamente en el encabezado del árbol para abrir el formulario de creación.
    - **Estructura Multi-modelo**: Cada base de datos muestra sub-nodos para sus motores integrados (**Document, Column, Graph, Vector, Object, Files**).
    - **Barras de Opciones Rápidas** ⭐: Al hacer clic en un tipo de motor (ej: Document), se despliega una barra con botones para:
        - **Añadir**: Insertar nuevos documentos, registros, vértices, etc.
        - **Índices**: Gestionar índices específicos para ese modelo de datos.
        - **Reglas**: Configurar reglas de validación y seguridad.
- **Crear**: Provisiona nuevas bases de datos lógicas instantáneamente.
    - **Nombre**: Identificador único de la base de datos.
    - **Contenedor Multi-modelo**: Todas las bases de datos creadas son contenedores multi-modelo por definición, permitiendo alojar colecciones de diferentes tipos (Document, Graph, Vector, etc.).
    - **Tipo de Almacenamiento (Storage)**:
        - **Persistent (Store)**: Los datos se guardan en disco (jettra-store).
        - **In-Memory**: Los datos residen exclusivamente en RAM para baja latencia.
- **Listar**: Visualiza todas las bases de datos registradas indicando su **Motor** y tipo de **Almacenamiento**.
- **Eliminar**: Borra bases de datos y todos sus datos asociados tras confirmación.
- **Gestión de Colecciones (Document Explorer)** ⭐: Dentro del árbol de la base de datos, en cada sección de motor (ej: **Document, Graph, Vector**), ahora puede:
    - **Añadir Colección**: Al dar clic en `+`, se abrirá un modal para ingresar el nombre y confirmar el **Motor (Engine)** especializado. Por defecto se sugiere el motor de la sección desde la que se invoca.
    - **Refrescar**: Sincronizar la lista de colecciones de ese motor específico.
    - **Renombrar/Eliminar**: Opciones rápidas para el ciclo de vida de la colección.
- **Gestión de Secuencias (Sequences Subtree)** ⭐: Un nuevo nodo "🔑 Sequences" aparece bajo cada base de datos.
    - **Gestión Visual**: Permite crear, listar, incrementar y borrar secuencias asociadas a esa base de datos específica sin necesidad de comandos.

### 4. Seguridad y Gestión de Usuarios ⭐
Control centralizado de acceso y roles para todo el cluster:
- **Gestión de Usuarios**: Formulario avanzado para crear y editar cuentas de usuario.
- **Asignación de Roles por Base de Datos** ⭐: El formulario de usuario lista todas las bases de datos disponibles y permite seleccionar un rol específico para cada una (ej: `bob` es `reader` en `db1` pero `writer-reader` en `db2`).
- **Edición de Usuarios**: Permite cambiar la contraseña y los roles de usuarios existentes de forma visual.
- **Roles Predefinidos**:
    - `super-user`: Rol exclusivo del usuario `admin` (built-in). Tiene control total absoluto y no puede ser eliminado ni modificado.
    - `admin`: Administrador de base de datos. Puede gestionar usuarios, crear/editar/eliminar sus bases de datos asignadas. 
    - `read`: Acceso de solo lectura.
    - `read-write`: Acceso de lectura y escritura (sin permisos administrativos).

### 5. Consola de Consultas SQL (Nuevo) ⭐
Permite ejecutar sentencias SQL directamente desde el navegador:
1.  **Editor SQL**: Un área de texto con resaltado (vía fuente monoespaciada) para ingresar comandos `SELECT`, `INSERT`, `UPDATE` o `DELETE`.
2.  **Ejecución Unificada**: Las consultas se envían al Placement Driver, el cual las enruta automáticamente al motor correspondiente (Document, Graph, Vector, etc.) basándose en la base de datos y colección especificada.
3.  **Visualizador de Resultados**: Muestra el resultado de la ejecución en formato JSON estructurado, con indicadores de éxito o error en tiempo real.

### 7. Llaves Secuenciales (Sequences) ⭐
Interfaz visual para la gestión de contadores:
- **Listado de Secuencias**: Vista de tabla con el valor actual, incremento y base de datos de cada secuencia.
- **Creación**: Formulario para provisionar nuevas secuencias con valores iniciales personalizados.
- **Interacción**: Botón "NEXT" para incrementar manualmente y visualizar el cambio de estado en tiempo real.
- **Eliminación**: Capacidad para borrar secuencias persistentes del sistema.

### 8. Resolución de Referencias (Resolve References) ⭐

El Dashboard incluye una opción global para la resolución automática de referencias entre documentos:
- **Casilla "Resolve Refs"**: Disponible en la Consola SQL y en el Explorador de Documentos.
- **Funcionamiento**: Al marcar esta casilla, las consultas que devuelvan documentos con campos que contengan un `jettraID` válido mostrarán el objeto referenciado completo en lugar de solo el ID.
- **Eficiencia**: Utiliza acceso directo a la memoria del cluster para evitar JOINS tradicionales, acelerando la visualización de datos normalizados.

## Procedimiento de Autenticación en la Web

1. Al acceder a la URL, el sistema redirigirá automáticamente a `login.html`.
2. Introduce tus credenciales (`super-user` / `superuser`).
3. El sistema generará un token JWT que se almacenará en el `localStorage` del navegador.
4. El token se enviará automáticamente en la cabecera `Authorization: Bearer <token>` en cada petición a la API.
5. Si el token expira o es inválido, serás redirigido nuevamente al Login.
