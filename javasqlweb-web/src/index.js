import React from 'react';
import ReactDOM from 'react-dom';
import './index.css';
import {
  BrowserRouter as Router,
  Switch,
  Route
} from "react-router-dom";
import JavaSqlAdmin from './JavaSqlAdmin';
import reportWebVitals from './reportWebVitals';
import Admin from './Admin';
import Login from './Login';
import SqlGuid from "./sqlGuid";


class Root extends React.Component {
  render() {
    return(
      <div>
        <Router>
          <Switch>
            <Route path="/login"><Login /></Route>
            <Route path="/admin"><Admin /></Route>
            <Route path="/guid"><SqlGuid /></Route>
            <Route path="/"><JavaSqlAdmin /></Route>
          </Switch>

        </Router>
      </div>
      
    )
  }
}

ReactDOM.render(
  <Root />,
  document.getElementById('root')
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
