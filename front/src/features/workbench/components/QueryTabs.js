import React from 'react';
import { Tabs } from 'antd';

const initialPanes = [
    {title: 'Tab 1', sql: '', data: [], key: '1'}
];

function QueryTabs() {
    const [activeKey, setActiveKey] = React.useState(initialPanes[0].key);

    return (
        <Tabs
            activeKey={activeKey}
            items={initialPanes.map((pane) => ({
                key: pane.key,
                label: pane.title,
                children: null,
            }))}
            onChange={setActiveKey}
            type="editable-card"
        />
    );
}

export default QueryTabs
