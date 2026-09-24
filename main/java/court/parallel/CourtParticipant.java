
package court.parallel;

/**
 * 朝会参与者接口。
 *
 * 皇帝、大臣都需要实现这个接口，
 * 使会议编排器能够统一调度不同的 Agent。
 */
public interface CourtParticipant {

    // 参与者的唯一标识，例如 emperor、minister
    String id();

    // 参与者的显示名称，例如 皇帝、丞相
    String name();

    // 根据当前会议信息进行思考，并返回行动
    CourtAction react(CourtSnapshot snapshot) throws Exception;

}
