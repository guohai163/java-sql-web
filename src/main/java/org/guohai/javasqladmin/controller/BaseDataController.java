package org.guohai.javasqladmin.controller;

import org.guohai.javasqladmin.beans.ConnectConfigBean;
import org.guohai.javasqladmin.beans.DatabaseNameBean;
import org.guohai.javasqladmin.beans.Result;
import org.guohai.javasqladmin.service.BaseDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
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

    @ResponseBody
    @RequestMapping(value = "/dblist")
    public Result<List<DatabaseNameBean>> getAllDBName(){
        return baseDataService.getDbName(1);
    }
}
