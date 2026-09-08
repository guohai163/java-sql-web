package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 供 Vanna 预热任务使用的服务器枚举项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VannaServerWarmupItem {
    private Integer serverCode;
    private String serverName;
    private String serverType;
}
