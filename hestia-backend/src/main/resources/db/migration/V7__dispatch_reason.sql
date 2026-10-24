-- 批次 3 第二轮：派单分配记录增加派单/改派原因列
-- 依据 docs/modules/08 第 2 节（拒单必须填写原因）与第 9 节（改派原因必填、
-- 运营手工改派写审计）。自动改派写入触发码（REJECT/EXPIRED），
-- 手工派单/改派写入运营填写的原因文本。
--
-- 注：人员维度的检索索引已在 V5 建表时给出（worker_id, status, accept_deadline
-- 三列，最左前缀覆盖本模块全部查询），此处不再重复建索引。

ALTER TABLE order_assignment
    ADD COLUMN dispatch_reason VARCHAR(255) NULL AFTER reject_reason_text;
