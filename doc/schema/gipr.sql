-- gipr 地理标志产品保护与专用标志使用核准监管 -- schema (chen-029)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_029

CREATE TABLE IF NOT EXISTS t_gipr_archive_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '历史用标卷宗转档流水批次码',
  row_no int DEFAULT NULL COMMENT '原册行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被对上的授权册代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行沉档卷数',
  chk_num decimal(12,2) DEFAULT NULL COMMENT '本行核对齐整数',
  put_kind varchar(32) DEFAULT NULL COMMENT '来路（县局自录移交／市里统交两条来路）',
  status int DEFAULT NULL COMMENT '行落地情形 0未勾 1已收下 2已退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='历史用标卷宗转档核收行';

CREATE TABLE IF NOT EXISTS t_gipr_case_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '专用标志使用核准办件的件次',
  holder_no varchar(64) DEFAULT NULL COMMENT '起件四样之一：哪一家企业（授权册上那一个代号）',
  prod_id int DEFAULT NULL COMMENT '起件四样之二：挂哪一个名录产品（名录底册id）',
  goods_kind varchar(128) DEFAULT NULL COMMENT '起件四样之三：拟贴在哪一类货上',
  apply_time datetime DEFAULT NULL COMMENT '起件四样之四：递件那一刻',
  idem_key varchar(96) DEFAULT NULL COMMENT '同一件并着进两回的幂等键（重取不重建）',
  stage int DEFAULT NULL COMMENT '当前关次 0..3（申请受理/材料初审/标志核准/备案发标）——以后端数出来的为准',
  view_seq int DEFAULT NULL COMMENT '报面自带序号（只供人眼对着看，库里存取一律按 stage 数出来的）',
  attempt_no int DEFAULT '0' COMMENT '进关累计趟次：退回再进按新起一次重新累计',
  cert_flag int DEFAULT '0' COMMENT '标志核发记号 0未写回 1已写回（末关两名核签齐了写回本行）',
  end_kind int DEFAULT '0' COMMENT '了结类别 0未完 1走满末格封口 2递件方撤回 3名录产品整段并走',
  open_holder varchar(64) DEFAULT NULL COMMENT '在办占位：未闭上=企业代号（同企业在办至多一条的硬约束），闭上/了结/抽走=NULL',
  status int DEFAULT NULL COMMENT '单子走到哪一关 0未起 1在办 2已封住',
  content varchar(255) DEFAULT NULL COMMENT '一关一记',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次挪关动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_case_idem (idem_key),
  UNIQUE KEY uk_case_open_holder (open_holder),
  KEY idx_case_holder_status (holder_no, status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专用标志使用核准办件流转单';

CREATE TABLE IF NOT EXISTS t_gipr_case_trace (
  id bigint NOT NULL COMMENT '主键',
  case_id bigint NOT NULL COMMENT '挂在哪一条件身后',
  stage int DEFAULT NULL COMMENT '这句话落在哪一格 0..3',
  attempt_no int DEFAULT '0' COMMENT '落字时本件的进关趟次（同格重进后旧句仍在、新句另计）',
  action_kind varchar(32) DEFAULT NULL COMMENT '动作类别 START起件/NOTE按语/ADVANCE推进/ROLLBACK退回/SIGN核签/ISSUE核发/CLOSE封口/CANCEL撤回/MERGE并走',
  content varchar(500) DEFAULT NULL COMMENT '那句按语原样（退回时原样挪入末尾）',
  trace_time datetime DEFAULT NULL COMMENT '这句话落下的那一刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_trace_case_time (case_id, trace_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='办件按语追溯串（只追加，回放只读不回写）';

CREATE TABLE IF NOT EXISTS t_gipr_case_sign (
  id bigint NOT NULL COMMENT '主键',
  case_id bigint NOT NULL COMMENT '挂在哪一条件（末关核签）',
  signer_no varchar(64) NOT NULL COMMENT '核签岗代号（两名须各落一笔，同人不算两名）',
  sign_time datetime DEFAULT NULL COMMENT '落笔那一刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sign_case_signer (case_id, signer_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备案发标前两名核签岗落笔记录';

CREATE TABLE IF NOT EXISTS t_gipr_cert_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '专用标志使用授权签批单',
  node_no int DEFAULT NULL COMMENT '当前所在关 0..2（县级初核/市级核认/省局核准）',
  sign_mode int DEFAULT NULL COMMENT '本关算齐办法 0一名即齐 1两名须同判',
  need_count int DEFAULT NULL COMMENT '本关应签人数',
  sign_count int DEFAULT NULL COMMENT '本关已到人数',
  status int DEFAULT NULL COMMENT '签批情形 0在签 1已点齐 2已压回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专用标志使用授权签批单';

CREATE TABLE IF NOT EXISTS t_gipr_check_item (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '年度核查提醒条目代号',
  due_at datetime DEFAULT NULL COMMENT '该发提醒那一一日（落库为 datetime）',
  amount decimal(12,2) DEFAULT NULL COMMENT '至多许提前几日',
  content varchar(255) DEFAULT NULL COMMENT '事由与所对企业的记要',
  status int DEFAULT NULL COMMENT '条目情形 0搁着 1已发出 2发不成',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='年度核查与续展提醒条目';

CREATE TABLE IF NOT EXISTS t_gipr_holder_book (
  id bigint NOT NULL COMMENT '主键',
  holder_no varchar(64) DEFAULT NULL COMMENT '用标企业授权册代号',
  prod_id int DEFAULT NULL COMMENT '挂在哪一个名录产品名下',
  prod_no varchar(64) DEFAULT NULL COMMENT '名录产品代号（冗余自名录底册）',
  src_kind varchar(32) DEFAULT NULL COMMENT '来路（县局代收／市里补报／企业直送三条来路）',
  area_num decimal(12,2) DEFAULT NULL COMMENT '在册经营面积数',
  content varchar(255) DEFAULT NULL COMMENT '随件交来的企业名号与主打品类记要',
  status int DEFAULT NULL COMMENT '册面情形 0新入册 1已核齐 2缺项',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专用标志使用企业授权底册';

CREATE TABLE IF NOT EXISTS t_gipr_product_book (
  id bigint NOT NULL COMMENT '主键',
  prod_no varchar(64) DEFAULT NULL COMMENT '地标产品名录代号',
  prod_name varchar(128) DEFAULT NULL COMMENT '名录全称',
  prod_kind varchar(32) DEFAULT NULL COMMENT '层类别（甲层种植类·乙层水产类·丙层初加工类）',
  road_name varchar(255) DEFAULT NULL COMMENT '产地范围详址（澜原省—渠州市—浚安县—镇—片区）',
  src_kind varchar(32) DEFAULT NULL COMMENT '来路（国家局核准公告下发／省里增补／本局自录）',
  status int DEFAULT NULL COMMENT '名录情形 0在册 1已移出',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='地理标志产品名录底册';

CREATE TABLE IF NOT EXISTS t_gipr_term_book (
  id bigint NOT NULL COMMENT '主键',
  term_no varchar(64) DEFAULT NULL COMMENT '名录词条代号（由系统排上，不留手填的格子）',
  term_name varchar(128) DEFAULT NULL COMMENT '词条名目（汉文正名／外文对照／拼音缩写）',
  term_lang varchar(32) DEFAULT NULL COMMENT '本行语种（汉／外／拼 三式各一行）',
  term_text varchar(255) DEFAULT NULL COMMENT '词条文面',
  content varchar(255) DEFAULT NULL COMMENT '核验串（随版次走）',
  term_kind int DEFAULT NULL COMMENT '词条取用三式 0只出汉文 1只出对照 2按层回落',
  lang_num int DEFAULT NULL COMMENT '一屏并列展示的语种条数 1..3（量程外的数视同没填）',
  level_kind varchar(32) DEFAULT NULL COMMENT '定档层级（省定／市定／县定／系统兜底）',
  parent_no varchar(64) DEFAULT NULL COMMENT '继承自哪一条',
  prod_id int DEFAULT NULL COMMENT '挂在哪一个名录产品名下',
  prod_no varchar(64) DEFAULT NULL COMMENT '适用的名录产品代号（冗余自名录底册）',
  prod_name varchar(128) DEFAULT NULL COMMENT '名录全称（冗余一份，以名录底册为准回写）',
  ver_no int DEFAULT NULL COMMENT '改动次序（数值大的先说话）',
  crt_num int DEFAULT NULL COMMENT '本项应收录条数',
  hit_num int DEFAULT NULL COMMENT '已对齐条数',
  lack_num int DEFAULT NULL COMMENT '还差几条（轧出来，不留填数的空位）',
  eff_start datetime DEFAULT NULL COMMENT '启用那一一日',
  eff_end datetime DEFAULT NULL COMMENT '交班那一一日(不含)',
  status int DEFAULT NULL COMMENT '行的情形 0在位 1已撤下',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='名录对外公示多语言词条册';

CREATE TABLE IF NOT EXISTS t_gipr_yield_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '换算贴线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  obj_kind varchar(32) DEFAULT NULL COMMENT '这条线管种植类、水产类还是初加工类',
  th1_max decimal(12,3) DEFAULT NULL COMMENT '关注限值',
  th2_max decimal(12,3) DEFAULT NULL COMMENT '告警限值',
  th3_max decimal(12,3) DEFAULT NULL COMMENT '封顶限值',
  unit_kind varchar(32) DEFAULT NULL COMMENT '计量单位口径（每亩公斤／每公顷吨 两式）',
  eff_start datetime DEFAULT NULL COMMENT '启用那一一日',
  eff_end datetime DEFAULT NULL COMMENT '交班那一一日(不含)',
  priority int DEFAULT NULL COMMENT '交班次序(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0在位 1已撤下',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='核定产量与特色指标换算贴线档';

-- 初始档案数据（id=1 启用 / id=2 停用）
-- 名录产品预置两条：prod id=0 在册、id=1 已移出，供「挂靠企业是否仍在册」与
-- 「已移出那一段不再收新行」两类业务判定取用。
-- 层类别（甲层/乙层）与产地范围按名录登记口径给值。
INSERT IGNORE INTO t_gipr_product_book (id, prod_no, prod_name, prod_kind, road_name, src_kind, status, del_flag, create_by, create_time)
VALUES (0, 'GP00', '澜原省渠州市浚安县浚浦蟹（甲层水产类·在册）', '甲层', '澜原省—渠州市—浚安县—北浚镇—浚浦水域', '国家局核准公告下发', 0, 0, 'seed', NOW()),
       (1, 'GP01', '南浚镇西片沙梨·已移出的旧名录（已移出）', '乙层', '澜原省—渠州市—浚安县—南浚镇—西片坡地', '省里增补', 1, 0, 'seed', NOW());

-- ---------------------------------------------------------------------------
-- 旧库升级段：早年立下的库只有 t_gipr_case_flow 老五列，以下逐列补齐；
-- 新库 CREATE TABLE 已含同名列，存储过程按 information_schema 判过，重复执行不报错。
-- 早年那批件与今年新起的件读法一致，不因立的迟早分岔。
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS gipr_case_flow_upgrade;
DELIMITER $$
CREATE PROCEDURE gipr_case_flow_upgrade()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 't_gipr_case_flow' AND column_name = 'holder_no') THEN
    ALTER TABLE t_gipr_case_flow ADD COLUMN holder_no varchar(64) DEFAULT NULL COMMENT '起件四样之一：哪一家企业（授权册代号）' AFTER biz_no;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 't_gipr_case_flow' AND column_name = 'prod_id') THEN
    ALTER TABLE t_gipr_case_flow ADD COLUMN prod_id int DEFAULT NULL COMMENT '起件四样之二：挂哪一个名录产品' AFTER holder_no;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 't_gipr_case_flow' AND column_name = 'goods_kind') THEN
    ALTER TABLE t_gipr_case_flow ADD COLUMN goods_kind varchar(128) DEFAULT NULL COMMENT '起件四样之三：拟贴在哪一类货上' AFTER prod_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 't_gipr_case_flow' AND column_name = 'apply_time') THEN
    ALTER TABLE t_gipr_case_flow ADD COLUMN apply_time datetime DEFAULT NULL COMMENT '起件四样之四：递件那一刻' AFTER goods_kind;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 't_gipr_case_flow' AND column_name = 'idem_key') THEN
    ALTER TABLE t_gipr_case_flow ADD COLUMN idem_key varchar(96) DEFAULT NULL COMMENT '同一件并着进两回的幂等键' AFTER apply_time;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 't_gipr_case_flow' AND column_name = 'view_seq') THEN
    ALTER TABLE t_gipr_case_flow ADD COLUMN view_seq int DEFAULT NULL COMMENT '报面自带序号（只供人眼看）' AFTER stage;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 't_gipr_case_flow' AND column_name = 'attempt_no') THEN
    ALTER TABLE t_gipr_case_flow ADD COLUMN attempt_no int DEFAULT 0 COMMENT '进关累计趟次' AFTER view_seq;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 't_gipr_case_flow' AND column_name = 'cert_flag') THEN
    ALTER TABLE t_gipr_case_flow ADD COLUMN cert_flag int DEFAULT 0 COMMENT '标志核发记号 0未写回 1已写回' AFTER attempt_no;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 't_gipr_case_flow' AND column_name = 'end_kind') THEN
    ALTER TABLE t_gipr_case_flow ADD COLUMN end_kind int DEFAULT 0 COMMENT '了结类别 0未完 1封口 2撤回 3并走' AFTER cert_flag;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 't_gipr_case_flow' AND column_name = 'open_holder') THEN
    ALTER TABLE t_gipr_case_flow ADD COLUMN open_holder varchar(64) DEFAULT NULL COMMENT '在办占位（闭上/了结置NULL）' AFTER end_kind;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 't_gipr_case_flow' AND index_name = 'uk_case_open_holder') THEN
    ALTER TABLE t_gipr_case_flow ADD UNIQUE KEY uk_case_open_holder (open_holder);
  END IF;
  -- 早年那批件：未闭上的把占位补上；同企业若已有多条在办（旧伤），只占位最早一条，
  -- 其余留 NULL，交由业务逐件了结，不把迁移卡死。
  UPDATE t_gipr_case_flow c
    JOIN (SELECT holder_no, MIN(id) AS min_id FROM t_gipr_case_flow
           WHERE holder_no IS NOT NULL AND del_flag = 0
             AND COALESCE(status, 1) = 1 AND COALESCE(end_kind, 0) = 0
           GROUP BY holder_no) m ON m.min_id = c.id
     SET c.open_holder = c.holder_no
   WHERE c.open_holder IS NULL;
  IF NOT EXISTS (SELECT 1 FROM (
                   SELECT open_holder FROM t_gipr_case_flow
                    WHERE open_holder IS NOT NULL GROUP BY open_holder HAVING COUNT(*) > 1) d) THEN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 't_gipr_case_flow' AND index_name = 'uk_case_open_holder') THEN
      ALTER TABLE t_gipr_case_flow ADD UNIQUE KEY uk_case_open_holder (open_holder);
    END IF;
  END IF;
END$$
DELIMITER ;
CALL gipr_case_flow_upgrade();
DROP PROCEDURE IF EXISTS gipr_case_flow_upgrade;

