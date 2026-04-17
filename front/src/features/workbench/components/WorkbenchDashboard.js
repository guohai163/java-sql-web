import React from 'react';
import { Button, Empty, Skeleton, Tag } from 'antd';

function getStatusMeta(status) {
  switch (status) {
    case 'ok':
      return { color: 'success', text: '正常' };
    case 'partial':
      return { color: 'processing', text: '部分受限' };
    case 'forbidden':
      return { color: 'warning', text: '权限不足' };
    case 'unsupported':
      return { color: 'default', text: '不支持' };
    default:
      return { color: 'default', text: '待加载' };
  }
}

function renderUpdatedAt(updatedAt) {
  if (!updatedAt) {
    return '等待首次加载';
  }
  return `更新于 ${updatedAt}`;
}

function WorkbenchDashboard({ data, error, loading, onRefresh }) {
  if (loading && !data) {
    return (
      <div className="workbench-dashboard-panel">
        <div className="workbench-dashboard-toolbar">
          <div className="workbench-dashboard-meta">正在加载数据库 dashboard...</div>
          <Button loading type="primary">
            刷新
          </Button>
        </div>
        <Skeleton active paragraph={{ rows: 8 }} />
      </div>
    );
  }

  const sections = data?.sections || [];

  return (
    <div className="workbench-dashboard-panel">
      <div className="workbench-dashboard-toolbar">
        <div className="workbench-dashboard-meta">
          {renderUpdatedAt(data?.updatedAt)}
        </div>
        <Button loading={loading} type="primary" onClick={onRefresh}>
          刷新
        </Button>
      </div>

      {error && !data ? (
        <div className="workbench-dashboard-feedback error">{error}</div>
      ) : null}

      {sections.length === 0 && !loading ? (
        <div className="workbench-dashboard-empty">
          <Empty description="选择数据库后即可查看 dashboard" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        </div>
      ) : null}

      {sections.map((section) => {
        const sectionMeta = getStatusMeta(section.status);
        return (
          <section className="workbench-dashboard-section" key={section.key}>
            <div className="workbench-panel-heading compact">
              <div>
                <h3>{section.title}</h3>
                <p>通过只读 SQL 实时拉取当前实例与数据库信息</p>
              </div>
              <Tag color={sectionMeta.color}>{sectionMeta.text}</Tag>
            </div>
            <div className="workbench-dashboard-grid-cards">
              {(section.items || []).map((item) => {
                const itemMeta = getStatusMeta(item.status);
                return (
                  <article className={`workbench-dashboard-card status-${item.status}`} key={item.key}>
                    <div className="workbench-dashboard-card-header">
                      <span>{item.label}</span>
                      <Tag color={itemMeta.color}>{itemMeta.text}</Tag>
                    </div>
                    <div className="workbench-dashboard-card-value">
                      {item.value || '--'}
                      {item.unit ? <small>{item.unit}</small> : null}
                    </div>
                    <div className="workbench-dashboard-card-helper">
                      {item.message || '通过 SQL 查询得到的当前快照'}
                    </div>
                  </article>
                );
              })}
            </div>
          </section>
        );
      })}
    </div>
  );
}

export default WorkbenchDashboard;
