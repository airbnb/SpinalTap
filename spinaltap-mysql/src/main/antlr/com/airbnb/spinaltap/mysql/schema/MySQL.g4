/*
Adapted from https://github.com/antlr/grammars-v4/tree/master/mysql
Changes:
    1. Use table_name in `ALTER TABLE table_name RENAME TO table_name` statement.
    2. Allow escaped backquotes in REVERSE_QUOTE_ID
    3. Allow dot be the first character in table_name
    4. Add support of automatic initialization and updating for TIMESTAMP and DATETIME types (https://dev.mysql.com/doc/refman/5.7/en/timestamp-initialization.html)
    5. Add BOOL/BOOLEAN/DOUBLE/JSON ... to data_type
    6. Add arrow operation(->) to expression

MySQL (Positive Technologies) grammar
The MIT License (MIT).
Copyright (c) 2015-2017, Ivan Kochurkin (kvanttt@gmail.com), Positive Technologies.
Copyright (c) 2017, Ivan Khudyashev (IHudyashov@ptsecurity.com)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/

grammar MySQL;

@header {
package com.airbnb.spinaltap.mysql.schema;
}

// Top Level Description

root
    : sql_statements? (MINUS MINUS)? EOF
   ;

sql_statements
    : (sql_statement (MINUS MINUS)? SEMI | empty_statement)* 
   (sql_statement ((MINUS MINUS)? SEMI)? | empty_statement)
   ;
   
sql_statement
    : ddl_statement | dml_statement | transaction_statement
   | replication_statement | prepared_statement
   | administration_statement | utility_statement
   ;

empty_statement
    : SEMI
   ;

ddl_statement
    : create_database | create_event | create_index
   | create_logfile_group | create_procedure | create_function
   | create_server | create_table | create_tablespace_innodb
   | create_tablespace_ndb | create_trigger | create_view 
   | alter_database | alter_event | alter_function 
   | alter_instance | alter_logfile_group | alter_procedure 
   | alter_server | alter_table | alter_tablespace | alter_view
   | drop_database | drop_event | drop_index 
   | drop_logfile_group | drop_procedure | drop_function 
   | drop_server | drop_table | drop_tablespace 
   | drop_trigger | drop_view 
   | rename_table | truncate_table
   ;

dml_statement
    : select_statement | insert_statement | update_statement 
   | delete_statement | replace_statement | call_statement
   | load_data_statement | load_xml_statement | do_statement
   | handler_statement
   ;

transaction_statement
    : start_transaction 
   | begin_work | commit_work | rollback_work
   | savepoint_statement | rollback_statement 
   | release_statement | lock_tables | unlock_tables
   ;

replication_statement
    : change_master | change_repl_filter | purge_binary_logs
   | reset_master | reset_slave | start_slave | stop_slave
   | start_group_repl | stop_group_repl 
   | xa_start_transaction | xa_end_transaction | xa_prepare
   | xa_commit_work | xa_rollback_work | xa_recover_work
   ;

prepared_statement
    : prepare_statement | execute_statement | deallocate_prepare
   ;

// remark: NOT INCLUDED IN sql_statement, but include in body
//  of routine's statements
compound_statement
    : block_statement 
   | case_statement | if_statement | leave_statement 
   | loop_statement | repeat_statement | while_statement 
   | iterate_statement | return_statement | cursor_statement
   ;

administration_statement
    : alter_user | create_user | drop_user | grant_statement 
   | grant_proxy | rename_user | revoke_statement 
   | revoke_proxy | analyze_table | check_table 
   | checksum_table | optimize_table | repair_table 
   | create_udfunction | install_plugin | uninstall_plugin 
   | set_statement | show_statement | binlog_statement 
   | cache_index_statement | flush_statement | kill_statement 
   | load_index_into_cache | reset_statement 
   | shutdown_statement
   ;

utility_statement
    : simple_describe_statement | full_describe_statement 
   | help_statement | use_statement
   ;


// Data Definition Language

//    Create statements

create_database
    : CREATE (DATABASE | SCHEMA) 
   if_not_exists? id_ create_database_option*
   ;

create_event
    : CREATE owner_statement? EVENT if_not_exists? full_id
     ON SCHEDULE schedule_expression
     (ON COMPLETION NOT? PRESERVE)?
     (ENABLE | DISABLE | DISABLE ON SLAVE)?
     (COMMENT STRING_LITERAL)?
   DO routine_body
   ;

create_index
    : CREATE 
     (ONLINE | OFFLINE)? 
     index_category=(UNIQUE | FULLTEXT | SPATIAL)? 
     INDEX id_ index_type? 
     ON table_name index_colname_list
     index_option* 
     (
       ALGORITHM '='? alg_type=(DEFAULT | INPLACE | COPY) 
       | LOCK '='? 
         lock_type=(DEFAULT | NONE | SHARED | EXCLUSIVE)
     )?
   ;

create_logfile_group
    : CREATE LOGFILE GROUP id_
     ADD UNDOFILE undo_file=STRING_LITERAL
     (INITIAL_SIZE '='? init_size=filesize_literal)?
     (UNDO_BUFFER_SIZE '='? undo_size=filesize_literal)?
     (REDO_BUFFER_SIZE '='? redo_size=filesize_literal)?
     (NODEGROUP '='? id_)?
     WAIT?
     (COMMENT '='? comment=STRING_LITERAL)?
     ENGINE '='? engine_name
   ;

create_procedure
    : CREATE owner_statement?
   PROCEDURE full_id 
     '(' proc_param? (',' proc_param)* ')' 
     routine_characteristic* 
   routine_body
   ;

create_function
    : CREATE owner_statement?
   FUNCTION full_id
     '(' func_param? (',' func_param)* ')' 
     RETURNS data_type 
     routine_characteristic* 
   routine_body
   ;

create_server
    : CREATE SERVER id_
   FOREIGN DATA WRAPPER (MYSQL | STRING_LITERAL)
   OPTIONS '(' server_option (',' server_option)* ')'
   ;

create_table
    : CREATE TEMPORARY? TABLE if_not_exists? 
      table_name (LIKE table_name | '(' LIKE table_name ')' )     #copyCreateTable
   | CREATE TEMPORARY? TABLE if_not_exists? 
      table_name column_def_table_constraints?
      ( table_option (','? table_option)* )?
      partition_options? (IGNORE | REPLACE)?
      AS? select_statement                               #queryCreateTable
   | CREATE TEMPORARY? TABLE if_not_exists? 
      table_name column_def_table_constraints 
      ( table_option (','? table_option)* )?
      partition_options?                                 #colCreateTable
   ;

create_tablespace_innodb
    : CREATE TABLESPACE id_ 
     ADD DATAFILE datafile=STRING_LITERAL
     (FILE_BLOCK_SIZE '=' fb_size=filesize_literal)?
     (ENGINE '='? engine_name)?
   ;

create_tablespace_ndb
    : CREATE TABLESPACE id_ 
     ADD DATAFILE datafile=STRING_LITERAL
     USE LOGFILE GROUP id_
     (EXTENT_SIZE '='? extent_size=filesize_literal)?
     (INITIAL_SIZE '='? initial_size=filesize_literal)?
     (AUTOEXTEND_SIZE '='? autoextend_size=filesize_literal)?
     (MAX_SIZE '='? max_size=filesize_literal)?
     (NODEGROUP '='? id_)?
     WAIT?
     (COMMENT '='? comment=STRING_LITERAL)?
     ENGINE '='? engine_name
   ;

create_trigger
    : CREATE owner_statement? 
     TRIGGER this_trigger=full_id 
     trigger_time=(BEFORE | AFTER)
     trigger_event=(INSERT | UPDATE | DELETE)
     ON table_name FOR EACH ROW 
     ((FOLLOWS | PRECEDES) other_trigger=full_id)?
   routine_body
   ;

create_view
    : CREATE (OR REPLACE)? 
     (
       ALGORITHM '=' alg_type=(UNDEFINED | MERGE | TEMPTABLE)
     )? 
     owner_statement? 
     (SQL SECURITY sec_context=(DEFINER | INVOKER))? 
     VIEW full_id ('(' id_list ')')? AS select_statement
     (WITH check_option=(CASCADED | LOCAL)? CHECK OPTION)?
   ;

// details

create_database_option
    : DEFAULT? character_set '='? charset_name
   | DEFAULT? COLLATE '='? collation_name
   ;

owner_statement
    : DEFINER '=' (user_name | CURRENT_USER ( '(' ')')?)
   ;

schedule_expression
    : AT timestamp_value interval_expr*                        #preciseSchedule
   | EVERY (decimal_literal | expression) interval_type
       (
         STARTS startts=timestamp_value 
         (start_intervals+=interval_expr)*
       )? 
       (
         ENDS endts=timestamp_value 
         (end_intervals+=interval_expr)*
       )?                                             #intervalSchedule
   ;

timestamp_value
    : CURRENT_TIMESTAMP
   | string_literal
   | decimal_literal
   | expression
   ;

interval_expr
    : '+' INTERVAL (decimal_literal | expression) interval_type
   ;

interval_type
    : interval_type_base
   | YEAR | YEAR_MONTH | DAY_HOUR | DAY_MINUTE
   | DAY_SECOND | HOUR_MINUTE | HOUR_SECOND | MINUTE_SECOND
   | SECOND_MICROSECOND | MINUTE_MICROSECOND
   | HOUR_MICROSECOND | DAY_MICROSECOND
   ;

index_type
    : USING (BTREE | HASH)
   ;

index_option
    : KEY_BLOCK_SIZE '='? filesize_literal
   | index_type
   | WITH PARSER id_
   | COMMENT STRING_LITERAL
   ;

proc_param
    : (IN | OUT | INOUT) id_ data_type
   ;

func_param
    : id_ data_type
   ;

routine_characteristic
    : COMMENT STRING_LITERAL                             #rcComment
   | LANGUAGE SQL                                     #rcSqllang
   | NOT? DETERMINISTIC                               #rcDeterm
   | (
       CONTAINS SQL | NO SQL | READS SQL DATA 
       | MODIFIES SQL DATA
     )                                                #rcSqldata
   | SQL SECURITY sec_context=(DEFINER | INVOKER)              #rcSecurestmt
   ;

server_option
    : HOST STRING_LITERAL
   | DATABASE STRING_LITERAL
   | USER STRING_LITERAL
   | PASSWORD STRING_LITERAL
   | SOCKET STRING_LITERAL
   | OWNER STRING_LITERAL
   | PORT decimal_literal
   ;

column_def_table_constraints
    : '(' 
     column_def_table_constraint
     (',' column_def_table_constraint)* 
   ')'
   ;

column_def_table_constraint
    : id_ column_definition                                 #columnDefinition
   | table_constraint                                    #constraintDefinition
   | index_column_definition                             #indexDefinition
   ;

column_definition
    : data_type separate_column_constraint*
   ;

separate_column_constraint
    : null_notnull                                       #colConstrNull
   | DEFAULT default_value                               #colConstrDflt
   | AUTO_INCREMENT                                   #colConstrAuInc
   | PRIMARY? KEY                                     #colConstrPK
   | UNIQUE KEY?                                      #colConstrUK
   | COMMENT STRING_LITERAL                              #colConstrComment
   | COLUMN_FORMAT colformat=(FIXED | DYNAMIC | DEFAULT)       #colConstrForm
   | STORAGE storageval=(DISK | MEMORY | DEFAULT)              #colConstrStorage
   | ON UPDATE current_timestamp                         #colConstrOnUpdate
   | (UNICODE | ASCII)                                   #colConstrEncoding
   | (GENERATED ALWAYS)? AS expression                   #colConstrGeneratedCol
   | (VIRTUAL | STORED)                                  #colConstrVirtualStored
   | reference_definition                                #colConstrRefdef
   ;

table_constraint
    : (CONSTRAINT constr_name=id_?)? 
     PRIMARY KEY index_type? index_colname_list index_option*     #tblConstrPK
   | (CONSTRAINT constr_name=id_?)? 
       UNIQUE (INDEX | KEY)? index_name=id_? index_type? 
       index_colname_list index_option*                     #tblConstrUK
   | (CONSTRAINT constr_name=id_?)? 
       FOREIGN KEY index_name=id_? index_colname_list 
       reference_definition                              #tblConstrFK
   | CHECK '(' expression ')'                            #tblConstCheck
   ;

reference_definition
    : REFERENCES table_name index_colname_list 
     (MATCH ref_match_type=(FULL | PARTIAL | SIMPLE))? 
     (on_delete_action | on_update_action)?
   ;

on_delete_action
    : ON DELETE reference_action_control_type
     (
       ON UPDATE reference_action_control_type
     )?
   ;
on_update_action
    : ON UPDATE reference_action_control_type
     (
       ON DELETE reference_action_control_type
     )?
   ;

reference_action_control_type
    : RESTRICT | CASCADE | SET NULL_LITERAL | NO ACTION
   ;

index_column_definition
    : (INDEX | KEY) id_? index_type? 
     index_colname_list index_option*                       #simpleIndex
   | (FULLTEXT | SPATIAL) 
       (INDEX | KEY)? id_? 
       index_colname_list index_option*                     #specIndex
   ;

table_option
    : ENGINE '='? engine_name                            #tblOptEngine
   | AUTO_INCREMENT '='? decimal_literal                    #tblOptAuInc
   | AVG_ROW_LENGTH '='? decimal_literal                    #tblOptAvgRLen
   | DEFAULT? character_set '='? charset_name         #tblOptDefCharSet
   | CHECKSUM '='? ('0' | '1')                              #tblOptChkSum
   | DEFAULT? COLLATE '='? collation_name                   #tblOptDefCollate
   | COMMENT '='? STRING_LITERAL                         #tblOptComment
   | COMPRESSION '='? STRING_LITERAL                        #tblOptCompr
   | CONNECTION '='? STRING_LITERAL                      #tblOptConn
   | DATA DIRECTORY '='? STRING_LITERAL                     #tblOptDataDir
   | DELAY_KEY_WRITE '='? ('0' | '1')                       #tblOptDelKW
   | ENCRYPTION '='? STRING_LITERAL                      #tblOptEncr
   | INDEX DIRECTORY '='? STRING_LITERAL                    #tblOptIndexDir
   | INSERT_METHOD '='? (NO | FIRST | LAST)                 #tblOptInsMeth
   | KEY_BLOCK_SIZE '='? filesize_literal                   #tblOptKeyBlockSz
   | MAX_ROWS '='? decimal_literal                          #tblOptMaxRows
   | MIN_ROWS '='? decimal_literal                          #tblOptMinRows
   | PACK_KEYS '='? ('0' | '1' | DEFAULT)                   #tblOptPackK
   | PASSWORD '='? STRING_LITERAL                           #tblOptPasswd
   | ROW_FORMAT '='? 
       (
         DEFAULT | DYNAMIC | FIXED | COMPRESSED
         | REDUNDANT | COMPACT
       )                                           #tblOptRowFormat
   | STATS_AUTO_RECALC '='? (DEFAULT | '0' | '1')              #tblOptStatAutoR
   | STATS_PERSISTENT '='? (DEFAULT | '0' | '1')               #tblOptStatPersist
   | STATS_SAMPLE_PAGES '='? decimal_literal                #tblOptStatSamplPg
   | TABLESPACE id_ (STORAGE (DISK | MEMORY | DEFAULT))?       #tblOptTablespace
   | UNION '='? '(' table_name (',' table_name)* ')'           #tblOptUnion
   ;

partition_options
    : PARTITION BY partition_function_definition 
     (PARTITIONS part_num=decimal_literal)? 
     (
       SUBPARTITION BY linear_partition_func_def
       (SUBPARTITIONS subpart_num=decimal_literal)? 
     )? 
   ('(' partition_def (',' partition_def)* ')')?
   ;

partition_function_definition
    : linear_partition_func_def
   | (RANGE | LIST) 
       (
         '(' expression ')' 
         | COLUMNS '(' id_list ')' 
       )
   ;

linear_partition_func_def
    : LINEAR? HASH '(' expression ')'
   | LINEAR? KEY (ALGORITHM '=' ('1' | '2'))? '(' id_list ')'
   ;

partition_def
    : PARTITION id_ 
     (
       VALUES 
       (
         LESS THAN 
           ( 
             '(' (expression | constant_list) ')' 
             | MAXVALUE
           )
         | IN  '(' constant_list ')' 
       ) 
     )?
     (STORAGE? ENGINE '='? engine_name)?
     (COMMENT '='? comment=STRING_LITERAL)?
     (DATA DIRECTORY '='? data_dir=STRING_LITERAL)?
     (INDEX DIRECTORY '='? index_dir=STRING_LITERAL)?
     (MAX_ROWS '='? max_row_num=decimal_literal)?
     (MIN_ROWS '='? min_row_num=decimal_literal)?
     (TABLESPACE '='? tblspace_id=id_)?
     (NODEGROUP '='? nodegroup_id=id_)?
     (subpartition_def (',' subpartition_def)*)?
   ;

subpartition_def
    : SUBPARTITION id_
     (STORAGE? ENGINE '='? engine_name)?
     (COMMENT '='? comment=STRING_LITERAL)?
     (DATA DIRECTORY '='? data_dir=STRING_LITERAL)?
     (INDEX DIRECTORY '='? index_dir=STRING_LITERAL)?
     (MAX_ROWS '='? max_row_num=decimal_literal)?
     (MIN_ROWS '='? min_row_num=decimal_literal)?
     (TABLESPACE '='? tblspace_id=id_)?
     (NODEGROUP '='? nodegroup_id=id_)?
   ;


//    Alter statements

alter_database
    : ALTER (DATABASE | SCHEMA) id_? create_database_option+      #alterDb
   | ALTER (DATABASE | SCHEMA) id_ 
       UPGRADE DATA DIRECTORY NAME                          #alterDbUpgradeName
   ;

alter_event
    : ALTER owner_statement? 
   EVENT full_id
     (ON SCHEDULE schedule_expression)?
     (ON COMPLETION NOT? PRESERVE)?
     (RENAME TO full_id)?
     (ENABLE | DISABLE | DISABLE ON SLAVE)?
     (COMMENT STRING_LITERAL)?
     (DO routine_body)?
   ;

alter_function
    : ALTER FUNCTION full_id routine_characteristic*
   ;

alter_instance
    : ALTER INSTANCE ROTATE INNODB MASTER KEY
   ;

alter_logfile_group
    : ALTER LOGFILE GROUP id_
   ADD UNDOFILE STRING_LITERAL
   (INITIAL_SIZE '='? filesize_literal)?
   WAIT? ENGINE '='? engine_name
   ;

alter_procedure
    : ALTER PROCEDURE full_id routine_characteristic*
   ;

alter_server
    : ALTER SERVER id_ OPTIONS 
   '(' server_option (',' server_option)* ')'
   ;

alter_table
    : ALTER (ONLINE | OFFLINE)? IGNORE? TABLE table_name
   alter_table_spec (',' alter_table_spec)* 
   partition_options*
   ;

alter_tablespace
    : ALTER TABLESPACE id_
   (ADD | DROP) DATAFILE STRING_LITERAL
   (INITIAL_SIZE '=' filesize_literal)?
   WAIT?
   ENGINE '='? engine_name
   ;

alter_view
    : ALTER 
     (
       ALGORITHM '=' alg_type=(UNDEFINED | MERGE | TEMPTABLE)
     )?
     owner_statement? 
     (SQL SECURITY sec_context=(DEFINER | INVOKER))?
     VIEW full_id ('(' id_list ')')? AS select_statement
     (WITH check_opt=(CASCADED | LOCAL)? CHECK OPTION)?
   ;

// details

alter_table_spec
    : table_option                                       #altblTableOpt
   | ADD COLUMN? id_ column_definition (FIRST | AFTER id_)?    #altblAddCol
   | ADD COLUMN? 
       '(' 
         id_ column_definition ( ',' id_ column_definition)*
       ')'                                            #altblAddCols
   | ADD (INDEX | KEY) id_? index_type? 
       index_colname_list index_option*                     #altblAddIndex
   | ADD (CONSTRAINT id_?)? PRIMARY KEY 
       index_type? index_colname_list index_option*            #altblAddPK
   | ADD (CONSTRAINT id_?)? UNIQUE (INDEX | KEY)? id_? 
       index_type? index_colname_list index_option*            #altblAddUK
   | ADD (FULLTEXT | SPATIAL) (INDEX | KEY)? id_? 
       index_colname_list index_option*                     #altblAddSpecIndex
   | ADD (CONSTRAINT id_?)? FOREIGN KEY id_? 
       index_colname_list reference_definition                 #altblAddFK
   | ALGORITHM '='? (DEFAULT | INPLACE | COPY)                 #altblAlg
   | ALTER COLUMN? id_ 
     (SET DEFAULT default_value | DROP DEFAULT)             #altblColDef
   | CHANGE COLUMN? id_ 
       id_ column_definition (FIRST | AFTER id_)?              #altblColChange
   | LOCK '='? (DEFAULT | NONE | SHARED | EXCLUSIVE)           #altblLock
   | MODIFY COLUMN? 
       id_ column_definition (FIRST | AFTER id_)?              #altblColMod
   | DROP COLUMN? id_                                    #altblColDrop
   | DROP PRIMARY KEY                                    #altblDropPK
   | DROP (INDEX | KEY) id_                              #altblDropIndex
   | DROP FOREIGN KEY id_                                #altblDropFK
   | DISABLE KEYS                                     #altblDisKey
   | ENABLE KEYS                                      #altblEnKey
   | RENAME (TO | AS)? table_name                              #altblRenameTbl
   | ORDER BY id_list                                    #altblResort
   | CONVERT TO character_set charset_name
       (COLLATE collation_name)?                         #altblConvert
   | DEFAULT? character_set '=' charset_name
       (COLLATE '=' collation_name)?                        #altblDefCharset
   | DISCARD TABLESPACE                               #altblDisTblspace
   | IMPORT TABLESPACE                                   #altblImpTblSpace
   | FORCE                                            #altblForce
   | (WITHOUT | WITH) VALIDATION                         #altblValid
   | ADD PARTITION partition_def                         #altblAddPart
   | DROP PARTITION id_list                              #altblDropPart
   | DISCARD PARTITION (id_list | ALL) TABLESPACE              #altblDiscartPart
   | IMPORT PARTITION (id_list | ALL) TABLESPACE               #altblImportPart
   | TRUNCATE PARTITION (id_list | ALL)                     #altblTruncPart
   | COALESCE PARTITION decimal_literal                     #altblCoalPart
   | REORGANIZE PARTITION id_list 
       INTO '(' partition_def (',' partition_def)* ')'            #altblReorgPart
   | EXCHANGE PARTITION id_ WITH TABLE table_name 
       ((WITH | WITHOUT) VALIDATION)?                       #altblExchPart
   | ANALYZE PARTITION (id_list | ALL)                      #altblAnalPart
   | CHECK PARTITION (id_list | ALL)                        #altblCheckPart
   | OPTIMIZE PARTITION (id_list | ALL)                     #altblOptimPart
   | REBUILD PARTITION (id_list | ALL)                      #altblRebuildPart
   | REPAIR PARTITION (id_list | ALL)                       #altblRepairPart
   | REMOVE PARTITIONING                                 #altblRemovePart
   | UPGRADE PARTITIONING                                #altblUpgrPart
   ;


//    Drop statements

drop_database
    : DROP (DATABASE | SCHEMA) if_exists? id_
   ;

drop_event
    : DROP EVENT if_exists? full_id
   ;

drop_index
    : DROP INDEX (ONLINE | OFFLINE)? id_ ON table_name
     (
       ALGORITHM '='? (DEFAULT | INPLACE | COPY)
     )? 
     (
       LOCK '='? (DEFAULT | NONE | SHARED | EXCLUSIVE)
     )?
   ;

drop_logfile_group
    : DROP LOGFILE GROUP id_ ENGINE '=' engine_name
   ;

drop_procedure
    : DROP PROCEDURE if_exists? full_id
   ;

drop_function
    : DROP FUNCTION if_exists? full_id
   ;

drop_server
    : DROP SERVER if_exists? id_
   ;

drop_table
    : DROP TEMPORARY? TABLE if_exists? 
   table_list (RESTRICT | CASCADE)?
   ;

drop_tablespace
    : DROP TABLESPACE id_ (ENGINE '='? engine_name)?
   ;

drop_trigger
    : DROP TRIGGER if_exists? full_id
   ;

drop_view
    : DROP VIEW if_exists? 
   full_id (',' full_id)* (RESTRICT | CASCADE)?
   ;


//    Other DDL statements

rename_table
    : RENAME TABLE 
     table_name TO table_name 
     (',' table_name TO table_name)*
   ;

truncate_table
    : TRUNCATE TABLE? table_name
   ;


// Data Manipulation Language

//    Primary DML Statements


call_statement
    : CALL full_id
   (
     '(' (constant_list | expression_list)? ')'
   )?
   ;

delete_statement
    : single_delete_statement | multiple_delete_statement
   ;

do_statement
    : DO expression_list
   ;

handler_statement
    : handler_open_statement
   | handler_read_index_statement
   | handler_read_statement
   | handler_close_statement
   ;

insert_statement
    : INSERT 
     (LOW_PRIORITY | DELAYED | HIGH_PRIORITY)? IGNORE?
     INTO? table_name
     (PARTITION '(' id_list ')' )?
     (
       ('(' id_list ')')? insert_statement_value
       | SET 
           set_firstelem=update_elem 
           (',' set_elem+=update_elem)*
     )
     (
       ON DUPLICATE KEY UPDATE 
       duplicate_firstelem=update_elem 
       (',' duplicate_elem+=update_elem)*
     )?
   ;

load_data_statement
    : LOAD DATA 
     priority=(LOW_PRIORITY | CONCURRENT)? 
     LOCAL? INFILE filename=STRING_LITERAL
     replaceignore=(REPLACE | IGNORE)? 
   INTO TABLE table_name
     (PARTITION '(' id_list ')' )?
     (character_set charset=charset_name)?
     (
       (FIELDS | COLUMNS)
       (TERMINATED BY terminatefieldsymb=STRING_LITERAL)?
       (OPTIONALLY? ENCLOSED BY enclosedsymb=STRING_LITERAL)?
       (ESCAPED BY escapesymb=STRING_LITERAL)?
     )?
     (
       LINES 
         (STARTING BY startingsymb=STRING_LITERAL)? 
         (TERMINATED BY terminatelinesymb=STRING_LITERAL)?
     )?
     ( 
       IGNORE decimal_literal (LINES | ROWS) 
     )?
   ( '(' col_or_uservar (',' col_or_uservar)* ')' )?
     (SET update_elem (',' update_elem)*)? 
   ;

load_xml_statement
    : LOAD XML 
     priority=(LOW_PRIORITY | CONCURRENT)? 
     LOCAL? INFILE STRING_LITERAL
     (REPLACE | IGNORE)? 
   INTO TABLE table_name
     (character_set charset_name)?
     (ROWS IDENTIFIED BY '<' STRING_LITERAL '>')?
     ( IGNORE decimal_literal (LINES | ROWS) )?
   ( '(' col_or_uservar (',' col_or_uservar)* ')' )?
     (SET update_elem (',' update_elem)*)? 
   ;

replace_statement
    : REPLACE (LOW_PRIORITY | DELAYED)? 
     INTO? table_name
     (PARTITION '(' id_list ')' )?
     (
       ('(' id_list ')')? insert_statement_value
       | SET 
           set_firstelem=update_elem 
           (',' set_elem+=update_elem)*
     )
   ;

select_statement
    : query_specification (FOR UPDATE | LOCK IN SHARE MODE)?      #simpleSelect
   | query_expression (FOR UPDATE | LOCK IN SHARE MODE)?          #parenSelect
   | query_specification_nointo union_statement+ 
       (
         UNION (ALL | DISTINCT)? 
         (query_specification | query_expression)
       )?
       order_by_clause? limit_clause? 
       (FOR UPDATE | LOCK IN SHARE MODE)?                   #unionSelect
   | query_expression_nointo union_parenth+ 
       (
         UNION (ALL | DISTINCT)?
         query_expression
       )? 
       order_by_clause? limit_clause? 
       (FOR UPDATE | LOCK IN SHARE MODE)?                   #unionParenSelect
   ;

update_statement
    : single_update_statement | multiple_update_statement
   ;

// details

insert_statement_value
    : select_statement
   | (VALUES | VALUE) 
       '(' expression_list ')' 
       (',' '(' expression_list ')')*
   ;

update_elem
    : full_column_name '=' expression
   ;

col_or_uservar
    : id_ | LOCAL_ID
   ;


//    Detailed DML Statements

single_delete_statement
    : DELETE LOW_PRIORITY? QUICK? IGNORE? FROM table_name
   (PARTITION '(' id_list ')' )?
   (WHERE expression)? 
   order_by_clause? (LIMIT decimal_literal)?
   ;

multiple_delete_statement
    : DELETE LOW_PRIORITY? QUICK? IGNORE?
   (
     table_name ('.' '*')? ( ',' table_name ('.' '*')? )* 
       FROM table_sources
     | FROM 
         table_name ('.' '*')? ( ',' table_name ('.' '*')? )*
         USING table_sources
   )
   (WHERE expression)?
   ;

handler_open_statement
    : HANDLER table_name OPEN (AS? id_)?
   ;

handler_read_index_statement
    : HANDLER table_name READ index=full_id 
     (
       comparison_operator '(' constant_list ')'
       | move_order=(FIRST | NEXT | PREV | LAST)
     )
     (WHERE expression)? (LIMIT decimal_literal)?
   ;

handler_read_statement
    : HANDLER table_name READ (FIRST | NEXT)
   (WHERE expression)? (LIMIT decimal_literal)?
   ;

handler_close_statement
    : HANDLER table_name CLOSE
   ;

single_update_statement
    : UPDATE LOW_PRIORITY? IGNORE? table_name (AS? id_)?
   SET update_elem (',' update_elem)*
   (WHERE expression)? order_by_clause? limit_clause?
   ;

multiple_update_statement
    : UPDATE LOW_PRIORITY? IGNORE? table_sources
   SET update_elem (',' update_elem)*
   (WHERE expression)?
   ;

// details

order_by_clause
    : ORDER BY order_by_expression (',' order_by_expression)*
   ;

order_by_expression
    : expression (ASC | DESC)?
   ;

table_sources
    : table_source (',' table_source)*
   ;

table_source
    : table_source_item join_part*
   | '(' table_source_item join_part* ')'
   ;

table_source_item
    : table_name 
     (PARTITION '(' id_list ')' )? (AS? alias=id_)? 
     (index_hint (',' index_hint)* )?                       #atomTableItem
   | (subquery | '(' subquery ')') AS? alias=id_               #subqueryTableItem
   | '(' table_sources ')'                               #tableSourcesItem
   ;

index_hint
    : (USE | IGNORE | FORCE) (INDEX|KEY) 
   (FOR (JOIN | ORDER BY | GROUP BY))? '(' id_list ')'
   ;

join_part
    : (INNER | CROSS)? JOIN 
     table_source_item 
     (
       ON expression 
       | USING '(' id_list ')'
     )?                                            #innerJoin
   | STRAIGHT_JOIN table_source_item (ON expression)?          #straightJoin
   | (LEFT | RIGHT) OUTER? JOIN 
       table_source_item 
       (
         ON expression | 
         USING '(' id_list ')'
       )                                           #outerJoin
   | NATURAL ((LEFT | RIGHT) OUTER?)? JOIN table_source_item      #naturalJoin
   ;

subquery
    : select_statement;


//    Select Statement's Details

query_expression
    : '(' query_specification ')'
   | '(' query_expression ')'
   ;

query_expression_nointo
    : '(' query_specification_nointo ')'
   | '(' query_expression_nointo ')'
   ;

query_specification
    : SELECT select_spec* select_list select_into_expression? 
   from_clause? order_by_clause? limit_clause?
   ;

query_specification_nointo
    : SELECT select_spec* select_list 
   from_clause? order_by_clause? limit_clause?
   ;

union_parenth
    : UNION (ALL|DISTINCT)? query_expression_nointo
   ;
   
union_statement
    : UNION (ALL|DISTINCT)? 
   (query_specification_nointo | query_expression_nointo)
   ;

// details

select_spec
    : (ALL|DISTINCT|DISTINCTROW)
   | HIGH_PRIORITY | STRAIGHT_JOIN | SQL_SMALL_RESULT 
   | SQL_BIG_RESULT | SQL_BUFFER_RESULT 
   | (SQL_CACHE|SQL_NO_CACHE)
   | SQL_CALC_FOUND_ROWS
   ;

select_list
    : ('*' | select_list_elem ) (',' select_list_elem)*
   ;

select_list_elem
    : full_id '.' '*'                                    #sellistelAllCol
   | full_column_name (AS? id_)?                         #sellistelCol
   | function_call (AS? id_)?                            #sellistelFunc
   | (LOCAL_ID VAR_ASSIGN)? expression (AS? id_)?              #sellistelExpr
   ;

select_into_expression
    : INTO (LOCAL_ID | id_) (',' (LOCAL_ID | id_) )*           #selectIntoVars
   | INTO DUMPFILE STRING_LITERAL                           #selectIntoDump
   | (
      INTO OUTFILE filename=STRING_LITERAL 
      (character_set charset=charset_name)?
      (
        (FIELDS | COLUMNS) 
          (TERMINATED BY terminatefieldsymb=STRING_LITERAL)?
          (
            OPTIONALLY? 
            ENCLOSED BY enclosedsymb=STRING_LITERAL
          )?
          (ESCAPED BY escapesymb=STRING_LITERAL)? 
      )? 
      (
        LINES (STARTING BY startingsymb=STRING_LITERAL)?
         (TERMINATED BY terminatelinesymb=STRING_LITERAL)?
      )?
     )                                                #selectIntoOutfile
   ;

from_clause
    : FROM table_sources 
   (WHERE expression)? 
   (
     GROUP BY 
     group_by_item (',' group_by_item)* 
     (WITH ROLLUP)? 
   )?
   (HAVING expression)?
   ;

group_by_item
    : expression (ASC | DESC)?
   ;

limit_clause
    : LIMIT 
   (
     (decimal_literal ',')? decimal_literal
     | decimal_literal OFFSET decimal_literal
   )
   ;


// Transaction's Statements

start_transaction
    : START TRANSACTION (transact_option (',' transact_option)* )?
   ;

begin_work
    : BEGIN WORK?
   ;

commit_work
    : COMMIT WORK? (AND NO? CHAIN)? (NO? RELEASE)?
   ;

rollback_work
    : ROLLBACK WORK? (AND NO? CHAIN)? (NO? RELEASE)?
   ;

savepoint_statement
    : SAVEPOINT id_
   ;

rollback_statement
    : ROLLBACK WORK? TO SAVEPOINT? id_
   ;

release_statement
    : RELEASE SAVEPOINT id_
   ;

lock_tables
    : LOCK TABLES lock_table_element (',' lock_table_element)*
   ;

unlock_tables
    : UNLOCK TABLES
   ;


// details

set_autocommit_statement
    : SET AUTOCOMMIT '=' ('0' | '1')
   ;

set_transaction_statement
    : SET (GLOBAL | SESSION)? TRANSACTION 
   trans_characteristic (',' trans_characteristic)*
   ;

transact_option
    : WITH CONSISTENT SNAPSHOT
   | READ WRITE
   | READ ONLY
   ;

lock_table_element
    : table_name (AS? id_)? (READ LOCAL? | LOW_PRIORITY? WRITE)
   ;

trans_characteristic
    : ISOLATION LEVEL transaction_level
   | READ WRITE
   | READ ONLY
   ;

transaction_level
    : REPEATABLE READ
   | READ COMMITTED
   | READ UNCOMMITTED
   | SERIALIZABLE
   ;


// Replication's Statements

//    Base Replication

change_master
    : CHANGE MASTER TO 
   master_option (',' master_option)* channel_option?
   ;

change_repl_filter
    : CHANGE REPLICATION FILTER repl_filter (',' repl_filter)*
   ;

purge_binary_logs
    : PURGE (BINARY | MASTER) LOGS (TO | BEFORE) STRING_LITERAL
   ;

reset_master
    : RESET MASTER
   ;

reset_slave
    : RESET SLAVE ALL? channel_option?
   ;

start_slave
    : START SLAVE (thread_type (',' thread_type)*)? 
   UNTIL until_option?  
   start_slave_connection_option* channel_option?
   ;

stop_slave
    : STOP SLAVE (thread_type (',' thread_type)*)?
   ;

start_group_repl
    : START GROUP_REPLICATION
   ;

stop_group_repl
    : START GROUP_REPLICATION
   ;

// details

master_option
    : string_master_option '=' STRING_LITERAL                  #masterOptString
   | decimal_master_option '=' decimal_literal                 #masterOptDecimal
   | bool_master_option '=' ('0' | '1')                     #masterOptBool
   | MASTER_HEARTBEAT_PERIOD '=' REAL_LITERAL                  #masterOptReal
   | IGNORE_SERVER_IDS '=' '(' (id_ (',' id_)*)? ')'           #masterOptIdList
   ;

string_master_option
    : MASTER_BIND | MASTER_HOST | MASTER_USER | MASTER_PASSWORD 
   | MASTER_LOG_FILE | RELAY_LOG_FILE | MASTER_SSL_CA 
   | MASTER_SSL_CAPATH | MASTER_SSL_CERT | MASTER_SSL_CRL 
   | MASTER_SSL_CRLPATH | MASTER_SSL_KEY | MASTER_SSL_CIPHER 
   | MASTER_TLS_VERSION
   ;
decimal_master_option
    : MASTER_PORT | MASTER_CONNECT_RETRY | MASTER_RETRY_COUNT 
   | MASTER_DELAY | MASTER_LOG_POS | RELAY_LOG_POS
   ;

bool_master_option
    : MASTER_AUTO_POSITION | MASTER_SSL 
   | MASTER_SSL_VERIFY_SERVER_CERT
   ;

channel_option
    : FOR CHANNEL STRING_LITERAL
   ;

repl_filter
    : REPLICATE_DO_DB '=' '(' id_list ')'                   #replfilterDbList
   | REPLICATE_IGNORE_DB '=' '(' id_list ')'                #replfilterDbList
   | REPLICATE_DO_TABLE '=' '(' table_list ')'                 #replfilterTableList
   | REPLICATE_IGNORE_TABLE '=' '(' table_list ')'             #replfilterTableList
   | REPLICATE_WILD_DO_TABLE '=' '(' simple_string_list ')'    #replfilterStableList
   | REPLICATE_WILD_IGNORE_TABLE 
      '=' '(' simple_string_list ')'                        #replfilterStableList
   | REPLICATE_REWRITE_DB '=' '(' table_pair_list ')'          #replfilterTablepairList
   ;

thread_type
    : IO_THREAD | SQL_THREAD
   ;

until_option
    : (SQL_BEFORE_GTIDS | SQL_AFTER_GTIDS) '=' gtid_set           #untilGtidSset
   | MASTER_LOG_FILE '=' STRING_LITERAL
     ',' MASTER_LOG_POS '=' decimal_literal                 #untilMasterLog
   | RELAY_LOG_FILE '=' STRING_LITERAL
     ',' RELAY_LOG_POS '=' decimal_literal                     #untilRelayLog
   | SQL_AFTER_MTS_GAPS                               #untilSqlGaps
   ;

start_slave_connection_option
    : USER '=' con_opt_user=STRING_LITERAL
   | PASSWORD '=' con_opt_password=STRING_LITERAL
   | DEFAULT_AUTH '=' con_opt_def_auth=STRING_LITERAL
   | PLUGIN_DIR '=' con_opt_plugin_dir=STRING_LITERAL
   ;

gtid_set
    : uuid_set (',' uuid_set)*
   | STRING_LITERAL
   ;


//    XA Transactions

xa_start_transaction
    : XA (START | BEGIN) xid (JOIN | RESUME)?
   ;

xa_end_transaction
    : XA END xid (SUSPEND (FOR MIGRATE)?)?
   ;

xa_prepare
    : XA PREPARE xid
   ;

xa_commit_work
    : XA COMMIT xid (ONE PHASE)?
   ;

xa_rollback_work
    : XA ROLLBACK xid
   ;

xa_recover_work
    : XA RECOVER (CONVERT xid)?
   ;


// Prepared Statements

prepare_statement
    : PREPARE id_ FROM (STRING_LITERAL | LOCAL_ID)
   ;

execute_statement
    : EXECUTE id_ (USING user_var_list)?
   ;

deallocate_prepare
    : (DEALLOCATE | DROP) PREPARE id_
   ;


// Compound Statements

routine_body
    : block_statement | sql_statement
   ;

// details

block_statement
    : (id_ ':')? BEGIN
   (
      (declare_variable SEMI)*
      (declare_condition SEMI)*
      (declare_cursor SEMI)*
      (declare_handler SEMI)*
      procedure_sql_statement+
   )?
   END id_?
   ;

case_statement
    : CASE (id_ | expression)?
   (
     WHEN (constant | expression) 
     THEN procedure_sql_statement+
   )+
   (ELSE procedure_sql_statement+)?
   END CASE
   ;

if_statement
    : IF expression THEN procedure_sql_statement+
   (ELSEIF expression THEN procedure_sql_statement+)*
   (ELSE procedure_sql_statement+ )?
   END IF
   ;

iterate_statement
    : ITERATE id_
   ;

leave_statement
    : LEAVE id_
   ;

loop_statement
    : (id_ ':')? 
   LOOP procedure_sql_statement+ 
   END LOOP id_?
   ;

repeat_statement
    : (id_ ':')? 
   REPEAT procedure_sql_statement+ 
   UNTIL expression 
   END REPEAT id_?
   ;

return_statement
    : RETURN expression
   ;

while_statement
    : (id_ ':')? 
   WHILE expression 
   DO procedure_sql_statement+ 
   END WHILE id_?
   ;

cursor_statement
    : CLOSE id_
   | FETCH (NEXT? FROM)? id_ INTO id_list
   | OPEN id_
   ;

// details

declare_variable
    : DECLARE id_list data_type (DEFAULT default_value)?
   ;

declare_condition
    : DECLARE id_ CONDITION FOR 
   (
     decimal_literal
     | SQLSTATE VALUE? STRING_LITERAL
   )
   ;

declare_cursor
    : DECLARE id_ CURSOR FOR select_statement
   ;

declare_handler
    : DECLARE (CONTINUE | EXIT | UNDO) HANDLER FOR 
   handler_condition_value (',' handler_condition_value)* 
   routine_body
   ;

handler_condition_value
    : decimal_literal
   | SQLSTATE VALUE? STRING_LITERAL
   | id_
   | SQLWARNING
   | NOT FOUND
   | SQLEXCEPTION
   ;

procedure_sql_statement
    : (compound_statement | sql_statement) SEMI
   ;

// Administration Statements

//    Account management statements

alter_user
    : ALTER USER 
     user_name user_password_option 
     ( ',' user_name user_password_option)*                 #alterUserMysql56
   | ALTER USER if_exists? 
       user_auth_option (',' user_auth_option)*
       (REQUIRE (NONE | tls_option (AND? tls_option)* ) )?
       (WITH user_resource_option+)?
       (user_password_option | user_lock_option)*              #alterUserMysql57
   ;

create_user
    : CREATE USER user_auth_option (',' user_auth_option)*        #createUserMysql56
   | CREATE USER if_not_exists? 
       user_auth_option (',' user_auth_option)*
       (REQUIRE (NONE | tls_option (AND? tls_option)* ) )?
       (WITH user_resource_option+)?
       (user_password_option | user_lock_option)*              #createUserMysql57
   ;

drop_user
    : DROP USER if_exists? user_name (',' user_name)*
   ;

grant_statement
    : GRANT privelege_clause (',' privelege_clause)*
   ON 
     priv_obj_type=(TABLE | FUNCTION | PROCEDURE)? 
     privilege_level
   TO user_auth_option (',' user_auth_option)*
   (REQUIRE (NONE | tls_option (AND? tls_option)* ) )?
   (WITH (GRANT OPTION | user_resource_option)* )?
   ;

grant_proxy
    : GRANT PROXY ON user_name
   TO user_name (',' user_name)*
   (WITH GRANT OPTION)?
   ;

rename_user
    : RENAME USER 
   user_name TO user_name 
   (',' user_name TO user_name)
   ;

revoke_statement
    : REVOKE privelege_clause (',' privelege_clause)*
   ON 
     priv_obj_type=(TABLE | FUNCTION | PROCEDURE)? 
     privilege_level
   FROM user_name (',' user_name)*                          #detailRevoke
   | REVOKE ALL PRIVILEGES? ',' GRANT OPTION
       FROM user_name (',' user_name)*                      #shortRevoke
   ;

revoke_proxy
    : REVOKE PROXY ON user_name FROM user_name (',' user_name)*
   ;

// details

set_password_statement
    : SET PASSWORD (FOR user_name)? '=' set_password_option
   ;

user_password_option
    : PASSWORD EXPIRE
   (DEFAULT | NEVER | INTERVAL decimal_literal DAY)?
   ;

user_auth_option
    : user_name IDENTIFIED BY PASSWORD hashedpwd=STRING_LITERAL      #authByPassword
   |  user_name
       IDENTIFIED (WITH auth_plugin)? BY STRING_LITERAL        #authByString
   | user_name 
       IDENTIFIED WITH auth_plugin 
       (AS STRING_LITERAL)?                              #authByHash
   ;

tls_option
    : SSL
   | X509
   | CIPHER STRING_LITERAL
   | ISSUER STRING_LITERAL
   | SUBJECT STRING_LITERAL
   ;

user_resource_option
    : MAX_QUERIES_PER_HOUR decimal_literal
   | MAX_UPDATES_PER_HOUR decimal_literal
   | MAX_CONNECTIONS_PER_HOUR decimal_literal
   | MAX_USER_CONNECTIONS decimal_literal
   ;

user_lock_option
    : ACCOUNT (LOCK | UNLOCK)
   ;

privelege_clause
    : privilege ( '(' id_list ')' )?
   ;

privilege
    : ALL PRIVILEGES?
   | ALTER ROUTINE?
   | CREATE 
      (TEMPORARY TABLES | ROUTINE | VIEW | USER | TABLESPACE)?
   | DELETE | DROP | EVENT | EXECUTE | FILE | GRANT OPTION
   | INDEX | INSERT | LOCK TABLES | PROCESS | PROXY
   | REFERENCES | RELOAD 
   | REPLICATION (CLIENT | SLAVE)
   | SELECT
   | SHOW (VIEW | DATABASES)
   | SHUTDOWN | SUPER | TRIGGER | UPDATE | USAGE
   ;

privilege_level
    : '*'
   | '*' '.' '*'
   | id_ '.' '*'
   | id_ '.' id_
   | id_
   ;

set_password_option
    : (PASSWORD | OLD_PASSWORD) '(' STRING_LITERAL ')'
   | STRING_LITERAL
   ;


//    Table maintenance statements

analyze_table
    : ANALYZE (NO_WRITE_TO_BINLOG | LOCAL)? TABLE table_list
   ;

check_table
    : CHECK TABLE table_list check_table_option*
   ;

checksum_table
    : CHECKSUM TABLE table_list (QUICK | EXTENDED)?
   ;
   
optimize_table
    : OPTIMIZE (NO_WRITE_TO_BINLOG | LOCAL)? TABLE table_list
   ;

repair_table
    : REPAIR (NO_WRITE_TO_BINLOG | LOCAL)? TABLE table_list
   QUICK? EXTENDED? USE_FRM?
   ;

// details

check_table_option
    : FOR UPGRADE | QUICK | FAST | MEDIUM | EXTENDED | CHANGED
   ;


//    Plugin and udf statements

create_udfunction
    : CREATE AGGREGATE? FUNCTION id_ 
   RETURNS (STRING | INTEGER | REAL | DECIMAL)
   SONAME STRING_LITERAL
   ;
   
install_plugin
    : INSTALL PLUGIN id_ SONAME STRING_LITERAL
   ;

 uninstall_plugin
    : UNINSTALL PLUGIN id_
   ;


//    Set and show statements

set_statement
    : SET variable_clause '=' expression 
     (',' variable_clause '=' expression)*                     #setVariableAssignment
   | SET character_set (charset_name | DEFAULT)    #setCharset
   | SET NAMES 
       (charset_name (COLLATE collation_name)? | DEFAULT)         #setNames
   | set_password_statement                              #setPasswordStatement
   | set_transaction_statement                              #setTransaction
   | set_autocommit_statement                            #setAutocommit
   ;

show_statement
    : SHOW (BINARY | MASTER) LOGS                           #showMasterlogs
   | SHOW (BINLOG | RELAYLOG) EVENTS (IN STRING_LITERAL)?
       (FROM from_pos=decimal_literal)?
       (LIMIT 
         (offset=decimal_literal ',')? 
         row_count=decimal_literal
       )?                                             #showLogevents
   | SHOW 
       (
         character_set | COLLATION | DATABASES | SCHEMAS
         | FUNCTION STATUS | PROCEDURE STATUS
         | (GLOBAL | SESSION)? (STATUS | VARIABLES)
       ) 
       show_filter?                                   #showObjWithFilter
   | SHOW FULL? (COLUMNS | FIELDS) (FROM | IN) table_name
       ((FROM | IN) id_)? show_filter?                      #showColumns
   | SHOW CREATE (DATABASE | SCHEMA) if_not_exists? id_        #showCreateDb
   | SHOW CREATE 
       (EVENT | FUNCTION | PROCEDURE | TABLE | TRIGGER | VIEW) 
       full_id                                        #showCreateFullidobj
   | SHOW CREATE USER user_name                          #showCreateUser
   | SHOW ENGINE engine_name (STATUS | MUTEX)                  #showEngine
   | SHOW 
       (
         STORAGE? ENGINES | MASTER STATUS | PLUGINS 
         | PRIVILEGES | FULL? PROCESSLIST | PROFILES
         | SLAVE HOSTS | AUTHORS | CONTRIBUTORS
       )                                           #showGlobalinfo
   | SHOW (ERRORS | WARNINGS)
       (LIMIT 
         (offset=decimal_literal ',')? 
         row_count=decimal_literal
       )                                           #showErrWarn
   | SHOW COUNT '(' '*' ')' (ERRORS | WARNINGS)             #showCountErrWarn
   | SHOW (EVENTS | TABLE STATUS | FULL? TABLES | TRIGGERS)
       ((FROM | IN) id_)? show_filter?                      #showFromschemaFilter
   | SHOW (FUNCTION | PROCEDURE) CODE full_id                  #showRoutinecode
   | SHOW GRANTS (FOR user_name)?                           #showGrants
   | SHOW (INDEX | INDEXES | KEYS) (FROM | IN) table_name
       ((FROM | IN) id_)? (WHERE expression)?                  #showIndexes
   | SHOW OPEN TABLES ( (FROM | IN) id_)? show_filter?            #showOpentables
   | SHOW PROFILE show_profile_type (',' show_profile_type)*
       (FOR QUERY decimal_literal)?
       (LIMIT 
         (offset=decimal_literal ',')? 
         row_count=decimal_literal
       )                                           #showProfile
   | SHOW SLAVE STATUS (FOR CHANNEL STRING_LITERAL)?           #showSlavestatus
   ;

// details

variable_clause
    : LOCAL_ID | GLOBAL_ID | ( ('@' '@')? (GLOBAL | SESSION)  )? id_
   ;

show_filter
    : LIKE STRING_LITERAL
   | WHERE expression
   ;

show_profile_type
    : ALL | BLOCK IO | CONTEXT SWITCHES | CPU | IPC | MEMORY 
   | PAGE FAULTS | SOURCE | SWAPS
   ;


//    Other administrative statements

binlog_statement
    : BINLOG STRING_LITERAL
   ;

cache_index_statement
    : CACHE INDEX tbl_index_list (',' tbl_index_list)*
   ( PARTITION '(' (id_list | ALL) ')' )?
   IN id_
   ;

flush_statement
    : FLUSH (NO_WRITE_TO_BINLOG | LOCAL)?
   flush_option (',' flush_option)*
   ;

kill_statement
    : KILL (CONNECTION | QUERY)? decimal_literal+
   ;

load_index_into_cache
    : LOAD INDEX INTO CACHE 
   load_tbl_index_list (',' load_tbl_index_list)*
   ;

// remark reser (maser | slave) describe in replication's
//  statements section
reset_statement
    : RESET QUERY CACHE
   ;

shutdown_statement
    : SHUTDOWN
   ;

// details

tbl_index_list
    : table_name ( (INDEX | KEY)? '(' id_list ')' )?
   ;

flush_option
    : DES_KEY_FILE | HOSTS
   | (BINARY | ENGINE | ERROR | GENERAL | RELAY | SLOW)? LOGS
   | RELAY LOGS channel_option?
   | OPTIMIZER_COSTS | PRIVILEGES | QUERY CACHE | STATUS 
   | USER_RESOURCES
   | TABLES (WITH READ LOCK)?
   | TABLES table_list (WITH READ LOCK | FOR EXPORT)?
   ;

load_tbl_index_list
    : table_name 
   ( PARTITION '(' (partition_list=id_list | ALL) ')' )?
   ( (INDEX | KEY)? '(' index_list=id_list ')' )?
   (IGNORE LEAVES)?
   ;


// Utility Statements


simple_describe_statement
    : (EXPLAIN | DESCRIBE | DESC) table_name
   (colname=id_ | col_wildcard=STRING_LITERAL)?
   ;

full_describe_statement
    : (EXPLAIN | DESCRIBE | DESC)
   (EXTENDED | PARTITIONS | FORMAT '=' (TRADITIONAL | JSON))?
   describe_object_clause
   ;

help_statement
    : HELP STRING_LITERAL
   ;

use_statement
    : USE id_
   ;

// details

describe_object_clause
    : (
     select_statement | delete_statement | insert_statement 
     | replace_statement | update_statement
   )                                               #descstmtDescObj
   | FOR CONNECTION id_                               #connectionDescObj
   ;


// Common Clauses

//    DB Objects

table_name
    : '.' id_
   | id_ (DOT_ID | '.' id_)?
   ;

full_id
    : id_ (DOT_ID | '.' id_)?
   ;

full_column_name
    : id_ (dot_ext_id dot_ext_id? )?
   ;

index_col_name
    : id_ ('(' decimal_literal ')')? (ASC | DESC)?
   ;

user_name
    : STRING_USER_NAME;

mysql_variable
    : LOCAL_ID
   | GLOBAL_ID
   ;

charset_name
    : BINARY
   | charset_name_base
   | STRING_LITERAL
   | CHARSET_REVERSE_QOUTE_STRING
   ;

collation_name
    : id_ | STRING_LITERAL;

engine_name
    : ARCHIVE | BLACKHOLE | CSV | FEDERATED | INNODB | MEMORY 
   | MRG_MYISAM | MYISAM | NDB | NDBCLUSTER | PERFOMANCE_SCHEMA
   ;

uuid_set
    : decimal_literal '-' decimal_literal '-' decimal_literal
   '-' decimal_literal '-' decimal_literal 
   (':' decimal_literal '-' decimal_literal)+
   ;

xid
    : xid_gtrid=xid_string_id 
   (
     ',' xid_bqual=xid_string_id 
     (',' xid_formatID=decimal_literal)?
   )?
   ;

xid_string_id
    : STRING_LITERAL
   | BIT_STRING
   | HEXADECIMAL_LITERAL+
   ;

auth_plugin
    : id_ | STRING_LITERAL
   ;

id_
    : simple_id
   //| DOUBLE_QUOTE_ID
   | REVERSE_QUOTE_ID
   | CHARSET_REVERSE_QOUTE_STRING
   ;
   
simple_id
    : ID
   | charset_name_base
   | transaction_level_base
   | engine_name
   | privileges_base
   | interval_type_base
   | data_type_base
   | keywords_can_be_id
   | function_name_base
   | spatial_data_type
   ;

dot_ext_id
    : DOT_ID
   | '.' id_
   ;


//    Literals

decimal_literal
    : DECIMAL_LITERAL | ZERO_DECIMAL | ONE_DECIMAL | TWO_DECIMAL
   ;

filesize_literal
    : FILESIZE_LITERAL | decimal_literal;

string_literal
    : (
     STRING_CHARSET_NAME? STRING_LITERAL 
     | START_NATIONAL_STRING_LITERAL
   ) STRING_LITERAL+
   | (
       STRING_CHARSET_NAME? STRING_LITERAL 
       | START_NATIONAL_STRING_LITERAL
     ) (COLLATE collation_name)?
   ;

boolean_literal
    : TRUE | FALSE;

hexadecimal_literal
    : STRING_CHARSET_NAME? HEXADECIMAL_LITERAL;

null_notnull
    : NOTNULL
   | (NULL_LITERAL | NULL_SPEC_LITERAL)
   ;

constant
    : string_literal | decimal_literal
   | hexadecimal_literal | boolean_literal
   | REAL_LITERAL | BIT_STRING | NOTNULL
   | (NULL_LITERAL | NULL_SPEC_LITERAL)
   ;

current_timestamp
    : (
         CURRENT_TIMESTAMP
       | NOW
       | LOCALTIME
       | LOCALTIMESTAMP
      ) ('(' DECIMAL_LITERAL? ')')?
    ;

// CHARSET is a synonym for CHARACTER SET.
character_set
    : CHARACTER SET | CHARSET;

//    Data Types

data_type
    : (CHAR | CHARACTER | VARCHAR | TINYTEXT | TEXT | MEDIUMTEXT | LONGTEXT | LONG)
     length_one_dimension? BINARY? 
     (character_set charset_name)? (COLLATE collation_name)?      #charDatatype
   | (TINYINT | SMALLINT | MEDIUMINT | INT | INTEGER | BIGINT | INT1 | INT2 | INT3 | INT4 | INT8)
     length_one_dimension? UNSIGNED? ZEROFILL?                 #dimensionDatatype
   | (REAL | DOUBLE PRECISION? | FLOAT)
     length_two_dimension? UNSIGNED? ZEROFILL?                 #dimensionDatatype
   | (DECIMAL | NUMERIC) 
     length_two_optional_dimension? UNSIGNED? ZEROFILL?        #dimensionDatatype
   | (DATE | YEAR | TINYBLOB | BLOB | MEDIUMBLOB | LONGBLOB | JSON)      #simpleDatatype
   | (BOOL | BOOLEAN)                                          #booleanDatatype
   | (BIT | TIME | TIMESTAMP | DATETIME | BINARY | VARBINARY) 
     length_one_dimension?                               #dimensionDatatype
   | (ENUM | SET) 
     '(' STRING_LITERAL (',' STRING_LITERAL)* ')' BINARY? 
     (character_set charset_name)? (COLLATE collation_name)?      #collectCharDatatype
   | SERIAL                                              #serialDatatype
   | spatial_data_type                                   #spatialDatatype
   ;

data_type_to_convert
    : (BINARY| NCHAR) length_one_dimension?
   | CHAR length_one_dimension? (character_set charset_name)?
   | DATE | DATETIME | TIME
   | DECIMAL length_two_dimension?
   | (SIGNED | UNSIGNED) INTEGER?
   ;

spatial_data_type
    : GEOMETRY | GEOMETRYCOLLECTION | LINESTRING | MULTILINESTRING
   | MULTIPOINT | MULTIPOLYGON | POINT | POLYGON
   ;

length_one_dimension
    : '(' decimal_literal ')'
   ;

length_two_dimension
    : '(' decimal_literal ',' decimal_literal ')'
   ;

length_two_optional_dimension
    : '(' decimal_literal (',' decimal_literal)? ')'
   ;


//    Common Lists

id_list
    : id_ (',' id_)*
   ;

table_list
    : table_name (',' table_name)*
   ;

table_pair_list
    : '(' table_name ',' table_name ')' 
   (',' '(' table_name ',' table_name ')')*
   ;

index_colname_list
    : '(' index_col_name (',' index_col_name)* ')'
   ;

expression_list
    : expression (',' expression)*
   ;

constant_list
    : constant (',' constant)*
   ;

simple_string_list
    : STRING_LITERAL (',' STRING_LITERAL)*
   ;

user_var_list
    : LOCAL_ID (',' LOCAL_ID)*
   ;


//    Common Expressons

default_value
    : NULL_LITERAL
   | constant
   | current_timestamp
   ;

if_exists
    : IF EXISTS;

if_not_exists
    : IF NOT EXISTS;


//    Functions

function_call
    : specific_function_call                             #specificFunctionCall
   | aggregate_windowed_function                         #aggregateFunctionCall
   | scalar_function_name '(' function_args? ')'               #scalarFunctionCall
   | id_ dot_ext_id? '(' function_args? ')'                 #udfFunctionCall
   ;

specific_function_call
    : (
     CURRENT_DATE | CURRENT_TIME | CURRENT_TIMESTAMP 
     | CURRENT_USER | LOCALTIME
   )                                               #simpleSpecificFCall
   | CONVERT '(' expression ',' data_type_to_convert ')'       #convertDataTypeFCall
   | CONVERT '(' expression USING charset_name ')'             #convertDataTypeFCall
   | CAST '(' expression AS data_type_to_convert ')'           #convertDataTypeFCall
   | VALUES '(' full_column_name ')'                        #valuesFCall
   | CASE expression 
     (WHEN condarg+=function_arg THEN resarg+=function_arg)+ 
     (ELSE function_arg)? END                            #caseFCall
   | CASE 
     (WHEN condarg+=function_arg THEN resarg+=function_arg )+ 
     (ELSE function_arg)? END                            #caseFCall
   | CHAR '(' function_args  (USING charset_name)? ')'            #charFCall
   | POSITION '(' (fstr=string_literal | fexpr=expression) 
     IN (sstr=string_literal | sexpr=expression) ')'           #positionFCall
   | (SUBSTR | SUBSTRING) '(' 
       (string_literal | fexpr=expression) FROM 
       (fdecimal=decimal_literal | sexpr=expression)
       (FOR (sdecimal=decimal_literal | texpr=expression) )? 
     ')'                                           #substrFCall
   | TRIM '(' 
       (BOTH | LEADING | TRAILING) 
       (fstr=string_literal | fexpr=expression)? 
       FROM (sstr=string_literal | sexpr=expression) 
     ')'                                           #trimFCall
   | TRIM '(' 
       (fstr=string_literal | fexpr=expression) 
       FROM (sstr=string_literal | sexpr=expression) 
     ')'                                           #trimFCall
   | WEIGHT_STRING '(' 
       (string_literal | expression) (AS (CHAR | BINARY) 
       '(' decimal_literal ')' )?  levels_in_weight_string?  
     ')'                                           #weightFCall
   | EXTRACT '(' 
       interval_type 
       FROM (fstr=string_literal | fexpr=expression) 
     ')'                                           #extractFCall
   | GET_FORMAT '(' (DATE|TIME|DATETIME) ',' string_literal ')'   #getFormatFCall
   ;

levels_in_weight_string
    : LEVEL 
     firstlevel=decimal_literal 
     firstord=(ASC | DESC | REVERSE)? 
     (
       ',' nextlevel+=decimal_literal 
       nextord+=(ASC | DESC | REVERSE)?
     )*                                            #levelWeightFList
   | LEVEL 
     firstlevel=decimal_literal '-' lastlevel=decimal_literal     #levelWeightFRange
   ;

aggregate_windowed_function
    : (AVG | MAX | MIN | SUM) 
     '(' (ALL | DISTINCT)? function_arg ')'
   | COUNT '(' ('*' | ALL? function_arg) ')'
   | COUNT '(' DISTINCT function_args ')'
   | (
       BIT_AND | BIT_OR | BIT_XOR | STD | STDDEV | STDDEV_POP 
       | STDDEV_SAMP | VAR_POP | VAR_SAMP | VARIANCE
     ) '(' ALL? function_arg ')'
   | GROUP_CONCAT '(' 
       DISTINCT? function_args 
       (ORDER BY 
         order_by_expression (',' order_by_expression)* 
       )? (SEPARATOR STRING_LITERAL)? 
     ')'
   ;

scalar_function_name
    : function_name_base
   | ASCII | CURDATE | CURRENT_DATE | CURRENT_TIME 
   | CURRENT_TIMESTAMP | CURTIME | DATE_ADD | DATE_SUB 
   | IF | LOCALTIME | LOCALTIMESTAMP | MID | NOW | REPLACE 
   | SUBSTR | SUBSTRING | SYSDATE | TRIM 
   | UTC_DATE | UTC_TIME | UTC_TIMESTAMP
   ;

function_args
    : (constant | full_column_name | function_call | expression) 
   (
     ',' 
     (constant | full_column_name | function_call | expression)
   )*
   ;

function_arg
    : constant | full_column_name | function_call | expression
   ;


//    Expressions, predicates

// Simplified approach for expression
expression
    : (NOT | '!') expression                             #notExpression
   | expression logical_operator expression                 #logicalExpression
   | predicate IS NOT? (TRUE | FALSE | UNKNOWN)             #isExpression
   | predicate                                        #predicateExpression
   ;

predicate
    : predicate NOT? IN '(' (subquery | expression_list) ')'      #inPredicate
   | predicate IS null_notnull                              #isNullPredicate
   | predicate comparison_operator predicate                #binaryComparasionPredicate
   | predicate 
     comparison_operator (ALL | ANY | SOME) '(' subquery ')'      #subqueryComparasionPredicate
   | predicate NOT? BETWEEN predicate AND predicate            #betweenPredicate
   | predicate SOUNDS LIKE predicate                        #soundsLikePredicate
   | predicate NOT? LIKE predicate (ESCAPE string_literal)?    #likePredicate
   | predicate NOT? (REGEXP|RLIKE) predicate                #regexpPredicate
   | (LOCAL_ID VAR_ASSIGN)? expression_atom                 #expressionAtomPredicate
   ;


// Add in ASTVisitor null_notnull in constant
expression_atom
    : DEFAULT                                         #defaultExpressionAtom
   | constant                                         #constantExpressionAtom
   | full_column_name                                    #fullColumnNameExpressionAtom
   | function_call                                    #functionCallExpressionAtom
   | mysql_variable                                   #mysqlVariableExpressionAtom
   | unary_operator expression_atom                      #unaryExpressionAtom
   | BINARY expression_atom                              #binaryExpressionAtom
   | '(' expression ')'                                  #nestedExpressionAtom
   | EXISTS? '(' subquery ')'                            #existsExpessionAtom
   | INTERVAL expression interval_type                      #intervalExpressionAtom
   | expression_atom bit_operator expression_atom              #bitExpressionAtom
   | expression_atom math_operator expression_atom             #mathExpressionAtom
   | expression_atom arrow_operator expression_atom            #arrowExpressionAtom
   ;

unary_operator
    : '!' | '~' | '+' | '-' | NOT
   ;

comparison_operator
    : '=' | '>' | '<' | '<' '=' | '>' '=' 
   | '<' '>' | '!' '=' | '<' '=' '>'
   ;

logical_operator
    : AND | '&' '&' | XOR | OR | '|' '|'
   ;

bit_operator
    : '<' '<' | '>' '>' | '&' | '^' | '|'
   ;

math_operator
    : '*' | '/' | '%' | DIV | MOD | '+' | '-'
   ;

arrow_operator
    : '-' '>'
   ;

//    Simple id sets
//     (that keyword, which can be id)

charset_name_base
    : ARMSCII8 | ASCII | BIG5 | CP1250 | CP1251 | CP1256 | CP1257
   | CP850 | CP852 | CP866 | CP932 | DEC8 | EUCJPMS | EUCKR 
   | GB2312 | GBK | GEOSTD8 | GREEK | HEBREW | HP8 | KEYBCS2 
   | KOI8R | KOI8U | LATIN1 | LATIN2 | LATIN5 | LATIN7 | MACCE
   | MACROMAN | SJIS | SWE7 | TIS620 | UCS2 | UJIS | UTF16 
   | UTF16LE | UTF32 | UTF8 | UTF8MB3 | UTF8MB4
   ;

transaction_level_base
    : REPEATABLE | COMMITTED | UNCOMMITTED | SERIALIZABLE
   ;

privileges_base
    : TABLES | ROUTINE | EXECUTE | FILE | PROCESS 
   | RELOAD | SHUTDOWN | SUPER | PRIVILEGES
   ;

interval_type_base
    : QUARTER | MONTH | DAY | HOUR 
   | MINUTE | WEEK | SECOND | MICROSECOND
   ;

data_type_base
    : DATE | TIME | TIMESTAMP | DATETIME | YEAR | ENUM | TEXT
   ;
 
keywords_can_be_id
    : ACTION | AFTER | ALGORITHM | ANY | AT | AUTHORS | AUTOCOMMIT
   | AUTOEXTEND_SIZE | AUTO_INCREMENT | AVG_ROW_LENGTH | BEGIN 
   | BINLOG | BIT | BTREE | CASCADED | CHAIN | CHECKSUM 
   | CIPHER | CLIENT | COALESCE | CODE | COLUMNS 
   | COLUMN_FORMAT | COMMENT | COMMIT | COMPACT | COMPLETION 
   | COMPRESSED | CONCURRENT | CONNECTION | CONSISTENT 
   | CONTAINS | CONTRIBUTORS | COPY | DATA | DATAFILE 
   | DEFINER | DELAY_KEY_WRITE | DIRECTORY | DISABLE | DISCARD 
   | DISK | DO | DUMPFILE| DUPLICATE | DYNAMIC | ENABLE | ENDS 
   | ENGINE | ENGINES | ERRORS | ESCAPE | EVEN | EVENT | EVENTS 
   | EVERY | EXCHANGE | EXCLUSIVE | EXPIRE | EXTENT_SIZE 
   | FIELDS | FIRST | FIXED | FULL | FUNCTION | GLOBAL | GRANTS 
   | HASH | HOST | IDENTIFIED | IMPORT | INITIAL_SIZE | INPLACE 
   | INSERT_METHOD | INVOKER | ISOLATION | ISSUER 
   | KEY_BLOCK_SIZE | LANGUAGE | LAST | LESS | LEVEL | LIST
   | LOCAL | LOGS | LOGFILE | MASTER | MAX_CONNECTIONS_PER_HOUR 
   | MAX_QUERIES_PER_HOUR | MAX_ROWS | MAX_SIZE 
   | MAX_UPDATES_PER_HOUR | MAX_USER_CONNECTIONS | MEMORY 
   | MERGE | MID | MIN_ROWS | MUTEX | SHARE | MODIFY | MYSQL 
   | NAME | NAMES | NCHAR | NO | NODEGROUP | NONE | OFFLINE 
   | OFFSET | OJ | OLD_PASSWORD | ONLINE | ONLY | OPTIONS 
   | OWNER | PACK_KEYS | PARSER | PARTIAL | PARTITIONING 
   | PARTITIONS | PASSWORD | PLUGINS | PORT | PRESERVE 
   | PROCESSLIST | PROFILE | PROFILES | PROXY | QUERY | QUICK 
   | REBUILD | REDO_BUFFER_SIZE | REDUNDANT | RELAYLOG | REMOVE 
   | REORGANIZE | REPAIR | REPLICATION | RETURNS | ROLLBACK 
   | ROLLUP | ROW | ROWS | ROW_FORMAT | SAVEPOINT | SCHEDULE 
   | SECURITY | SERVER | SESSION | SHARE | SHARED | SIGNED 
   | SIMPLE | SLAVE | SNAPSHOT | SOCKET | SOME | SOUNDS 
   | SQL_BUFFER_RESULT | SQL_CACHE | SQL_NO_CACHE | START 
   | STARTS | STATS_AUTO_RECALC | STATS_PERSISTENT 
   | STATS_SAMPLE_PAGES | STATUS | STORAGE | SUBJECT 
   | SUBPARTITION | SUBPARTITIONS | TABLESPACE | TEMPORARY 
   | TEMPTABLE | THAN | TRANSACTION | TRUNCATE | UNDEFINED 
   | UNDOFILE | UNDO_BUFFER_SIZE | UNKNOWN | UPGRADE | USER 
   | VALUE | VARIABLES | VIEW | WAIT | WARNINGS | WORK 
   | WRAPPER | X509 | XML
   ;

function_name_base
    : ABS | ACOS | ADDDATE | ADDTIME | AES_DECRYPT | AES_ENCRYPT 
   | AREA | ASBINARY | ASIN | ASTEXT | ASWKB | ASWKT 
   | ASYMMETRIC_DECRYPT | ASYMMETRIC_DERIVE 
   | ASYMMETRIC_ENCRYPT | ASYMMETRIC_SIGN | ASYMMETRIC_VERIFY 
   | ATAN | ATAN2 | BENCHMARK | BIN | BIT_COUNT | BIT_LENGTH 
   | BUFFER | CEIL | CEILING | CENTROID | CHARACTER_LENGTH 
   | CHARSET | CHAR_LENGTH | COERCIBILITY | COLLATION 
   | COMPRESS | CONCAT | CONCAT_WS | CONNECTION_ID | CONV 
   | CONVERT_TZ | COS | COT | COUNT | CRC32 
   | CREATE_ASYMMETRIC_PRIV_KEY | CREATE_ASYMMETRIC_PUB_KEY 
   | CREATE_DH_PARAMETERS | CREATE_DIGEST | CROSSES | DATE 
   | DATEDIFF | DATE_FORMAT | DAY | DAYNAME | DAYOFMONTH 
   | DAYOFWEEK | DAYOFYEAR | DECODE | DEGREES | DES_DECRYPT 
   | DES_ENCRYPT | DIMENSION | DISJOINT | ELT | ENCODE 
   | ENCRYPT | ENDPOINT | ENVELOPE | EQUALS | EXP | EXPORT_SET 
   | EXTERIORRING | EXTRACTVALUE | FIELD | FIND_IN_SET | FLOOR 
   | FORMAT | FOUND_ROWS | FROM_BASE64 | FROM_DAYS 
   | FROM_UNIXTIME | GEOMCOLLFROMTEXT | GEOMCOLLFROMWKB 
   | GEOMETRYCOLLECTION | GEOMETRYCOLLECTIONFROMTEXT 
   | GEOMETRYCOLLECTIONFROMWKB | GEOMETRYFROMTEXT 
   | GEOMETRYFROMWKB | GEOMETRYN | GEOMETRYTYPE | GEOMFROMTEXT 
   | GEOMFROMWKB | GET_FORMAT | GET_LOCK | GLENGTH | GREATEST 
   | GTID_SUBSET | GTID_SUBTRACT | HEX | HOUR | IFNULL 
   | INET6_ATON | INET6_NTOA | INET_ATON | INET_NTOA | INSTR 
   | INTERIORRINGN | INTERSECTS | ISCLOSED | ISEMPTY | ISNULL 
   | ISSIMPLE | IS_FREE_LOCK | IS_IPV4 | IS_IPV4_COMPAT 
   | IS_IPV4_MAPPED | IS_IPV6 | IS_USED_LOCK | LAST_INSERT_ID 
   | LCASE | LEAST | LEFT | LENGTH | LINEFROMTEXT | LINEFROMWKB
   | LINESTRING | LINESTRINGFROMTEXT | LINESTRINGFROMWKB | LN 
   | LOAD_FILE | LOCATE | LOG | LOG10 | LOG2 | LOWER | LPAD 
   | LTRIM | MAKEDATE | MAKETIME | MAKE_SET | MASTER_POS_WAIT 
   | MBRCONTAINS | MBRDISJOINT | MBREQUAL | MBRINTERSECTS 
   | MBROVERLAPS | MBRTOUCHES | MBRWITHIN | MD5 | MICROSECOND 
   | MINUTE | MLINEFROMTEXT | MLINEFROMWKB | MONTH | MONTHNAME 
   | MPOINTFROMTEXT | MPOINTFROMWKB | MPOLYFROMTEXT 
   | MPOLYFROMWKB | MULTILINESTRING | MULTILINESTRINGFROMTEXT 
   | MULTILINESTRINGFROMWKB | MULTIPOINT | MULTIPOINTFROMTEXT 
   | MULTIPOINTFROMWKB | MULTIPOLYGON | MULTIPOLYGONFROMTEXT 
   | MULTIPOLYGONFROMWKB | NAME_CONST | NULLIF | NUMGEOMETRIES 
   | NUMINTERIORRINGS | NUMPOINTS | OCT | OCTET_LENGTH | ORD 
   | OVERLAPS | PERIOD_ADD | PERIOD_DIFF | PI | POINT 
   | POINTFROMTEXT | POINTFROMWKB | POINTN | POLYFROMTEXT 
   | POLYFROMWKB | POLYGON | POLYGONFROMTEXT | POLYGONFROMWKB 
   | POSITION| POW | POWER | QUARTER | QUOTE | RADIANS | RAND 
   | RANDOM_BYTES | RELEASE_LOCK | REVERSE | RIGHT | ROUND 
   | ROW_COUNT | RPAD | RTRIM | SECOND | SEC_TO_TIME 
   | SESSION_USER | SHA | SHA1 | SHA2 | SIGN | SIN | SLEEP 
   | SOUNDEX | SQL_THREAD_WAIT_AFTER_GTIDS | SQRT | SRID 
   | STARTPOINT | STRCMP | STR_TO_DATE | ST_AREA | ST_ASBINARY 
   | ST_ASTEXT | ST_ASWKB | ST_ASWKT | ST_BUFFER | ST_CENTROID 
   | ST_CONTAINS | ST_CROSSES | ST_DIFFERENCE | ST_DIMENSION 
   | ST_DISJOINT | ST_DISTANCE | ST_ENDPOINT | ST_ENVELOPE 
   | ST_EQUALS | ST_EXTERIORRING | ST_GEOMCOLLFROMTEXT 
   | ST_GEOMCOLLFROMTXT | ST_GEOMCOLLFROMWKB 
   | ST_GEOMETRYCOLLECTIONFROMTEXT 
   | ST_GEOMETRYCOLLECTIONFROMWKB | ST_GEOMETRYFROMTEXT 
   | ST_GEOMETRYFROMWKB | ST_GEOMETRYN | ST_GEOMETRYTYPE 
   | ST_GEOMFROMTEXT | ST_GEOMFROMWKB | ST_INTERIORRINGN 
   | ST_INTERSECTION | ST_INTERSECTS | ST_ISCLOSED | ST_ISEMPTY
   | ST_ISSIMPLE | ST_LINEFROMTEXT | ST_LINEFROMWKB 
   | ST_LINESTRINGFROMTEXT | ST_LINESTRINGFROMWKB 
   | ST_NUMGEOMETRIES | ST_NUMINTERIORRING 
   | ST_NUMINTERIORRINGS | ST_NUMPOINTS | ST_OVERLAPS 
   | ST_POINTFROMTEXT | ST_POINTFROMWKB | ST_POINTN 
   | ST_POLYFROMTEXT | ST_POLYFROMWKB | ST_POLYGONFROMTEXT 
   | ST_POLYGONFROMWKB | ST_SRID | ST_STARTPOINT 
   | ST_SYMDIFFERENCE | ST_TOUCHES | ST_UNION | ST_WITHIN 
   | ST_X | ST_Y | SUBDATE | SUBSTRING_INDEX | SUBTIME 
   | SYSTEM_USER | TAN | TIME | TIMEDIFF | TIMESTAMP 
   | TIMESTAMPADD | TIMESTAMPDIFF | TIME_FORMAT | TIME_TO_SEC 
   | TOUCHES | TO_BASE64 | TO_DAYS | TO_SECONDS | UCASE 
   | UNCOMPRESS | UNCOMPRESSED_LENGTH | UNHEX | UNIX_TIMESTAMP
   | UPDATEXML | UPPER | UUID | UUID_SHORT 
   | VALIDATE_PASSWORD_STRENGTH | VERSION 
   | WAIT_UNTIL_SQL_THREAD_AFTER_GTIDS | WEEK | WEEKDAY 
   | WEEKOFYEAR | WEIGHT_STRING | WITHIN | YEAR | YEARWEEK 
   | Y_FUNCTION | X_FUNCTION
   ;

//Lexers
// SKIP

SPACE:                               [ \t\r\n]+    -> channel(HIDDEN);
SPEC_MYSQL_COMMENT:                  '/*!' .+? '*/' -> channel(HIDDEN);
COMMENT_INPUT:                       '/*' .*? '*/' -> channel(HIDDEN);
LINE_COMMENT:                        (('-- ' | '#') ~[\r\n]* ('\r'? '\n' | EOF) | '--' ('\r'? '\n' | EOF)) -> channel(HIDDEN);


// Keywords
// Common Keywords

ADD:                                 A D D;
ALL:                                 A L L;
ALTER:                               A L T E R;
ALWAYS:                              A L W A Y S;
ANALYZE:                             A N A L Y Z E;
AND:                                 A N D;
AS:                                  A S;
ASC:                                 A S C;
BEFORE:                              B E F O R E;
BETWEEN:                             B E T W E E N;
BOTH:                                B O T H;
BY:                                  B Y;
CALL:                                C A L L;
CASCADE:                             C A S C A D E;
CASE:                                C A S E;
CAST:                                C A S T;
CHANGE:                              C H A N G E;
CHARACTER:                           C H A R A C T E R;
CHECK:                               C H E C K;
COLLATE:                             C O L L A T E;
COLUMN:                              C O L U M N;
CONDITION:                           C O N D I T I O N;
CONSTRAINT:                          C O N S T R A I N T;
CONTINUE:                            C O N T I N U E;
CONVERT:                             C O N V E R T;
CREATE:                              C R E A T E;
CROSS:                               C R O S S;
CURRENT_USER:                        C U R R E N T '_' U S E R;
CURSOR:                              C U R S O R;
DATABASE:                            D A T A B A S E;
DATABASES:                           D A T A B A S E S;
DECLARE:                             D E C L A R E;
DEFAULT:                             D E F A U L T;
DELAYED:                             D E L A Y E D;
DELETE:                              D E L E T E;
DESC:                                D E S C;
DESCRIBE:                            D E S C R I B E;
DETERMINISTIC:                       D E T E R M I N I S T I C;
DISTINCT:                            D I S T I N C T;
DISTINCTROW:                         D I S T I N C T R O W;
DROP:                                D R O P;
EACH:                                E A C H;
ELSE:                                E L S E;
ELSEIF:                              E L S E I F;
ENCLOSED:                            E N C L O S E D;
ESCAPED:                             E S C A P E D;
EXISTS:                              E X I S T S;
EXIT:                                E X I T;
EXPLAIN:                             E X P L A I N;
FALSE:                               F A L S E;
FETCH:                               F E T C H;
FOR:                                 F O R;
FORCE:                               F O R C E;
FOREIGN:                             F O R E I G N;
FROM:                                F R O M;
FULLTEXT:                            F U L L T E X T;
GENERATED:                           G E N E R A T E D;
GRANT:                               G R A N T;
GROUP:                               G R O U P;
HAVING:                              H A V I N G;
HIGH_PRIORITY:                       H I G H '_' P R I O R I T Y;
IF:                                  I F;
IGNORE:                              I G N O R E;
IN:                                  I N;
INDEX:                               I N D E X;
INFILE:                              I N F I L E;
INNER:                               I N N E R;
INOUT:                               I N O U T;
INSERT:                              I N S E R T;
INTERVAL:                            I N T E R V A L;
INTO:                                I N T O;
IS:                                  I S;
ITERATE:                             I T E R A T E;
JOIN:                                J O I N;
KEY:                                 K E Y;
KEYS:                                K E Y S;
KILL:                                K I L L;
LEADING:                             L E A D I N G;
LEAVE:                               L E A V E;
LEFT:                                L E F T;
LIKE:                                L I K E;
LIMIT:                               L I M I T;
LINEAR:                              L I N E A R;
LINES:                               L I N E S;
LOAD:                                L O A D;
LOCK:                                L O C K;
LOOP:                                L O O P;
LOW_PRIORITY:                        L O W '_' P R I O R I T Y;
MASTER_BIND:                         M A S T E R '_' B I N D;
MASTER_SSL_VERIFY_SERVER_CERT:       M A S T E R '_' S S L '_' V E R I F Y '_' S E R V E R '_' C E R T;
MATCH:                               M A T C H;
MAXVALUE:                            M A X V A L U E;
MODIFIES:                            M O D I F I E S;
NATURAL:                             N A T U R A L;
NOTNULL:                             N O T SPACE+ (N U L L | '\\N');
NOT:                                 N O T;
NO_WRITE_TO_BINLOG:                  N O '_' W R I T E '_' T O '_' B I N L O G;
NULL_LITERAL:                        N U L L;
ON:                                  O N;
OPTIMIZE:                            O P T I M I Z E;
OPTION:                              O P T I O N;
OPTIONALLY:                          O P T I O N A L L Y;
OR:                                  O R;
ORDER:                               O R D E R;
OUT:                                 O U T;
OUTER:                               O U T E R;
OUTFILE:                             O U T F I L E;
PARTITION:                           P A R T I T I O N;
PRIMARY:                             P R I M A R Y;
PROCEDURE:                           P R O C E D U R E;
PURGE:                               P U R G E;
RANGE:                               R A N G E;
READ:                                R E A D;
READS:                               R E A D S;
REFERENCES:                          R E F E R E N C E S;
REGEXP:                              R E G E X P;
RELEASE:                             R E L E A S E;
RENAME:                              R E N A M E;
REPEAT:                              R E P E A T;
REPLACE:                             R E P L A C E;
REQUIRE:                             R E Q U I R E;
RESTRICT:                            R E S T R I C T;
RETURN:                              R E T U R N;
REVOKE:                              R E V O K E;
RIGHT:                               R I G H T;
RLIKE:                               R L I K E;
SCHEMA:                              S C H E M A;
SCHEMAS:                             S C H E M A S ;
SELECT:                              S E L E C T;
SET:                                 S E T;
SEPARATOR:                           S E P A R A T O R;
SHOW:                                S H O W;
SPATIAL:                             S P A T I A L;
SQL:                                 S Q L;
SQLEXCEPTION:                        S Q L E X C E P T I O N;
SQLSTATE:                            S Q L S T A T E;
SQLWARNING:                          S Q L W A R N I N G;
SQL_BIG_RESULT:                      S Q L '_' B I G '_' R E S U L T;
SQL_CALC_FOUND_ROWS:                 S Q L '_' C A L C '_' F O U N D '_' R O W S;
SQL_SMALL_RESULT:                    S Q L '_' S M A L L '_' R E S U L T;
SSL:                                 S S L;
STARTING:                            S T A R T I N G;
STORED:                              S T O R E D;
STRAIGHT_JOIN:                       S T R A I G H T '_' J O I N;
TABLE:                               T A B L E;
TERMINATED:                          T E R M I N A T E D;
THEN:                                T H E N;
TO:                                  T O;
TRAILING:                            T R A I L I N G;
TRIGGER:                             T R I G G E R;
TRUE:                                T R U E;
UNDO:                                U N D O;
UNICODE:                             U N I C O D E;
UNION:                               U N I O N;
UNIQUE:                              U N I Q U E;
UNLOCK:                              U N L O C K;
UNSIGNED:                            U N S I G N E D;
UPDATE:                              U P D A T E;
USAGE:                               U S A G E;
USE:                                 U S E;
USING:                               U S I N G;
VALUES:                              V A L U E S;
VIRTUAL:                             V I R T U A L;
WHEN:                                W H E N;
WHERE:                               W H E R E;
WHILE:                               W H I L E;
WITH:                                W I T H;
WRITE:                               W R I T E;
XOR:                                 X O R;
ZEROFILL:                            Z E R O F I L L;


// DATA TYPE Keywords
BOOL:                                B O O L;
BOOLEAN:                             B O O L E A N;
INT1:                                I N T '1';
INT2:                                I N T '2';
INT3:                                I N T '3';
INT4:                                I N T '4';
INT8:                                I N T '8';
TINYINT:                             T I N Y I N T;
SMALLINT:                            S M A L L I N T;
MEDIUMINT:                           M E D I U M I N T;
INT:                                 I N T;
INTEGER:                             I N T E G E R;
BIGINT:                              B I G I N T;
REAL:                                R E A L;
DOUBLE:                              D O U B L E;
PRECISION:                           P R E C I S I O N;
FLOAT:                               F L O A T;
DECIMAL:                             D E C I M A L;
NUMERIC:                             N U M E R I C;
DATE:                                D A T E;
TIME:                                T I M E;
TIMESTAMP:                           T I M E S T A M P;
DATETIME:                            D A T E T I M E;
YEAR:                                Y E A R;
CHAR:                                C H A R;
VARCHAR:                             V A R C H A R;
BINARY:                              B I N A R Y;
VARBINARY:                           V A R B I N A R Y;
TINYBLOB:                            T I N Y B L O B;
BLOB:                                B L O B;
MEDIUMBLOB:                          M E D I U M B L O B;
LONGBLOB:                            L O N G B L O B;
TINYTEXT:                            T I N Y T E X T;
TEXT:                                T E X T;
MEDIUMTEXT:                          M E D I U M T E X T;
LONG:                                L O N G;
LONGTEXT:                            L O N G T E X T;
ENUM:                                E N U M;
SERIAL:                              S E R I A L;


// Interval type Keywords

YEAR_MONTH:                          Y E A R '_' M O N T H;
DAY_HOUR:                            D A Y '_' H O U R;
DAY_MINUTE:                          D A Y '_' M I N U T E;
DAY_SECOND:                          D A Y '_' S E C O N D;
HOUR_MINUTE:                         H O U R '_' M I N U T E;
HOUR_SECOND:                         H O U R '_' S E C O N D;
MINUTE_SECOND:                       M I N U T E '_' S E C O N D;
SECOND_MICROSECOND:                  S E C O N D '_' M I C R O S E C O N D;
MINUTE_MICROSECOND:                  M I N U T E '_' M I C R O S E C O N D;
HOUR_MICROSECOND:                    H O U R '_' M I C R O S E C O N D;
DAY_MICROSECOND:                     D A Y '_' M I C R O S E C O N D;


// Group function Keywords

AVG:                                 A V G;
BIT_AND:                             B I T '_' A N D;
BIT_OR:                              B I T '_' O R;
BIT_XOR:                             B I T '_' X O R;
COUNT:                               C O U N T;
GROUP_CONCAT:                        G R O U P '_' C O N C A T;
MAX:                                 M A X;
MIN:                                 M I N;
STD:                                 S T D;
STDDEV:                              S T D D E V;
STDDEV_POP:                          S T D D E V '_' P O P;
STDDEV_SAMP:                         S T D D E V '_' S A M P;
SUM:                                 S U M;
VAR_POP:                             V A R '_' P O P;
VAR_SAMP:                            V A R '_' S A M P;
VARIANCE:                            V A R I A N C E;


// Common function Keywords

CURRENT_DATE:                        C U R R E N T '_' D A T E;
CURRENT_TIME:                        C U R R E N T '_' T I M E;
CURRENT_TIMESTAMP:                   C U R R E N T '_' T I M E S T A M P;
LOCALTIME:                           L O C A L T I M E;
CURDATE:                             C U R D A T E;
CURTIME:                             C U R T I M E;
DATE_ADD:                            D A T E '_' A D D;
DATE_SUB:                            D A T E '_' S U B;
EXTRACT:                             E X T R A C T;
LOCALTIMESTAMP:                      L O C A L T I M E S T A M P;
NOW:                                 N O W;
POSITION:                            P O S I T I O N;
SUBSTR:                              S U B S T R;
SUBSTRING:                           S U B S T R I N G;
SYSDATE:                             S Y S D A T E;
TRIM:                                T R I M;
UTC_DATE:                            U T C '_' D A T E;
UTC_TIME:                            U T C '_' T I M E;
UTC_TIMESTAMP:                       U T C '_' T I M E S T A M P;



// Keywords, but can be ID
// Common Keywords, but can be ID

ACCOUNT:                             A C C O U N T;
ACTION:                              A C T I O N;
AFTER:                               A F T E R;
AGGREGATE:                           A G G R E G A T E;
ALGORITHM:                           A L G O R I T H M;
ANY:                                 A N Y;
AT:                                  A T;
AUTHORS:                             A U T H O R S;
AUTOCOMMIT:                          A U T O C O M M I T;
AUTOEXTEND_SIZE:                     A U T O E X T E N D '_' S I Z E;
AUTO_INCREMENT:                      A U T O '_' I N C R E M E N T;
AVG_ROW_LENGTH:                      A V G '_' R O W '_' L E N G T H;
BEGIN:                               B E G I N;
BINLOG:                              B I N L O G;
BIT:                                 B I T;
BLOCK:                               B L O C K;
BTREE:                               B T R E E;
CACHE:                               C A C H E;
CASCADED:                            C A S C A D E D;
CHAIN:                               C H A I N;
CHANGED:                             C H A N G E D;
CHANNEL:                             C H A N N E L;
CHECKSUM:                            C H E C K S U M;
CIPHER:                              C I P H E R;
CLIENT:                              C L I E N T;
CLOSE:                               C L O S E;
COALESCE:                            C O A L E S C E;
CODE:                                C O D E;
COLUMNS:                             C O L U M N S;
COLUMN_FORMAT:                       C O L U M N '_' F O R M A T;
COMMENT:                             C O M M E N T;
COMMIT:                              C O M M I T;
COMPACT:                             C O M P A C T;
COMPLETION:                          C O M P L E T I O N;
COMPRESSED:                          C O M P R E S S E D;
COMPRESSION:                         C O M P R E S S I O N;
CONCURRENT:                          C O N C U R R E N T;
CONNECTION:                          C O N N E C T I O N;
CONSISTENT:                          C O N S I S T E N T;
CONTAINS:                            C O N T A I N S;
CONTEXT:                             C O N T E X T;
CONTRIBUTORS:                        C O N T R I B U T O R S;
COPY:                                C O P Y;
CPU:                                 C P U;
DATA:                                D A T A;
DATAFILE:                            D A T A F I L E;
DEALLOCATE:                          D E A L L O C A T E;
DEFAULT_AUTH:                        D E F A U L T '_' A U T H;
DEFINER:                             D E F I N E R;
DELAY_KEY_WRITE:                     D E L A Y '_' K E Y '_' W R I T E;
DES_KEY_FILE:                        D E S '_' K E Y '_' F I L E;
DIRECTORY:                           D I R E C T O R Y;
DISABLE:                             D I S A B L E;
DISCARD:                             D I S C A R D;
DISK:                                D I S K;
DO:                                  D O;
DUMPFILE:                            D U M P F I L E;
DUPLICATE:                           D U P L I C A T E;
DYNAMIC:                             D Y N A M I C;
ENABLE:                              E N A B L E;
ENCRYPTION:                          E N C R Y P T I O N;
END:                                 E N D;
ENDS:                                E N D S;
ENGINE:                              E N G I N E;
ENGINES:                             E N G I N E S;
ERROR:                               E R R O R;
ERRORS:                              E R R O R S;
ESCAPE:                              E S C A P E;
EVEN:                                E V E N;
EVENT:                               E V E N T;
EVENTS:                              E V E N T S;
EVERY:                               E V E R Y;
EXCHANGE:                            E X C H A N G E;
EXCLUSIVE:                           E X C L U S I V E;
EXPIRE:                              E X P I R E;
EXPORT:                              E X P O R T;
EXTENDED:                            E X T E N D E D;
EXTENT_SIZE:                         E X T E N T '_' S I Z E;
FAST:                                F A S T;
FAULTS:                              F A U L T S;
FIELDS:                              F I E L D S;
FILE_BLOCK_SIZE:                     F I L E '_' B L O C K '_' S I Z E;
FILTER:                              F I L T E R;
FIRST:                               F I R S T;
FIXED:                               F I X E D;
FLUSH:                               F L U S H;
FOLLOWS:                             F O L L O W S;
FOUND:                               F O U N D;
FULL:                                F U L L;
FUNCTION:                            F U N C T I O N;
GENERAL:                             G E N E R A L;
GLOBAL:                              G L O B A L;
GRANTS:                              G R A N T S;
GROUP_REPLICATION:                   G R O U P '_' R E P L I C A T I O N;
HANDLER:                             H A N D L E R;
HASH:                                H A S H;
HELP:                                H E L P;
HOST:                                H O S T;
HOSTS:                               H O S T S;
IDENTIFIED:                          I D E N T I F I E D;
IGNORE_SERVER_IDS:                   I G N O R E '_' S E R V E R '_' I D S;
IMPORT:                              I M P O R T;
INDEXES:                             I N D E X E S;
INITIAL_SIZE:                        I N I T I A L '_' S I Z E;
INPLACE:                             I N P L A C E;
INSERT_METHOD:                       I N S E R T '_' M E T H O D;
INSTALL:                             I N S T A L L;
INSTANCE:                            I N S T A N C E;
INVOKER:                             I N V O K E R;
IO:                                  I O;
IO_THREAD:                           I O '_' T H R E A D;
IPC:                                 I P C;
ISOLATION:                           I S O L A T I O N;
ISSUER:                              I S S U E R;
JSON:                                J S O N;
KEY_BLOCK_SIZE:                      K E Y '_' B L O C K '_' S I Z E;
LANGUAGE:                            L A N G U A G E;
LAST:                                L A S T;
LEAVES:                              L E A V E S;
LESS:                                L E S S;
LEVEL:                               L E V E L;
LIST:                                L I S T;
LOCAL:                               L O C A L;
LOGFILE:                             L O G F I L E;
LOGS:                                L O G S;
MASTER:                              M A S T E R;
MASTER_AUTO_POSITION:                M A S T E R '_' A U T O '_' P O S I T I O N;
MASTER_CONNECT_RETRY:                M A S T E R '_' C O N N E C T '_' R E T R Y;
MASTER_DELAY:                        M A S T E R '_' D E L A Y;
MASTER_HEARTBEAT_PERIOD:             M A S T E R '_' H E A R T B E A T '_' P E R I O D;
MASTER_HOST:                         M A S T E R '_' H O S T;
MASTER_LOG_FILE:                     M A S T E R '_' L O G '_' F I L E;
MASTER_LOG_POS:                      M A S T E R '_' L O G '_' P O S;
MASTER_PASSWORD:                     M A S T E R '_' P A S S W O R D;
MASTER_PORT:                         M A S T E R '_' P O R T;
MASTER_RETRY_COUNT:                  M A S T E R '_' R E T R Y '_' C O U N T;
MASTER_SSL:                          M A S T E R '_' S S L;
MASTER_SSL_CA:                       M A S T E R '_' S S L '_' C A;
MASTER_SSL_CAPATH:                   M A S T E R '_' S S L '_' C A P A T H;
MASTER_SSL_CERT:                     M A S T E R '_' S S L '_' C E R T;
MASTER_SSL_CIPHER:                   M A S T E R '_' S S L '_' C I P H E R;
MASTER_SSL_CRL:                      M A S T E R '_' S S L '_' C R L;
MASTER_SSL_CRLPATH:                  M A S T E R '_' S S L '_' C R L P A T H;
MASTER_SSL_KEY:                      M A S T E R '_' S S L '_' K E Y;
MASTER_TLS_VERSION:                  M A S T E R '_' T L S '_' V E R S I O N;
MASTER_USER:                         M A S T E R '_' U S E R;
MAX_CONNECTIONS_PER_HOUR:            M A X '_' C O N N E C T I O N S '_' P E R '_' H O U R;
MAX_QUERIES_PER_HOUR:                M A X '_' Q U E R I E S '_' P E R '_' H O U R;
MAX_ROWS:                            M A X '_' R O W S;
MAX_SIZE:                            M A X '_' S I Z E;
MAX_UPDATES_PER_HOUR:                M A X '_' U P D A T E S '_' P E R '_' H O U R;
MAX_USER_CONNECTIONS:                M A X '_' U S E R '_' C O N N E C T I O N S;
MEDIUM:                              M E D I U M;
MERGE:                               M E R G E;
MID:                                 M I D;
MIGRATE:                             M I G R A T E;
MIN_ROWS:                            M I N '_' R O W S;
MODE:                                M O D E;
MODIFY:                              M O D I F Y;
MUTEX:                               M U T E X;
MYSQL:                               M Y S Q L;
NAME:                                N A M E;
NAMES:                               N A M E S;
NCHAR:                               N C H A R;
NEVER:                               N E V E R;
NEXT:                                N E X T;
NO:                                  N O;
NODEGROUP:                           N O D E G R O U P;
NONE:                                N O N E;
OFFLINE:                             O F F L I N E;
OFFSET:                              O F F S E T;
OJ:                                  O J;
OLD_PASSWORD:                        O L D '_' P A S S W O R D;
ONE:                                 O N E;
ONLINE:                              O N L I N E;
ONLY:                                O N L Y;
OPEN:                                O P E N;
OPTIMIZER_COSTS:                     O P T I M I Z E R '_' C O S T S;
OPTIONS:                             O P T I O N S;
OWNER:                               O W N E R;
PACK_KEYS:                           P A C K '_' K E Y S;
PAGE:                                P A G E;
PARSER:                              P A R S E R;
PARTIAL:                             P A R T I A L;
PARTITIONING:                        P A R T I T I O N I N G;
PARTITIONS:                          P A R T I T I O N S;
PASSWORD:                            P A S S W O R D;
PHASE:                               P H A S E;
PLUGIN:                              P L U G I N;
PLUGIN_DIR:                          P L U G I N '_' D I R;
PLUGINS:                             P L U G I N S;
PORT:                                P O R T;
PRECEDES:                            P R E C E D E S;
PREPARE:                             P R E P A R E;
PRESERVE:                            P R E S E R V E;
PREV:                                P R E V;
PROCESSLIST:                         P R O C E S S L I S T;
PROFILE:                             P R O F I L E;
PROFILES:                            P R O F I L E S;
PROXY:                               P R O X Y;
QUERY:                               Q U E R Y;
QUICK:                               Q U I C K;
REBUILD:                             R E B U I L D;
RECOVER:                             R E C O V E R;
REDO_BUFFER_SIZE:                    R E D O '_' B U F F E R '_' S I Z E;
REDUNDANT:                           R E D U N D A N T;
RELAY:                               R E L A Y;
RELAY_LOG_FILE:                      R E L A Y '_' L O G '_' F I L E;
RELAY_LOG_POS:                       R E L A Y '_' L O G '_' P O S;
RELAYLOG:                            R E L A Y L O G;
REMOVE:                              R E M O V E;
REORGANIZE:                          R E O R G A N I Z E;
REPAIR:                              R E P A I R;
REPLICATE_DO_DB:                     R E P L I C A T E '_' D O '_' D B;
REPLICATE_DO_TABLE:                  R E P L I C A T E '_' D O '_' T A B L E;
REPLICATE_IGNORE_DB:                 R E P L I C A T E '_' I G N O R E '_' D B;
REPLICATE_IGNORE_TABLE:              R E P L I C A T E '_' I G N O R E '_' T A B L E;
REPLICATE_REWRITE_DB:                R E P L I C A T E '_' R E W R I T E '_' D B;
REPLICATE_WILD_DO_TABLE:             R E P L I C A T E '_' W I L D '_' D O '_' T A B L E;
REPLICATE_WILD_IGNORE_TABLE:         R E P L I C A T E '_' W I L D '_' I G N O R E '_' T A B L E;
REPLICATION:                         R E P L I C A T I O N;
RESET:                               R E S E T;
RESUME:                              R E S U M E;
RETURNS:                             R E T U R N S;
ROLLBACK:                            R O L L B A C K;
ROLLUP:                              R O L L U P;
ROTATE:                              R O T A T E;
ROW:                                 R O W;
ROWS:                                R O W S;
ROW_FORMAT:                          R O W '_' F O R M A T;
SAVEPOINT:                           S A V E P O I N T;
SCHEDULE:                            S C H E D U L E;
SECURITY:                            S E C U R I T Y;
SERVER:                              S E R V E R;
SESSION:                             S E S S I O N;
SHARE:                               S H A R E;
SHARED:                              S H A R E D;
SIGNED:                              S I G N E D;
SIMPLE:                              S I M P L E;
SLAVE:                               S L A V E;
SLOW:                                S L O W;
SNAPSHOT:                            S N A P S H O T;
SOCKET:                              S O C K E T;
SOME:                                S O M E;
SONAME:                              S O N A M E;
SOUNDS:                              S O U N D S;
SOURCE:                              S O U R C E;
SQL_AFTER_GTIDS:                     S Q L '_' A F T E R '_' G T I D S;
SQL_AFTER_MTS_GAPS:                  S Q L '_' A F T E R '_' M T S '_' G A P S;
SQL_BEFORE_GTIDS:                    S Q L '_' B E F O R E '_' G T I D S;
SQL_BUFFER_RESULT:                   S Q L '_' B U F F E R '_' R E S U L T;
SQL_CACHE:                           S Q L '_' C A C H E;
SQL_NO_CACHE:                        S Q L '_' N O '_' C A C H E;
SQL_THREAD:                          S Q L '_' T H R E A D;
START:                               S T A R T;
STARTS:                              S T A R T S;
STATS_AUTO_RECALC:                   S T A T S '_' A U T O '_' R E C A L C;
STATS_PERSISTENT:                    S T A T S '_' P E R S I S T E N T;
STATS_SAMPLE_PAGES:                  S T A T S '_' S A M P L E '_' P A G E S;
STATUS:                              S T A T U S;
STOP:                                S T O P;
STORAGE:                             S T O R A G E;
STRING:                              S T R I N G;
SUBJECT:                             S U B J E C T;
SUBPARTITION:                        S U B P A R T I T I O N;
SUBPARTITIONS:                       S U B P A R T I T I O N S;
SUSPEND:                             S U S P E N D;
SWAPS:                               S W A P S;
SWITCHES:                            S W I T C H E S;
TABLESPACE:                          T A B L E S P A C E;
TEMPORARY:                           T E M P O R A R Y;
TEMPTABLE:                           T E M P T A B L E;
THAN:                                T H A N;
TRADITIONAL:                         T R A D I T I O N A L;
TRANSACTION:                         T R A N S A C T I O N;
TRIGGERS:                            T R I G G E R S;
TRUNCATE:                            T R U N C A T E;
UNDEFINED:                           U N D E F I N E D;
UNDOFILE:                            U N D O F I L E;
UNDO_BUFFER_SIZE:                    U N D O '_' B U F F E R '_' S I Z E;
UNINSTALL:                           U N I N S T A L L;
UNKNOWN:                             U N K N O W N;
UNTIL:                               U N T I L;
UPGRADE:                             U P G R A D E;
USER:                                U S E R;
USE_FRM:                             U S E '_' F R M;
USER_RESOURCES:                      U S E R '_' R E S O U R C E S;
VALIDATION:                          V A L I D A T I O N;
VALUE:                               V A L U E;
VARIABLES:                           V A R I A B L E S;
VIEW:                                V I E W;
WAIT:                                W A I T;
WARNINGS:                            W A R N I N G S;
WITHOUT:                             W I T H O U T;
WORK:                                W O R K;
WRAPPER:                             W R A P P E R;
X509:                                X '5' '0' '9';
XA:                                  X A ;
XML:                                 X M L;


// Date format Keywords

EUR:                                 E U R;
USA:                                 U S A;
JIS:                                 J I S;
ISO:                                 I S O;
INTERNAL:                            I N T E R N A L;


// Interval type Keywords

QUARTER:                             Q U A R T E R;
MONTH:                               M O N T H;
DAY:                                 D A Y;
HOUR:                                H O U R;
MINUTE:                              M I N U T E;
WEEK:                                W E E K;
SECOND:                              S E C O N D;
MICROSECOND:                         M I C R O S E C O N D;


// PRIVILEGES

TABLES:                              T A B L E S;
ROUTINE:                             R O U T I N E;
EXECUTE:                             E X E C U T E;
FILE:                                F I L E;
PROCESS:                             P R O C E S S;
RELOAD:                              R E L O A D;
SHUTDOWN:                            S H U T D O W N;
SUPER:                               S U P E R;
PRIVILEGES:                          P R I V I L E G E S;


// Charsets

ARMSCII8:                            A R M S C I I '8';
ASCII:                               A S C I I;
BIG5:                                B I G '5';
CP1250:                              C P '1' '2' '5' '0';
CP1251:                              C P '1' '2' '5' '1';
CP1256:                              C P '1' '2' '5' '6';
CP1257:                              C P '1' '2' '5' '7';
CP850:                               C P '8' '5' '0';
CP852:                               C P '8' '5' '2';
CP866:                               C P '8' '6' '6';
CP932:                               C P '9' '3' '2';
DEC8:                                D E C '8';
EUCJPMS:                             E U C J P M S;
EUCKR:                               E U C K R;
GB2312:                              G B '2' '3' '1' '2';
GBK:                                 G B K;
GEOSTD8:                             G E O S T D '8';
GREEK:                               G R E E K;
HEBREW:                              H E B R E W;
HP8:                                 H P '8';
KEYBCS2:                             K E Y B C S '2';
KOI8R:                               K O I '8' R;
KOI8U:                               K O I '8' U;
LATIN1:                              L A T I N '1';
LATIN2:                              L A T I N '2';
LATIN5:                              L A T I N '5';
LATIN7:                              L A T I N '7';
MACCE:                               M A C C E;
MACROMAN:                            M A C R O M A N;
SJIS:                                S J I S;
SWE7:                                S W E '7';
TIS620:                              T I S '6' '2' '0';
UCS2:                                U C S '2';
UJIS:                                U J I S;
UTF16:                               U T F '1' '6';
UTF16LE:                             U T F '1' '6' L E;
UTF32:                               U T F '3' '2';
UTF8:                                U T F '8';
UTF8MB3:                             U T F '8' M B '3';
UTF8MB4:                             U T F '8' M B '4';


// DB Engines

ARCHIVE:                             A R C H I V E;
BLACKHOLE:                           B L A C K H O L E;
CSV:                                 C S V;
FEDERATED:                           F E D E R A T E D;
INNODB:                              I N N O D B;
MEMORY:                              M E M O R Y;
MRG_MYISAM:                          M R G '_' M Y I S A M;
MYISAM:                              M Y I S A M;
NDB:                                 N D B;
NDBCLUSTER:                          N D B C L U S T E R;
PERFOMANCE_SCHEMA:                   P E R F O M A N C E '_' S C H E M A;


// Transaction Levels

REPEATABLE:                          R E P E A T A B L E;
COMMITTED:                           C O M M I T T E D;
UNCOMMITTED:                         U N C O M M I T T E D;
SERIALIZABLE:                        S E R I A L I Z A B L E;


// Spatial data types
GEOMETRY:                            G E O M E T R Y;
GEOMETRYCOLLECTION:                  G E O M E T R Y C O L L E C T I O N;
LINESTRING:                          L I N E S T R I N G;
MULTILINESTRING:                     M U L T I L I N E S T R I N G;
MULTIPOINT:                          M U L T I P O I N T;
MULTIPOLYGON:                        M U L T I P O L Y G O N;
POINT:                               P O I N T;
POLYGON:                             P O L Y G O N;


// Common function names

ABS:                                 A B S;
ACOS:                                A C O S;
ADDDATE:                             A D D D A T E;
ADDTIME:                             A D D T I M E;
AES_DECRYPT:                         A E S '_' D E C R Y P T;
AES_ENCRYPT:                         A E S '_' E N C R Y P T;
AREA:                                A R E A;
ASBINARY:                            A S B I N A R Y;
ASIN:                                A S I N;
ASTEXT:                              A S T E X T;
ASWKB:                               A S W K B;
ASWKT:                               A S W K T;
ASYMMETRIC_DECRYPT:                  A S Y M M E T R I C '_' D E C R Y P T;
ASYMMETRIC_DERIVE:                   A S Y M M E T R I C '_' D E R I V E;
ASYMMETRIC_ENCRYPT:                  A S Y M M E T R I C '_' E N C R Y P T;
ASYMMETRIC_SIGN:                     A S Y M M E T R I C '_' S I G N;
ASYMMETRIC_VERIFY:                   A S Y M M E T R I C '_' V E R I F Y;
ATAN:                                A T A N;
ATAN2:                               A T A N '2';
BENCHMARK:                           B E N C H M A R K;
BIN:                                 B I N;
BIT_COUNT:                           B I T '_' C O U N T;
BIT_LENGTH:                          B I T '_' L E N G T H;
BUFFER:                              B U F F E R;
CEIL:                                C E I L;
CEILING:                             C E I L I N G;
CENTROID:                            C E N T R O I D;
CHARACTER_LENGTH:                    C H A R A C T E R '_' L E N G T H;
CHARSET:                             C H A R S E T;
CHAR_LENGTH:                         C H A R '_' L E N G T H;
COERCIBILITY:                        C O E R C I B I L I T Y;
COLLATION:                           C O L L A T I O N;
COMPRESS:                            C O M P R E S S;
CONCAT:                              C O N C A T;
CONCAT_WS:                           C O N C A T '_' W S;
CONNECTION_ID:                       C O N N E C T I O N '_' I D;
CONV:                                C O N V;
CONVERT_TZ:                          C O N V E R T '_' T Z;
COS:                                 C O S;
COT:                                 C O T;
CRC32:                               C R C '3' '2';
CREATE_ASYMMETRIC_PRIV_KEY:          C R E A T E '_' A S Y M M E T R I C '_' P R I V '_' K E Y;
CREATE_ASYMMETRIC_PUB_KEY:           C R E A T E '_' A S Y M M E T R I C '_' P U B '_' K E Y;
CREATE_DH_PARAMETERS:                C R E A T E '_' D H '_' P A R A M E T E R S;
CREATE_DIGEST:                       C R E A T E '_' D I G E S T;
CROSSES:                             C R O S S E S;
DATEDIFF:                            D A T E D I F F;
DATE_FORMAT:                         D A T E '_' F O R M A T;
DAYNAME:                             D A Y N A M E;
DAYOFMONTH:                          D A Y O F M O N T H;
DAYOFWEEK:                           D A Y O F W E E K;
DAYOFYEAR:                           D A Y O F Y E A R;
DECODE:                              D E C O D E;
DEGREES:                             D E G R E E S;
DES_DECRYPT:                         D E S '_' D E C R Y P T;
DES_ENCRYPT:                         D E S '_' E N C R Y P T;
DIMENSION:                           D I M E N S I O N;
DISJOINT:                            D I S J O I N T;
ELT:                                 E L T;
ENCODE:                              E N C O D E;
ENCRYPT:                             E N C R Y P T;
ENDPOINT:                            E N D P O I N T;
ENVELOPE:                            E N V E L O P E;
EQUALS:                              E Q U A L S;
EXP:                                 E X P;
EXPORT_SET:                          E X P O R T '_' S E T;
EXTERIORRING:                        E X T E R I O R R I N G;
EXTRACTVALUE:                        E X T R A C T V A L U E;
FIELD:                               F I E L D;
FIND_IN_SET:                         F I N D '_' I N '_' S E T;
FLOOR:                               F L O O R;
FORMAT:                              F O R M A T;
FOUND_ROWS:                          F O U N D '_' R O W S;
FROM_BASE64:                         F R O M '_' B A S E '6' '4';
FROM_DAYS:                           F R O M '_' D A Y S;
FROM_UNIXTIME:                       F R O M '_' U N I X T I M E;
GEOMCOLLFROMTEXT:                    G E O M C O L L F R O M T E X T;
GEOMCOLLFROMWKB:                     G E O M C O L L F R O M W K B;
GEOMETRYCOLLECTIONFROMTEXT:          G E O M E T R Y C O L L E C T I O N F R O M T E X T;
GEOMETRYCOLLECTIONFROMWKB:           G E O M E T R Y C O L L E C T I O N F R O M W K B;
GEOMETRYFROMTEXT:                    G E O M E T R Y F R O M T E X T;
GEOMETRYFROMWKB:                     G E O M E T R Y F R O M W K B;
GEOMETRYN:                           G E O M E T R Y N;
GEOMETRYTYPE:                        G E O M E T R Y T Y P E;
GEOMFROMTEXT:                        G E O M F R O M T E X T;
GEOMFROMWKB:                         G E O M F R O M W K B;
GET_FORMAT:                          G E T '_' F O R M A T;
GET_LOCK:                            G E T '_' L O C K;
GLENGTH:                             G L E N G T H;
GREATEST:                            G R E A T E S T;
GTID_SUBSET:                         G T I D '_' S U B S E T;
GTID_SUBTRACT:                       G T I D '_' S U B T R A C T;
HEX:                                 H E X;
IFNULL:                              I F N U L L;
INET6_ATON:                          I N E T '6' '_' A T O N;
INET6_NTOA:                          I N E T '6' '_' N T O A;
INET_ATON:                           I N E T '_' A T O N;
INET_NTOA:                           I N E T '_' N T O A;
INSTR:                               I N S T R;
INTERIORRINGN:                       I N T E R I O R R I N G N;
INTERSECTS:                          I N T E R S E C T S;
ISCLOSED:                            I S C L O S E D;
ISEMPTY:                             I S E M P T Y;
ISNULL:                              I S N U L L;
ISSIMPLE:                            I S S I M P L E;
IS_FREE_LOCK:                        I S '_' F R E E '_' L O C K;
IS_IPV4:                             I S '_' I P V '4';
IS_IPV4_COMPAT:                      I S '_' I P V '4' '_' C O M P A T;
IS_IPV4_MAPPED:                      I S '_' I P V '4' '_' M A P P E D;
IS_IPV6:                             I S '_' I P V '6';
IS_USED_LOCK:                        I S '_' U S E D '_' L O C K;
LAST_INSERT_ID:                      L A S T '_' I N S E R T '_' I D;
LCASE:                               L C A S E;
LEAST:                               L E A S T;
LENGTH:                              L E N G T H;
LINEFROMTEXT:                        L I N E F R O M T E X T;
LINEFROMWKB:                         L I N E F R O M W K B;
LINESTRINGFROMTEXT:                  L I N E S T R I N G F R O M T E X T;
LINESTRINGFROMWKB:                   L I N E S T R I N G F R O M W K B;
LN:                                  L N;
LOAD_FILE:                           L O A D '_' F I L E;
LOCATE:                              L O C A T E;
LOG:                                 L O G;
LOG10:                               L O G '1' '0';
LOG2:                                L O G '2';
LOWER:                               L O W E R;
LPAD:                                L P A D;
LTRIM:                               L T R I M;
MAKEDATE:                            M A K E D A T E;
MAKETIME:                            M A K E T I M E;
MAKE_SET:                            M A K E '_' S E T;
MASTER_POS_WAIT:                     M A S T E R '_' P O S '_' W A I T;
MBRCONTAINS:                         M B R C O N T A I N S;
MBRDISJOINT:                         M B R D I S J O I N T;
MBREQUAL:                            M B R E Q U A L;
MBRINTERSECTS:                       M B R I N T E R S E C T S;
MBROVERLAPS:                         M B R O V E R L A P S;
MBRTOUCHES:                          M B R T O U C H E S;
MBRWITHIN:                           M B R W I T H I N;
MD5:                                 M D '5';
MLINEFROMTEXT:                       M L I N E F R O M T E X T;
MLINEFROMWKB:                        M L I N E F R O M W K B;
MONTHNAME:                           M O N T H N A M E;
MPOINTFROMTEXT:                      M P O I N T F R O M T E X T;
MPOINTFROMWKB:                       M P O I N T F R O M W K B;
MPOLYFROMTEXT:                       M P O L Y F R O M T E X T;
MPOLYFROMWKB:                        M P O L Y F R O M W K B;
MULTILINESTRINGFROMTEXT:             M U L T I L I N E S T R I N G F R O M T E X T;
MULTILINESTRINGFROMWKB:              M U L T I L I N E S T R I N G F R O M W K B;
MULTIPOINTFROMTEXT:                  M U L T I P O I N T F R O M T E X T;
MULTIPOINTFROMWKB:                   M U L T I P O I N T F R O M W K B;
MULTIPOLYGONFROMTEXT:                M U L T I P O L Y G O N F R O M T E X T;
MULTIPOLYGONFROMWKB:                 M U L T I P O L Y G O N F R O M W K B;
NAME_CONST:                          N A M E '_' C O N S T;
NULLIF:                              N U L L I F;
NUMGEOMETRIES:                       N U M G E O M E T R I E S;
NUMINTERIORRINGS:                    N U M I N T E R I O R R I N G S;
NUMPOINTS:                           N U M P O I N T S;
OCT:                                 O C T;
OCTET_LENGTH:                        O C T E T '_' L E N G T H;
ORD:                                 O R D;
OVERLAPS:                            O V E R L A P S;
PERIOD_ADD:                          P E R I O D '_' A D D;
PERIOD_DIFF:                         P E R I O D '_' D I F F;
PI:                                  P I;
POINTFROMTEXT:                       P O I N T F R O M T E X T;
POINTFROMWKB:                        P O I N T F R O M W K B;
POINTN:                              P O I N T N;
POLYFROMTEXT:                        P O L Y F R O M T E X T;
POLYFROMWKB:                         P O L Y F R O M W K B;
POLYGONFROMTEXT:                     P O L Y G O N F R O M T E X T;
POLYGONFROMWKB:                      P O L Y G O N F R O M W K B;
POW:                                 P O W;
POWER:                               P O W E R;
QUOTE:                               Q U O T E;
RADIANS:                             R A D I A N S;
RAND:                                R A N D;
RANDOM_BYTES:                        R A N D O M '_' B Y T E S;
RELEASE_LOCK:                        R E L E A S E '_' L O C K;
REVERSE:                             R E V E R S E;
ROUND:                               R O U N D;
ROW_COUNT:                           R O W '_' C O U N T;
RPAD:                                R P A D;
RTRIM:                               R T R I M;
SEC_TO_TIME:                         S E C '_' T O '_' T I M E;
SESSION_USER:                        S E S S I O N '_' U S E R;
SHA:                                 S H A;
SHA1:                                S H A '1';
SHA2:                                S H A '2';
SIGN:                                S I G N;
SIN:                                 S I N;
SLEEP:                               S L E E P;
SOUNDEX:                             S O U N D E X;
SQL_THREAD_WAIT_AFTER_GTIDS:         S Q L '_' T H R E A D '_' W A I T '_' A F T E R '_' G T I D S;
SQRT:                                S Q R T;
SRID:                                S R I D;
STARTPOINT:                          S T A R T P O I N T;
STRCMP:                              S T R C M P;
STR_TO_DATE:                         S T R '_' T O '_' D A T E;
ST_AREA:                             S T '_' A R E A;
ST_ASBINARY:                         S T '_' A S B I N A R Y;
ST_ASTEXT:                           S T '_' A S T E X T;
ST_ASWKB:                            S T '_' A S W K B;
ST_ASWKT:                            S T '_' A S W K T;
ST_BUFFER:                           S T '_' B U F F E R;
ST_CENTROID:                         S T '_' C E N T R O I D;
ST_CONTAINS:                         S T '_' C O N T A I N S;
ST_CROSSES:                          S T '_' C R O S S E S;
ST_DIFFERENCE:                       S T '_' D I F F E R E N C E;
ST_DIMENSION:                        S T '_' D I M E N S I O N;
ST_DISJOINT:                         S T '_' D I S J O I N T;
ST_DISTANCE:                         S T '_' D I S T A N C E;
ST_ENDPOINT:                         S T '_' E N D P O I N T;
ST_ENVELOPE:                         S T '_' E N V E L O P E;
ST_EQUALS:                           S T '_' E Q U A L S;
ST_EXTERIORRING:                     S T '_' E X T E R I O R R I N G;
ST_GEOMCOLLFROMTEXT:                 S T '_' G E O M C O L L F R O M T E X T;
ST_GEOMCOLLFROMTXT:                  S T '_' G E O M C O L L F R O M T X T;
ST_GEOMCOLLFROMWKB:                  S T '_' G E O M C O L L F R O M W K B;
ST_GEOMETRYCOLLECTIONFROMTEXT:       S T '_' G E O M E T R Y C O L L E C T I O N F R O M T E X T;
ST_GEOMETRYCOLLECTIONFROMWKB:        S T '_' G E O M E T R Y C O L L E C T I O N F R O M W K B;
ST_GEOMETRYFROMTEXT:                 S T '_' G E O M E T R Y F R O M T E X T;
ST_GEOMETRYFROMWKB:                  S T '_' G E O M E T R Y F R O M W K B;
ST_GEOMETRYN:                        S T '_' G E O M E T R Y N;
ST_GEOMETRYTYPE:                     S T '_' G E O M E T R Y T Y P E;
ST_GEOMFROMTEXT:                     S T '_' G E O M F R O M T E X T;
ST_GEOMFROMWKB:                      S T '_' G E O M F R O M W K B;
ST_INTERIORRINGN:                    S T '_' I N T E R I O R R I N G N;
ST_INTERSECTION:                     S T '_' I N T E R S E C T I O N;
ST_INTERSECTS:                       S T '_' I N T E R S E C T S;
ST_ISCLOSED:                         S T '_' I S C L O S E D;
ST_ISEMPTY:                          S T '_' I S E M P T Y;
ST_ISSIMPLE:                         S T '_' I S S I M P L E;
ST_LINEFROMTEXT:                     S T '_' L I N E F R O M T E X T;
ST_LINEFROMWKB:                      S T '_' L I N E F R O M W K B;
ST_LINESTRINGFROMTEXT:               S T '_' L I N E S T R I N G F R O M T E X T;
ST_LINESTRINGFROMWKB:                S T '_' L I N E S T R I N G F R O M W K B;
ST_NUMGEOMETRIES:                    S T '_' N U M G E O M E T R I E S;
ST_NUMINTERIORRING:                  S T '_' N U M I N T E R I O R R I N G;
ST_NUMINTERIORRINGS:                 S T '_' N U M I N T E R I O R R I N G S;
ST_NUMPOINTS:                        S T '_' N U M P O I N T S;
ST_OVERLAPS:                         S T '_' O V E R L A P S;
ST_POINTFROMTEXT:                    S T '_' P O I N T F R O M T E X T;
ST_POINTFROMWKB:                     S T '_' P O I N T F R O M W K B;
ST_POINTN:                           S T '_' P O I N T N;
ST_POLYFROMTEXT:                     S T '_' P O L Y F R O M T E X T;
ST_POLYFROMWKB:                      S T '_' P O L Y F R O M W K B;
ST_POLYGONFROMTEXT:                  S T '_' P O L Y G O N F R O M T E X T;
ST_POLYGONFROMWKB:                   S T '_' P O L Y G O N F R O M W K B;
ST_SRID:                             S T '_' S R I D;
ST_STARTPOINT:                       S T '_' S T A R T P O I N T;
ST_SYMDIFFERENCE:                    S T '_' S Y M D I F F E R E N C E;
ST_TOUCHES:                          S T '_' T O U C H E S;
ST_UNION:                            S T '_' U N I O N;
ST_WITHIN:                           S T '_' W I T H I N;
ST_X:                                S T '_' X;
ST_Y:                                S T '_' Y;
SUBDATE:                             S U B D A T E;
SUBSTRING_INDEX:                     S U B S T R I N G '_' I N D E X;
SUBTIME:                             S U B T I M E;
SYSTEM_USER:                         S Y S T E M '_' U S E R;
TAN:                                 T A N;
TIMEDIFF:                            T I M E D I F F;
TIMESTAMPADD:                        T I M E S T A M P A D D;
TIMESTAMPDIFF:                       T I M E S T A M P D I F F;
TIME_FORMAT:                         T I M E '_' F O R M A T;
TIME_TO_SEC:                         T I M E '_' T O '_' S E C;
TOUCHES:                             T O U C H E S;
TO_BASE64:                           T O '_' B A S E '6' '4';
TO_DAYS:                             T O '_' D A Y S;
TO_SECONDS:                          T O '_' S E C O N D S;
UCASE:                               U C A S E;
UNCOMPRESS:                          U N C O M P R E S S;
UNCOMPRESSED_LENGTH:                 U N C O M P R E S S E D '_' L E N G T H;
UNHEX:                               U N H E X;
UNIX_TIMESTAMP:                      U N I X '_' T I M E S T A M P;
UPDATEXML:                           U P D A T E X M L;
UPPER:                               U P P E R;
UUID:                                U U I D;
UUID_SHORT:                          U U I D '_' S H O R T;
VALIDATE_PASSWORD_STRENGTH:          V A L I D A T E '_' P A S S W O R D '_' S T R E N G T H;
VERSION:                             V E R S I O N;
WAIT_UNTIL_SQL_THREAD_AFTER_GTIDS:   W A I T '_' U N T I L '_' S Q L '_' T H R E A D '_' A F T E R '_' G T I D S;
WEEKDAY:                             W E E K D A Y;
WEEKOFYEAR:                          W E E K O F Y E A R;
WEIGHT_STRING:                       W E I G H T '_' S T R I N G;
WITHIN:                              W I T H I N;
YEARWEEK:                            Y E A R W E E K;
Y_FUNCTION:                          Y;
X_FUNCTION:                          X;



// Operators
// Operators. Assigns

VAR_ASSIGN:                          ':=';
PLUS_ASSIGN:                         '+=';
MINUS_ASSIGN:                        '-=';
MULT_ASSIGN:                         '*=';
DIV_ASSIGN:                          '/=';
MOD_ASSIGN:                          '%=';
AND_ASSIGN:                          '&=';
XOR_ASSIGN:                          '^=';
OR_ASSIGN:                           '|=';


// Operators. Arithmetics

STAR:                                '*';
DIVIDE:                              '/';
MODULE:                              '%';
PLUS:                                '+';
MINUS:                               '-';
DIV:                                 D I V;
MOD:                                 M O D;


// Operators. Comparation

EQUAL_SYMBOL:                        '=';
GREATER_SYMBOL:                      '>';
LESS_SYMBOL:                         '<';
EXCLAMATION_SYMBOL:                  '!';


// Operators. Bit

BIT_NOT_OP:                          '~';
BIT_OR_OP:                           '|';
BIT_AND_OP:                          '&';
BIT_XOR_OP:                          '^';


// Constructors symbols

DOT:                                 '.';
LR_BRACKET:                          '(';
RR_BRACKET:                          ')';
COMMA:                               ',';
SEMI:                                ';';
AT_SIGN:                             '@';
ZERO_DECIMAL:                        '0';
ONE_DECIMAL:                         '1';
TWO_DECIMAL:                         '2';
SINGLE_QUOTE_SYMB:                   '\'';
DOUBLE_QUOTE_SYMB:                   '"';
REVERSE_QUOTE_SYMB:                  '`';
COLON_SYMB:                          ':';



// Charsets

CHARSET_REVERSE_QOUTE_STRING:        '`' CHARSET_NAME '`';



// File's sizes


FILESIZE_LITERAL:                    DEC_DIGIT+ (K|M|G|T);



// Literal Primitives


START_NATIONAL_STRING_LITERAL:       N SQUOTA_STRING;
STRING_LITERAL:                      DQUOTA_STRING | SQUOTA_STRING;
DECIMAL_LITERAL:                     DEC_DIGIT+;
HEXADECIMAL_LITERAL:                 X '\'' (HEX_DIGIT HEX_DIGIT)+ '\''
                                     | '0x' HEX_DIGIT+;

REAL_LITERAL:                        (DEC_DIGIT+)? '.' DEC_DIGIT+
                                     | DEC_DIGIT+ '.' EXPONENT_NUM_PART
                                     | (DEC_DIGIT+)? '.' (DEC_DIGIT+ EXPONENT_NUM_PART)
                                     | DEC_DIGIT+ EXPONENT_NUM_PART;
NULL_SPEC_LITERAL:                   '\\' 'N';
BIT_STRING:                          BIT_STRING_L;
STRING_CHARSET_NAME:                 '_' CHARSET_NAME;




// Hack for dotID
// Prevent recognize string:         .123somelatin AS ((.123), FLOAT_LITERAL), ((somelatin), ID)
//  it must recoginze:               .123somelatin AS ((.), DOT), (123somelatin, ID)

DOT_ID:                              '.' ID_LITERAL;



// Identifiers

ID:                                  ID_LITERAL;
// DOUBLE_QUOTE_ID:                  '"' ~'"'+ '"';
REVERSE_QUOTE_ID:                    '`' (~'`' | '``' )+ '`'; //escaped reverse quote is allowed
STRING_USER_NAME:                    (
                                       SQUOTA_STRING | DQUOTA_STRING
                                       | BQUOTA_STRING | ID_LITERAL
                                     ) '@'
                                     (
                                       SQUOTA_STRING | DQUOTA_STRING
                                       | BQUOTA_STRING | ID_LITERAL
                                     );
LOCAL_ID:                            '@'
                                (
                                  [a-zA-Z0-9._$]+
                                  | SQUOTA_STRING
                                  | DQUOTA_STRING
                                  | BQUOTA_STRING
                                );
GLOBAL_ID:                           '@' '@'
                                (
                                  [a-zA-Z0-9._$]+
                                  | BQUOTA_STRING
                                );


// Fragments for Literal primitives

fragment CHARSET_NAME:               ARMSCII8 | ASCII | BIG5 | BINARY | CP1250
                                     | CP1251 | CP1256 | CP1257 | CP850
                                     | CP852 | CP866 | CP932 | DEC8 | EUCJPMS
                                     | EUCKR | GB2312 | GBK | GEOSTD8 | GREEK
                                     | HEBREW | HP8 | KEYBCS2 | KOI8R | KOI8U
                                     | LATIN1 | LATIN2 | LATIN5 | LATIN7
                                     | MACCE | MACROMAN | SJIS | SWE7 | TIS620
                                     | UCS2 | UJIS | UTF16 | UTF16LE | UTF32
                                     | UTF8 | UTF8MB4;

fragment EXPONENT_NUM_PART:          E '-'? DEC_DIGIT+;
fragment ID_LITERAL:                 [a-zA-Z_$0-9]*?[a-zA-Z_$]+?[a-zA-Z_$0-9]*;
fragment DQUOTA_STRING:              '"' ( '\\'. | '""' | ~('"'| '\\') )* '"';
fragment SQUOTA_STRING:              '\'' ('\\'. | '\'\'' | ~('\'' | '\\'))* '\'';
fragment BQUOTA_STRING:              '`' ( '\\'. | '``' | ~('`'|'\\'))* '`';
fragment HEX_DIGIT:                  [0-9A-Fa-f];
fragment DEC_DIGIT:                  [0-9];
fragment BIT_STRING_L:               'b' '\'' [01]+ '\'';



// Letters

fragment A:                          [aA];
fragment B:                          [bB];
fragment C:                          [cC];
fragment D:                          [dD];
fragment E:                          [eE];
fragment F:                          [fF];
fragment G:                          [gG];
fragment H:                          [hH];
fragment I:                          [iI];
fragment J:                          [jJ];
fragment K:                          [kK];
fragment L:                          [lL];
fragment M:                          [mM];
fragment N:                          [nN];
fragment O:                          [oO];
fragment P:                          [pP];
fragment Q:                          [qQ];
fragment R:                          [rR];
fragment S:                          [sS];
fragment T:                          [tT];
fragment U:                          [uU];
fragment V:                          [vV];
fragment W:                          [wW];
fragment X:                          [xX];
fragment Y:                          [yY];
fragment Z:                          [zZ];