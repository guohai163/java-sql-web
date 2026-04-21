import React from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Button, Empty, Select, Space, Table, Tag } from 'antd';
import {
  DatabaseOutlined,
  FundOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';

const RANGE_OPTIONS = [
  { value: '24h', label: '近24小时' },
  { value: '7d', label: '近7天' },
  { value: '30d', label: '近30天' },
];

const GRAIN_OPTIONS = {
  '24h': [
    { value: 'hour', label: '按小时' },
    { value: 'day', label: '按天' },
  ],
  '7d': [{ value: 'day', label: '按天' }],
  '30d': [{ value: 'day', label: '按天' }],
};

const HOTSPOT_COLORS = ['#0f766e', '#1d4ed8', '#f59e0b', '#f97316', '#14b8a6', '#ef4444', '#8b5cf6', '#84cc16'];

function formatCompactNumber(value) {
  if (value == null) {
    return '0';
  }
  return new Intl.NumberFormat('zh-CN', {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value);
}

function formatInteger(value) {
  return new Intl.NumberFormat('zh-CN').format(value || 0);
}

function formatLatency(value) {
  if (!value) {
    return '0 ms';
  }
  return `${Math.round(value)} ms`;
}

function formatTimestamp(value) {
  if (!value) {
    return '-';
  }
  return String(value).replace('T', ' ');
}

function getRuntimeStatusMeta(status) {
  switch (status) {
    case 'ok':
      return { color: 'success', text: '正常' };
    case 'warning':
      return { color: 'warning', text: '警告' };
    case 'cooldown':
      return { color: 'error', text: '冷却中' };
    default:
      return { color: 'default', text: '未使用' };
  }
}

function bucketLabel(value, range) {
  if (!value) {
    return '';
  }
  if (range === '24h') {
    return value.slice(11, 16);
  }
  return value.slice(5);
}

function buildSummaryCards(summary) {
  return [
    {
      key: 'users',
      title: '用户总览',
      icon: <TeamOutlined />,
      value: formatInteger(summary?.totalUsers),
      helper: `新增 ${formatInteger(summary?.newUsers)}`,
      accent: 'teal',
    },
    {
      key: 'instances',
      title: '数据实例',
      icon: <DatabaseOutlined />,
      value: formatInteger(summary?.totalInstances),
      helper: '未探活',
      accent: 'amber',
    },
    {
      key: 'pool',
      title: '连接池概览',
      icon: <ThunderboltOutlined />,
      value: formatInteger(summary?.totalPoolConnections),
      helper: `活跃 ${formatInteger(summary?.activePoolConnections)} / 空闲 ${formatInteger(summary?.idlePoolConnections)} / 等待 ${formatInteger(summary?.waitingPoolThreads)}`,
      accent: 'indigo',
    },
    {
      key: 'queries',
      title: '查询次数',
      icon: <FundOutlined />,
      value: formatInteger(summary?.queryCount),
      helper: '当前时间范围内',
      accent: 'emerald',
    },
    {
      key: 'rows',
      title: '返回条数',
      icon: <UnorderedListOutlined />,
      value: formatCompactNumber(summary?.totalReturnedRows),
      helper: '累计返回记录',
      accent: 'coral',
    },
    {
      key: 'latency',
      title: '平均耗时',
      icon: <ThunderboltOutlined />,
      value: formatLatency(summary?.averageQueryConsuming),
      helper: '查询响应时间',
      accent: 'rose',
    },
    {
      key: 'dynamic-pool',
      title: '目标库连接池',
      icon: <DatabaseOutlined />,
      value: formatInteger(summary?.activeDynamicPools),
      helper: `冷却 ${formatInteger(summary?.cooldownDynamicPools)} / 连接 ${formatInteger(summary?.dynamicPoolConnections)} / 等待 ${formatInteger(summary?.dynamicPoolWaitingThreads)}`,
      accent: 'slate',
    },
  ];
}

function AdminDashboard({ data, filter, loading, updatedAt, onRangeChange, onGrainChange, onRefresh }) {
  const summaryCards = buildSummaryCards(data?.summary);
  const userRankingColumns = [
    { title: '排名', dataIndex: 'rank', width: 72 },
    { title: '用户', dataIndex: 'userName', ellipsis: true },
    { title: '查询次数', dataIndex: 'queryCount', render: (value) => formatInteger(value), width: 120 },
    { title: '返回条数', dataIndex: 'totalReturnedRows', render: (value) => formatCompactNumber(value), width: 120 },
    { title: '涉及库数', dataIndex: 'databaseCount', width: 110 },
    { title: '涉及表数', dataIndex: 'tableCount', width: 110 },
    { title: '平均耗时', dataIndex: 'averageQueryConsuming', render: (value) => formatLatency(value), width: 130 },
  ];

  const recentQueryColumns = [
    { title: '查询时间', dataIndex: 'queryTime', render: formatTimestamp, width: 168 },
    { title: '查询人', dataIndex: 'queryName', width: 120 },
    { title: '实例', dataIndex: 'serverName', width: 160, ellipsis: true },
    { title: '数据库', dataIndex: 'queryDatabase', width: 140, ellipsis: true },
    { title: '涉及表', dataIndex: 'targetTables', ellipsis: true },
    { title: '返回条数', dataIndex: 'resultRowCount', render: (value) => formatInteger(value), width: 120 },
    { title: '耗时(ms)', dataIndex: 'queryConsuming', render: (value) => formatInteger(value), width: 110 },
    { title: 'SQL 摘要', dataIndex: 'querySqlscript', ellipsis: true },
  ];

  const dynamicPoolColumns = [
    { title: '服务器', dataIndex: 'serverName', width: 140, ellipsis: true },
    { title: '类型', dataIndex: 'dbType', width: 100 },
    {
      title: '状态',
      dataIndex: 'runtimeStatus',
      width: 110,
      render: (value) => {
        const meta = getRuntimeStatusMeta(value);
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    { title: '活跃', dataIndex: 'activeConnections', render: formatInteger, width: 90 },
    { title: '空闲', dataIndex: 'idleConnections', render: formatInteger, width: 90 },
    { title: '总数', dataIndex: 'totalConnections', render: formatInteger, width: 90 },
    { title: '等待', dataIndex: 'threadsAwaitingConnection', render: formatInteger, width: 90 },
    {
      title: '冷却剩余',
      dataIndex: 'cooldownRemainingSeconds',
      width: 120,
      render: (value, record) => (record?.inCooldown && value != null ? `${value}s` : '-'),
    },
    { title: '最近错误', dataIndex: 'lastError', ellipsis: true },
  ];

  const userChartData = data?.userRanking || [];
  const databaseHotspots = data?.databaseHotspots || [];
  const tableHotspots = data?.tableHotspots || [];
  const trendData = data?.trend || [];
  const recentQueries = data?.recentQueries || [];
  const dynamicTargetPools = data?.dynamicTargetPools || [];
  const maxTableQueryCount = tableHotspots[0]?.queryCount || 1;

  return (
    <div className="admin-dashboard">
      <div className="admin-dashboard-toolbar">
        <div className="admin-toolbar-cluster">
          <label className="admin-dashboard-label">时间范围</label>
          <Select
            className="admin-dashboard-select"
            options={RANGE_OPTIONS}
            value={filter.range}
            onChange={onRangeChange}
          />
        </div>
        <div className="admin-toolbar-cluster">
          <label className="admin-dashboard-label">粒度</label>
          <Select
            className="admin-dashboard-select"
            options={GRAIN_OPTIONS[filter.range]}
            value={filter.grain}
            onChange={onGrainChange}
          />
        </div>
        <div className="admin-toolbar-cluster grow">
          <div className="admin-dashboard-meta">
            {updatedAt ? `更新于 ${updatedAt}` : '等待首次加载'}
          </div>
        </div>
        <Button loading={loading} type="primary" onClick={onRefresh}>
          刷新
        </Button>
      </div>

      <div className="admin-summary-grid">
        {summaryCards.map((card) => (
          <div className={`admin-kpi-card accent-${card.accent}`} key={card.key}>
            <div className="admin-kpi-icon">{card.icon}</div>
            <div className="admin-kpi-content">
              <div className="admin-kpi-title">{card.title}</div>
              <div className="admin-kpi-value">{card.value}</div>
              <div className="admin-kpi-helper">{card.helper}</div>
            </div>
          </div>
        ))}
      </div>

      <div className="admin-dashboard-grid">
        <section className="admin-dashboard-card admin-dashboard-trend">
          <div className="admin-card-heading">
            <div>
              <h3>查询趋势</h3>
              <p>同时观察查询次数与返回条数的波动</p>
            </div>
            <Tag color="cyan">{filter.range === '24h' ? '小时级' : '天级'}</Tag>
          </div>
          {trendData.length > 0 ? (
            <div className="admin-chart-shell large">
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={trendData}>
                  <defs>
                    <linearGradient id="rowsGradient" x1="0" x2="0" y1="0" y2="1">
                      <stop offset="5%" stopColor="#0f766e" stopOpacity={0.32} />
                      <stop offset="95%" stopColor="#0f766e" stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#d7e3ea" vertical={false} />
                  <XAxis
                    axisLine={false}
                    dataKey="timeBucket"
                    tickFormatter={(value) => bucketLabel(value, filter.range)}
                    tickLine={false}
                  />
                  <YAxis axisLine={false} tickFormatter={formatCompactNumber} tickLine={false} />
                  <YAxis
                    axisLine={false}
                    orientation="right"
                    tickFormatter={formatCompactNumber}
                    tickLine={false}
                    yAxisId="rows"
                  />
                  <Tooltip
                    formatter={(value, name) => [
                      name === 'totalReturnedRows' ? formatCompactNumber(value) : formatInteger(value),
                      name === 'totalReturnedRows' ? '返回条数' : '查询次数',
                    ]}
                    labelFormatter={(label) => `时间 ${label}`}
                  />
                  <Legend
                    formatter={(value) => (value === 'queryCount' ? '查询次数' : '返回条数')}
                  />
                  <Area
                    dataKey="totalReturnedRows"
                    fill="url(#rowsGradient)"
                    name="totalReturnedRows"
                    stroke="#0f766e"
                    strokeWidth={2}
                    type="monotone"
                    yAxisId="rows"
                  />
                  <Line
                    dataKey="queryCount"
                    dot={false}
                    name="queryCount"
                    stroke="#1d4ed8"
                    strokeWidth={3}
                    type="monotone"
                  />
                </ComposedChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <Empty description="当前时间范围还没有查询记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </section>

        <section className="admin-dashboard-card admin-dashboard-ranking">
          <div className="admin-card-heading">
            <div>
              <h3>用户查询排行</h3>
              <p>按查询次数排序，补充返回条数和活跃对象维度</p>
            </div>
          </div>
          {userChartData.length > 0 ? (
            <>
              <div className="admin-chart-shell medium">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={[...userChartData].reverse()} layout="vertical" margin={{ left: 8, right: 8 }}>
                    <CartesianGrid horizontal={false} stroke="#d7e3ea" strokeDasharray="3 3" />
                    <XAxis axisLine={false} tickFormatter={formatCompactNumber} tickLine={false} type="number" />
                    <YAxis
                      axisLine={false}
                      dataKey="userName"
                      tickLine={false}
                      type="category"
                      width={90}
                    />
                    <Tooltip formatter={(value) => [formatInteger(value), '查询次数']} />
                    <Bar dataKey="queryCount" fill="#1d4ed8" radius={[0, 8, 8, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <Table
                columns={userRankingColumns}
                dataSource={userChartData}
                pagination={false}
                rowKey={(record) => `rank-${record.rank}-${record.userName}`}
                size="small"
              />
            </>
          ) : (
            <Empty description="暂无用户查询排行" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </section>

        <section className="admin-dashboard-card">
          <div className="admin-card-heading">
            <div>
              <h3>活跃数据库 Top 5</h3>
              <p>按查询次数观察最近最常被访问的库</p>
            </div>
          </div>
          {databaseHotspots.length > 0 ? (
            <div className="admin-chart-shell medium">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={[...databaseHotspots].reverse()} layout="vertical">
                  <CartesianGrid horizontal={false} stroke="#d7e3ea" strokeDasharray="3 3" />
                  <XAxis axisLine={false} tickFormatter={formatCompactNumber} tickLine={false} type="number" />
                  <YAxis axisLine={false} dataKey="objectName" tickLine={false} type="category" width={150} />
                  <Tooltip
                    formatter={(value, name, context) => {
                      if (name === 'queryCount') {
                        return [formatInteger(value), '查询次数'];
                      }
                      return [formatCompactNumber(value), '返回条数'];
                    }}
                    labelFormatter={(label) => label}
                  />
                  <Bar dataKey="queryCount" fill="#0f766e" radius={[0, 8, 8, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <Empty description="暂无数据库热点" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </section>

        <section className="admin-dashboard-card">
          <div className="admin-card-heading">
            <div>
              <h3>活跃表 Top 10</h3>
              <p>按库表维度查看近期最活跃的数据对象</p>
            </div>
          </div>
          {tableHotspots.length > 0 ? (
            <div className="admin-hotspot-list">
              {tableHotspots.map((item, index) => (
                <div className="admin-hotspot-row" key={`${item.objectName}-${index}`}>
                  <div className="admin-hotspot-index">{index + 1}</div>
                  <div className="admin-hotspot-main">
                    <div className="admin-hotspot-name">{item.objectName}</div>
                    <div className="admin-hotspot-subtitle">
                      {item.serverName || '未知实例'} · 返回 {formatCompactNumber(item.totalReturnedRows)}
                    </div>
                  </div>
                  <div className="admin-hotspot-value">
                    <span>{formatInteger(item.queryCount)}</span>
                    <small>次</small>
                  </div>
                  <div className="admin-hotspot-bar">
                    <div
                      className="admin-hotspot-bar-fill"
                      style={{
                        width: `${Math.max(10, (item.queryCount / maxTableQueryCount) * 100)}%`,
                        background: HOTSPOT_COLORS[index % HOTSPOT_COLORS.length],
                      }}
                    />
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <Empty description="暂无表热点" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </section>

        <section className="admin-dashboard-card">
          <div className="admin-card-heading">
            <div>
              <h3>动态目标库连接池</h3>
              <p>观察活跃、等待和冷却中的动态目标库连接池</p>
            </div>
            <Tag color="geekblue">{formatInteger(dynamicTargetPools.length)}</Tag>
          </div>
          {dynamicTargetPools.length > 0 ? (
            <Table
              columns={dynamicPoolColumns}
              dataSource={dynamicTargetPools}
              pagination={false}
              rowKey={(record) => `dynamic-pool-${record.serverCode}`}
              size="small"
            />
          ) : (
            <Empty description="当前没有活跃或异常的动态目标库连接池" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </section>

        <section className="admin-dashboard-card admin-dashboard-recent">
          <div className="admin-card-heading">
            <div>
              <h3>最近 10 条查询</h3>
              <p>展示最新查询对象、返回条数和执行耗时</p>
            </div>
            <Space size={8}>
              <Tag color="blue">脱敏 SQL</Tag>
              <Tag color="green">最近活动</Tag>
            </Space>
          </div>
          <Table
            columns={recentQueryColumns}
            dataSource={recentQueries}
            locale={{
              emptyText: <Empty description="当前时间范围没有查询记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />,
            }}
            pagination={false}
            rowKey={(record) => `recent-${record.queryTime || 'unknown'}-${record.queryName || 'unknown'}`}
            size="small"
          />
        </section>
      </div>
    </div>
  );
}

export default AdminDashboard;
