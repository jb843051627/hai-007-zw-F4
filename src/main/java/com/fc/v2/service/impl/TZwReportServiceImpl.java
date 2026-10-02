package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.mapper.auto.TZwIssueMapper;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwIssue;
import com.fc.v2.model.auto.TZwLimRule;
import com.fc.v2.service.ITZwLimRuleService;
import com.fc.v2.service.ITZwReportService;
import com.fc.v2.service.zw.ImpExcelIO;
import com.fc.v2.service.zw.ImpLineParser;
import com.fc.v2.service.zw.IssueType;
import com.fc.v2.service.zw.WaterGrade;

/**
 * 水质检测明细报表 Service实现。
 *
 * <p>报表只照两张明细表当场拼：正式账 {@code t_zw_imp_row} 与异常台账 {@code t_zw_issue}
 * 按（批次,行次）并成一行，逐行落「原始行、校验结论、核实结论、核实人、核实时间、归档标识」。
 * 报表不落库、不缓存，页面或导出拿到的永远是明细当下口径。
 *
 * <p>确定性：行固定按批次号、原表行次升序；工作簿不写导出时刻、属性时间钉死、zip 条目时间钉死
 * （见 {@link ImpExcelIO}），同一筛选条件导出两次字节完全一致。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Service
public class TZwReportServiceImpl implements ITZwReportService {

    private static final String DAY_PATTERN = "yyyyMMdd";

    /** 明细列序：报表每一列都来自明细行，没有一列是报表自己算的 */
    private static final List<String> DETAIL_HEADERS = unmodifiable(
            "采样批次", "原表行次", "点位代号", "项目代号", "样本类别", "检测读数", "检测时间",
            "是否有效", "是否已复核", "评价等级", "定级规则代号", "规则生效日", "落地处",
            "原始行", "校验结论", "异常类", "核实结论", "核实原因/说明", "核实人", "核实时间", "归档标识");

    private static final List<String> SUMMARY_HEADERS = unmodifiable(
            "点位代号", "明细行数", "合格", "关注", "超标", "严重超标", "未定级", "异常行数", "已归档异常");

    @javax.annotation.Resource
    private TZwImpRowMapper zwImpRowMapper;
    @javax.annotation.Resource
    private TZwIssueMapper zwIssueMapper;
    @javax.annotation.Resource
    private ITZwLimRuleService zwLimRuleService;

    @Override
    public byte[] exportDetailReport(String siteNo, Integer validFlag, Integer reviewFlag, Date statDate) {
        String site = siteNo == null ? "" : siteNo.trim();

        QueryWrapper<TZwImpRow> qw1 = new QueryWrapper<TZwImpRow>().eq("del_flag", 0);
        if (!site.isEmpty()) {
            qw1.eq("site_no", site);
        }
        if (validFlag != null) {
            qw1.eq("valid_flag", validFlag.intValue());
        }
        if (reviewFlag != null) {
            qw1.eq("review_flag", reviewFlag.intValue());
        }
        List<TZwImpRow> landed = this.zwImpRowMapper.selectList(qw1);

        QueryWrapper<TZwIssue> qw2 = new QueryWrapper<TZwIssue>().eq("del_flag", 0);
        if (!site.isEmpty()) {
            qw2.eq("site_no", site);
        }
        if (validFlag != null) {
            qw2.eq("valid_flag", validFlag.intValue());
        }
        if (reviewFlag != null) {
            qw2.eq("review_flag", reviewFlag.intValue());
        }
        List<TZwIssue> issues = this.zwIssueMapper.selectList(qw2);

        List<ReportRow> rows = new ArrayList<ReportRow>();
        for (TZwImpRow r : landed) {
            rows.add(ReportRow.fromLanded(r));
        }
        for (TZwIssue s : issues) {
            // 异常行的规则生效日不另存：按命中代号 + 检测当天调条文带出，口径与核实并排页一致
            Date ruleEff = null;
            if (s.getRuleCodeHit() != null && s.getTestAt() != null) {
                TZwLimRule hit = this.zwLimRuleService.findForReview(s.getRuleCodeHit(), s.getTestAt());
                ruleEff = hit == null ? null : hit.getEffStart();
            }
            rows.add(ReportRow.fromIssue(s, ruleEff));
        }
        // 确定性排序：批次号升序，再按原表行次；同批同行只会落在一张表
        Collections.sort(rows, new Comparator<ReportRow>() {
            @Override
            public int compare(ReportRow a, ReportRow b) {
                int c = a.batchNo.compareTo(b.batchNo);
                if (c != 0) {
                    return c;
                }
                return a.rowNo - b.rowNo;
            }
        });

        List<List<String>> detailSheet = new ArrayList<List<String>>();
        for (ReportRow r : rows) {
            detailSheet.add(r.toCells());
        }
        List<List<String>> summarySheet = summarize(rows);
        // 两个工作表顺序固定：明细在前、汇总在后；不写导出时刻，同一筛选条件导出两次字节一致
        return ImpExcelIO.writeTwoSheets(DETAIL_HEADERS, detailSheet, "检测明细",
                SUMMARY_HEADERS, summarySheet, "点位汇总");
    }

    @Override
    public String buildFileName(String siteNo, Date statDate) {
        String site = siteNo == null || siteNo.trim().isEmpty() ? "ALL" : siteNo.trim();
        String day = statDate == null ? "" : new SimpleDateFormat(DAY_PATTERN).format(statDate);
        return "水质检测明细_" + site + "_" + day + ".xlsx";
    }

    /** 按点位汇总：等级计数与异常/归档计数，全部照同一批筛选后的明细行点算 */
    private List<List<String>> summarize(List<ReportRow> rows) {
        Map<String, int[]> agg = new TreeMap<String, int[]>();
        // 每点位 8 格：总数, 合格, 关注, 超标, 严重, 未定级, 异常, 已归档
        for (ReportRow r : rows) {
            int[] a = agg.get(r.siteNo);
            if (a == null) {
                a = new int[8];
                agg.put(r.siteNo, a);
            }
            a[0]++;
            if (r.gradeLevel == null) {
                a[5]++;
            } else {
                switch (r.gradeLevel.intValue()) {
                    case WaterGrade.NORMAL:
                        a[1]++;
                        break;
                    case WaterGrade.WATCH:
                        a[2]++;
                        break;
                    case WaterGrade.OVER:
                        a[3]++;
                        break;
                    case WaterGrade.SEVERE:
                        a[4]++;
                        break;
                    default:
                        a[5]++;
                }
            }
            if (r.fromIssue) {
                a[6]++;
                if (r.archived == 1) {
                    a[7]++;
                }
            }
        }
        List<List<String>> out = new ArrayList<List<String>>();
        for (Map.Entry<String, int[]> e : agg.entrySet()) {
            int[] a = e.getValue();
            List<String> line = new ArrayList<String>();
            line.add(e.getKey());
            for (int v : a) {
                line.add(String.valueOf(v));
            }
            out.add(line);
        }
        return out;
    }

    private static List<String> unmodifiable(String... s) {
        return Collections.unmodifiableList(java.util.Arrays.asList(s));
    }

    /** 一行统一视图：正式账行与异常台账行并成同一列序 */
    private static final class ReportRow {
        String batchNo;
        int rowNo;
        String siteNo;
        String itemCode;
        String sampleType;
        String qty;
        Date testAt;
        int validFlag;
        int reviewFlag;
        Integer gradeLevel;
        String ruleCode;
        Date ruleEffDate;
        String landed;
        String rawLine;
        String checkResult;
        String issueType;
        Integer verifyResult;
        String verifyReason;
        String verifyBy;
        Date verifyTime;
        int archived;
        boolean fromIssue;

        static ReportRow fromLanded(TZwImpRow r) {
            ReportRow x = new ReportRow();
            x.batchNo = nz(r.getBatchNo());
            x.rowNo = r.getRowNo() == null ? 0 : r.getRowNo().intValue();
            x.siteNo = nz(r.getSiteNo());
            x.itemCode = nz(r.getItemCode());
            x.sampleType = nz(r.getSampleType());
            x.qty = r.getQty() == null ? "" : r.getQty().toPlainString();
            x.testAt = r.getTestAt();
            x.validFlag = flag(r.getValidFlag());
            x.reviewFlag = flag(r.getReviewFlag());
            x.gradeLevel = r.getGradeLevel();
            x.ruleCode = nz(r.getRuleCode());
            x.ruleEffDate = r.getRuleEffDate();
            x.landed = "正式账";
            x.rawLine = nz(r.getRawLine());
            x.checkResult = nz(r.getImpIssueMsg());
            x.archived = 0;
            x.fromIssue = false;
            return x;
        }

        static ReportRow fromIssue(TZwIssue s, Date ruleEffDate) {
            ReportRow x = new ReportRow();
            x.batchNo = nz(s.getBatchNo());
            x.rowNo = s.getRowNo() == null ? 0 : s.getRowNo().intValue();
            x.siteNo = nz(s.getSiteNo());
            x.itemCode = nz(s.getItemCode());
            x.sampleType = nz(s.getSampleType());
            x.qty = s.getQty() == null ? "" : s.getQty().toPlainString();
            x.testAt = s.getTestAt();
            x.validFlag = flag(s.getValidFlag());
            x.reviewFlag = flag(s.getReviewFlag());
            x.gradeLevel = s.getGradeLevel();
            x.ruleCode = nz(s.getRuleCodeHit());
            x.ruleEffDate = ruleEffDate;
            x.landed = "异常台账";
            x.rawLine = nz(s.getRawLine());
            x.checkResult = nz(s.getCheckResult());
            x.issueType = s.getIssueType() == null ? "" : IssueType.text(s.getIssueType());
            x.verifyResult = s.getVerifyResult();
            x.verifyReason = nz(s.getVerifyReason());
            x.verifyBy = nz(s.getVerifyBy());
            x.verifyTime = s.getVerifyTime();
            x.archived = s.getArchived() == null ? 0 : s.getArchived().intValue();
            x.fromIssue = true;
            return x;
        }

        List<String> toCells() {
            List<String> c = new ArrayList<String>();
            c.add(batchNo);
            c.add(String.valueOf(rowNo));
            c.add(siteNo);
            c.add(itemCode);
            c.add(sampleType);
            c.add(qty);
            c.add(ImpLineParser.formatTime(testAt));
            c.add(yesNo(validFlag));
            c.add(yesNo(reviewFlag));
            c.add(gradeLevel == null ? "未定级" : WaterGrade.gradeText(gradeLevel.intValue()));
            c.add(ruleCode);
            c.add(ruleEffDate == null ? "" : ImpLineParser.formatTime(ruleEffDate));
            c.add(landed);
            c.add(rawLine);
            c.add(checkResult);
            c.add(issueType == null ? "" : issueType);
            c.add(verifyResult == null ? "未核实" : (verifyResult.intValue() == 1 ? "核实成" : "核实不成"));
            c.add(verifyReason == null ? "" : verifyReason);
            c.add(verifyBy == null ? "" : verifyBy);
            c.add(ImpLineParser.formatTime(verifyTime));
            c.add(archived == 1 ? "已归档" : "未归档");
            return c;
        }

        private static int flag(Integer v) {
            return v == null ? 0 : v.intValue();
        }

        private static String yesNo(int v) {
            return v == 1 ? "是" : "否";
        }

        private static String nz(String s) {
            return s == null ? "" : s;
        }
    }
}
