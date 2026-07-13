
mvn clean package -DskipTests
java -XX:+UseZGC --enable-preview -jar target/JettraStoreEngine-1.0-SNAPSHOT.jar