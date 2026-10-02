package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TZwIssue;
import com.fc.v2.service.zw.IssueDiff;
import com.fc.v2.service.zw.IssueView;

/**
 * 水质异常核实台账 Service接口（verify-once 形状：生成 → 确认 → 一次核实 → 归档只读）。
 *
 * <p>核实以<b>批次</b>为单位只做一次：{@link #verifyBatch} 当场把整批未归档异常一并钉死
 * （核实结论/原因/核实人/核实时刻）并全部置归档，之后该批只读；同批重新导入不会翻出
 * 已归档条目重复核。谁都可以只读地查归档结果、核实结论、核实人、核实时间——
 * 这几样若被旁路改动，以核实那一次写入的为准（条件更新保证只有一次写入能成）。
 *
 * @author fuce
 * @date 2026-10-02
 */
public interface ITZwIssueService {

    /** 按主键回查 */
    TZwIssue selectTZwIssueById(Long id);

    /** 台账查询（列表页筛选：批次/点位/异常类/确认/归档） */
    List<TZwIssue> selectTZwIssueList(QueryWrapper<TZwIssue> queryWrapper);

    /** 下钻并排视图：原始行 / 规则条文 / 复核结论，页面不自算 */
    IssueView buildView(Long id);

    /**
     * 当面确认一条异常：确认情形 0→1（只钉一次，重复确认原样返回当前行）。
     * @return 更新后的行；行不存在或已删除返回 null
     */
    TZwIssue confirm(Long id, String who);

    /**
     * 整批确认：把该批所有待确认异常一并确认，返回本次新确认条数（已确认的不重写确认时刻）。
     */
    int confirmBatch(String batchNo, String who);

    /**
     * 整批一次核实并归档。
     * <ul>
     *   <li>批内无异常 → 0；批内已有任何归档条目（=已核实过）→ -1，整次动作拒收；</li>
     *   <li>核实不成（result=2）必须写原因，缺原因 → -2；</li>
     *   <li>存在未确认条目 → -3，先逐条/整批确认再核实；</li>
     *   <li>成功：同一核实时刻把批内每条异常的结论、原因、核实人钉死并归档，返回条数。</li>
     * </ul>
     *
     * @param result 1核实成 2核实不成
     */
    int verifyBatch(String batchNo, int result, String reason, String who);

    /**
     * 本期与上期差异：两个批次按同一行次口径逐行比原始行，指出哪几行对不上。
     */
    List<IssueDiff.RowDiff> diffBatches(String currentBatch, String previousBatch);

    /** 出现过的全部批次号（正式账与异常台账合并，按号降序，供下拉选择） */
    List<String> listBatchNos();
}
