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
        if(undefined === token){
            this.props.history.push('/login');
        }
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/version').then(response => {
            if(response.jsonData.status){
                config.version = response.jsonData.data
            }
        })
        client.get('/user/check',{headers: { 'User-Token': token }})
            .then(response => {
                if(response.status !== 200){
                    this.props.history.push('/login');
                }
                else if(!response.jsonData.status){
                    this.props.history.push('/login');
                    
                }
                else {
                    config.userName = response.jsonData.data.userName;
                }
            })
            .catch(rejected => {
                console.log('catch',rejected)
                this.props.history.push('/login');
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