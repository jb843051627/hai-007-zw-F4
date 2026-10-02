-- zw 城市供水水质检测调度与达标管理 -- schema (hai-007)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：hai_007

CREATE TABLE IF NOT EXISTS t_zw_arch_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '水源地/水厂档案卡号',
  node_no int DEFAULT NULL COMMENT '本卡版本次序',
  site_id int DEFAULT NULL COMMENT '所属底档',
  site_no varchar(64) DEFAULT NULL COMMENT '所属底档代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡供水规模(万吨/日)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本年度新增供水规模(万吨/日)',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版本次序走）',
  grade_level int DEFAULT NULL COMMENT '供水规模折到的保障强度层级',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已压死',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水源地/水厂档案卡';

CREATE TABLE IF NOT EXISTS t_zw_case_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '处置工单号',
  site_no varchar(64) DEFAULT NULL COMMENT '所属底档代号（只认代号不认名字）',
  stage int DEFAULT NULL COMMENT '当前处置段 0..3（登记/初检/处置/复核封卷）',
  status int DEFAULT NULL COMMENT '工单落定 0未起 1在办 2已封存',
  content varchar(255) DEFAULT NULL COMMENT '工单记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水质异常处置工单';

CREATE TABLE IF NOT EXISTS t_zw_imp_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '检测数据上传批次号（采样批次）',
  row_no int DEFAULT NULL COMMENT '原表行次',
  site_no varchar(64) DEFAULT NULL COMMENT '采样点位代号（随样本入账，水质核实只认代号）',
  item_code varchar(64) DEFAULT NULL COMMENT '被校验出的数据项代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行检测读数',
  sample_type varchar(32) DEFAULT NULL COMMENT '样本类别(水源水/出厂水/管网末梢)',
  test_at datetime DEFAULT NULL COMMENT '检测发生那天（用哪条线只看这天，不看今天）',
  valid_flag int DEFAULT NULL COMMENT '申报是否有效 0否 1是（随原始行入账，筛选口径）',
  review_flag int DEFAULT NULL COMMENT '申报是否已复核 0否 1是（随原始行入账，筛选口径）',
  grade_level int DEFAULT NULL COMMENT '评价等级 1合格 2关注 3超标 4严重超标；空=未定级(无可用标准)',
  rule_code varchar(64) DEFAULT NULL COMMENT '定级所依标准线代号（服务层回值钉死，写过不再改）',
  rule_eff_date datetime DEFAULT NULL COMMENT '所依标准的生效那天（历史复核只认此编号上下文）',
  raw_line varchar(1000) DEFAULT NULL COMMENT '原始行原样留档（七列原样拼接，下钻先看这一条）',
  imp_issue_msg varchar(500) DEFAULT NULL COMMENT '本行校验结论文面（异常细账在 t_zw_issue，明细为准）',
  status int DEFAULT NULL COMMENT '行落地情形 0待核 1已入库 2退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_imp_batch_row (batch_no, row_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检测数据导入核对行（只收格式合格行）';

-- 异常核实台账：异常的生成、确认、核实、归档全留在这里，只追加核实结论一次；
-- 核实成/核实不成连同核实人、核实时刻当场钉死，归档后原样锁住，任何导入/动作不得再改。
CREATE TABLE IF NOT EXISTS t_zw_issue (
  id bigint NOT NULL COMMENT '主键',
  issue_no varchar(64) DEFAULT NULL COMMENT '异常事项号（批次号-行次，一次生成）',
  batch_no varchar(64) DEFAULT NULL COMMENT '来源上传批次号',
  row_no int DEFAULT NULL COMMENT '原表行次',
  site_no varchar(64) DEFAULT NULL COMMENT '采样点位代号（只认代号不认名字）',
  item_code varchar(64) DEFAULT NULL COMMENT '被校验出的数据项代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行检测读数（照原始行抄入）',
  sample_type varchar(32) DEFAULT NULL COMMENT '样本类别(水源水/出厂水/管网末梢)',
  test_at datetime DEFAULT NULL COMMENT '检测时间（唯一解析口径，页面与接口同一把尺）',
  valid_flag int DEFAULT NULL COMMENT '申报是否有效 0否 1是',
  review_flag int DEFAULT NULL COMMENT '申报是否已复核 0否 1是',
  rule_code_decl varchar(64) DEFAULT NULL COMMENT '申报规则代号（原始行里带的那条）',
  rule_code_hit varchar(64) DEFAULT NULL COMMENT '裁决出口按检测当天选中的规则代号',
  grade_level int DEFAULT NULL COMMENT '裁决出口给的等级；无可用标准/读数越界为空',
  raw_line varchar(1000) DEFAULT NULL COMMENT '原始行原样留档（归档后锁住的就是这一条）',
  check_result varchar(500) DEFAULT NULL COMMENT '校验结论：本行能不能过校验、为什么，逐行写明',
  issue_type varchar(32) DEFAULT NULL COMMENT '异常类 INVALID读数越界/NO_RULE无可用标准/RULE_MISMATCH规则代号对不上',
  confirm_status int DEFAULT '0' COMMENT '确认情形 0待确认 1已确认（异常先入册、再当面确认）',
  confirm_by varchar(64) DEFAULT NULL COMMENT '确认人',
  confirm_time datetime DEFAULT NULL COMMENT '确认时刻',
  verify_result int DEFAULT NULL COMMENT '核实结论 1核实成 2核实不成；空=尚未核实',
  verify_reason varchar(500) DEFAULT NULL COMMENT '核实不成的原因（核实不成必填，核实成留补充说明）',
  verify_by varchar(64) DEFAULT NULL COMMENT '核实人（谁核实的写谁，以这一次为准）',
  verify_time datetime DEFAULT NULL COMMENT '核实时刻（只写一次，系统日志可翻出哪一次写的）',
  archived int DEFAULT '0' COMMENT '归档标识 0未归档 1已归档（归档即只读，原样锁住不能再改）',
  status int DEFAULT '0' COMMENT '台账情形 0在办 1已封档',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除（归档行永不置删）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_issue_no (issue_no),
  UNIQUE KEY uk_issue_batch_row (batch_no, row_no),
  KEY idx_issue_site (site_no),
  KEY idx_issue_batch (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水质异常核实台账（一次核实、归档只读）';

CREATE TABLE IF NOT EXISTS t_zw_lim_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '水质限值判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '判定线名',
  sample_type varchar(32) DEFAULT NULL COMMENT '适用样本类别(水源水/出厂水/管网末梢)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '关注档指标上限',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '超标档指标上限',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '严重超标档指标上限',
  eff_start datetime DEFAULT NULL COMMENT '启用之日',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)',
  priority int DEFAULT NULL COMMENT '判定线顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '判定线情形 0现行 1已让位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水质指标限值分档判定线';

CREATE TABLE IF NOT EXISTS t_zw_sam_plan (
  id bigint NOT NULL COMMENT '主键',
  plan_no varchar(64) DEFAULT NULL COMMENT '检测计划周期代号',
  site_no varchar(64) DEFAULT NULL COMMENT '点位代号（派单时带入事项条）',
  team_id bigint DEFAULT NULL COMMENT '直挂承接班组(部门主键)；空=派发时按各班组当前负载加权分派',
  period_days int DEFAULT NULL COMMENT '周期自然日数（隔多少天采一次）',
  amount decimal(12,2) DEFAULT NULL COMMENT '提前浮出待办的自然日数（全站唯一可调档）',
  first_due date DEFAULT NULL COMMENT '首个约定采样日（其后按周期顺排）',
  eff_start datetime DEFAULT NULL COMMENT '计划生效之时',
  eff_end datetime DEFAULT NULL COMMENT '计划停用之时(不含)；空=长期现行',
  content varchar(255) DEFAULT NULL COMMENT '采样事由',
  status int DEFAULT NULL COMMENT '计划情形 0现行 1停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sam_plan_no (plan_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采样检测计划周期底档';

CREATE TABLE IF NOT EXISTS t_zw_sam_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '采样事项号（计划代号-采样日，系统一次生成）',
  plan_no varchar(64) DEFAULT NULL COMMENT '检测计划周期代号（底档带入）',
  site_no varchar(64) DEFAULT NULL COMMENT '点位代号（底档带入）',
  team_id bigint DEFAULT NULL COMMENT '承接班组(部门主键)；空=派发时按当前负载加权分派后回写',
  due_at datetime DEFAULT NULL COMMENT '约定采样那一天的终结时刻(当日23:59:59)——全站唯一口径，任何动作不得挪动',
  amount decimal(12,2) DEFAULT NULL COMMENT '提前浮出待办的自然日数（唯一可调档）',
  dispatch_at datetime DEFAULT NULL COMMENT '真正派出时刻（首次钉库，后到动作不得覆盖）',
  cancel_at datetime DEFAULT NULL COMMENT '撤单时刻',
  content varchar(255) DEFAULT NULL COMMENT '采样事由与受派班组记要',
  hang_reason varchar(255) DEFAULT NULL COMMENT '挂起原因（点位退役/班组停班/无班可派）',
  status int DEFAULT NULL COMMENT '条目情形 0待派 1已派 2挂起 3撤单 4转作他理',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（撤单/转理留痕追加于此）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sam_item_no (item_no),
  UNIQUE KEY uk_sam_plan_site_due (plan_no, site_no, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采样任务到期派单';

CREATE TABLE IF NOT EXISTS t_zw_source (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '水源地/水厂档案代号',
  site_name varchar(128) DEFAULT NULL COMMENT '档案名称',
  site_type varchar(32) DEFAULT NULL COMMENT '档案类别(水源地/水厂)',
  road_name varchar(128) DEFAULT NULL COMMENT '所属行政区划(省—市—区—街道)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单档供水规模一档上限(万吨/日)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档上限(万吨/日)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档上限(万吨/日)',
  status int DEFAULT NULL COMMENT '底档情形 0在用 1已退役',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水源地/水厂底档';

-- 旧版停供单带手填计数列（sign_mode/need_count/sign_count），与"枚数顺着签核记录点算"不相容，结构重建。
DROP TABLE IF EXISTS t_zw_stop_bill;
CREATE TABLE IF NOT EXISTS t_zw_stop_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '停供/限供申请签核单号（点位代号-年-当年序）',
  site_no varchar(64) DEFAULT NULL COMMENT '水源点代号（只认代号不认名字）',
  bill_year int DEFAULT NULL COMMENT '申请所属自然年（当年序按此归年）',
  year_seq int DEFAULT NULL COMMENT '同一水源点当年第几份申请（第二份当场单独点名）',
  reason varchar(32) DEFAULT NULL COMMENT '停水事由代号 DEPLETED水源枯竭/OVER_LIMIT指标连续超标/REPAIR检修',
  alt_plan varchar(500) DEFAULT NULL COMMENT '停水后的替代供水方案',
  notice_text varchar(500) DEFAULT NULL COMMENT '用户公告文案（按事由从名录带出，不经人手抄录）',
  node_no int DEFAULT NULL COMMENT '当前所在道 0业务提 1水质核 2调度评 3主管准',
  round_no int DEFAULT '1' COMMENT '签核轮次；主管打回一轮旧签核整轮作废，补签另起一轮，两轮不摆在一起',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已终止（不同意/撤回）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stop_bill_no (bill_no),
  KEY idx_stop_site_year (site_no, bill_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='停供/限供申请签核单';

-- 签核台账：只追加，任何关口动作都在此落一笔；关口过没过、落了几枚名，全由服务层顺着这张表点算。
CREATE TABLE IF NOT EXISTS t_zw_stop_sign (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint NOT NULL COMMENT '所属签核单',
  round_no int NOT NULL COMMENT '落印轮次（作废轮原样留痕，计数只数当前轮）',
  node_no int NOT NULL COMMENT '关口 0业务提 1水质核 2调度评 3主管准',
  sign_role varchar(32) NOT NULL COMMENT '落名口 BUSINESS经办/WATER_QUA水质核实/DISP_QTY水量调配评估/DISP_NET管网影响评估/DISP_CHIEF调度主管/APPLICANT申请人',
  signer varchar(64) NOT NULL COMMENT '落名人（各评各的，互不替签）',
  action varchar(16) NOT NULL COMMENT '落印动作 AGREE同意/DISAGREE不同意/WITHDRAW撤回/SENDBACK主管打回',
  opinion varchar(500) DEFAULT NULL COMMENT '签核意见',
  sign_time datetime NOT NULL COMMENT '落印时刻（同一分钟两枚仍是两笔）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常（只追加，不置删）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stop_sign_slot (bill_id, round_no, node_no, sign_role),
  KEY idx_stop_sign_bill (bill_id, round_no, node_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='停供申请签核台账（只追加）';

-- 初始档案数据：id=0 在用、id=1 已退役
-- 供水规模上限 30/80/200 万吨/日：等于上限算高一档。
INSERT IGNORE INTO t_zw_source (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'ZS00', '青坪市城北水源地（在用）', '水源地', '青坪省—青坪市—城东区—东湖街道', 30.00, 80.00, 200.00, 0, 0, 'seed', NOW()),
       (1, 'ZS01', '青坪市城南水厂（已退役）', '水厂', '青坪省—青坪市—城南区—望湖街道', 30.00, 80.00, 200.00, 1, 0, 'seed', NOW());

-- 检测计划周期底档种子：
-- PL-ZS00-M：城北水源地月度例行（30 天一周期，提前 3 天浮出，班组不直挂→按负载加权派班）；
-- PL-ZS01-Q：城南水厂季度（直挂 2 号班组；点位已退役，开单环节直接跳过、不生成新派单；
--     若库里还有退役前落下的待派条，跑轮时仍就地转挂起——旧账可查、新账不开）。
INSERT IGNORE INTO t_zw_sam_plan (id, plan_no, site_no, team_id, period_days, amount, first_due, eff_start, eff_end, content, status, del_flag, create_by, create_time)
VALUES (10, 'PL-ZS00-M', 'ZS00', NULL, 30, 3.00, '2026-09-01', '2026-09-01 00:00:00', NULL, '城北水源地月度例行采样', 0, 0, 'seed', NOW()),
       (11, 'PL-ZS01-Q', 'ZS01', 2, 90, 3.00, '2026-09-15', '2026-09-15 00:00:00', NULL, '城南水厂季度采样（点位已退役，开单即挂起）', 0, 0, 'seed', NOW());

-- 水质限值分档判定线种子（出厂水余氯 mg/L）：
-- 100 旧线 CL-FW-2024：上年口径，上限松，严重超标一格早期入册本就留空（空=不设此档，随超标档下推），
--     管到 2026-01-01（不含），现已让位，只作回溯依据，不参与现行评价；
-- 101 新线 CL-FW-2026：今年收紧，自 2026-01-01 00:00:00 起现行——交接日全厂一套口径，旧线管到进程、新线从结局起算；
-- 102/103 水源水、管网末梢各一条，与出厂水各管各的。
-- 读数值与界值相同算高档；顺位相同并行使权时按代号序列排后者胜出（确定性，四季一致）。
INSERT IGNORE INTO t_zw_lim_rule (id, rule_code, rule_name, sample_type, th1_max, th2_max, th3_max, eff_start, eff_end, priority, status, del_flag, create_by, create_time, remark)
VALUES (100, 'CL-FW-2024', '出厂水余氯分档线(上年口径)', '出厂水', 0.30, 0.50, NULL, '2024-01-01 00:00:00', '2026-01-01 00:00:00', 10, 1, 0, 'seed', NOW(), '上年宽口径；严重超标档留空即不设此档'),
       (101, 'CL-FW-2026', '出厂水余氯分档线(今年收紧)', '出厂水', 0.20, 0.40, 0.60, '2026-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), '今年收紧口径'),
       (102, 'CL-SW-2026', '水源水余氯分档线', '水源水', 0.20, 0.30, 0.50, '2026-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), NULL),
       (103, 'CL-PN-2026', '管网末梢余氯分档线', '管网末梢', 0.15, 0.25, 0.40, '2026-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), NULL);

-- 停水公告名录（系统字典 zw_stop_notice）：文案按停水事由从名录带出，页面只展示、不另请人抄录。
-- dict_value=停水事由代号，dict_label=名录短名，remark=完整公告模板。
INSERT IGNORE INTO t_sys_dict_data (id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
VALUES (880001, 1, '水源枯竭停水公告', 'DEPLETED', 'zw_stop_notice', '', 'warning', 'N', '0', 'seed', NOW(),
        '因{site}水源枯竭，定于停水时段暂停供水，请受影响用户提前蓄水，饮用水请煮沸后使用。'),
       (880002, 2, '指标连续超标停水公告', 'OVER_LIMIT', 'zw_stop_notice', '', 'danger', 'N', '0', 'seed', NOW(),
        '因{site}水质指标连续超标，为保障用水安全，定于停水时段暂停供水，恢复时间另行通知。'),
       (880003, 3, '水厂检修停水公告', 'REPAIR', 'zw_stop_notice', '', 'info', 'N', '0', 'seed', NOW(),
        '因{site}设施检修，定于停水时段暂停供水，检修完成后即时恢复，请用户提前做好储水准备。');

-- 城北水源地近三个月水源水余氯检测行：0.62/0.65/0.71，对现行线 CL-SW-2026 连续超标，供第二道核实对照。
INSERT IGNORE INTO t_zw_imp_row (id, batch_no, row_no, site_no, item_code, qty, sample_type, test_at, valid_flag, review_flag, grade_level, rule_code, rule_eff_date, status, del_flag, create_by, create_time)
VALUES (9001, 'B202607', 1, 'ZS00', 'CL', 0.62, '水源水', '2026-07-18 09:00:00', 1, 1, 4, 'CL-SW-2026', '2026-01-01 00:00:00', 1, 0, 'seed', NOW()),
       (9002, 'B202608', 1, 'ZS00', 'CL', 0.65, '水源水', '2026-08-18 09:00:00', 1, 1, 4, 'CL-SW-2026', '2026-01-01 00:00:00', 1, 0, 'seed', NOW()),
       (9003, 'B202609', 1, 'ZS00', 'CL', 0.71, '水源水', '2026-09-18 09:00:00', 1, 1, 4, 'CL-SW-2026', '2026-01-01 00:00:00', 1, 0, 'seed', NOW());

-- 未完结处置单演示：ZS02 名下挂着在办工单，该点的停供申请到第二道即被压住，不得往前。
INSERT IGNORE INTO t_zw_source (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (2, 'ZS02', '青坪市青西水源地（在用·有在办处置单）', '水源地', '青坪省—青坪市—城西区—青西街道', 30.00, 80.00, 200.00, 0, 0, 'seed', NOW());
INSERT IGNORE INTO t_zw_case_flow (id, biz_no, site_no, stage, status, content, last_action, del_flag, create_by, create_time)
VALUES (9101, 'CF-202609-01', 'ZS02', 1, 1, '余氯异常处置中', '初检', 0, 'seed', NOW());

-- 既有库升级（旧库已建过 t_zw_imp_row 的，手动补这四列后再发版；全新库由上面的 CREATE TABLE 带出）：
-- ALTER TABLE t_zw_imp_row
--   ADD COLUMN valid_flag int DEFAULT NULL COMMENT '申报是否有效 0否 1是' AFTER test_at,
--   ADD COLUMN review_flag int DEFAULT NULL COMMENT '申报是否已复核 0否 1是' AFTER valid_flag,
--   ADD COLUMN raw_line varchar(1000) DEFAULT NULL COMMENT '原始行原样留档' AFTER rule_eff_date,
--   ADD COLUMN imp_issue_msg varchar(500) DEFAULT NULL COMMENT '本行校验结论文面' AFTER raw_line,
--   ADD UNIQUE KEY uk_imp_batch_row (batch_no, row_no);

-- 菜单与按钮权限（t_sys_permission）：化验读数导入/明细、水质异常核实台账。
-- 881000 为「水质检测」目录；按钮 perms 串与 Controller @RequiresPermissions 逐一对齐。
INSERT IGNORE INTO t_sys_permission
  (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES
  (881000, '水质检测', '水质检测调度目录', NULL, 0, 0, NULL, 0, 'layui-icon-water', 90, 0, 'seed', NOW()),
  (881010, '化验读数导入与明细', 'Excel 整批导入与正式账查询', '/zwImpData/view', 0, 881000, 'zw:zwImpData:view', 1, 'layui-icon-upload-circle', 1, 0, 'seed', NOW()),
  (881011, '检测明细查询', '正式账列表查询', '/zwImpData/list', 0, 881010, 'zw:zwImpData:list', 2, NULL, NULL, 0, 'seed', NOW()),
  (881012, '化验读数导入', '整文件校验导入', '/zwImpData/upload', 0, 881010, 'zw:zwImpData:import', 2, NULL, NULL, 0, 'seed', NOW()),
  (881013, '检测明细报表导出', 'UTF-8 Excel 导出', '/zwImpData/report', 0, 881010, 'zw:zwImpData:export', 2, NULL, NULL, 0, 'seed', NOW()),
  (881020, '水质异常核实台账', '异常列表/下钻/确认/整批一次核实归档', '/zwIssue/view', 0, 881000, 'zw:zwIssue:view', 1, 'layui-icon-about', 2, 0, 'seed', NOW()),
  (881021, '异常台账查询', '异常列表查询', '/zwIssue/list', 0, 881020, 'zw:zwIssue:list', 2, NULL, NULL, 0, 'seed', NOW()),
  (881022, '异常当面确认', '逐条/整批确认', '/zwIssue/confirm', 0, 881020, 'zw:zwIssue:confirm', 2, NULL, NULL, 0, 'seed', NOW()),
  (881023, '异常整批核实归档', '整批一次核实，归档只读', '/zwIssue/verify', 0, 881020, 'zw:zwIssue:verify', 2, NULL, NULL, 0, 'seed', NOW());

-- 授给管理员角色（id=488243256161730560）；其他角色按岗位在权限管理里另授。
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id, create_by, create_time)
SELECT p.pid, 488243256161730560, p.pid, 'seed', NOW()
FROM (
  SELECT 881010 AS pid UNION ALL SELECT 881011 UNION ALL SELECT 881012 UNION ALL SELECT 881013
  UNION ALL SELECT 881020 UNION ALL SELECT 881021 UNION ALL SELECT 881022 UNION ALL SELECT 881023
) p;

