package com.fc.v2.controller.admin;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TZwIssue;
import com.fc.v2.service.ITZwIssueService;
import com.fc.v2.service.zw.IssueDiff;
import com.fc.v2.service.zw.IssueView;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 水质异常核实台账 Controller（异常是长期事项，不止当月一张表）。
 *
 * <p>每条异常可下钻看并排视图（原始行 / 规则条文 / 复核结论）；核实以批次为单位只做一次，
 * 核实后整批归档只读。归档结果、核实结论、核实人、核实时间任何登录用户都可只读查询，
 * 页面没有任何改写这四样的入口。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Api(value = "水质异常核实台账")
@Controller
@RequestMapping("/zwIssue")
public class ZwIssueController extends BaseController {

    private final String prefix = "admin/zwIssue";

    @Autowired
    private ITZwIssueService zwIssueService;

    @ApiOperation(value = "异常台账跳转", notes = "异常台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("zw:zwIssue:view")
    public String view(ModelMap model) {
        model.put("batchNos", zwIssueService.listBatchNos());
        return prefix + "/list";
    }

    @Log(title = "水质异常台账查询", action = "list")
    @ApiOperation(value = "异常列表", notes = "可按批次/点位/异常类/确认/归档筛选；归档行照常可翻、只读")
    @GetMapping("/list")
    @RequiresPermissions("zw:zwIssue:list")
    @ResponseBody
    public ResultTable list(TZwIssue record) {
        QueryWrapper<TZwIssue> qw = new QueryWrapper<TZwIssue>();
        qw.like(StringUtils.isNotEmpty(record.getBatchNo()), "batch_no", record.getBatchNo());
        qw.like(StringUtils.isNotEmpty(record.getSiteNo()), "site_no", record.getSiteNo());
        qw.eq(StringUtils.isNotEmpty(record.getIssueType()), "issue_type", record.getIssueType());
        qw.eq(StringUtils.isNotNull(record.getConfirmStatus()), "confirm_status", record.getConfirmStatus());
        qw.eq(StringUtils.isNotNull(record.getArchived()), "archived", record.getArchived());
        qw.orderByDesc("batch_no").orderByAsc("row_no");
        startPage();
        PageInfo<TZwIssue> page = new PageInfo<TZwIssue>(zwIssueService.selectTZwIssueList(qw));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "异常下钻页跳转", notes = "原始行/规则条文/复核结论并排，时间页面接口同一尺")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("zw:zwIssue:view")
    public String detail(@PathVariable("id") Long id, ModelMap model) {
        model.put("view", zwIssueService.buildView(id));
        return prefix + "/detail";
    }

    @Log(title = "水质异常并排数据", action = "viewData")
    @ApiOperation(value = "并排视图数据", notes = "页面只取回这一份，不自算规则与结论")
    @GetMapping("/viewData/{id}")
    @RequiresPermissions("zw:zwIssue:view")
    @ResponseBody
    public AjaxResult viewData(@PathVariable("id") Long id) {
        IssueView view = zwIssueService.buildView(id);
        return view == null ? AjaxResult.error("异常不存在或已删除") : AjaxResult.successData(200, view);
    }

    @Log(title = "水质异常当面确认", action = "confirm")
    @ApiOperation(value = "确认一条", notes = "异常先入册，再由人当面确认；只确认一次")
    @PostMapping("/confirm")
    @RequiresPermissions("zw:zwIssue:confirm")
    @ResponseBody
    public AjaxResult confirm(Long id) {
        return zwIssueService.confirm(id, ShiroUtils.getLoginName()) == null
                ? AjaxResult.error("异常不存在或已删除")
                : AjaxResult.success();
    }

    @Log(title = "水质异常整批确认", action = "confirmBatch")
    @ApiOperation(value = "整批确认", notes = "把该批待确认异常一并确认；已归档行不动")
    @PostMapping("/confirmBatch")
    @RequiresPermissions("zw:zwIssue:confirm")
    @ResponseBody
    public AjaxResult confirmBatch(String batchNo) {
        if (StringUtils.isEmpty(batchNo)) {
            return AjaxResult.error("请先指定批次号");
        }
        int n = zwIssueService.confirmBatch(batchNo, ShiroUtils.getLoginName());
        return AjaxResult.successData(200, Integer.valueOf(n));
    }

    @Log(title = "水质异常整批核实归档", action = "verify")
    @ApiOperation(value = "整批一次核实", notes = "核实成/核实不成当场写明；不成必填原因；整批只准核实一次")
    @PostMapping("/verify")
    @RequiresPermissions("zw:zwIssue:verify")
    @ResponseBody
    public AjaxResult verify(String batchNo, Integer result, String reason) {
        if (StringUtils.isEmpty(batchNo) || result == null) {
            return AjaxResult.error("批次号与核实结论缺一不可");
        }
        int n = zwIssueService.verifyBatch(batchNo, result.intValue(), reason, ShiroUtils.getLoginName());
        if (n == -2) {
            return AjaxResult.error("核实不成必须写明原因");
        }
        if (n == -3) {
            return AjaxResult.error("批内还有未当面确认的异常，请先逐条或整批确认");
        }
        if (n == -1) {
            return AjaxResult.error("该批已核实例行归档，或已有部分归档，不得二次核实");
        }
        if (n == 0) {
            return AjaxResult.error("该批次没有待核实异常（已归档或不存在）");
        }
        return AjaxResult.successData(200, Integer.valueOf(n));
    }

    @Log(title = "批次原始行差异比对", action = "diff")
    @ApiOperation(value = "本期与上期差异", notes = "按同一行次口径比原始行，指出哪几行对不上")
    @GetMapping("/diff")
    @RequiresPermissions("zw:zwIssue:view")
    @ResponseBody
    public AjaxResult diff(String currentBatch, String previousBatch) {
        if (StringUtils.isEmpty(currentBatch) || StringUtils.isEmpty(previousBatch)) {
            return AjaxResult.error("请选齐本期与上期批次");
        }
        if (currentBatch.equals(previousBatch)) {
            return AjaxResult.error("本期与上期不能是同一个批次");
        }
        List<IssueDiff.RowDiff> diffs = zwIssueService.diffBatches(currentBatch, previousBatch);
        return AjaxResult.successData(200, diffs);
    }
}
