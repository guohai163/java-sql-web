import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import WorkbenchDashboard from '@/features/workbench/components/WorkbenchDashboard';

describe('WorkbenchDashboard', () => {
  test('renders dashboard sections and refresh action', async () => {
    const refreshSpy = jest.fn();
    render(
      <WorkbenchDashboard
        data={{
          updatedAt: '2026-04-17 10:00:00',
          sections: [
            {
              key: 'system',
              title: '系统信息',
              status: 'partial',
              items: [
                {
                  key: 'version',
                  label: '版本',
                  value: '8.0.36',
                  status: 'ok',
                  message: '',
                },
                {
                  key: 'memory',
                  label: '内存',
                  value: '--',
                  status: 'unsupported',
                  message: '当前数据库不支持该指标',
                },
              ],
            },
          ],
        }}
        error=""
        loading={false}
        onRefresh={refreshSpy}
      />,
    );

    expect(screen.getByText('系统信息')).toBeInTheDocument();
    expect(screen.getByText('8.0.36')).toBeInTheDocument();
    expect(screen.getByText('当前数据库不支持该指标')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /刷\s*新/ }));
    expect(refreshSpy).toHaveBeenCalledTimes(1);
  });

  test('renders empty state when dashboard has no data', () => {
    render(
      <WorkbenchDashboard
        data={null}
        error=""
        loading={false}
        onRefresh={() => {}}
      />,
    );

    expect(screen.getByText('选择数据库后即可查看 dashboard')).toBeInTheDocument();
  });
});
