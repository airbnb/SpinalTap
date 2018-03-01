namespace java com.airbnb.jitney.event.spinaltap.v1
namespace rb airbnb.jitney.event.spinaltap.v1

enum MutationType {
  INSERT = 0x1
  UPDATE
  DELETE
  INVALID
}

struct DataSource {
  1: required string hostname,
  2: required i32 port,
  3: required string synapse_service,
}

struct Column {
  1: required i64 type,
  2: required bool is_primary_key = false,
  3: required string name,
  4: optional i32 position,
}

struct BinlogHeader {
  1: required string pos,
  2: required i64 server_id,
  3: required i64 timestamp,
  4: required i32 type,
  5: optional string last_transaction_pos,
  6: optional i64 last_transaction_timestamp,
  7: optional i64 leader_epoch,
  8: optional i64 id,
  9: optional i32 event_row_position,
}

struct Table {
  1: required i64 id,
  2: required string name,
  3: required string database,
  4: required set<string> primary_key,
  5: required map<string, Column> columns,
}

struct Mutation {
  31337: optional string schema = "com.airbnb.jitney.event.spinaltap:Mutation:1.0.0",
  1: required MutationType type,
  2: required i64 timestamp,
  3: required string source_id,
  4: required DataSource data_source,
  5: required BinlogHeader binlog_header,
  6: required Table table,
  7: required map<string, binary> entity,
  8: optional map<string, binary> previous_entity,
}
