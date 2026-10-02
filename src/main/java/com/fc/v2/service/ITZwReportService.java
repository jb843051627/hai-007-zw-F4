package com.fc.v2.service;

import java.util.Date;

/**
 * 水质检测明细报表 Service接口。
 *
 * <p>报表不另存数据：每次都照着明细表（正式账 t_zw_imp_row ＋ 异常台账 t_zw_issue）当场生成，
 * 原始行、校验结论、核实结论、核实人、核实时间、归档标识逐项落到一行。报表改了不算数，
 * 明细表里那条原始行才是准。筛选项：点位代号、是否有效、是否已复核。
 *
 * <p>导出的 xlsx 为 UTF-8、文件名带点位代号与统计日期；内容不含任何随导出时刻变化的东西，
 * 同一筛选条件导出两次字节完全一致（本机不装 Office，文件交业务人员下载后自行打开核对）。
 *
 * @author fuce
 * @date 2026-10-02
 */
public interface ITZwReportService {

    /**
     * 生成明细报表 xlsx（明细 + 按点位×等级汇总两个工作表，全部由同一份筛选结果算出）。
     *
     * @param siteNo     点位代号（空＝全部点位）
     * @param validFlag  是否有效 0/1（null＝不限）
     * @param reviewFlag 是否已复核 0/1（null＝不限）
     * @param statDate   统计日期（进文件名与表内说明，不写导出时刻）
     */
    byte[] exportDetailReport(String siteNo, Integer validFlag, Integer reviewFlag, Date statDate);

    /** 文件名：水质检测明细_点位代号_统计日期.xlsx（无点位筛选时用 ALL） */
    String buildFileName(String siteNo, Date statDate);
}
