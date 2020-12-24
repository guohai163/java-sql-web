package org.guohai.javasqlweb.controller;

import org.guohai.javasqlweb.beans.QueryLogBean;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.service.BackstageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/backstage")
@CrossOrigin
public class BackstageController {

    @Autowired
    BackstageService backstageService;

    @ResponseBody
    @RequestMapping(value = "/querylog")
    public Result<List<QueryLogBean>> getQueryLog(){
        return backstageService.getQueryLog();
    }
}
