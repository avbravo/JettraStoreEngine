# Cloud Native Deployment

JettraDB is designed for modern cloud environments. This guide explains how to deploy a full cluster using Docker and Kubernetes.

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
Open your browser at `http://localhost:8081` to see the JettraDB Dashboard.

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
