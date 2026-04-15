package org.guohai.javasqlweb.util;

import org.guohai.javasqlweb.beans.QueryLogTargetBean;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlTargetExtractorTests {

    @Test
    void extractsTablesFromJoinQuery() {
        List<QueryLogTargetBean> targets = SqlTargetExtractor.extract(
                "select a.id from orders a join user_detail b on a.user_id=b.id",
                "biz_db"
        );

        assertEquals(2, targets.size());
        assertEquals("biz_db", targets.get(0).getDatabaseName());
        assertEquals("orders", targets.get(0).getTableName());
        assertEquals("biz_db", targets.get(1).getDatabaseName());
        assertEquals("user_detail", targets.get(1).getTableName());
    }

    @Test
    void extractsQualifiedNamesAndSkipsSubqueryWrapper() {
        List<QueryLogTargetBean> targets = SqlTargetExtractor.extract(
                "select * from (select * from analytics.daily_orders) t join `sales`.`refunds` r on t.id=r.order_id",
                "fallback_db"
        );

        assertEquals(2, targets.size());
        assertEquals("analytics", targets.get(0).getDatabaseName());
        assertEquals("daily_orders", targets.get(0).getTableName());
        assertEquals("sales", targets.get(1).getDatabaseName());
        assertEquals("refunds", targets.get(1).getTableName());
    }
}
