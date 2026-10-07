-- 仅测试用：H2 内存库 MySQL 兼容模式，列与 gipr.sql 的实体契约对齐
CREATE ALIAS IF NOT EXISTS GET_LOCK FOR "com.fc.v2.service.H2LockFunctions.getLock";
CREATE ALIAS IF NOT EXISTS RELEASE_LOCK FOR "com.fc.v2.service.H2LockFunctions.releaseLock";

DROP TABLE IF EXISTS t_gipr_holder_book;
CREATE TABLE t_gipr_holder_book (
  id bigint NOT NULL PRIMARY KEY,
  holder_no varchar(64),
  prod_id int,
  prod_no varchar(64),
  src_kind varchar(32),
  area_num decimal(12,2),
  content varchar(255),
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

DROP TABLE IF EXISTS t_gipr_product_book;
CREATE TABLE t_gipr_product_book (
  id bigint NOT NULL PRIMARY KEY,
  prod_no varchar(64),
  prod_name varchar(128),
  prod_kind varchar(32),
  road_name varchar(255),
  src_kind varchar(32),
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

DROP TABLE IF EXISTS t_gipr_case_flow;
CREATE TABLE t_gipr_case_flow (
  id bigint NOT NULL PRIMARY KEY,
  biz_no varchar(64),
  holder_no varchar(64),
  prod_id int,
  goods_kind varchar(128),
  apply_time datetime,
  idem_key varchar(96),
  stage int,
  view_seq int,
  attempt_no int DEFAULT 0,
  cert_flag int DEFAULT 0,
  end_kind int DEFAULT 0,
  open_holder varchar(64),
  status int,
  content varchar(255),
  last_action varchar(64),
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  CONSTRAINT uk_case_idem UNIQUE (idem_key),
  CONSTRAINT uk_case_open_holder UNIQUE (open_holder)
);

DROP TABLE IF EXISTS t_gipr_case_trace;
CREATE TABLE t_gipr_case_trace (
  id bigint NOT NULL PRIMARY KEY,
  case_id bigint NOT NULL,
  stage int,
  attempt_no int DEFAULT 0,
  action_kind varchar(32),
  content varchar(500),
  trace_time datetime,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);
CREATE INDEX idx_trace_case_time ON t_gipr_case_trace (case_id, trace_time, id);

DROP TABLE IF EXISTS t_gipr_case_sign;
CREATE TABLE t_gipr_case_sign (
  id bigint NOT NULL PRIMARY KEY,
  case_id bigint NOT NULL,
  signer_no varchar(64) NOT NULL,
  sign_time datetime,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  CONSTRAINT uk_sign_case_signer UNIQUE (case_id, signer_no)
);

INSERT INTO t_gipr_product_book (id, prod_no, prod_name, prod_kind, road_name, src_kind, status, del_flag)
VALUES (0, 'GP00', '在册名录', '甲层', '浚浦水域', '国家局核准公告下发', 0, 0),
       (1, 'GP01', '已移出名录', '乙层', '西片坡地', '省里增补', 1, 0);
