import React from 'react';
import ReactDOM from 'react-dom';
import './index.css';
import {
  BrowserRouter as Router,
  Switch,
  Route
} from "react-router-dom";
import App from './App';
import reportWebVitals from './reportWebVitals';


class Root extends React.Component {
  render() {
    return(
      <div>
        <Router>
          <Switch>
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
