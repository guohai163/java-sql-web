package org.guohai.javasqlweb.service.dashboard;

import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.WorkbenchDashboardSection;
import org.guohai.javasqlweb.service.operation.DbOperation;

import java.util.List;

public interface WorkbenchDashboardProvider {

    String getDbType();

    List<WorkbenchDashboardSection> buildSections(DbOperation operation, String dbName, ConnectConfigBean config);
}
