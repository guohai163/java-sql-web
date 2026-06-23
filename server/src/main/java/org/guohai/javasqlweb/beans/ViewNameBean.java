package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视图
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewNameBean {

    /**
     * 视图名
     */
    private String viewName;


    /**
     * 视图创建语句
     */
    private String viewData;

    /**
     * 视图备注
     */
    private String viewComment;

    public ViewNameBean(String name){
        this(name, null, "");
    }

    public ViewNameBean(String name, String viewData) {
        this(name, viewData, "");
    }
}
