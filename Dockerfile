# Empleando una imagen ligera orientada a un runtime óptimo y moderno
# Usando BellSoft Liberica JRE con Alpaquita Linux para menor tamaño y mayor rendimiento
FROM bellsoft/liberica-runtime-container:jre-25-stream-musl AS runtime

LABEL maintainer="Jettra Development Team"
LABEL description="JettraStoreEngine - Motor Multimodelo Optimizado"

WORKDIR /app

# Copiar el JAR precompilado
COPY target/JettraStoreEngine-1.0-SNAPSHOT-shaded.jar /app/JettraStoreEngine.jar
# Copiar propiedades por defecto
COPY jettrastoreengine.properties /app/jettrastoreengine.properties

# Entrenar la JVM para generar el AOT Cache (AppCDS)
# Ejecutamos la app por 10 segundos para cargar las clases y luego forzamos la salida guardando el archivo .jsa
RUN timeout 15s java -XX:ArchiveClassesAtExit=app.jsa -jar JettraStoreEngine.jar || true

# Configuración de los volúmenes de datos
VOLUME /data

# Puertos
EXPOSE 8080
EXPOSE 50051

# Opciones de JVM para optimización de rendimiento en contenedores y habilitación de AOT Cache
ENV JAVA_OPTS="-XX:+UseCompactObjectHeaders -XX:+UseZGC -Xmx512m -XX:MaxRAMPercentage=75.0 -XX:SharedArchiveFile=app.jsa"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar JettraStoreEngine.jar"]

# Configuracion para el Shell de JettraSecurityDB
CMD []
