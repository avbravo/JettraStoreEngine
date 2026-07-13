# JettraDB Docker Deployment Guide

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
