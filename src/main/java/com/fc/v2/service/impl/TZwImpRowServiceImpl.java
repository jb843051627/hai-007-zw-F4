package com.fc.v2.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.mapper.auto.TZwIssueMapper;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwIssue;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.service.ITZwImpRowService;
import com.fc.v2.service.ITZwLimRuleService;
import com.fc.v2.service.zw.ImpExcelIO;
import com.fc.v2.service.zw.ImpLine;
import com.fc.v2.service.zw.ImpLineParser;
import com.fc.v2.service.zw.ImportOutcome;
import com.fc.v2.service.zw.IssueType;
import com.fc.v2.service.zw.WaterGrade;
import com.fc.v2.service.zw.WaterVerdict;

/**
 * 检测数据导入核对行 Service业务层处理（excel-import 形状：整文件导入，正式账只读）。
 *
 * <p><b>两层校验，处置分开：</b>
 * <ul>
 *   <li>硬错（结构/口径对不上）：点位代号不在底档（含已退役）、检测时间认不出或跨出批次当月、
 *       读数不是数字、必填缺失、一张表里出现两个批次号——解析与核对阶段整批拦下，
 *       <b>一笔不落</b>，回值逐行写明第几行什么原因；</li>
 *   <li>软错（结构合格但内容异常）：读数越界（裁决 INVALID）、检测当天无可用标准（NO_RULE）、
 *       申报规则代号与裁决出口按当天选中的线对不上（RULE_MISMATCH）、自报「是否有效=否」
 *       （INVALID_FLAG）——逐行登记进 t_zw_issue 让人当场处理，不进正式账；</li>
 *   <li>其余行才落 t_zw_imp_row，等级/规则代号/规则生效日一律取 {@link WaterVerdict} 回值钉死。</li>
 * </ul>
 *
 * <p><b>幂等只认（批次号, 原表行次）</b>：同批重新导入，已落正式账的行与已登记的异常
 * （含已核实、已归档）整行跳过，绝不把已核实的几条翻出来重复核。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Service
public class TZwImpRowServiceImpl implements ITZwImpRowService {

    private static final int STATUS_OK = 1;
    private static final Pattern BATCH_MONTH = Pattern.compile("^[A-Za-z]?(\\d{6})$");

    @javax.annotation.Resource
    private TZwImpRowMapper zwImpRowMapper;
    @javax.annotation.Resource
    private TZwIssueMapper zwIssueMapper;
    @javax.annotation.Resource
    private TZwSourceMapper zwSourceMapper;
    @javax.annotation.Resource
    private ITZwLimRuleService zwLimRuleService;

    @Override
    public TZwImpRow selectTZwImpRowById(Long id) {
        return this.zwImpRowMapper.selectById(id);
    }

    @Override
    public List<TZwImpRow> selectTZwImpRowList(QueryWrapper<TZwImpRow> queryWrapper) {
        return this.zwImpRowMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportOutcome importExcel(byte[] content, String declaredBatch, String operator) {
        List<String[]> rawRows;
        try {
            rawRows = ImpExcelIO.readRows(new ByteArrayInputStream(content));
        } catch (IOException e) {
            // 文件本身打不开/不是有效 Excel：整批挡回，一笔不落
            return ImportOutcome.rejected(declaredBatch, 0,
                    java.util.Collections.singletonList(
                            new ImpLineParser.LineError(0, "文件读不开：" + e.getMessage())));
        }
        ImpLineParser.ParseOutcome parsed = ImpLineParser.parse(rawRows, declaredBatch);
        if (parsed.isRejected()) {
            // 结构硬错：整批拦下，不允许好行先入库
            return ImportOutcome.rejected(declaredBatch, rawRows.size(), parsed.getErrors());
        }
        List<ImpLine> lines = parsed.getLines();
        if (lines.isEmpty()) {
            return ImportOutcome.ok(declaredBatch, 0, 0, 0, 0);
        }
        String batchNo = lines.get(0).getBatchNo();

        // ---- 硬错第二轮：点位必须在底档（只认代号，已退役也不收）；时间必须同属批次当月 ----
        List<ImpLineParser.LineError> hardErrors = new ArrayList<ImpLineParser.LineError>();
        hardErrors.addAll(checkSites(lines));
        List<ImpLineParser.LineError> monthErrors = checkMonthScope(batchNo, lines);
        hardErrors.addAll(monthErrors);
        if (!hardErrors.isEmpty()) {
            return ImportOutcome.rejected(batchNo, lines.size(), hardErrors);
        }

        // ---- 幂等：（批次号,行次）已在正式账或异常台账的，整行跳过 ----
        Set<Integer> landedRows = new HashSet<Integer>();
        for (TZwImpRow r : this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .select("row_no").eq("batch_no", batchNo).eq("del_flag", 0))) {
            landedRows.add(r.getRowNo());
        }
        Set<Integer> issuedRows = new HashSet<Integer>();
        boolean batchSealed = false;
        for (TZwIssue s : this.zwIssueMapper.selectList(new QueryWrapper<TZwIssue>()
                .select("row_no", "archived").eq("batch_no", batchNo).eq("del_flag", 0))) {
            issuedRows.add(s.getRowNo());
            if (s.getArchived() != null && s.getArchived() == 1) {
                batchSealed = true;
            }
        }

        if (batchSealed) {
            // 已核实归档的批次：原样重导全部跳过（绝不翻出已核实条目重复核）；
            // 但凡夹带任何新行次，整批拦下——封批后不能再追加，也不许半批只读半批新账
            List<ImpLineParser.LineError> sealedErrors = new ArrayList<ImpLineParser.LineError>();
            int skippedSealed = 0;
            for (ImpLine line : lines) {
                if (landedRows.contains(line.getRowNo()) || issuedRows.contains(line.getRowNo())) {
                    skippedSealed++;
                } else {
                    sealedErrors.add(new ImpLineParser.LineError(line.getRowNo(),
                            "批次「" + batchNo + "」已核实归档（只读封批），第 " + line.getRowNo()
                                    + " 行是封批后的新行，不能再追加；已核实条目原样保留"));
                }
            }
            if (!sealedErrors.isEmpty()) {
                return ImportOutcome.rejected(batchNo, lines.size(), sealedErrors);
            }
            return ImportOutcome.ok(batchNo, lines.size(), 0, 0, skippedSealed);
        }

        int landed = 0;
        int issues = 0;
        int skipped = 0;
        for (ImpLine line : lines) {
            if (landedRows.contains(line.getRowNo()) || issuedRows.contains(line.getRowNo())) {
                // 已核实/已归档的异常也走这里：原样锁住，不翻出来重复核
                skipped++;
                continue;
            }
            if (registerOrLand(line, batchNo, operator)) {
                landed++;
            } else {
                issues++;
            }
        }
        return ImportOutcome.ok(batchNo, lines.size(), landed, issues, skipped);
    }

    /** 点位代号必须查得到未删除底档；已退役点位同样硬挡（退役点不再收新数据） */
    private List<ImpLineParser.LineError> checkSites(List<ImpLine> lines) {
        List<TZwSource> all = this.zwSourceMapper.selectList(new QueryWrapper<TZwSource>()
                .select("site_no", "status").eq("del_flag", 0));
        Set<String> active = new HashSet<String>();
        Set<String> retired = new HashSet<String>();
        for (TZwSource s : all) {
            if (s.getStatus() != null && s.getStatus() == 1) {
                retired.add(s.getSiteNo());
            } else {
                active.add(s.getSiteNo());
            }
        }
        List<ImpLineParser.LineError> errors = new ArrayList<ImpLineParser.LineError>();
        Set<String> done = new HashSet<String>();
        for (ImpLine line : lines) {
            String site = line.getSiteNo();
            if (active.contains(site) || !done.add(site)) {
                continue;
            }
            if (retired.contains(site)) {
                errors.add(new ImpLineParser.LineError(line.getRowNo(),
                        "地点对不上：点位「" + site + "」已退役，不再收新数据"));
            } else {
                errors.add(new ImpLineParser.LineError(line.getRowNo(),
                        "地点对不上：点位代号「" + site + "」不在底档，先建档再导入"));
            }
        }
        return errors;
    }

    /**
     * 采样时间口径：批次号形如 B202610 时当月就是 2026-10；否则以第一行检测时间所在月立口径。
     * 同一批里任何一行跨到别的月，整批拦下（当月数据混进别月读数，以后分析全跟着错）。
     */
    private List<ImpLineParser.LineError> checkMonthScope(String batchNo, List<ImpLine> lines) {
        Integer expectYear = null;
        Integer expectMonth = null;
        Matcher m = BATCH_MONTH.matcher(batchNo == null ? "" : batchNo);
        if (m.matches()) {
            String ym = m.group(1);
            expectYear = Integer.valueOf(ym.substring(0, 4));
            expectMonth = Integer.valueOf(ym.substring(4, 6));
        }
        List<ImpLineParser.LineError> errors = new ArrayList<ImpLineParser.LineError>();
        String expectText = null;
        for (ImpLine line : lines) {
            Calendar c = Calendar.getInstance();
            c.setTime(line.getTestAt());
            int y = c.get(Calendar.YEAR);
            int mo = c.get(Calendar.MONTH) + 1;
            if (expectYear == null) {
                expectYear = Integer.valueOf(y);
                expectMonth = Integer.valueOf(mo);
            }
            expectText = expectYear + "-" + String.format("%02d", expectMonth);
            if (y != expectYear.intValue() || mo != expectMonth.intValue()) {
                errors.add(new ImpLineParser.LineError(line.getRowNo(),
                        "采样时间对不上：本行检测时间 " + ImpLineParser.formatTime(line.getTestAt())
                                + " 不在批次当月 " + expectText));
            }
        }
        return errors;
    }

    /**
     * 逐行裁决：true=落正式账；false=登记异常。定级只过唯一评价出口，
     * 等级、规则代号、生效日全取回值，本服务不自判、不拼装。
     */
    private boolean registerOrLand(ImpLine line, String batchNo, String operator) {
        WaterVerdict verdict = this.zwLimRuleService.evaluate(
                line.getQty(), line.getTestAt(), line.getSampleType());
        String declaredRule = line.getRuleCodeDecl();

        if (verdict.getKind() == WaterVerdict.Kind.INVALID) {
            insertIssue(line, batchNo, operator, IssueType.INVALID, verdict,
                    "校验不通过：" + verdict.getMessage(), declaredRule);
            return false;
        }
        if (verdict.getKind() == WaterVerdict.Kind.NO_RULE) {
            insertIssue(line, batchNo, operator, IssueType.NO_RULE, verdict,
                    "校验不通过：" + verdict.getMessage(), declaredRule);
            return false;
        }
        // GRADED：库里按检测当天选中的线代号必须与申报的对得上
        if (declaredRule != null && !declaredRule.equals(verdict.getRuleCode())) {
            insertIssue(line, batchNo, operator, IssueType.RULE_MISMATCH, verdict,
                    "校验不通过：申报规则代号「" + declaredRule + "」与检测当天("
                            + ImpLineParser.formatTime(line.getTestAt()) + ")该样本类别裁决线「"
                            + verdict.getRuleCode() + "」对不上", declaredRule);
            return false;
        }
        if (line.getValidFlag() == 0) {
            insertIssue(line, batchNo, operator, IssueType.INVALID_FLAG, verdict,
                    "校验不通过：原始行自报「是否有效=否」，读数挂起待核实，不入正式账", declaredRule);
            return false;
        }
        TZwImpRow row = new TZwImpRow();
        row.setBatchNo(batchNo);
        row.setRowNo(Integer.valueOf(line.getRowNo()));
        row.setSiteNo(line.getSiteNo());
        row.setItemCode(line.getItemCode());
        row.setQty(line.getQty());
        row.setSampleType(line.getSampleType());
        row.setTestAt(line.getTestAt());
        row.setValidFlag(Integer.valueOf(line.getValidFlag()));
        row.setReviewFlag(Integer.valueOf(line.getReviewFlag()));
        // 等级连同所依标准代号、生效日一并钉进结果，写过不再改
        row.setGradeLevel(Integer.valueOf(verdict.getGrade()));
        row.setRuleCode(verdict.getRuleCode());
        row.setRuleEffDate(verdict.getRuleEffDate());
        row.setRawLine(line.getRawLine());
        row.setImpIssueMsg("校验通过：" + WaterGrade.gradeText(verdict.getGrade())
                + "（依 " + verdict.getRuleCode() + "）");
        row.setStatus(Integer.valueOf(STATUS_OK));
        row.setDelFlag(0);
        row.setCreateBy(operator);
        row.setCreateTime(new Date());
        this.zwImpRowMapper.insert(row);
        return true;
    }

    /** 登记一条异常：生成、确认、核实、归档之后全在这张台账上走，这里只管「生成」一笔 */
    private void insertIssue(ImpLine line, String batchNo, String operator, String type,
                             WaterVerdict verdict, String checkResult, String declaredRule) {
        TZwIssue issue = new TZwIssue();
        issue.setIssueNo(batchNo + "-" + line.getRowNo());
        issue.setBatchNo(batchNo);
        issue.setRowNo(Integer.valueOf(line.getRowNo()));
        issue.setSiteNo(line.getSiteNo());
        issue.setItemCode(line.getItemCode());
        issue.setQty(line.getQty());
        issue.setSampleType(line.getSampleType());
        issue.setTestAt(line.getTestAt());
        issue.setValidFlag(Integer.valueOf(line.getValidFlag()));
        issue.setReviewFlag(Integer.valueOf(line.getReviewFlag()));
        issue.setRuleCodeDecl(declaredRule);
        issue.setRuleCodeHit(verdict.isGraded() ? verdict.getRuleCode() : null);
        issue.setGradeLevel(verdict.isGraded() ? Integer.valueOf(verdict.getGrade()) : null);
        issue.setRawLine(line.getRawLine());
        issue.setCheckResult(checkResult);
        issue.setIssueType(type);
        issue.setConfirmStatus(0);
        issue.setArchived(0);
        issue.setStatus(0);
        issue.setDelFlag(0);
        issue.setCreateBy(operator);
        issue.setCreateTime(new Date());
        this.zwIssueMapper.insert(issue);
    }
}
