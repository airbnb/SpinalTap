[![Build Status](https://travis-ci.org/airbnb/SpinalTap.svg?branch=master)](https://travis-ci.org/airbnb/SpinalTap)
# SpinalTap
SpinalTap is a general-purpose reliable Change Data Capture (CDC) service, capable of detecting data mutations with low-latency across different data sources, and propagating them as standardized events to downstream consumers.
SpinalTap has become an integral component in Airbnb's infrastructure platform and data processing pipeline, on which several critical applications are reliant on. More information from our engineering [blog](https://medium.com/airbnb-engineering/capturing-data-evolution-in-a-service-oriented-architecture-72f7c643ee6f).

## Disclaimer

**SpinalTap does not support message authentication yet as part of its current open-source
offering.**

Message authentication is a requirement to enforce authenticity and accessibility of messages
propagated in the message bus, as well as source verification of transmitted messages. Given
SpinalTap is by design a source & destination agnostic library, we believe the burden of supplying a
message authentication workflow lies on the message bus solution used, or as an extension to the
destination component implement adopted. This is inline with the usage within Airbnb’s
infrastructure, where SpinalTap is just another client to our internal message transport which in
itself should supply message authentication as a general offering for all its clients. If the need
arises in the future, message authentication can be seamlessly integrated within the library as a
potential supported feature.

## Getting Started
### Install Thrift 0.9.3 Compiler
https://thrift.apache.org/docs/install/
### Building SpinalTap Standalone JAR
```
cd spinaltap-standalone
../gradlew shadowjar
```
### Download and Build Kafka
Recommend the version 0.9.0.1 or above (https://kafka.apache.org/downloads).
```
cd kafka
./gradle build
```
### MySQL Configuration
MySQL server should be configured to use `Row-based` binlog:
```
[mysqld]
log-bin=mysql-bin-changelog
binlog-format=ROW
server-id=1
```

## SpinalTap Standalone Configuration
```
zk-connection-string: localhost:2181 
zk-namespace: spinaltap-standalone
kafka-config:
  bootstrap_servers: localhost:9092
mysql-user: spinaltap
mysql-password: spinaltap
mysql-server-id: 12345
mysql-schema-store:
  host: schema-store.xxx
  port: 3306
  database: schema-store
  archive-database: schema-store-archive
mysql-sources:
  - name: mysql-db-1
    host: localhost
    port: 3306
    host_role: MASTER
    socket_timeout_seconds: -1
    schema_version_enabled: true
    initial_binlog_position:
      fileName: mysql-bin-changelog.001234
      position: 4
      nextPosition: 4
    tables:
      - test:users
      - test:places
    destination:
      pool_size: 2
      buffer_size: 1000
  - name: mysql-db-2
    host: mysql-db-2.xxx
    port: 3306
    tables:
      - db1:table1
    destination:
      buffer_size: 1000
```
- **zk-connection-string**: ZK connection string.
- **zk-namespace**: ZK namespace, please make sure it exists.
- **kafka-config**: Kafka destination config, `boostrap_servers` is required.
- **mysql-user**: username to connect to MySQL server, `SELECT`, `REPLICATION SLAVE`, `REPLICATION CLIENT`, `SHOW VIEW` permissions are required. 
- **mysql-password**: password to connect to MySQL server.
- **mysql-server-id**: MySQL server id for replication purpose, in the range from 1 to 2^32 – 1. Must be unique across whole replication group. If you have other slaves that connect to the MySQL server, SpinalTap's server id must be different from theirs.
### MySQL Schema Store
MySQL schema store is a MySQL instance which is used to store SpinalTap MySQL table schemas and their version history. When this feature is enabled, SpinalTap will track table schema changes and save the schema version snapshots into the schema store.
- **host**: schema store hostname.
- **port**: schema store port.
- **database**: database which stores table schema history.
- **archive-database**: database which stores the archived table schema history.
### MySQL Source Configuration
- **name**: source name, must be unique among other sources.
- **host**: MySQL server host.
- **port**: MySQL server port.
- **host_role**: Could be `MASTER`, `REPLICA`, or `MIGRATION`. The corresponding Kafka topic prefixes are different. The default value is `MASTER`.
- **socket_timeout_seconds**: MySQL Binlog client socket connection timeout in seconds. The default value is 90. A negative value disables [SO_TIMEOUT](https://docs.oracle.com/javase/8/docs/api/java/net/SocketOptions.html#SO_TIMEOUT)
- **schema_version_enabled**: Whether schema versioning is enabled for this source. The default value is `false`.
- **initial_binlog_position**: The binlog position SpinalTap should start streaming when SpinalTap connects to this source for the first time. **By default SpinalTap streams from the latest binlog position.**
- **tables**: table list spinaltap should listen and stream mutations from. The format is `<database_name>:<table_name>`.
- **destination**: Destination pool/buffer config.

## Launch ZooKeeper Cluster
```
cd kafka
bin/zookeeper-server-start.sh config/zookeeper.properties
```

## Launch Kafka Broker
```
cd kafka
bin/kafka-server-start.sh config/server.properties
```

## Launch SpinalTap Standalone
```
java -jar build/libs/spinaltap-standalone-all.jar spinaltap_standalone.yaml
```

