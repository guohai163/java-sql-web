import {useEffect} from "react";


export default function DataDisplayFast(props) {

    function printTableHead(){
        if(props.data[0] !== undefined){

            let data = props.data[0]
            return (
                <tr>
                    {
                        Object.keys(data).map(key => {
                            return(<th>{key}</th>)
                        })
                    }
                </tr>
            );
        }
        else{
            return(
                <tr><th></th></tr>
            )
        }
    }

    function dataColumnShow(columnData) {
        if(null == columnData){
            return 'null'
        }
        switch(typeof columnData) {
            case 'boolean':
                return columnData?'true':'false';
            default:
                return columnData;
        }
    }

    function printTableData(){
        console.log("in print tableData"+ props.data.length)
        if(props.data !== undefined){
            let data = props.data

            return (
                data.map( row => {
                        return(<tr key={row[0]}>{
                            Object.keys(row).map( col => {
                                return (<td>{dataColumnShow(row[col])}</td>)
                            })
                        }</tr>)
                    }
                )

            );
        }
        else{
            return(
                <tr><td>无数据...</td></tr>
            )
        }
    }

    useEffect(()=>{
        console.log(props.data)
    },[props.dataAreaRefresh])
    return(
        <>
             <table className="table_results ajax pma_table">
                 <thead>
                {printTableHead()}

           </thead>
                 <tbody>
                    {printTableData()}
            </tbody>
             </table>
        </>
    );
}