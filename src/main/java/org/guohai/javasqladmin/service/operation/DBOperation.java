package org.guohai.javasqladmin.service.operation;

import org.guohai.javasqladmin.beans.DatabaseNameBean;

import java.sql.SQLException;
import java.util.List;

public interface DBOperation {

    List<DatabaseNameBean> getDBList() throws SQLException, ClassNotFoundException;
}
