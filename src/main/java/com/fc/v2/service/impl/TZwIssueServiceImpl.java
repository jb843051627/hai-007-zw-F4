package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.mapper.auto.TZwIssueMapper;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwIssue;
import com.fc.v2.model.auto.TZwLimRule;
import com.fc.v2.service.ITZwIssueService;
import com.fc.v2.service.ITZwLimRuleService;
import com.fc.v2.service.zw.IssueDiff;
import com.fc.v2.service.zw.IssueView;

/**
 * 水质异常核实台账 Service业务层处理（verify-once 形状）。
 *
 * <p><b>核实只做一次</b>：{@link #verifyBatch} 先查批内是否已有归档条目——有就整次拒收，
 * 不存在「再核一次把上次结论盖掉」。真正写入时每条都走带 {@code archived=0} 条件的原子更新，
 * 并发两笔核实撞在一起，只有一笔能 0 行失败，另一笔整体回滚；核实结论、原因、核实人、
 * 核实时刻与归档标识在同一条 UPDATE 里钉死，之后整行只读。
 *
 * <p>查询永远只读：归档结果、核实结论、核实人、核实时间谁都能翻，但没有任何更新入口
 * 能改这四样——唯一的写口就是核实那一次（操作同时落系统日志，翻得出是哪一次写的）。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Service
public class TZwIssueServiceImpl implements ITZwIssueService {

    /** 核实结论 */
    private static final int VERIFY_PASS = 1;
    private static final int VERIFY_FAIL = 2;

    @javax.annotation.Resource
    private TZwIssueMapper zwIssueMapper;
    @javax.annotation.Resource
    private TZwImpRowMapper zwImpRowMapper;
    @javax.annotation.Resource
    private ITZwLimRuleService zwLimRuleService;

    @Override
    public TZwIssue selectTZwIssueById(Long id) {
        return this.zwIssueMapper.selectById(id);
    }

    @Override
    public List<TZwIssue> selectTZwIssueList(QueryWrapper<TZwIssue> queryWrapper) {
        return this.zwIssueMapper.selectList(queryWrapper);
    }

    @Override
    public IssueView buildView(Long id) {
        TZwIssue issue = this.zwIssueMapper.selectById(id);
        if (issue == null || (issue.getDelFlag() != null && issue.getDelFlag() != 0)) {
            return null;
        }
        IssueView view = new IssueView();
        view.setIssue(issue);
        // 规则条文两块并排：申报代号那条 + 裁决命中那条；查不到明写底档无此编号
        if (issue.getRuleCodeDecl() != null) {
            view.setDeclaredRule(this.zwLimRuleService.findForReview(
                    issue.getRuleCodeDecl(), issue.getTestAt()));
        }
        if (issue.getRuleCodeHit() != null) {
            view.setHitRule(this.zwLimRuleService.findForReview(
                    issue.getRuleCodeHit(), issue.getTestAt()));
        }
        List<TZwIssue> batch = listByBatch(issue.getBatchNo());
        int confirmed = 0;
        boolean archived = false;
        for (TZwIssue s : batch) {
            if (s.getConfirmStatus() != null && s.getConfirmStatus() == 1) {
                confirmed++;
            }
            if (s.getArchived() != null && s.getArchived() == 1) {
                archived = true;
            }
        }
        view.setBatchTotal(batch.size());
        view.setBatchConfirmed(confirmed);
        view.setBatchArchived(archived);
        return view;
    }

    @Override
    public TZwIssue confirm(Long id, String who) {
        TZwIssue issue = this.zwIssueMapper.selectById(id);
        if (issue == null || (issue.getDelFlag() != null && issue.getDelFlag() != 0)) {
            return null;
        }
        // 已确认/已归档原样返回当前行，不重写确认人与确认时刻
        if (issue.getConfirmStatus() != null && issue.getConfirmStatus() == 1) {
            return issue;
        }
        int rows = this.zwIssueMapper.update(null, new UpdateWrapper<TZwIssue>()
                .set("confirm_status", 1)
                .set("confirm_by", who)
                .set("confirm_time", new Date())
                .eq("id", id)
                .eq("confirm_status", 0)
                .eq("archived", 0)
                .eq("del_flag", 0));
        return rows > 0 ? this.zwIssueMapper.selectById(id) : issue;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmBatch(String batchNo, String who) {
        Date now = new Date();
        // 只确认未确认且未归档的；已归档的绝不回写（四样只认核实那一次）
        return this.zwIssueMapper.update(null, new UpdateWrapper<TZwIssue>()
                .set("confirm_status", 1)
                .set("confirm_by", who)
                .set("confirm_time", now)
                .eq("batch_no", batchNo)
                .eq("confirm_status", 0)
                .eq("archived", 0)
                .eq("del_flag", 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int verifyBatch(String batchNo, int result, String reason, String who) {
        if (result != VERIFY_PASS && result != VERIFY_FAIL) {
            return -1;
        }
        if (result == VERIFY_FAIL && (reason == null || reason.trim().isEmpty())) {
            // 核实不成必须写原因
            return -2;
        }
        List<TZwIssue> batch = listByBatch(batchNo);
        if (batch.isEmpty()) {
            return 0;
        }
        boolean archived = false;
        boolean pending = false;
        boolean unconfirmed = false;
        for (TZwIssue s : batch) {
            boolean isArchived = s.getArchived() != null && s.getArchived() == 1;
            archived |= isArchived;
            if (!isArchived) {
                pending = true;
                if (s.getConfirmStatus() == null || s.getConfirmStatus() != 1) {
                    unconfirmed = true;
                }
            }
        }
        if (archived) {
            if (!pending) {
                // 整批早已核实归档：幂等返回，不允许第二次核实盖写
                return 0;
            }
            // 已归档之外又冒出未归档行（如同批号补传了新行）：不许混在一起二次核实
            return -1;
        }
        if (unconfirmed) {
            // 先当面确认，再核实
            return -3;
        }
        // 整批同一核实时刻——一次核实就是一次，系统日志里翻得出这一次
        Date verifyTime = new Date();
        for (TZwIssue s : batch) {
            int rows = this.zwIssueMapper.update(null, new UpdateWrapper<TZwIssue>()
                    .set("verify_result", result)
                    .set("verify_reason", reason)
                    .set("verify_by", who)
                    .set("verify_time", verifyTime)
                    .set("archived", 1)
                    .set("status", 1)
                    .eq("id", s.getId())
                    .eq("archived", 0)
                    .eq("del_flag", 0));
            if (rows != 1) {
                // 并发下被别的核实抢先：整次回滚，不允许一批两种结论
                throw new IllegalStateException("批次 " + batchNo + " 核实时发生并发冲突，请刷新重查");
            }
        }
        return batch.size();
    }

    @Override
    public List<IssueDiff.RowDiff> diffBatches(String currentBatch, String previousBatch) {
        List<IssueDiff.Snapshot> cur = snapshots(currentBatch);
        List<IssueDiff.Snapshot> prev = snapshots(previousBatch);
        return IssueDiff.compare(cur, prev);
    }

    @Override
    public List<String> listBatchNos() {
        Map<String, Void> nos = new LinkedHashMap<String, Void>();
        for (TZwImpRow r : this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .select("batch_no").eq("del_flag", 0).groupBy("batch_no"))) {
            if (r.getBatchNo() != null) {
                nos.put(r.getBatchNo(), null);
            }
        }
        for (TZwIssue s : this.zwIssueMapper.selectList(new QueryWrapper<TZwIssue>()
                .select("batch_no").eq("del_flag", 0).groupBy("batch_no"))) {
            if (s.getBatchNo() != null) {
                nos.put(s.getBatchNo(), null);
            }
        }
        List<String> list = new ArrayList<String>(nos.keySet());
        Collections.sort(list, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.compareTo(a);
            }
        });
        return list;
    }

    private List<TZwIssue> listByBatch(String batchNo) {
        return this.zwIssueMapper.selectList(new QueryWrapper<TZwIssue>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    /**
     * 一批的逐行原始行快照：正式账与异常台账按（批次,行次）合并——
     * 一行要么落账要么挂异常，两表不重行；比对只认明细里这条原始行。
     */
    private List<IssueDiff.Snapshot> snapshots(String batchNo) {
        Map<Integer, String> byRow = new LinkedHashMap<Integer, String>();
        for (TZwImpRow r : this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .eq("batch_no", batchNo).eq("del_flag", 0))) {
            byRow.put(r.getRowNo(), r.getRawLine());
        }
        for (TZwIssue s : listByBatch(batchNo)) {
            if (!byRow.containsKey(s.getRowNo())) {
                byRow.put(s.getRowNo(), s.getRawLine());
            }
        }
        List<IssueDiff.Snapshot> list = new ArrayList<IssueDiff.Snapshot>();
        for (Map.Entry<Integer, String> e : byRow.entrySet()) {
            list.add(new IssueDiff.Snapshot(e.getKey().intValue(), e.getValue()));
        }
        return list;
    }
}
