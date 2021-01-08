package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.dao.BaseConfigDao;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台服务
 * @author guohai
 * @date 2021-1-1
 */
@Service
public class BackstageServiceImpl implements BackstageService{
    @Autowired
    BaseConfigDao baseConfigDao;

    @Autowired
    UserManageDao userDao;
    /**
     * 获取查询日志
     *
     * @return
     */
    @Override
    public Result<List<QueryLogBean>> getQueryLog() {

        return new Result<>(true,"", baseConfigDao.getQueryLog());
    }

    /**
     * 获取连接表
     *
     * @return
     */
    @Override
    public Result<List<ConnectConfigBean>> getConnData() {
        List<ConnectConfigBean> listConn = baseConfigDao.getConnData();

        return new Result<>(true, "", listConn);
    }

    /**
     * 增加服务器
     *
     * @param server
     * @return
     */
    @Override
    public Result<String> addConnServer(ConnectConfigBean server) {
        if(null != baseConfigDao.getConnectConfigByName(server.getDbServerName())){
            return new Result<>(false,"","同名服务器已经存在");
        }
        baseConfigDao.addConnServer(server);
        return new Result<>(true, "","服务器增加成功");
    }

    /**
     * 获取站点基础数据
     *
     * @return
     */
    @Override
    public Result<Map> getSiteBaseData() {
        Map<String,Object> baseData = new HashMap<String, Object>(2);
        baseData.put("user_count", userDao.getUserList().size());
        baseData.put("server_count", baseConfigDao.getAllConnectConfig().size());
        return new Result<>(true, "", baseData);
    }

    /**
     * 获取完整用户数据
     *
     * @return
     */
    @Override
    public Result<List<UserBean>> getUserData() {
        return new Result<>(true, "", userDao.getUserList());
    }

    /**
     * 增加新用户
     *
     * @param user 用户
     * @return
     */
    @Override
    public Result<String> addNewUser(UserBean user) {
        // 检查用户是否存在
        if(null != userDao.getUserByName(user.getUserName())){
            return new Result<>(false, "","用户已存在" ) ;
        }
        userDao.addNewUser(user.getUserName(),user.getPassWord());
        return new Result<>(true, "","成功" ) ;
    }

    /**
     * 删除指定用户
     *
     * @param userName
     * @return
     */
    @Override
    public Result<String> delUser(String userName) {
        if(null == userDao.getUserByName(userName)){
            return new Result<>(false, "","用户不存在" ) ;
        }
        userDao.delUser(userName);
        return new Result<>(true, "","删除成功");
    }

    /**
     * 删除指定服务器连接
     *
     * @param code
     * @return
     */
    @Override
    public Result<String> delServer(Integer code) {
        if(null == baseConfigDao.getConnectConfig(code)){
            return new Result<>(false, "","无此服务器" ) ;
        }
        baseConfigDao.delServerByCode(code);
        return new Result<>(true, "","删除成功");
    }

    /**
     * 通过有效token修改用户密码
     *
     * @param token
     * @param newPass
     * @return
     */
    @Override
    public Result<String> changeUserPass(String token, String newPass) {
        UserBean user = userDao.getUserByToken(token);
        if(null == user || user.getLoginStatus() != UserLoginStatus.LOGGED){
            return new Result<>(false,"","用户token无效");
        }
        userDao.changeUserPassword(token, newPass);
        return new Result<>(true, "","密码修改成功");
    }
}
