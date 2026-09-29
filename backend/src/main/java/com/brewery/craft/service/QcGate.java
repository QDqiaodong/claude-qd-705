package com.brewery.craft.service;

import org.springframework.stereotype.Component;

import com.brewery.craft.domain.Batch;
import com.brewery.craft.domain.QcRound;

/**
 * 质检放行对下游（灌装/出货/批次推进）的【唯一】裁决入口。
 *
 * <p>所有拦截都在这里给出稳定的业务原因，控制器与页面不得各写一套判断，
 * 避免出现「页面显示已放行、后端仍放行下游」的口径漂移。
 */
@Component
public final class QcGate {

    private QcGate() {
    }

    /** 批次的当前有效质检态（以最新一轮为准；历史轮次不影响这个结果，只用于追溯） */
    public enum QcState {
        NONE,        // 从未送检
        OPEN,        // 首轮送检中（待质检）
        RECHECK,     // 第 N(>1) 轮送检中（复检中）
        PASSED,      // 最新一轮明确放行
        REJECTED,    // 最新一轮驳回
        REVOKED      // 放行被撤销（旧放行轮保留为 REVOKED，不删除）
    }

    public static QcState stateOf(QcRound latest) {
        if (latest == null) {
            return QcState.NONE;
        }
        return switch (latest.getResult()) {
            case "OPEN" -> latest.getRoundNo() != null && latest.getRoundNo() > 1 ? QcState.RECHECK : QcState.OPEN;
            case "PASSED" -> QcState.PASSED;
            case "REJECTED" -> QcState.REJECTED;
            case "REVOKED" -> QcState.REVOKED;
            default -> QcState.NONE;
        };
    }

    /** 可成熟状态：只有熟成中 / 已灌装的批次才谈得上「放行投产」 */
    public static boolean mature(Batch b) {
        return "CONDITION".equals(b.getStatus()) || "PACKAGED".equals(b.getStatus());
    }

    public static boolean hasNecessaryReadings(Batch b) {
        return b.getTempReadings() != null && !b.getTempReadings().isEmpty()
                && b.getGravityReadings() != null && !b.getGravityReadings().isEmpty();
    }

    /**
     * 灌装准入：返回 null 表示放行，否则返回要给操作人员看的业务原因。
     * 条件：批次未报废 + 可成熟状态 + 有必要测量记录 + 最新一轮明确放行。
     */
    public static String bottlingBlockReason(Batch b, QcRound latest) {
        if ("SCRAPPED".equals(b.getStatus())) {
            return "批次已报废，不能新建灌装单";
        }
        if (!mature(b)) {
            return "批次当前为「" + com.brewery.craft.dto.Views.batchStatusName(b.getStatus())
                    + "」，未进入可成熟（熟成）状态，不能灌装";
        }
        String qc = qcBlockReason(latest);
        if (qc != null) {
            return qc;
        }
        if (!hasNecessaryReadings(b)) {
            return "批次缺少必要的逐日温度/糖度测量记录，不能灌装";
        }
        return null;
    }

    /**
     * 出货准入（批次维度；灌装记录匹配在服务层另查）。
     * 出货必须再次确认放行结论【仍】有效。
     */
    public static String shipmentBlockReason(Batch b, QcRound latest) {
        if ("SCRAPPED".equals(b.getStatus())) {
            return "批次已报废，不能新建出货单";
        }
        return qcBlockReason(latest);
    }

    /** 纯质检维度的拦截原因（推进到已灌装、新建灌装/出货共用） */
    public static String qcBlockReason(QcRound latest) {
        QcState state = stateOf(latest);
        return switch (state) {
            case NONE -> "批次还没有质检记录，须发起质检并明确放行后才能继续";
            case OPEN -> "批次正在质检中（第 " + latest.getRoundNo() + " 轮送检），尚未放行";
            case RECHECK -> "批次处于复检中（第 " + latest.getRoundNo()
                    + " 轮），原放行结论已挂起，暂不能操作下游";
            case REJECTED -> "批次最新质检结论为【驳回】"
                    + reasonPart(latest.getReason());
            case REVOKED -> "批次的放行已被撤销"
                    + reasonPart(latest.getReason());
            case PASSED -> null;
        };
    }

    private static String reasonPart(String reason) {
        return reason == null || reason.isBlank() ? "" : "，原因：" + reason;
    }
}
