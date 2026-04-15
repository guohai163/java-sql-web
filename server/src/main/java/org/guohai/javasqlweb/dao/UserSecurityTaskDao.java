package org.guohai.javasqlweb.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.guohai.javasqlweb.beans.UserSecurityTaskBean;
import org.springframework.stereotype.Repository;

import java.util.Date;

/**
 * 用户安全任务DAO
 */
@Repository
public interface UserSecurityTaskDao {

    /**
     * 插入安全任务
     * @param task 任务
     * @return 是否成功
     */
    @Insert("INSERT INTO user_security_task_tb (task_uuid_hash,user_code,task_type,task_status,expire_time,used_time,created_by,created_time) " +
            "VALUES (#{task.taskUuidHash},#{task.userCode},#{task.taskType},#{task.taskStatus},#{task.expireTime},#{task.usedTime},#{task.createdBy},#{task.createdTime})")
    Boolean addTask(@Param("task") UserSecurityTaskBean task);

    /**
     * 查询任务及用户状态
     * @param taskUuidHash 任务哈希
     * @return 任务
     */
    @Select("SELECT t.code,t.task_uuid_hash,t.user_code,t.task_type,t.task_status,t.expire_time,t.used_time,t.created_by,t.created_time, " +
            "u.user_name,u.email,u.account_status,u.auth_status,u.login_status,u.auth_secret,u.token " +
            "FROM user_security_task_tb t " +
            "JOIN user_tb u ON u.code = t.user_code " +
            "WHERE t.task_uuid_hash=#{taskUuidHash}")
    UserSecurityTaskBean getTaskByHash(@Param("taskUuidHash") String taskUuidHash);

    /**
     * 将用户的未完成任务标记为取消
     * @param userCode 用户编号
     * @return 更新数量
     */
    @Update("UPDATE user_security_task_tb SET task_status='CANCELLED' " +
            "WHERE user_code=#{userCode} AND task_status IN ('PENDING_PASSWORD','PENDING_OTP')")
    Integer cancelPendingTasksByUser(@Param("userCode") Integer userCode);

    /**
     * 将用户过期任务标记为已过期
     * @param userCode 用户编号
     * @param now 当前时间
     * @return 更新数量
     */
    @Update("UPDATE user_security_task_tb SET task_status='EXPIRED' " +
            "WHERE user_code=#{userCode} AND task_status IN ('PENDING_PASSWORD','PENDING_OTP') AND expire_time < #{now}")
    Integer expirePendingTasksByUser(@Param("userCode") Integer userCode, @Param("now") Date now);

    /**
     * 标记指定任务已过期
     * @param taskCode 任务编号
     * @return 更新数量
     */
    @Update("UPDATE user_security_task_tb SET task_status='EXPIRED' WHERE code=#{taskCode} AND task_status IN ('PENDING_PASSWORD','PENDING_OTP')")
    Integer markExpired(@Param("taskCode") Integer taskCode);

    /**
     * 标记任务下一阶段状态
     * @param taskCode 任务编号
     * @param taskStatus 任务状态
     * @return 更新数量
     */
    @Update("UPDATE user_security_task_tb SET task_status=#{taskStatus} WHERE code=#{taskCode}")
    Integer updateTaskStatus(@Param("taskCode") Integer taskCode, @Param("taskStatus") String taskStatus);

    /**
     * 标记任务已使用
     * @param taskCode 任务编号
     * @param usedTime 使用时间
     * @return 更新数量
     */
    @Update("UPDATE user_security_task_tb SET task_status='USED',used_time=#{usedTime} WHERE code=#{taskCode}")
    Integer markUsed(@Param("taskCode") Integer taskCode, @Param("usedTime") Date usedTime);
}
