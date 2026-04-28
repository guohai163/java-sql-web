import { buildConnListQuery, summarizeSyncResult } from '@/features/admin/Admin';

describe('Admin helpers', () => {
  test('buildConnListQuery should include keyword and exact dbName', () => {
    const query = buildConnListQuery('order_db', 'mysql');

    expect(query).toContain('keyword=order_db');
    expect(query).toContain('dbName=order_db');
    expect(query).toContain('serverType=mysql');
  });

  test('buildConnListQuery should ignore all-type filter', () => {
    const query = buildConnListQuery('order_db', 'all');

    expect(query).toContain('keyword=order_db');
    expect(query).toContain('dbName=order_db');
    expect(query).not.toContain('serverType=');
  });

  test('summarizeSyncResult should include failure details', () => {
    const content = summarizeSyncResult({
      totalServers: 2,
      successCount: 1,
      failCount: 1,
      syncedAt: '2026-04-28 12:00:00',
      failures: [
        {
          serverCode: 8,
          serverName: 'core',
          message: 'connect timeout',
        },
      ],
    });

    expect(content).toContain('总实例数：2');
    expect(content).toContain('成功：1');
    expect(content).toContain('失败：1');
    expect(content).toContain('同步时间：2026-04-28 12:00:00');
    expect(content).toContain('core(8)：connect timeout');
  });
});
