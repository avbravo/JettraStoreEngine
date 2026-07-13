# Guía de Ejemplos y Primeros Pasos con JettraStorageEngine

Esta guía detalla paso a paso cómo preparar tu entorno de desarrollo, instalar los drivers y escribir tu primer programa conectándote a **JettraStorageEngine**. Incluye ejemplos básicos de CRUD (Crear, Leer, Actualizar, Borrar) y casos de uso avanzados como **Patrón Repository, Fluent API, Agregaciones y Transacciones**.

---

## 1. Java (con Maven)

### Requisitos Previos
1. Instalar **Java Development Kit (JDK) 21** o superior.
2. Instalar **Apache Maven**.

### Paso 1: Crear el proyecto y dependencias
En `pom.xml`:
```xml
<dependencies>
    <dependency>
        <groupId>com.jettra</groupId>
        <artifactId>JettraStoreDriverJava</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### Paso 2: Código Base (CRUD Básico)

```java
import com.jettra.driver.JettraClient;
import com.jettra.driver.models.DBRef;

public class CrudExample {
    public record Author(String id, String name, String country) {}
    public record Book(String id, String title, DBRef author) {}

    public static void main(String[] args) {
        JettraClient client = JettraClient.builder()
            .host("localhost").port(9090)
            .credentials("admin", "admin").build();

        var db = client.database("library_db");
        var books = db.collection("books", Book.class);

        // 1. Crear (Insert)
        books.insert(new Book("B1", "Cien Años de Soledad", new DBRef("authors", "A1")));

        // 2. Leer (Read)
        Book book = books.findById("B1");
        System.out.println("Libro encontrado: " + book.title());

        // 3. Actualizar (Update)
        books.update("B1", new Book("B1", "Cien Años (Ilustrada)", new DBRef("authors", "A1")));

        // 4. Eliminar (Delete)
        books.delete("B1");
    }
}
```

---

## 2. Java: Características Avanzadas

### 2.1 Patrón Repository

El driver Java permite construir repositorios fuertemente tipados.

```java
import com.jettra.driver.java.JettraRepository;

public class RepositoryExample {
    public record Planet(String id, String name, double mass, boolean hasRings) {}

    public static void main(String[] args) {
        JettraClient client = new JettraClient("127.0.0.1", 8080);
        client.connect();
        
        // Instanciar un Repository para el Record Planet
        JettraRepository<Planet> planetRepo = client.repository(Planet.class, "DOCUMENT", "planets");
        
        Planet mars = new Planet("p_001", "Mars", 0.107, false);
        planetRepo.save(mars.id(), mars);
        
        planetRepo.findById("p_001").ifPresent(planet -> {
            System.out.println("Recuperado: " + planet.name());
        });
    }
}
```

### 2.1.2 Patrón Repository con Interfaces

Para proyectos más estructurados, se recomienda definir contratos mediante interfaces, lo cual facilita los tests (Mocks) y la inyección de dependencias.

```java
import com.jettra.driver.java.JettraRepository;
import java.util.List;
import java.util.Optional;

// 1. Definir la entidad
public record User(String id, String username, String email, boolean active) {}

// 2. Definir la Interfaz del Repositorio
public interface UserRepository extends JettraRepository<User> {
    List<User> findByActiveTrue();
    User findByEmail(String email);
}

// 3. Implementar la Interfaz
public class UserRepositoryImpl implements UserRepository {
    private final JettraRepository<User> baseRepo;
    private final JettraClient client;

    public UserRepositoryImpl(JettraClient client) {
        this.client = client;
        // Instanciamos el repositorio base manejado por Jettra
        this.baseRepo = client.repository(User.class, "DOCUMENT", "users");
    }

    // Métodos delegados del contrato base JettraRepository
    @Override
    public void save(String id, User entity) { baseRepo.save(id, entity); }

    @Override
    public Optional<User> findById(String id) { return baseRepo.findById(id); }

    @Override
    public void delete(String id) { baseRepo.delete(id); }

    @Override
    public JettraClient getClient() { return this.client; }

    // Implementación de métodos de dominio usando Fluent API
    @Override
    public List<User> findByActiveTrue() {
        return client.document().collection("users")
            .find().where("active").eq(true).execute()
            .stream().map(doc -> new User(
                (String) doc.get("id"), (String) doc.get("username"), 
                (String) doc.get("email"), (Boolean) doc.get("active")
            )).toList();
    }

    @Override
    public User findByEmail(String email) {
        var results = client.document().collection("users")
            .find().where("email").eq(email).limit(1).execute();
            
        if (results.isEmpty()) return null;
        var doc = results.get(0);
        return new User(
            (String) doc.get("id"), (String) doc.get("username"), 
            (String) doc.get("email"), (Boolean) doc.get("active")
        );
    }
}

// 4. Uso en la aplicación
public class InterfaceRepositoryExample {
    public static void main(String[] args) {
        JettraClient client = new JettraClient("127.0.0.1", 8080);
        client.connect();
        
        UserRepository userRepo = new UserRepositoryImpl(client);
        
        // Crear
        userRepo.save("u_1", new User("u_1", "alice", "alice@example.com", true));
        
        // Consultar con método personalizado
        User found = userRepo.findByEmail("alice@example.com");
        if (found != null) {
            System.out.println("Usuario encontrado: " + found.username());
        }
    }
}
```

### 2.2 Uso de la API Fluida (Fluent API)

Si necesitas realizar consultas dinámicas sin acoplarte a un Repository estático, la API Fluida permite encadenar condiciones fácilmente.

```java
public class FluentAPIExample {
    public static void main(String[] args) {
        JettraClient client = new JettraClient("127.0.0.1", 8080);
        
        // Búsqueda fluida
        client.document().collection("planets")
            .find()
            .where("hasRings").eq(true)
            .and("mass").gt(50.0)
            .orderBy("name", "ASC")
            .limit(5)
            .execute()
            .forEach(doc -> System.out.println("Planeta grande con anillos: " + doc.get("name")));
    }
}
```

### 2.3 Agregaciones y Analítica

Construye pipelines de agregación para obtener analíticas directas en el motor de base de datos.

```java
public class AggregationExample {
    public static void main(String[] args) {
        JettraClient client = new JettraClient("127.0.0.1", 8080);
        
        // Agrupando ventas por categoría
        String pipeline = "[" +
            "{\"$match\": {\"status\": \"COMPLETED\"}}," +
            "{\"$group\": {\"_id\": \"$category\", \"totalSales\": {\"$sum\": \"$amount\"}}}" +
        "]";

        List<Object> results = client.aggregate("orders", pipeline).await().indefinitely();
        
        for(Object result : results) {
            System.out.println("Resultado Agregación: " + result);
        }
    }
}
```

### 2.4 Transacciones y Rollback

Para casos donde múltiples operaciones en distintas colecciones deben ser atómicas.

```java
import com.jettra.driver.java.TransactionCoordinator;

public class TransactionExample {
    public static void main(String[] args) {
        JettraClient client = new JettraClient("127.0.0.1", 8080);
        TransactionCoordinator tc = client.getTransactionCoordinator();

        // 1. Iniciar Transacción
        String txId = tc.begin();
        
        try {
            // 2. Operaciones asociadas a la transacción
            client.document().collection("accounts").update(txId, "acc01", "{balance: 900}");
            client.document().collection("accounts").update(txId, "acc02", "{balance: 1100}");
            
            // 3. Fase Prepare (2PC)
            boolean prepared = tc.prepare(txId, List.of("group1", "group2"));
            
            if (prepared) {
                // 4. Commit
                tc.commit(txId);
                System.out.println("Transacción completada.");
            } else {
                // Rollback explícito si no hay quórum de preparación
                tc.abort(txId); 
                System.out.println("Transacción abortada (Rollback).");
            }
        } catch (Exception ex) {
            // 5. Rollback en caso de error
            tc.abort(txId);
            System.err.println("Se ejecutó un Rollback por excepción: " + ex.getMessage());
        }
    }
}
```

### 2.5 Agregaciones y Consultas Complejas Optimizadas (Joins/Lookups)

Para escenarios complejos con clases referenciadas, JettraStoreEngine optimiza internamente las consultas cruzadas (`Factura -> DetalleFactura -> Productos -> Clientes -> Sucursal -> Empresa`). Puedes usar la etapa de `$lookup` (similar a los joins) y anidarlos para construir la respuesta completa en una sola llamada al motor.

```java
public class ComplexAggregationExample {
    public static void main(String[] args) {
        JettraClient client = new JettraClient("127.0.0.1", 8080);
        
        // Pipeline que trae la factura y resuelve todas las referencias subyacentes
        // Optimizando la lectura para evitar el problema N+1.
        String pipeline = "[" +
            // Filtro inicial: facturas con alto importe
            "{\"$match\": {\"total\": {\"$gt\": 1000}}}," +
            
            // Join con Cliente
            "{\"$lookup\": {\"from\": \"clientes\", \"localField\": \"cliente_id\", \"foreignField\": \"_id\", \"as\": \"cliente\"}}," +
            "{\"$unwind\": \"$cliente\"}," +
            
            // Join con Sucursal desde Cliente
            "{\"$lookup\": {\"from\": \"sucursales\", \"localField\": \"cliente.sucursal_id\", \"foreignField\": \"_id\", \"as\": \"sucursal\"}}," +
            "{\"$unwind\": \"$sucursal\"}," +
            
            // Join con Empresa desde Sucursal
            "{\"$lookup\": {\"from\": \"empresas\", \"localField\": \"sucursal.empresa_id\", \"foreignField\": \"_id\", \"as\": \"empresa\"}}," +
            "{\"$unwind\": \"$empresa\"}," +
            
            // Join con Detalles y Productos
            "{\"$lookup\": {\"from\": \"detalles_factura\", \"localField\": \"_id\", \"foreignField\": \"factura_id\", \"as\": \"detalles\"}}," +
            
            // Proyección optimizada para traer solo lo necesario
            "{\"$project\": {" +
                "\"factura_id\": \"$_id\"," +
                "\"total\": 1," +
                "\"cliente_nombre\": \"$cliente.nombre\"," +
                "\"empresa_nombre\": \"$empresa.nombre\"," +
                "\"cantidad_detalles\": {\"$size\": \"$detalles\"}" +
            "}}" +
        "]";

        List<Object> facturas = client.aggregate("facturas", pipeline).await().indefinitely();
        System.out.println("Facturas Complejas Recuperadas: " + facturas.size());
    }
}
```

### 2.6 Soporte Interno de Reglas (JettraRules)

JettraStoreEngine integra soporte nativo para **JettraRules**. Esto significa que puedes aplicar reglas de negocio y cómputos directamente sobre las entidades. Las reglas se evalúan de forma ágil, facilitando desde validaciones hasta cálculos automáticos.

```java
import com.jettra.store.engine.rules.JettraRule;
import com.jettra.store.engine.rules.RuleEngine;

// Entidad a evaluar
public record Factura(String id, double subtotal, double total, String tipoCliente) {}

// Definición de una regla
public class DescuentoClienteVIPRule implements JettraRule<Factura> {
    @Override
    public boolean evaluate(Factura factura) {
        return "VIP".equals(factura.tipoCliente()) && factura.subtotal() > 500.0;
    }

    @Override
    public void execute(Factura factura) {
        double descuento = factura.subtotal() * 0.10; // 10% de descuento
        // Se aplica el descuento en la lógica
        System.out.println("Regla Aplicada: Descuento VIP de $" + descuento + " a la factura " + factura.id());
    }
}

public class JettraRulesExample {
    public static void main(String[] args) {
        // Inicializar el Motor de Reglas
        RuleEngine<Factura> engine = new RuleEngine<>();
        engine.addRule(new DescuentoClienteVIPRule());

        // Evaluar reglas localmente o delegarlo al servidor Jettra
        Factura facturaPrueba = new Factura("F-001", 1000.0, 1000.0, "VIP");
        
        // Ejecución de las reglas
        engine.applyRules(facturaPrueba);
    }
}
```

### 2.6.1 Reglas y Operaciones Embebidas en la Entidad (Records)

Además de definir reglas en clases separadas, **JettraStoreEngine** permite definir las reglas de negocio y los campos calculados de manera declarativa directamente sobre las propiedades de un `record` de Java, utilizando las anotaciones `@Rules` y `@Compute`. Estas validaciones se ejecutan automáticamente a nivel del motor de base de datos al momento de guardar o actualizar la entidad.

```java
import io.jettra.rules.annotations.Rules;
import io.jettra.rules.annotations.Compute;
import io.jettra.rules.annotations.OperationType;

public record CuentaModel(
    Double saldo,
    
    @Rules(apply="lessorequals", than="saldo", message="El descuento no puede ser mayor al saldo")
    Double descuento,

    @Compute(operation=OperationType.SUBTRACTION, fields={"saldo", "descuento"}, editable=false)
    Double saldoNeto
) {}
```

En este ejemplo, cuando se guarda un objeto `CuentaModel` en la base de datos, `JettraStoreEngine` de forma automática:
1. **Calcula campos (`@Compute`)**: Ejecuta la operación definida (`SUBTRACTION` entre `saldo` y `descuento`) y asigna el resultado a `saldoNeto` antes de la persistencia.
2. **Valida reglas (`@Rules`)**: Evalúa que la propiedad `descuento` sea menor o igual (`lessorequals`) a la propiedad `saldo`. Si la regla falla, la base de datos aborta la operación y lanza una excepción con el mensaje indicado, garantizando la consistencia de los datos directamente en la capa de almacenamiento.

---

## 3. CRUD Multimodelo (Todos los Motores)

JettraStoreEngine soporta 8 motores de almacenamiento sobre la misma base de datos. A continuación, ejemplos de **Creación, Lectura, Actualización y Eliminación** para cada modelo, empleando la API Java.

### 3.1 Motor Documental (Document)
Ideal para JSON y estructuras jerárquicas flexibles.
```java
var docs = client.document().collection("users");
// Crear
docs.insert("usr_1", "{\"name\":\"Bob\", \"age\":30}");
// Leer
String bob = docs.get("usr_1");
// Actualizar
docs.update("usr_1", "{\"name\":\"Bob\", \"age\":31}");
// Eliminar
docs.delete("usr_1");
```

### 3.2 Motor Clave-Valor (Key-Value)
Ideal para cachés o acceso por llave ultra-rápido en memoria.
```java
var kv = client.keyvalue().collection("cache");
// Crear
kv.put("session_123", "token_abc");
// Leer
String token = kv.get("session_123");
// Actualizar
kv.put("session_123", "token_xyz"); // Sobrescribe el valor
// Eliminar
kv.delete("session_123");
```

### 3.3 Motor Vectorial (Vector)
Diseñado para búsquedas de similitud (Embeddings, IA).
```java
var vectors = client.vector().collection("ai_docs");
// Crear
vectors.insert("vec_1", "{\"vector\": [0.2, 0.5, 0.8], \"metadata\": \"Doc1\"}");
// Leer (Búsqueda por similitud - KNN)
var nearest = vectors.findNearest(new double[]{0.2, 0.5, 0.7}, 5); // top 5
// Actualizar
vectors.update("vec_1", "{\"vector\": [0.21, 0.51, 0.81]}");
// Eliminar
vectors.delete("vec_1");
```

### 3.4 Motor de Grafos (Graph)
Para redes interconectadas (social, fraudes, topologías).
```java
var graph = client.graph().collection("social");
// Crear Nodos
graph.addNode("node_a", "{\"name\":\"Alice\"}");
graph.addNode("node_b", "{\"name\":\"Bob\"}");
// Crear Relación (Edge)
graph.addEdge("edge_1", "node_a", "node_b", "{\"relation\":\"KNOWS\"}");
// Leer (Traversal)
var friends = graph.traverse("node_a", "KNOWS");
// Actualizar Nodo
graph.updateNode("node_a", "{\"name\":\"Alice Smith\"}");
// Eliminar Relación y Nodo
graph.deleteEdge("edge_1");
graph.deleteNode("node_b");
```

### 3.5 Motor de Series de Tiempo (Time-Series)
Almacenamiento eficiente de métricas con timestamp.
```java
var ts = client.timeseries().collection("cpu_metrics");
// Crear
ts.insert("server_1", System.currentTimeMillis(), "{\"load\": 45.5}");
// Leer (Rango de tiempo)
var data = ts.range("server_1", 1700000000L, 1700005000L);
// Actualizar (Por lo general, las TS son inmutables, pero se soporta corrección)
ts.update("server_1", 1700000000L, "{\"load\": 46.0}");
// Eliminar (Purga de datos viejos)
ts.deleteRange("server_1", 0L, 1600000000L);
```

### 3.6 Motor Columnar (Column)
Analíticas pesadas (OLAP) con compresión.
```java
var col = client.column().collection("analytics");
// Crear (Fila y familia de columnas)
col.insert("row_1", "cf_metrics", "{\"views\": 100, \"clicks\": 5}");
// Leer
var row = col.get("row_1", "cf_metrics");
// Actualizar
col.update("row_1", "cf_metrics", "{\"views\": 101, \"clicks\": 5}");
// Eliminar
col.deleteRow("row_1");
```

### 3.7 Motor Geoespacial (Geospatial)
Búsquedas espaciales (Latitud/Longitud).
```java
var geo = client.geospatial().collection("places");
// Crear
geo.insert("poi_1", 40.7128, -74.0060, "{\"name\":\"New York\"}");
// Leer (Punto específico)
var place = geo.get("poi_1");
// Búsqueda por Radio (Radius Search)
var nearPlaces = geo.findInRadius(40.7, -74.0, 10.0); // 10 km
// Actualizar
geo.update("poi_1", 40.7130, -74.0065, "{\"name\":\"NY Downtown\"}");
// Eliminar
geo.delete("poi_1");
```

### 3.8 Motor de Objetos (Object)
Para guardar objetos Java/binarios nativos directamente, ideal para state management.
```java
var obj = client.object().collection("game_states");
byte[] stateData = new byte[]{0x01, 0x02, 0x03}; // Representación serializada
// Crear
obj.insert("session_1", stateData);
// Leer
byte[] retrieved = obj.get("session_1");
// Actualizar
obj.update("session_1", new byte[]{0x01, 0x02, 0x04});
// Eliminar
obj.delete("session_1");
```

---

## 4. Python

### Configuración e Instalación
```bash
pip install jettra-driver
```

### Código (CRUD Básico)
```python
from jettra import JettraClient

client = JettraClient("localhost:9090", "admin", "admin")
db = client.database("library_db")
books = db.collection("books")

# Crear
books.insert({
    "id": "B1", 
    "title": "Cien Años de Soledad",
    "author": {"$ref": "authors", "id": "A1"}
})

# Leer
book = books.find_by_id("B1")
print(f"Libro: {book['title']}")

# Actualizar
books.update("B1", {"title": "Cien Años (Ilustrada)"})

# Eliminar
books.delete("B1")
```

---

## 5. Golang

### Configuración e Instalación
```bash
go get github.com/jettra/jettra-driver-go
```

### Código (CRUD Básico)
```go
package main

import (
	"context"
	"fmt"
	"github.com/jettra/jettra-driver-go/jettra"
)

type Book struct {
	ID        string       `json:"id"`
	Title     string       `json:"title"`
}

func main() {
	ctx := context.Background()
	client, _ := jettra.Connect("localhost:9090", "admin", "admin")
	defer client.Close()

	db := client.Database("library_db")

	// Crear
	book := Book{ID: "B1", Title: "Cien Años de Soledad"}
	db.Collection("books").Insert(ctx, book)

	// Leer
	var result Book
	db.Collection("books").FindByID(ctx, "B1", &result)
	fmt.Printf("Libro encontrado: %s\n", result.Title)

	// Actualizar
	result.Title = "Cien Años (Ed. Especial)"
	db.Collection("books").Update(ctx, "B1", result)

	// Eliminar
	db.Collection("books").Delete(ctx, "B1")
}
```

---

## 6. Operaciones Avanzadas (Agrupación, Ordenación, Totales, Estadísticas y Paginación)

JettraStoreEngine soporta un potente motor de consultas que permite realizar analítica, paginación y transformaciones complejas en los datos a través de una API fluida o un pipeline de agregación. Estos conceptos aplican principalmente a motores como el **Documental, Columnar y Time-Series**.

A continuación, ejemplos en Java utilizando la API Fluida y Pipelines de Agregación.

### 6.1 Paginación y Ordenación (Pagination & Sorting)
Ideal para presentar grandes cantidades de datos en una interfaz de usuario.
```java
public class PaginationExample {
    public static void main(String[] args) {
        JettraClient client = new JettraClient("127.0.0.1", 8080);
        
        // Obtener la página 2 (10 elementos por página) ordenado por fecha descendente
        int pageNumber = 2;
        int pageSize = 10;
        int offset = (pageNumber - 1) * pageSize;

        List<Object> results = client.document().collection("events")
            .find()
            .orderBy("timestamp", "DESC")
            .skip(offset)
            .limit(pageSize)
            .execute();
            
        results.forEach(doc -> System.out.println("Evento: " + doc));
    }
}
```

### 6.2 Agrupación y Totales (Grouping & Totals)
Calculando el monto total por cada categoría de producto.
```java
public class GroupingExample {
    public static void main(String[] args) {
        JettraClient client = new JettraClient("127.0.0.1", 8080);
        
        String pipeline = "[" +
            "{\"$match\": {\"status\": \"SOLD\"}}," +
            "{\"$group\": {" +
                "\"_id\": \"$category\"," + // Agrupar por categoría
                "\"totalRevenue\": {\"$sum\": \"$price\"}," + // Suma total
                "\"itemsSold\": {\"$sum\": 1}" + // Conteo
            "}}," +
            "{\"$sort\": {\"totalRevenue\": -1}}" + // Ordenar por ingresos descendente
        "]";

        List<Object> results = client.aggregate("products", pipeline).await().indefinitely();
        results.forEach(res -> System.out.println("Categoría: " + res));
    }
}
```

### 6.3 Cálculos Estadísticos (Min, Max, Avg)
Extrayendo estadísticas métricas desde el motor Columnar o Documental.
```java
public class StatisticsExample {
    public static void main(String[] args) {
        JettraClient client = new JettraClient("127.0.0.1", 8080);
        
        String pipeline = "[" +
            "{\"$match\": {\"region\": \"US-EAST\"}}," +
            "{\"$group\": {" +
                "\"_id\": null," + // null significa calcular para todo el conjunto filtrado
                "\"averageLoad\": {\"$avg\": \"$cpu_load\"}," +
                "\"maxTemperature\": {\"$max\": \"$temperature\"}," +
                "\"minTemperature\": {\"$min\": \"$temperature\"}" +
            "}}" +
        "]";

        List<Object> stats = client.aggregate("server_metrics", pipeline).await().indefinitely();
        System.out.println("Estadísticas del Servidor: " + stats.get(0));
    }
}
```

### 6.4 Agregaciones en Motores Especializados

Aunque el paradigma de agregación (Pipeline) es nativo para bases de datos Documentales y Columnares, JettraStoreEngine extiende estas capacidades lógicas a sus otros motores utilizando un motor de MapReduce interno o extensiones específicas de consulta.

#### Motor de Grafos (Graph)
Puedes calcular estadísticas de la red, como el conteo de nodos adyacentes o la densidad de la red.
```java
// Contar cuántas relaciones "KNOWS" tiene Alice
long friendCount = client.graph().collection("social")
    .traverse("node_alice", "KNOWS")
    .size();
System.out.println("Amigos de Alice: " + friendCount);
```

#### Motor Geoespacial (Geospatial)
Puedes agrupar y contar puntos de interés (POIs) por zonas o radios específicos.
```java
// Contar cuántos lugares hay en un radio de 5km de un punto
long placesNearMe = client.geospatial().collection("places")
    .findInRadius(40.7128, -74.0060, 5.0)
    .size();
System.out.println("Lugares en 5km: " + placesNearMe);
```

#### Motor Vectorial (Vector)
Paginación y analítica semántica.
```java
// Paginación en búsqueda de similitud (Página 2, tamaño 10)
int offset = 10;
int limit = 10;
var page2 = client.vector().collection("ai_docs")
    .findNearest(new double[]{0.5, 0.2, 0.8}, offset + limit) // Buscar los primeros 20
    .subList(offset, offset + limit); // Quedarse con del 11 al 20
```

#### Motor Clave-Valor (Key-Value) y Objetos (Object)
Para estos motores, enfocados en latencias de microsegundos, el paradigma de agregación tradicional se traslada a patrones `MapReduce` o extracciones de rangos.
```java
// Ejemplo de agregación iterativa en Key-Value (Conteo de sesiones activas)
long activeSessionsCount = client.keyvalue().collection("cache")
    .scanPrefix("session_active_")
    .size();
System.out.println("Sesiones activas: " + activeSessionsCount);
```
