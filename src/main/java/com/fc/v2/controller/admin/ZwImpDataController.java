package com.fc.v2.controller.admin;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletResponse;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.service.ITZwImpRowService;
import com.fc.v2.service.ITZwReportService;
import com.fc.v2.service.zw.ImpExcelIO;
import com.fc.v2.service.zw.ImportOutcome;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.io.IOUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 化验读数 Excel 导入与检测明细 Controller。
 *
 * <p>上传先走服务层两层校验：结构硬错整批拦回（一笔不落）、内容软错逐条进异常台账，
 * 只有格式合格的行落正式账。本控制器不判任何读数，只把回值原样交代给前台。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Api(value = "化验读数导入与检测明细")
@Controller
@RequestMapping("/zwImpData")
public class ZwImpDataController extends BaseController {

    private final String prefix = "admin/zwImpData";

    @Autowired
    private ITZwImpRowService zwImpRowService;
    @Autowired
    private ITZwReportService zwReportService;

    @ApiOperation(value = "导入与明细台账跳转", notes = "导入与明细台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("zw:zwImpData:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "检测明细台账查询", action = "list")
    @ApiOperation(value = "正式账查询", notes = "只放格式合格并已落账的行；可按批次/点位/有效/复核筛")
    @GetMapping("/list")
    @RequiresPermissions("zw:zwImpData:list")
    @ResponseBody
    public ResultTable list(TZwImpRow record) {
        QueryWrapper<TZwImpRow> qw = new QueryWrapper<TZwImpRow>();
        qw.like(StringUtils.isNotEmpty(record.getBatchNo()), "batch_no", record.getBatchNo());
        qw.like(StringUtils.isNotEmpty(record.getSiteNo()), "site_no", record.getSiteNo());
        qw.eq(StringUtils.isNotNull(record.getValidFlag()), "valid_flag", record.getValidFlag());
        qw.eq(StringUtils.isNotNull(record.getReviewFlag()), "review_flag", record.getReviewFlag());
        qw.orderByDesc("batch_no").orderByAsc("row_no");
        startPage();
        PageInfo<TZwImpRow> page = new PageInfo<TZwImpRow>(zwImpRowService.selectTZwImpRowList(qw));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "化验读数Excel导入", action = "import")
    @ApiOperation(value = "整文件导入", notes = "结构硬错整批拦下一笔不落；软错逐行进异常台账；同批同次幂等")
    @PostMapping("/upload")
    @RequiresPermissions("zw:zwImpData:import")
    @ResponseBody
    public AjaxResult upload(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "batchNo", required = false) String batchNo) {
        if (file == null || file.isEmpty()) {
            return AjaxResult.error("没有收到文件，请先选择旧系统导出的当月 xlsx");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx")) {
            // 本机不装 Office，只认 xlsx；旧 xls 请另存为 xlsx 再传
            return AjaxResult.error("只收 .xlsx 文件，请把旧表另存为 xlsx 后重传");
        }
        try {
            ImportOutcome outcome = zwImpRowService.importExcel(
                    file.getBytes(), batchNo, ShiroUtils.getLoginName());
            if (outcome.isRejected()) {
                // 整批拦下：把逐行原因带回前台当场处理，绝不悄悄塞库
                return AjaxResult.error("整批未通过校验，一笔未落库").put("data", outcome);
            }
            return AjaxResult.successData(200, outcome);
        } catch (IOException e) {
            return AjaxResult.error("文件读取失败：" + e.getMessage());
        }
    }

    @ApiOperation(value = "导入模板下载", notes = "七列表头 + 一行填法示范，UTF-8 xlsx")
    @GetMapping("/template")
    @RequiresPermissions("zw:zwImpData:import")
    public void template(HttpServletResponse response) throws IOException {
        byte[] bytes = ImpExcelIO.writeTemplate();
        String fileName = URLEncoder.encode("化验读数导入模板.xlsx", "UTF-8").replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + fileName);
        IOUtils.write(bytes, response.getOutputStream());
    }

    @Log(title = "检测明细报表导出", action = "export")
    @ApiOperation(value = "明细报表导出", notes = "照两表明细生成；同筛选条件导出两次字节一致")
    @GetMapping("/report")
    @RequiresPermissions("zw:zwImpData:export")
    public void report(@RequestParam(value = "siteNo", required = false) String siteNo,
                       @RequestParam(value = "validFlag", required = false) Integer validFlag,
                       @RequestParam(value = "reviewFlag", required = false) Integer reviewFlag,
                       @RequestParam(value = "statDate", required = false) String statDate,
                       HttpServletResponse response) throws IOException {
        Date day = parseDay(statDate);
        byte[] bytes = zwReportService.exportDetailReport(siteNo, validFlag, reviewFlag, day);
        String fileName = zwReportService.buildFileName(siteNo, day);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"));
        IOUtils.write(bytes, response.getOutputStream());
    }

    /** 统计日期只认 yyyy-MM-dd；空着就用今天，口径与检测时间尺分开 */
    private static Date parseDay(String text) {
        if (StringUtils.isEmpty(text)) {
            return new Date();
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setLenient(false);
            return sdf.parse(text.trim());
        } catch (Exception e) {
            return new Date();
        }
    }
}
