# JettraStoreEngine

# JettraStorageEngine \- The Definitive Guide

Welcome to the official documentation for **JettraStorageEngine**, the next-generation, cloud-native, multi-model database engine built natively on the JettraStack ecosystem.

## Table of Contents

1. [Introduction & Architecture](#1-introduction--architecture)  
2. [Compilation & Execution](#2-compilation--execution)  
3. [Administration, Security & Monitoring](#3-administration-security--monitoring)  
4. [Performance Optimization](#4-performance-optimization)  
5. [Advanced Operations (Queries, Deletions)](#5-advanced-operations)  
6. [Driver Patterns (Fluent API vs Repository)](#6-driver-patterns)  
7. [Comprehensive Business Examples](#7-comprehensive-business-examples)  
8. [Multi-Model Engine Exhaustive Examples](#8-multi-model-engine-exhaustive-examples)

---

## 1\. Introduction & Architecture

JettraStorageEngine is a highly optimized, lightweight database built on Java 25\. It leverages **Java Compact Headers** and **ZGC (Z Garbage Collector)** for maximum memory efficiency.

### Hybrid LSM \+ B-Tree Storage Engine

To achieve unparalleled read/write speeds, the core engine uses a hybrid architecture:

- **LSM (Log-Structured Merge-tree)**: Absorbs massive write workloads into memory (MemTables).  
- **B-Tree**: Once memory flushes to disk, data is indexed in highly optimized B-Tree blocks within `.jettra` files, ensuring fast point lookups, pagination, and range scans.

### Document Versioning (History)

Every write operation generates a **new version**. This append-only design provides a built-in **history of changes**, allowing you to effortlessly restore a document to any previous point in time.

---

### Execute locally (Standalone)

Run the generated JAR directly. Node IP configuration and Raft peers can be managed via the `src/main/resources/jettrastorengine.properties` file:

jettra.cluster.peers=192.168.1.100:50051,192.168.1.101:50051,192.168.1.102:50051

```shell
java -XX:+UseZGC -XX:+ZGenerational --enable-preview -jar target/JettraStoreEngine-1.0-SNAPSHOT.jar
```

*To start a cluster (Raft Consensus): `docker-compose up -d --build`*

---

## 2\. Compilation & Execution

```shell
mvn clean package -DskipTests

java -XX:+UseZGC -XX:+ZGenerational --enable-preview -jar target/JettraStoreEngine-1.0-SNAPSHOT.jar
```

*To start a cluster (Raft Consensus): `docker-compose up -d --build`*

---

## 3\. Administration, Security & Monitoring

The default user is `admin/admin` (requires immediate password change). Use the **JettraWUI ([http://localhost:8080/wui](http://localhost:8080/wui))** to visualize the 3D Raft topology, manage users, monitor ZGC Heap and Disk IO, and execute Cron backups.

---

## 4\. Performance Optimization

To ensure optimal performance over time, JettraStorageEngine incorporates several background optimizations:

- **LSM Compaction Service**: Automatically merges immutable SSTables in the background. This turns fragmented sequential writes into perfectly balanced B-Trees, removing tombstones and optimizing read speeds.  
- **Bloom Filters**: Pre-filters disk reads to guarantee `O(1)` memory lookups before touching disk blocks.  
- **ZGC Tuning**: Pauses are strictly sub-millisecond, preventing any GC-induced lag during high-throughput ingestion.

---

## 5\. Advanced Operations

### Paginación Optimizada (Keyset)

\-- Scales to O(log N). Never use OFFSET\!

FIND IN users WHERE status \= 'ACTIVE' AFTER id '1000' LIMIT 50;

### Deletions & Subqueries

\-- Writes a tombstone

DELETE FROM users WHERE id \= 123;

\-- Subqueries

SELECT name FROM users WHERE department\_id IN (SELECT id FROM departments WHERE tier \= 'PREMIUM');

### Aggregations

SELECT department, AVG(salary), COUNT(id) FROM employees GROUP BY department HAVING COUNT(id) \> 10;

---

## 6\. Driver Installation & Patterns

### 6.1. Installation & Imports

Before adopting any patterns, you need to install the respective driver for your language.

**Java (Maven)** Add the dependency to your `pom.xml`:

\<dependency\>

    \<groupId\>com.jettra\</groupId\>

    \<artifactId\>JettraStoreDriverJava\</artifactId\>

    \<version\>1.0-SNAPSHOT\</version\>

\</dependency\>

import com.jettra.driver.JettraClient;

import com.jettra.driver.models.Document;

**Python (pip)** Install via pip or add to `requirements.txt`:

pip install jettra-driver

from jettra import JettraClient

**Golang (go get)** Fetch the module via `go get`:

go get github.com/jettra/jettra-driver-go

import (

    "github.com/jettra/jettra-driver-go/jettra"

)

### 6.2. Java Driver Patterns

When using the Java Driver, you can adopt two main architectures:

#### Fluent API Pattern

Ideal for functional, dynamic queries.

client.database("mydb").collection("users")

      .filter(eq("status", "ACTIVE"))

      .sort(asc("createdAt"))

      .limit(50)

      .execute();

### Repository Pattern (Recommended)

Ideal for enterprise Spring/Jakarta environments.

public class PersonaRepository {

    private final Collection\<Persona\> col;

    public PersonaRepository(JettraClient client) {

        this.col \= client.database("mydb").collection("personas", Persona.class);

    }

    public void save(Persona p) { col.insert(p); }

    public List\<Persona\> findAll(String lastId, int limit) {

        return col.query().after(lastId).limit(limit).list();

    }

}

---

## 7\. Comprehensive Business Examples

### 7.1. CRUD `Persona` (with Pagination)

// 1\. Definition

public record Persona(String id, String nombre, int edad) {}

// 2\. Create (Insert)

repo.save(new Persona("P001", "Alice", 25));

// 3\. Read (Find with Pagination using B-Tree Keyset)

List\<Persona\> page1 \= repo.findAll(null, 10);

String lastSeenId \= page1.get(page1.size() \- 1).id();

List\<Persona\> page2 \= repo.findAll(lastSeenId, 10);

// 4\. Update

repo.save(new Persona("P001", "Alice Smith", 26)); // New Version

// 5\. Delete

repo.delete("P001"); // Writes tombstone

### 7.2. Master-Detail (Facturación / Invoicing)

A complete business operation combining embedded documents and KV engine updates in a single transaction.

public record LineaFactura(String articuloId, int cantidad, double precioTotal) {}

public record Factura(String numero, String clienteId, List\<LineaFactura\> lineas) {}

client.transaction(tx \-\> {

    Factura factura \= new Factura("F-100", "C-500", List.of(

        new LineaFactura("A-1", 2, 50.0),

        new LineaFactura("A-2", 1, 100.0)

    ));

    

    // 1\. Save Master

    tx.database("sales").collection("facturas").insert(factura);

    

    // 2\. Update Details (Decrease stock in KV engine)

    tx.database("sales").kv("stock").decrement("A-1", 2);

    tx.database("sales").kv("stock").decrement("A-2", 1);

});

---

## 8\. Multi-Model Engine Exhaustive Examples

Before executing commands, you must connect: **Shell**: `./jettra-shell connect --host localhost --port 9090 -u admin -p admin` **Java**: `JettraClient client = JettraClient.builder().host("localhost").port(9090).credentials("admin","admin").build();` **Python**: `client = JettraClient("localhost:9090", "admin", "admin")` **Go**: `client, _ := jettra.Connect("localhost:9090", "admin", "admin")`

### 8.1. Document Engine

**Use Case:** Highly structured/unstructured JSON data with references and historical versioning.

**Document References Example:**

- **Shell**:  
    
  CREATE COLLECTION companies ENGINE \= DOCUMENT;  
    
  CREATE COLLECTION users ENGINE \= DOCUMENT;  
    
  INSERT INTO companies {"id": "C1", "name": "TechCorp"};  
    
  INSERT INTO users {"id": 1, "name": "Bob", "company": REF("companies", "C1")};  
    
  \-- Restore version  
    
  RESTORE DOCUMENT "1" FROM users TO VERSION 1718223344;  
    
- **Java**:  
    
  client.database("mydb").collection("users").insert(new Document()  
    
      .put("id", 1).put("name", "Bob")  
    
      .put("company", new DBRef("companies", "C1")));  
    
- **Python**:  
    
  db.collection("users").insert({"id": 1, "name": "Bob", "company": {"$ref": "companies", "id": "C1"}})  
    
- **Go**:  
    
  db.Collection("users").Insert(ctx, map\[string\]interface{}{  
    
      "id": 1, "name": "Bob", "company": jettra.DBRef{"companies", "C1"},  
    
  })

### 8.2. Vector Engine (JVector)

**Use Case:** LLM Embeddings, Similarity/Semantic searches using KNN.

- **Shell**:  
    
  CREATE COLLECTION embeddings ENGINE \= VECTOR DIMENSIONS \= 3;  
    
  INSERT INTO embeddings VECTOR \[0.1, 0.5, 0.9\] METADATA {"word":"AI"};  
    
  FIND SIMILAR TO \[0.1, 0.4, 0.8\] IN embeddings LIMIT 5;  
    
- **Java**: `client.database("mydb").collection("embeddings").vectorSearch(new float[]{0.1f, 0.5f, 0.9f}, 5);`  
- **Python**: `db.collection("embeddings").vector_search([0.1, 0.5, 0.9], top_k=5)`  
- **Go**: `db.Collection("embeddings").VectorSearch([]float32{0.1, 0.5, 0.9}, 5)`

### 8.3. Graph Engine

**Use Case:** Deep relationships, social networks, fraud detection.

- **Shell**:  
    
  CREATE COLLECTION social ENGINE \= GRAPH;  
    
  INSERT NODE (User {name: "Alice"});  
    
  INSERT NODE (User {name: "Bob"});  
    
  CREATE EDGE (Alice)-\[FRIENDS\_WITH {since: 2024}\]-\>(Bob);  
    
- **Java**: `client.database("mydb").graph("social").addEdge("Alice", "FRIENDS_WITH", "Bob").set("since", 2024);`  
- **Python**: `db.graph("social").add_edge("Alice", "FRIENDS_WITH", "Bob", properties={"since": 2024})`  
- **Go**: `db.Graph("social").AddEdge("Alice", "FRIENDS_WITH", "Bob", map[string]interface{}{"since": 2024})`

### 8.4. Key-Value Engine

**Use Case:** Session storage, ultra-fast cache via MemTable.

- **Shell**:  
    
  CREATE COLLECTION cache ENGINE \= KEY\_VALUE;  
    
  PUT "session\_123" \= "user\_data\_json";  
    
  GET "session\_123";  
    
  DELETE "session\_123" FROM cache;  
    
- **Java**: `client.database("mydb").kv("cache").put("session_123", "user_data_json");`  
- **Python**: `db.kv("cache").put("session_123", "user_data_json")`  
- **Go**: `db.KV("cache").Put("session_123", "user_data_json")`

### 8.5. Time-Series Engine

**Use Case:** Metrics, logs, immutable events.

- **Shell**:  
    
  CREATE COLLECTION cpu\_metrics ENGINE \= TIME\_SERIES;  
    
  INSERT INTO cpu\_metrics TIMESTAMP NOW VALUE {"host": "server-1", "load": 45};  
    
  SELECT \* FROM cpu\_metrics WHERE TIMESTAMP BETWEEN NOW \- 1h AND NOW;  
    
- **Java**: `client.database("mydb").timeseries("cpu_metrics").append(System.currentTimeMillis(), new Data("load", 45));`  
- **Python**: `db.timeseries("cpu_metrics").append(time.time(), {"load": 45})`  
- **Go**: `db.TimeSeries("cpu_metrics").Append(time.Now(), map[string]interface{}{"load": 45})`

### 8.6. Column Engine

**Use Case:** Analytics, OLAP workloads, bulk aggregations.

- **Shell**:  
    
  CREATE COLLECTION analytics ENGINE \= COLUMN;  
    
  INSERT INTO analytics COLUMNS (age, salary, region) VALUES (30, 50000, "NY");  
    
  SELECT SUM(salary) FROM analytics WHERE region \= "NY";  
    
- **Java**: `client.database("mydb").column("analytics").aggregate(Aggregation.SUM, "salary").where(eq("region", "NY"));`  
- **Python**: `db.column("analytics").aggregate("SUM", "salary", where={"region": "NY"})`  
- **Go**: `db.Column("analytics").Aggregate("SUM", "salary", jettra.Where("region", "NY"))`

### 8.7. Object Engine

**Use Case:** Zero-serialization native Java Records (Eclipse Store style).

- **Shell**: *Only visible via WUI or Java Native Drivers.*  
- **Java Only**:  
    
  public record ConfigMap(String env, int threads) {}  
    
  // Create  
    
  client.database("mydb").objectStore("system").store(new ConfigMap("PROD", 16));  
    
  // Read  
    
  ConfigMap cfg \= client.database("mydb").objectStore("system").fetch(ConfigMap.class);

### 8.8. Geospatial Engine

**Use Case:** Proximity searches, map queries.

- **Shell**:  
    
  CREATE COLLECTION maps ENGINE \= GEOSPATIAL;  
    
  INSERT INTO maps POINT(40.7128, \-74.0060) AS "New York";  
    
  FIND NEAR POINT(40.7, \-74.0) IN maps DISTANCE 10km;  
    
- **Java**: `client.database("mydb").geo("maps").findNear(40.7, -74.0, 10, Unit.KILOMETERS);`  
- **Python**: `db.geo("maps").find_near(40.7, -74.0, 10, "km")`  
- **Go**: `db.Geo("maps").FindNear(40.7, -74.0, 10, "km")`

---

## 9\. Advanced Enterprise Features (Core)

Based on the advanced design inherited by Jettra, the database implements the following core systems:

### 9.1. Distributed Sequences

JettraStoreEngine supports persistent, monotonically increasing internal counters called **Sequences**. They are managed across the Raft cluster to guarantee no gaps.

// Java Driver Example

long nextId \= client.nextSequenceValue("user\_id\_seq").await().indefinitely();

### 9.2. Distributed Transactions

The database implements a robust Two-Phase Commit (2PC) over Raft.

client.transaction(tx \-\> {

    tx.database("db").collection("users").insert(doc1);

    tx.database("db").kv("cache").put("status", "updated");

}); // Commits automatically, or rollbacks on exception

### 9.3. Global Auditing System

A built-in `AuditService` tracks all critical operations. Every state change (Prepare, Commit, Abort) in a distributed transaction is automatically logged in an append-only, immutable structure, making it perfect for financial and compliance applications.

---

## 10\. Complete Application Example: Library Management

This section provides a complete, end-to-end example of building a Library application (managing Books, Authors, and Publishers using Document references) in Java, Python, and Golang.

### 10.1. Java (Maven)

**1\. `pom.xml`**

\<project\>

    \<modelVersion\>4.0.0\</modelVersion\>

    \<groupId\>com.example\</groupId\>

    \<artifactId\>library-app\</artifactId\>

    \<version\>1.0\</version\>

    

    \<dependencies\>

        \<dependency\>

            \<groupId\>com.jettra\</groupId\>

            \<artifactId\>JettraStoreDriverJava\</artifactId\>

            \<version\>1.0-SNAPSHOT\</version\>

        \</dependency\>

    \</dependencies\>

\</project\>

**2\. `Main.java`**

import com.jettra.driver.JettraClient;

import com.jettra.driver.models.DBRef;

import java.util.List;

public class LibraryApp {

    // Domain Records

    public record Author(String id, String name, String country) {}

    public record Publisher(String id, String name) {}

    public record Book(String id, String title, DBRef author, DBRef publisher) {}

    public static void main(String\[\] args) {

        // 1\. Connect to JettraStoreEngine

        JettraClient client \= JettraClient.builder()

            .host("localhost").port(9090)

            .credentials("admin", "admin")

            .build();

        var db \= client.database("library\_db");

        var authors \= db.collection("authors", Author.class);

        var publishers \= db.collection("publishers", Publisher.class);

        var books \= db.collection("books", Book.class);

        // 2\. CREATE

        authors.insert(new Author("A1", "Gabriel Garcia Marquez", "Colombia"));

        publishers.insert(new Publisher("P1", "Editorial Sudamericana"));

        

        books.insert(new Book("B1", "Cien Años de Soledad", 

            new DBRef("authors", "A1"), 

            new DBRef("publishers", "P1")));

        // 3\. READ

        Book book \= books.findById("B1");

        System.out.println("Found Book: " \+ book.title());

        // 4\. UPDATE

        books.update("B1", new Book("B1", "Cien Años de Soledad (Edición Especial)", 

            new DBRef("authors", "A1"), 

            new DBRef("publishers", "P1")));

        // 5\. DELETE

        books.delete("B1");

        System.out.println("Book deleted successfully.");

    }

}

### 10.2. Python

**1\. Installation**

pip install jettra-driver

**2\. `main.py`**

from jettra import JettraClient

\# 1\. Connect

client \= JettraClient("localhost:9090", "admin", "admin")

db \= client.database("library\_db")

\# Collections

authors \= db.collection("authors")

publishers \= db.collection("publishers")

books \= db.collection("books")

\# 2\. CREATE

authors.insert({"id": "A1", "name": "Gabriel Garcia Marquez", "country": "Colombia"})

publishers.insert({"id": "P1", "name": "Editorial Sudamericana"})

books.insert({

    "id": "B1", 

    "title": "Cien Años de Soledad",

    "author": {"$ref": "authors", "id": "A1"},

    "publisher": {"$ref": "publishers", "id": "P1"}

})

\# 3\. READ

book \= books.find\_by\_id("B1")

print(f"Found Book: {book\['title'\]}")

\# 4\. UPDATE

books.update("B1", {"title": "Cien Años de Soledad (Edición Especial)"})

\# 5\. DELETE

books.delete("B1")

print("Book deleted successfully.")

### 10.3. Golang

**1\. Initialization**

go mod init library-app

go get github.com/jettra/jettra-driver-go

**2\. `main.go`**

package main

import (

	"context"

	"fmt"

	"github.com/jettra/jettra-driver-go/jettra"

)

type Author struct {

	ID      string \`json:"id"\`

	Name    string \`json:"name"\`

	Country string \`json:"country"\`

}

type Publisher struct {

	ID   string \`json:"id"\`

	Name string \`json:"name"\`

}

type Book struct {

	ID        string       \`json:"id"\`

	Title     string       \`json:"title"\`

	Author    jettra.DBRef \`json:"author"\`

	Publisher jettra.DBRef \`json:"publisher"\`

}

func main() {

	ctx := context.Background()

	// 1\. Connect

	client, err := jettra.Connect("localhost:9090", "admin", "admin")

	if err \!= nil {

		panic(err)

	}

	defer client.Close()

	db := client.Database("library\_db")

	// 2\. CREATE

	db.Collection("authors").Insert(ctx, Author{ID: "A1", Name: "Gabriel Garcia Marquez", Country: "Colombia"})

	db.Collection("publishers").Insert(ctx, Publisher{ID: "P1", Name: "Editorial Sudamericana"})

	book := Book{

		ID:        "B1",

		Title:     "Cien Años de Soledad",

		Author:    jettra.DBRef{Collection: "authors", ID: "A1"},

		Publisher: jettra.DBRef{Collection: "publishers", ID: "P1"},

	}

	db.Collection("books").Insert(ctx, book)

	// 3\. READ

	var result Book

	db.Collection("books").FindByID(ctx, "B1", \&result)

	fmt.Printf("Found Book: %s\\n", result.Title)

	// 4\. UPDATE

	result.Title \= "Cien Años de Soledad (Edición Especial)"

	db.Collection("books").Update(ctx, "B1", result)

	// 5\. DELETE

	db.Collection("books").Delete(ctx, "B1")

	fmt.Println("Book deleted successfully.")

}

---

*Powered by JettraStack. Designed for extreme multimodality and performance.*

---

## 5\. Ejemplos de uso de los Drivers (Java, Python, Golang)

JettraStoreEngine soporta integración nativa mediante drivers para múltiples lenguajes, facilitando el desarrollo en ecosistemas políglotas.

### Driver Java (`JettraStoreDriverJava`)

El driver oficial de Java está diseñado para integrarse fácilmente a cualquier proyecto Maven/Gradle.

import com.jettra.driver.java.JettraClient;

public class JavaApp {

    public static void main(String\[\] args) {

        try {

            // 1\. Conectar al cluster principal

            JettraClient client \= new JettraClient("127.0.0.1", 8082);

            client.connect();

            

            // 2\. Autenticación (Requerido)

            if (client.login("super-user", "superUserZ")) {

                System.out.println("Login exitoso\!");

                

                // 3\. Insertar Documento

                String jsonDoc \= "{\\"name\\":\\"Alice\\", \\"role\\":\\"Admin\\"}";

                client.insertDocument("users", "usr\_101", jsonDoc);

                

                // 4\. Recuperar Documento

                String retrieved \= client.getDocument("users", "usr\_101");

                System.out.println("Usuario: " \+ retrieved);

            }

            

            client.close();

        } catch (Exception e) {

            e.printStackTrace();

        }

    }

}

### Driver Python (`JettraStoreDriverPython`)

El cliente en Python es ligero y soporta manejo de sesiones usando `requests`.

from jettra\_driver.client import JettraClient

\# 1\. Instanciar y conectar

client \= JettraClient("http://127.0.0.1:8082")

\# 2\. Login

if client.login("super-user", "superUserZ"):

    print("Conectado a JettraStore")

    

    \# 3\. Insertar

    doc \= {"name": "Bob", "role": "Developer"}

    client.insert("users", "usr\_102", doc)

    

    \# 4\. Leer

    result \= client.get("users", "usr\_102")

    print("Documento:", result)

    

    \# 5\. Borrar

    client.delete("users", "usr\_102")

### Driver Golang (`JettraStoreDriverGo`)

El driver en Go es ideal para microservicios y sistemas de alto rendimiento, gestionando conexiones de forma concurrente.

package main

import (

    "fmt"

    "log"

    "github.com/jettra/jettra-go-driver"

)

func main() {

    client := jettra.NewClient("http://127.0.0.1:8082")

    

    err := client.Login("super-user", "superUserZ")

    if err \!= nil {

        log.Fatal(err)

    }

    

    doc := map\[string\]interface{}{

        "name": "Charlie",

        "role": "QA",

    }

    

    client.Insert("users", "usr\_103", doc)

    

    result, \_ := client.Get("users", "usr\_103")

    fmt.Printf("Recuperado: %v\\n", result)

}

---

## 6\. Configuración de Clúster Raft (Alta Disponibilidad)

JettraStoreEngine garantiza resiliencia y alta disponibilidad agrupando los nodos en un clúster manejado mediante el algoritmo de consenso **Raft (Apache Ratis)**.

### Despliegue con Docker Compose

La forma más sencilla de gestionar el clúster es mediante contenedores Docker. Revisa el archivo `docker-compose.yml` incluido en la raíz de `JettraStoreEngine`.

Para iniciar un clúster de 3 nodos (1 Líder y 2 Seguidores):

docker-compose up \-d

### Orquestación y Variables

Cada nodo en el clúster requiere conocer a sus pares para poder elegir al líder. Esto se configura inyectando las siguientes variables de entorno:

- `NODE_ID`: El identificador único del nodo (ej. `node1`, `node2`, `node3`).  
- `RAFT_PEERS`: La lista de pares en la red Raft (formato `id,ip:port`).  
- `JETTRA_DATA_DIR`: Ruta de almacenamiento local para los archivos del nodo.  
- `JETTRA_DB_PORT`: El puerto donde escucha la API REST (ej. `8082`, `8083`).

Cualquier escritura (Inserción/Actualización) que envíes al controlador REST será automáticamente redirigida y evaluada por el clúster Raft. Solo si la mayoría (Quórum) está de acuerdo, la transacción se almacena físicamente, brindando total seguridad ante caídas de hardware.

---

## 7\. Panel de Administración Web (JettraWUI)

Para facilitar las operaciones, el motor incluye un panel de control interactivo (Web UI) accesible desde cualquier navegador.

### Cómo Acceder

1. Ejecuta JettraStoreEngine (`java -jar target/JettraStoreEngine-1.0-SNAPSHOT.jar`).  
2. Abre tu navegador web y dirígete a: `http://localhost:8082/wui/` (Asegúrate de cambiar el puerto si utilizaste `jettra.node.port` distinto).

### Funcionalidades del Portal

- **Autenticación (JWT)**: Ingresa las credenciales del sistema (por defecto `super-user` / `superUserZ`).  
- **Exploración Multimodelo**: Tras iniciar sesión, dispones de un menú desplegable (Dropdown) para seleccionar qué Motor quieres consultar (`document`, `vector`, `graph`, `timeseries`, `column`, `keyvalue`, `geospatial` u `object`).  
- **Resultados en Tiempo Real**: Introduce el nombre de la Colección y el ID, y el panel te devolverá la estructura JSON exacta almacenada en el clúster (si existe).

El portal fue construido utilizando HTML puro, CSS y JavaScript sin dependencias externas pesadas, garantizando una administración ligera, remota y fluida.

---

## 8\. Soporte Multimodelo Extendido (8 Motores)

JettraStoreEngine soporta **8 modelos de datos** distintos sobre su misma arquitectura base. Todos los drivers incluyen un método genérico (por ejemplo, `insertModel` o `InsertModel`) al que debes pasarle el tipo de modelo: `vector`, `graph`, `document`, `column`, `keyvalue`, `timeseries`, `geospatial` o `object`.

### Ejemplos en Java

// 1\. Vector (Embeddings KNN)

client.insertModel("vector", "ai\_docs", "vec\_1", "{\\"vector\\": \[0.2, 0.5, 0.8\]}");

// 2\. Graph (Nodos y Aristas)

client.insertModel("graph", "social", "node\_alice", "{\\"name\\":\\"Alice\\", \\"age\\":30}");

// 3\. Document (JSON validable con JettraRules)

client.insertModel("document", "users", "usr\_1", "{\\"\_class\\":\\"User\\", \\"name\\":\\"Bob\\"}");

// 4\. Column (Familias de Columnas)

client.insertModel("column", "metrics\_cf", "row\_a", "{\\"cf1:col1\\":\\"val1\\"}");

// 5\. Key-Value (Almacenamiento ultra-rápido)

client.insertModel("keyvalue", "cache", "session\_123", "token\_abc\_xyz");

// 6\. Time-Series (Métricas temporales)

client.insertModel("timeseries", "cpu\_usage", "ts\_1", "{\\"cpu\\": 45.5}");

// 7\. Geospatial (Coordenadas y metadata)

client.insertModel("geospatial", "places", "poi\_1", "{\\"lat\\":40.71, \\"lon\\":-74.00, \\"name\\":\\"NY\\"}");

// 8\. Object (Serialización de estados Java/JSON)

client.insertModel("object", "states", "obj\_1", "{\\"score\\":100, \\"level\\":5}");

### Ejemplos en Python

\# Insertando en los 8 motores

client.insert\_model("vector", "ai\_docs", "vec\_1", {"vector": \[0.2, 0.5, 0.8\]})

client.insert\_model("graph", "social", "node\_alice", {"name": "Alice"})

client.insert\_model("document", "users", "usr\_1", {"\_class": "User", "name": "Bob"})

client.insert\_model("column", "metrics\_cf", "row\_a", {"cf1:col1": "val1"})

client.insert\_model("keyvalue", "cache", "session\_123", "token\_abc\_xyz")

client.insert\_model("timeseries", "cpu\_usage", "ts\_1", {"cpu": 45.5})

client.insert\_model("geospatial", "places", "poi\_1", {"lat": 40.71, "lon": \-74.00, "name": "NY"})

client.insert\_model("object", "states", "obj\_1", {"score": 100, "level": 5})

\# Recuperando de un motor específico

data \= client.get\_model("timeseries", "cpu\_usage", "ts\_1")

print(data)

### Ejemplos en Golang

// Enviar un Payload JSON

doc := \`{"vector": \[0.2, 0.5, 0.8\]}\`

client.InsertModel("vector", "ai\_docs", "vec\_1", doc)

graphDoc := \`{"name": "Alice"}\`

client.InsertModel("graph", "social", "node\_alice", graphDoc)

// ... Y de igual forma para document, column, keyvalue, timeseries, geospatial, object

// Para Key-Value, enviar string plano:

client.InsertModel("keyvalue", "cache", "session\_123", "token\_abc\_xyz")

---

## 9\. Uso de JettraStoreShell (CLI Multimodelo)

JettraStack provee un cliente interactivo por consola (`JettraStoreShell`) para administrar el clúster directamente desde la terminal. A diferencia de las fases tempranas, ahora el shell acepta operaciones para los 8 motores:

1. Levanta el Shell: `java -jar JettraStoreShell-1.0-SNAPSHOT.jar`  
2. Conéctate: `connect 127.0.0.1 8082`  
3. Inicia sesión: `login super-user superUserZ`  
4. Insertar en cualquier modelo: `insert <model> <collection> <id> <json>` *Ejemplo:* `insert graph red_social nodo1 {"nombre":"Alicia"}`  
5. Consultar cualquier modelo: `get <model> <collection> <id>` *Ejemplo:* `get graph red_social nodo1`



## JettraStoreEngine Web Administration UI
JettraStoreEngine now features a built-in futuristic Web User Interface accessible at `http://<direccion>:<puerto>/wui` (using the `jettra.node.port`). This interface provides a powerful, aesthetic dashboard (defaulting to the Astro-inspired `BlackStart` theme) to manage the engine.
Capabilities include:
- **Database Management**: View and manage all database models (Document, Vector, Graph, TimeSeries, Column, KeyValue, Geospatial, Object).
- **User and Privilege Administration**: Easily manage users, roles, and privileges.
- **Cluster Node Monitoring**: View connected nodes, active node status (Master), and network health. (Note: Only the Master node can execute insertions, edits, deletions, stop nodes, or add new nodes).
- **Resource Consumption**: Monitor real-time RAM and Disk consumption, including disk space occupied per database.
- **Backup and Restore**: Perform backups and trigger restores directly from the UI.
- **Rule and Trigger Management**: Support for JettraStore rules and history of database changes.
