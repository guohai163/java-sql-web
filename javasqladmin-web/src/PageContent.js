import React from 'react';

class PageContent extends React.Component {
    constructor(props){
        super(props)
        this.state = {
            selectServer: '0'
        }
    }

    render(){
        return (
            <div className='page_content'>{this.state.selectServer}
            </div>
            
        )
    }
}

export default PageContent