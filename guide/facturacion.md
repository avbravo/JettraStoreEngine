# Sistema de Facturación con JettraStorageEngine Multimodelo

Este documento demuestra cómo modelar y persistir un sistema de facturación completo en **JettraStorageEngine**, aprovechando todos sus 8 motores de almacenamiento simultáneamente (Políglota nativo). También incluye ejemplos de uso en Java, Python, Go y la consola Shell.

## 1. Definición del Dominio (Java Records)

Aquí tienes las definiciones de los **Java Records** estructurados para el sistema de facturación, listos para usar:

```java
import java.time.LocalDate;
import java.util.List;

// --- 1. IDENTIDAD Y CLIENTES ---

public enum TipoIdentificacion {
    CEDULA_CIUDADANIA, CEDULA_EXTRANJERIA, NIT, PASAPORTE, RUC
}

public record Direccion(
    String calle,
    String ciudad,
    String estadoProvincia,
    String codigoPostal,
    String pais
) {}

public record Contribuyente(
    String id,
    TipoIdentificacion tipoId,
    String nombreLegal,
    String email,
    Direccion direccion,
    String telefono
) {}

// --- 2. PRODUCTOS Y FISCALIDAD ---

public record ImpuestoCategoria(
    String codigo,
    String nombre,
    double porcentaje
) {}

public record Producto(
    String sku,
    String nombre,
    String descripcion,
    double precioUnitario,
    ImpuestoCategoria impuesto
) {}

// --- 3. DETALLE DE LA TRANSACCIÓN ---

public record LineaFactura(
    int numeroLinea,
    Producto producto,
    double cantidad,
    double porcentajeDescuento
) {
    public LineaFactura {
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        if (porcentajeDescuento < 0 || porcentajeDescuento > 100) throw new IllegalArgumentException("Descuento inválido");
    }

    public double subtotalBruto() { return producto.precioUnitario() * cantidad; }
    public double montoDescuento() { return subtotalBruto() * (porcentajeDescuento / 100.0); }
    public double subtotalNeto() { return subtotalBruto() - montoDescuento(); }
    public double montoImpuesto() { return subtotalNeto() * (producto.impuesto().porcentaje() / 100.0); }
    public double totalLinea() { return subtotalNeto() + montoImpuesto(); }
}

// --- 4. ESTRUCTURA RAÍZ Y TOTALES ---

public enum EstadoFactura { BORRADOR, EMITIDA, PAGADA, ANULADA }
public enum MetodoPago { EFECTIVO, TARJETA_CREDITO, TRANSFERENCIA_BANCARIA, CREDITO }

public record TotalesFactura(
    double totalBruto,
    double totalDescuentos,
    double totalNeto,
    double totalImpuestos,
    double granTotal
) {}

public record Factura(
    String numeroFactura,
    String claveAccesoElectronica,
    LocalDate fechaEmision,
    LocalDate fechaVencimiento,
    Contribuyente emisor,
    Contribuyente receptor,
    List<LineaFactura> lineas,
    MetodoPago metodoPago,
    EstadoFactura estado
) {
    public Factura {
        lineas = List.copyOf(lineas);
    }

    public TotalesFactura calcularTotales() {
        double bruto = lineas.stream().mapToDouble(LineaFactura::subtotalBruto).sum();
        double descuentos = lineas.stream().mapToDouble(LineaFactura::montoDescuento).sum();
        double neto = lineas.stream().mapToDouble(LineaFactura::subtotalNeto).sum();
        double impuestos = lineas.stream().mapToDouble(LineaFactura::montoImpuesto).sum();
        double granTotal = lineas.stream().mapToDouble(LineaFactura::totalLinea).sum();

        return new TotalesFactura(bruto, descuentos, neto, impuestos, granTotal);
    }
}
```

## 2. Ejemplos por Motor de Base de Datos en Java

Jettra nos permite decidir la mejor estrategia de persistencia por contexto.

### 2.1 Document (Factura completa)
Ideal para guardar la factura jerárquica con sus líneas sin normalizar.
```java
JettraRepository<Factura> facturasRepo = client.repository(Factura.class, "DOCUMENT", "facturas");
// Crear
Factura miFactura = new Factura(...);
facturasRepo.save(miFactura.numeroFactura(), miFactura);
// Leer y agregar
long facturasPagadas = client.document().collection("facturas").find().where("estado").eq("PAGADA").count();
```

### 2.2 Key-Value (Caché Rápido de Facturas)
Almacena facturas serializadas como Strings para alta concurrencia.
```java
var kv = client.keyvalue().collection("cache_facturas");
// Insertar
kv.put("fac_cache_1001", "{\"numeroFactura\":\"1001\", \"granTotal\": 1500.5}");
// Recuperar en O(1)
String cacheStr = kv.get("fac_cache_1001");
```

### 2.3 Column (Analítica OLAP)
Ideal para sumar impuestos por categoría sin cargar toda la factura.
```java
var col = client.column().collection("olap_facturacion");
// Insertar (Fila = Factura_ID, Familia Columnas = 'totales', Columna = 'impuesto')
col.insert("1001", "totales", "{\"totalImpuestos\": 150.0, \"totalBruto\": 1350.5}");

// Agregación analítica masiva
long totalTax = col.aggregateColumn("totales", "totalImpuestos", "SUM");
```

### 2.4 Graph (Grafo de Comercio)
Conecta emisores con receptores para encontrar redes de fraude o dependencia comercial.
```java
var graph = client.graph().collection("red_comercial");
// Crear Nodos
graph.addNode("emisor_900", "{\"nombre\":\"Tech Corp\"}");
graph.addNode("receptor_001", "{\"nombre\":\"Juan Perez\"}");
// Relación (Edge) de facturación
graph.addEdge("fac_1001", "emisor_900", "receptor_001", "{\"total\": 1500.5}");
// Recorrido: Obtener clientes directos
var clientes = graph.traverse("emisor_900", "fac_1001");
```

### 2.5 Time-Series (Métricas Financieras)
Para tableros en tiempo real sobre ingresos y facturas emitidas por hora.
```java
var ts = client.timeseries().collection("finanzas_ts");
long timestamp = System.currentTimeMillis();
// Registrar evento financiero inmutable
ts.insert("ingresos_diarios", timestamp, "{\"monto\": 1500.5, \"factura\": \"1001\"}");
// Consulta de rango para sumatoria
var rangoMes = ts.range("ingresos_diarios", timestamp - 2592000000L, timestamp);
```

### 2.6 Vector (Búsqueda Semántica de Productos)
Buscar facturas o productos similares basándose en descripciones (Embeddings AI).
```java
var vector = client.vector().collection("productos_ai");
// Guardar el embedding del producto
vector.insert("prod_A1", "{\"vector\": [0.22, 0.91, 0.3], \"nombre\": \"Laptop Pro\"}");
// Búsqueda k-NN para recomendar productos
var similares = vector.findNearest(new double[]{0.21, 0.90, 0.31}, 5);
```

### 2.7 Geospatial (Zonificación de Ventas)
Buscar clientes y agrupar facturas por zonas geográficas.
```java
var geo = client.geospatial().collection("ventas_geoloc");
// Insertar venta en coordenada de la 'Direccion'
geo.insert("fac_1001", 4.7110, -74.0721, "{\"total\": 1500.5, \"ciudad\":\"Bogotá\"}");
// Contar facturas en 10km a la redonda
long facturasBogota = geo.findInRadius(4.7110, -74.0721, 10.0).size();
```

### 2.8 Object (Binario Nativo)
Persistir directamente el Record en memoria cruda sin transformarlo a JSON.
```java
var obj = client.object().collection("facturas_bin");
byte[] facturaBinaria = serializeRecord(miFactura);
// Crear
obj.insert("fac_bin_1001", facturaBinaria);
// Leer de vuelta a Record Java
byte[] recuperado = obj.get("fac_bin_1001");
```

---

## 3. Ejemplo Completo por Lenguaje y Shell

El siguiente ejemplo simula el flujo de registrar una `Factura` como Documento y al mismo tiempo actualizar las estadísticas OLAP (Column) y Time-Series.

### 3.1 Golang
```go
package main

import (
	"context"
	"fmt"
	"time"
	"github.com/jettra/jettra-driver-go/jettra"
)

func main() {
	ctx := context.Background()
	client, _ := jettra.Connect("localhost:9090", "admin", "admin")
	defer client.Close()
	db := client.Database("erp_db")

	// 1. Guardar en Documental (Estructura Jerárquica)
	factura := map[string]interface{}{
		"numeroFactura": "1001",
		"estado":        "EMITIDA",
		"total":         1500.5,
	}
	db.Collection("facturas_docs").Insert(ctx, factura)

	// 2. Guardar en Time-Series (Métricas rápidas)
	db.TimeSeries("metricas_ingresos").Insert(ctx, "facturacion_global", time.Now().UnixMilli(), map[string]interface{}{"monto": 1500.5})

	// 3. Consulta de Agregación
	resultado := db.Collection("facturas_docs").Aggregate(ctx, `[{"$match": {"estado": "EMITIDA"}}, {"$group": {"_id": null, "suma": {"$sum": "$total"}}}]`)
	fmt.Printf("Total Emitido: %v\n", resultado)
}
```

### 3.2 Python
```python
from jettra import JettraClient
import time

client = JettraClient("localhost:9090", "admin", "admin")
db = client.database("erp_db")

# 1. Guardar en Documental
db.collection("facturas_docs").insert({
    "numeroFactura": "1001",
    "estado": "EMITIDA",
    "total": 1500.5
})

# 2. Guardar en Key-Value (Caché rápido)
db.keyvalue("facturas_cache").put("fac_1001", "EMITIDA")

# 3. Consulta de Agregación
pipeline = [{"$match": {"estado": "EMITIDA"}}, {"$group": {"_id": None, "suma": {"$sum": "$total"}}}]
resultado = db.collection("facturas_docs").aggregate(pipeline)
print(f"Total Emitido: {resultado[0]['suma']}")
```

### 3.3 JettraDB Interactive Shell
Para interactuar en vivo con la consola de administración.
```bash
jettra> use erp_db

# 1. Crear la factura en la base Documental
jettra> db.facturas_docs.insert({ "numeroFactura": "1001", "estado": "EMITIDA", "total": 1500.5, "cliente": "Juan Perez" })

# 2. Registrar en la base Geoespacial (Mapa de calor)
jettra> db.ventas_geoloc.geospatial.insert({ "_id": "fac_1001", "lat": 4.7110, "lon": -74.0721, "total": 1500.5 })

# 3. Consulta Avanzada: Sumar totales de facturas emitidas (Documental)
jettra> db.facturas_docs.aggregate([
    { $match: { estado: "EMITIDA" } },
    { $group: { _id: "$estado", sumaTotal: { $sum: "$total" } } }
])

# 4. Encontrar ventas cercanas a una coordenada (Geoespacial)
jettra> db.ventas_geoloc.geospatial.findInRadius({ lat: 4.7110, lon: -74.0721, radiusKm: 10.0 })
```
