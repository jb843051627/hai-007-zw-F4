package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.service.ITZwImpRowService;
import com.fc.v2.service.ITZwLimRuleService;
import com.fc.v2.service.zw.WaterVerdict;

/**
 * 检测数据导入核对行 Service业务层处理（batch-process 形状：整批提交）
 *
 * <p>逐行校验：合法行入正式账、非法行入失败明细（保留原始行号），谁也不连累谁——
 * 坏一行不许让整批合法数据停在库外。整批在一个事务内落账，库级异常整体回滚。
 *
 * <p><b>定级只过唯一出口</b>：每行的原始读数、评价等级、所依标准编号与生效日，一律以
 * {@link ITZwLimRuleService#evaluate(java.math.BigDecimal, java.util.Date, String)} 的回值
 * {@link WaterVerdict} 为准，本服务不自行判档、不预填结论：
 * <ul>
 *   <li>GRADED：以回值钉死等级、线代号、生效日，写过不再改；</li>
 *   <li>INVALID：读数不合法，置退回（化验员重录）；</li>
 *   <li>NO_RULE：读数本身没问题但检测当日无可用标准——照常入库，等级/编号留空，
 *       备注明写「无可用标准」，绝不拿临近日期凑合、绝不硬给结论。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwImpRowServiceImpl implements ITZwImpRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TZwImpRowMapper zwImpRowMapper;

    @javax.annotation.Resource
    private ITZwLimRuleService zwLimRuleService;

    @Override
    public TZwImpRow selectTZwImpRowById(Long id) {
        return this.zwImpRowMapper.selectById(id);
    }

    /**
     * 整批提交。
     * <ul>
     *   <li>空批次 → 0，不当成执行出错；</li>
     *   <li>行数超 {@link #MAX_ROWS} → -1，整批一笔不入库；</li>
     *   <li>同批次号重复提交幂等：已落过账直接返回既有成功行数，不重复入库（定级写过不再改）；</li>
     *   <li>逐行校验：事项编号空 / 数量空或非正、读数不合法 → 退回；其余逐行调唯一评价出口，
     *       定级信息以回值钉死；无可用标准的行入库但不写等级，备注明写。返回成功入库行数。</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TZwImpRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        String no = batchNo == null || batchNo.trim().isEmpty() ? rows.get(0).getBatchNo() : batchNo;
        // 同批次重复提交：已有账就原样返回成功行数，绝不重复落（定级一并锁死）
        Integer existed = this.zwImpRowMapper.selectCount(new QueryWrapper<TZwImpRow>()
                .eq("batch_no", no)
                .eq("del_flag", 0));
        if (existed != null && existed > 0) {
            Integer okExisted = this.zwImpRowMapper.selectCount(new QueryWrapper<TZwImpRow>()
                    .eq("batch_no", no)
                    .eq("status", STATUS_OK)
                    .eq("del_flag", 0));
            return okExisted == null ? 0 : okExisted;
        }
        int ok = 0;
        for (int i = 0; i < rows.size(); i++) {
            TZwImpRow r = rows.get(i);
            r.setRowNo(Integer.valueOf(i + 1));
            r.setBatchNo(no);
            r.setDelFlag(0);
            if (structurallyInvalid(r)) {
                // 事项编号空、读数空/非正这类连评价出口都不必进——直接退回化验员
                r.setStatus(STATUS_FAIL);
            } else {
                // 落在哪一档、凭哪条线，全由唯一出口当场定夺
                WaterVerdict verdict = this.zwLimRuleService.evaluate(
                        r.getQty(), r.getTestAt(), r.getSampleType());
                if (verdict.getKind() == WaterVerdict.Kind.INVALID) {
                    // 负数、超大怪值：不参与定级，退回重录
                    r.setStatus(STATUS_FAIL);
                    r.setRemark(verdict.getMessage());
                } else {
                    r.setStatus(STATUS_OK);
                    ok++;
                    if (verdict.getKind() == WaterVerdict.Kind.GRADED) {
                        // 等级连同所依标准代号、生效日一并钉进结果，写过不再改
                        r.setGradeLevel(Integer.valueOf(verdict.getGrade()));
                        r.setRuleCode(verdict.getRuleCode());
                        r.setRuleEffDate(verdict.getRuleEffDate());
                    } else {
                        // NO_RULE：不硬给结论，等级/编号留空，明写「无可用标准」
                        r.setGradeLevel(null);
                        r.setRuleCode(null);
                        r.setRuleEffDate(null);
                        r.setRemark(verdict.getMessage());
                    }
                }
            }
            this.zwImpRowMapper.insert(r);
        }
        return ok;
    }

    /** 事项编号空、数量空或非正，连评价出口都不进，直接算退回行 */
    private static boolean structurallyInvalid(TZwImpRow r) {
        return r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                || r.getQty() == null
                || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0;
    }

    @Override
    public List<TZwImpRow> listErrors(String batchNo) {
        return this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .eq("batch_no", batchNo)
                .eq("status", STATUS_FAIL)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }
}
