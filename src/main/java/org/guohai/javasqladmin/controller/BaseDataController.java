package org.guohai.javasqladmin.controller;

import org.guohai.javasqladmin.beans.ConnectConfigBean;
import org.guohai.javasqladmin.beans.DatabaseNameBean;
import org.guohai.javasqladmin.beans.Result;
import org.guohai.javasqladmin.beans.TablesNameBean;
import org.guohai.javasqladmin.service.BaseDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据基础控制器
 */
@RestController
@RequestMapping(value = "/database")
public class BaseDataController {
    private static final Logger LOG  = LoggerFactory.getLogger(BaseDataController.class);

    @Autowired
    BaseDataService baseDataService;

    @ResponseBody
    @RequestMapping(value = "/infoall")
    public Result<List<ConnectConfigBean>> getAllConnect(){
        return baseDataService.getAllDataConnect();
    }

    /**
     * 通过DBCode获得所有库
     * @param dbCode
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/dblist/{dbCode}")
    public Result<List<DatabaseNameBean>> getAllDBName(@PathVariable("dbCode") String dbCode){
        return baseDataService.getDbName(Integer.parseInt(dbCode));
    }

    /**
     * 通过库编号+库名获得表列表
     * @param dbCode
     * @param dbName
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/tablelist/{dbCode}/{dbName}")
    public Result<List<TablesNameBean>> getTableName(@PathVariable("dbCode") String dbCode, @PathVariable("dbName") String dbName){
        return baseDataService.getTableList(Integer.parseInt(dbCode),dbName);
    }
}
