package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.service.zw.ImportOutcome;

/**
 * 检测数据导入核对行 Service接口（excel-import 形状：整文件导入 + 正式账只读查询）。
 *
 * <p>导入分两层：结构不合格（点位不在底档、采样时间跨日、时间/读数认不出、批次号不一致）
 * <b>整批拦下一笔不落</b>；结构合格但内容异常（读数越界、无可用标准、申报规则代号与裁决线不符、
 * 自报无效）逐行挑进异常台账，不进正式账；只有格式与内容都合格的行才落 t_zw_imp_row，
 * 落库时等级、规则代号、规则生效日一并钉死。
 *
 * @author fuce
 * @date 2026-10-02
 */
public interface ITZwImpRowService {

    /** 按主键回查单行 */
    TZwImpRow selectTZwImpRowById(Long id);

    /** 正式账查询（台账页/报表共用，条件由 Controller 按筛选口径拼） */
    List<TZwImpRow> selectTZwImpRowList(QueryWrapper<TZwImpRow> queryWrapper);

    /**
     * 整文件导入：读表 → 结构校验（硬错整批拦）→ 逐行裁决（软错进异常台账）→ 合格行落正式账。
     * 同批同次（批次号+原表行次）幂等：重新导入不重复落账、不重开已核实/已归档的异常。
     *
     * @param content       上传文件字节（xlsx）
     * @param declaredBatch 前台声明的批次号（可空；不空时每行批次号都得与它一致）
     * @param operator      操作人（落 create_by，异常追溯到人）
     */
    ImportOutcome importExcel(byte[] content, String declaredBatch, String operator);
}
