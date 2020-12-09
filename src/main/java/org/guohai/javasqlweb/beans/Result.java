package org.guohai.javasqlweb.beans;

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
public class Result<T> {

    /**
     * 状态
     */
    private Boolean status;

    /**
     * 返回的数据体
     */
    private T data;

}
