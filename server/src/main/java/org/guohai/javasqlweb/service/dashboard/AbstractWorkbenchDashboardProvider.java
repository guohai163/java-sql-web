package org.guohai.javasqlweb.service.dashboard;

import org.guohai.javasqlweb.beans.WorkbenchDashboardItem;
import org.guohai.javasqlweb.beans.WorkbenchDashboardSection;
import org.guohai.javasqlweb.service.operation.DbOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

abstract class AbstractWorkbenchDashboardProvider implements WorkbenchDashboardProvider {

    protected WorkbenchDashboardSection section(String key, String title, WorkbenchDashboardItem... items) {
        WorkbenchDashboardSection section = new WorkbenchDashboardSection();
        section.setKey(key);
        section.setTitle(title);
        List<WorkbenchDashboardItem> itemList = new ArrayList<>();
        boolean hasSuccess = false;
        boolean hasFailure = false;
        for (WorkbenchDashboardItem item : items) {
            if (item == null) {
                continue;
            }
            itemList.add(item);
            if ("ok".equals(item.getStatus())) {
                hasSuccess = true;
            } else {
                hasFailure = true;
            }
        }
        section.setItems(itemList);
        if (hasSuccess && hasFailure) {
            section.setStatus("partial");
        } else if (hasSuccess) {
            section.setStatus("ok");
        } else if (!itemList.isEmpty()) {
            section.setStatus(itemList.get(0).getStatus());
        } else {
            section.setStatus("empty");
        }
        return section;
    }

    protected WorkbenchDashboardItem staticItem(String key, String label, Object value) {
        WorkbenchDashboardItem item = new WorkbenchDashboardItem();
        item.setKey(key);
        item.setLabel(label);
        item.setValue(stringify(value));
        item.setStatus("ok");
        return item;
    }

    protected WorkbenchDashboardItem queryItem(DbOperation operation,
                                               String dbName,
                                               String key,
                                               String label,
                                               String sql,
                                               String... candidateKeys) {
        return queryItem(operation, dbName, key, label, sql, row -> {
            Object value = pickValue(row, candidateKeys);
            return stringify(value);
        });
    }

    protected WorkbenchDashboardItem queryItem(DbOperation operation,
                                               String dbName,
                                               String key,
                                               String label,
                                               String sql,
                                               Function<Map<String, Object>, String> valueExtractor) {
        WorkbenchDashboardItem item = new WorkbenchDashboardItem();
        item.setKey(key);
        item.setLabel(label);
        try {
            Map<String, Object> row = queryFirstRow(operation, dbName, sql);
            if (row.isEmpty()) {
                item.setStatus("unsupported");
                item.setMessage("当前数据库未返回数据");
                item.setValue("--");
                return item;
            }
            String value = valueExtractor.apply(row);
            if (value == null || value.trim().isEmpty()) {
                item.setStatus("unsupported");
                item.setMessage("当前数据库未返回该指标");
                item.setValue("--");
                return item;
            }
            item.setStatus("ok");
            item.setValue(value);
            return item;
        } catch (Exception exception) {
            item.setStatus(resolveFailureStatus(exception));
            item.setValue("--");
            item.setMessage(resolveFailureMessage(exception));
            return item;
        }
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> queryFirstRow(DbOperation operation, String dbName, String sql) throws Exception {
        Object[] result = operation.queryDatabaseBySql(dbName, sql, 1);
        if (result == null || result.length < 3 || !(result[2] instanceof List)) {
            return new LinkedHashMap<>();
        }
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result[2];
        return rows == null || rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    protected Object pickValue(Map<String, Object> row, String... candidateKeys) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        for (String candidateKey : candidateKeys) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(candidateKey)) {
                    return entry.getValue();
                }
            }
        }
        return row.values().iterator().next();
    }

    protected String stringify(Object value) {
        if (value == null) {
            return "--";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "--" : text;
    }

    private String resolveFailureStatus(Exception exception) {
        String message = exception == null ? "" : String.valueOf(exception.getMessage()).toLowerCase();
        if (message.contains("permission")
                || message.contains("denied")
                || message.contains("not granted")
                || message.contains("access")
                || message.contains("must be superuser")) {
            return "forbidden";
        }
        return "unsupported";
    }

    private String resolveFailureMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().trim().isEmpty()) {
            return "当前数据库不支持该指标";
        }
        String message = exception.getMessage().trim();
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("permission")
                || lowerMessage.contains("denied")
                || lowerMessage.contains("not granted")
                || lowerMessage.contains("access")
                || lowerMessage.contains("must be superuser")) {
            return "当前账号权限不足";
        }
        return message;
    }
}
