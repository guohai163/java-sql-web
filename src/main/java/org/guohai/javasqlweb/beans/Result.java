package org.guohai.javasqlweb.beans;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 返回结构休
 * @param <T> 泛型参数
 * @author guohai
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("返回前端用的标准集")
public class Result<T> {

    /**
     * 状态
     */
    Boolean status;
    /**
     * 说明信息
     */
    String message;
    /**
     * 返回的数据体
     */
    T data;

}
