import React from 'react';
import {withRouter} from "react-router-dom";
import Navigation from './Navigation';
import PageContent from './PageContent';
import cookie from 'react-cookies'
import FetchHttpClient, { json } from 'fetch-http-client';
import config from "./config";

class JavaSqlAdmin extends React.Component {

    
    componentWillMount(){
        let token = cookie.load('token')
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/user/check',{headers: { 'User-Token': token }})
            .then(response => {
                if(!response.jsonData.status){
                    this.props.history.push('/login');
                    
                }
            })
    }
    render(){
        return (
            <div>
                <Navigation />
                <PageContent />
            </div>
            
        )
    }
}

export default withRouter(JavaSqlAdmin);