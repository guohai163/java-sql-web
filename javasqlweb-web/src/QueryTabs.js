import React from 'react';
import { Tabs } from 'antd';
import PageContent from "./PageContent";
const {TabPane } = Tabs;

const initialPanes = [
    {title: 'Tab 1', sql: '', data: [], key: '1'}
];
class QueryTabs extends React.Component {
    state = {
        activeKey: initialPanes[0].key,
        panes: initialPanes
    };
    TabsOnChange(){

    };

    render() {
        const {panes, activeKey} = this.state;
        return(
            <Tabs type="editable-card" onChange={this.TabsOnChange}></Tabs>
        );
    }
}

export default QueryTabs