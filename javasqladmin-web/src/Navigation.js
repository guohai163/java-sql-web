import React from 'react';
import './Navigation.css';
import logo from './images/logo_left.png'
import dot from './images/dot.gif'

class Navigation extends React.Component {
    render(){
        return (
            <div id='navigation'>
                <div id='navigation_resizer'></div>
                <div id="navigation_content">
                    <div id="navigation_header">
                        <div id="logo">
                            <img src={logo} alt="logo" />
                        </div>
                        <div id="navipanellinks">
                            <a href="#" title="设置">
                                <img src={dot} alt="setting" className="icon ic_s_cog"></img>
                            </a>
                            <a href="#" title="退出">
                                <img src={dot} alt="exit" className="icon ic_s_loggoff"></img>
                            </a>
                        </div>
                    </div>
                    <div id="navigation_tree">
                        <div className="navigation_server">
                            <label>服务器：</label>
                            <select id="select_server">
                                <option>a</option>
                            </select>
                        </div>
                    </div>
                </div>
            </div>
            
        )
    }
}

export default Navigation