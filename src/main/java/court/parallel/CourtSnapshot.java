
package court.parallel;

import court.Edict;

import java.util.List;

/**
 * 朝会快照。
 *
 * 保存某一时刻的会议状态，
 * 供不同 Agent 独立读取和思考。
 */
public record CourtSnapshot(

        // 当前会议版本号
        long version,

        // 本场朝会已经发生的公开事件
        List<CourtEvent> events,

        // 开场读取的圣旨快照，本场不更新
        List<Edict> edicts,

        // 当前政务信息
        String currentAffairs,

        // 本场可路由的参与者 ID，包含 user，不包含内部 system 身份。
        List<String> participantIds

) {

    /**
     * 创建快照时复制列表，
     * 避免外部继续修改快照中的数据。
     */
    public CourtSnapshot {

        events = List.copyOf(events);

        edicts = List.copyOf(edicts);

        participantIds = List.copyOf(participantIds);

    }

}
