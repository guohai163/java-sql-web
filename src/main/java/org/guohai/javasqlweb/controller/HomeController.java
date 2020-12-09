package org.guohai.javasqlweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 基础控制器
 * @author guohai
 */
@Controller
public class HomeController {
    
    @RequestMapping(value = "/ftl")
    public String home() {

        return "home";
    }
}
