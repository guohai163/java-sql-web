import { render, screen } from '@testing-library/react';
import AdminDashboard from '@/features/admin/components/AdminDashboard';

jest.mock('recharts', () => ({
  ResponsiveContainer: ({ children }) => <div>{children}</div>,
  ComposedChart: ({ children }) => <svg>{children}</svg>,
  AreaChart: ({ children }) => <svg>{children}</svg>,
  BarChart: ({ children }) => <svg>{children}</svg>,
  CartesianGrid: () => null,
  Legend: () => null,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Area: () => null,
  Line: () => null,
  Bar: ({ children }) => <g>{children}</g>,
  Cell: () => null,
}));

beforeAll(() => {
  window.matchMedia = window.matchMedia || (() => ({
    matches: false,
    addListener() {},
    removeListener() {},
    addEventListener() {},
    removeEventListener() {},
    dispatchEvent() {},
  }));
});

describe('AdminDashboard', () => {
  test('renders summary cards and recent query table from dashboard payload', () => {
    render(
      <AdminDashboard
        data={{
          summary: {
            totalUsers: 12,
            newUsers: 3,
            totalInstances: 6,
            healthyInstances: 5,
            totalPoolConnections: 18,
            activePoolConnections: 4,
            idlePoolConnections: 14,
            waitingPoolThreads: 1,
            queryCount: 66,
            totalReturnedRows: 1024,
            averageQueryConsuming: 28.4,
          },
          trend: [{ timeBucket: '2026-04-16 03:00', queryCount: 5, totalReturnedRows: 50 }],
          userRanking: [
            {
              rank: 1,
              userName: 'alice',
              queryCount: 30,
              totalReturnedRows: 700,
              databaseCount: 3,
              tableCount: 9,
              averageQueryConsuming: 12,
            },
          ],
          databaseHotspots: [{ objectName: 'core / order_db', queryCount: 20, totalReturnedRows: 300 }],
          tableHotspots: [{ objectName: 'order_db.orders', queryCount: 16, totalReturnedRows: 220, serverName: 'core' }],
          recentQueries: [
            {
              queryTime: '2026-04-16 03:00:00',
              queryName: 'alice',
              serverName: 'core',
              queryDatabase: 'order_db',
              targetTables: 'order_db.orders',
              resultRowCount: 20,
              queryConsuming: 11,
              querySqlscript: 'select * from orders limit 20',
            },
          ],
        }}
        filter={{ range: '24h', grain: 'hour' }}
        loading={false}
        updatedAt="2026-04-16 03:00:00"
        onRangeChange={() => {}}
        onGrainChange={() => {}}
        onRefresh={() => {}}
      />,
    );

    expect(screen.getByText('用户总览')).toBeInTheDocument();
    expect(screen.getByText('查询趋势')).toBeInTheDocument();
    expect(screen.getByText('用户查询排行')).toBeInTheDocument();
    expect(screen.getByText('最近 10 条查询')).toBeInTheDocument();
    expect(screen.getByText('未探活')).toBeInTheDocument();
    expect(screen.getAllByText('alice').length).toBeGreaterThan(0);
    expect(screen.getAllByText('order_db.orders').length).toBeGreaterThan(0);
  });

  test('renders empty states when dashboard has no data', () => {
    render(
      <AdminDashboard
        data={{
          summary: {},
          trend: [],
          userRanking: [],
          databaseHotspots: [],
          tableHotspots: [],
          recentQueries: [],
        }}
        filter={{ range: '24h', grain: 'hour' }}
        loading={false}
        updatedAt=""
        onRangeChange={() => {}}
        onGrainChange={() => {}}
        onRefresh={() => {}}
      />,
    );

    expect(screen.getByText('当前时间范围还没有查询记录')).toBeInTheDocument();
    expect(screen.getByText('暂无用户查询排行')).toBeInTheDocument();
  });
});
