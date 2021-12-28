import React, {useEffect} from "react";
import XSpreadsheet from "x-data-spreadsheet";

import "x-data-spreadsheet/dist/xspreadsheet.css";

export default function Spreadsheet(props) {
    let sheetStyle = {showToolbar: false,
        showBottomBar: false,
        showContextmenu: true,

    };
    const sheetEl = React.useRef(null);
    const sheetRef = React.useRef(null);
    const styles = [
            {
                "bgcolor": "#93d051"
            }
            ]
    function dataTransfer(param) {

        if(param[0] !== undefined) {

            const rows10 = {};
            const cells = {};
            let colNum=0;
            Object.keys(props.data[0]).map((col,cindex) => {
                colNum++;
                cells[cindex] = {text:col, style: 0}
            })
            sheetStyle = {showToolbar: false,
                showBottomBar: false,
                mode: 'read',
                showContextmenu: false,
                row:{len:param.length+1},
                col:{len:colNum},
            view:{height: ()=> (param.length+4)*25}}

            rows10[0] = {cells}

            props.data.map((row, rindex)=>{
                const cells = {};
                Object.keys(row).map((col,cindex) => {

                    cells[cindex] = {text:row[col] === null?'null':row[col].toString()}
                })
                rows10[rindex+1] = {
                    cells
                }

            })
            return {name:"t",rows: rows10,styles};
        }
        return {};
    }



    useEffect(()=>{

        const sheetData = dataTransfer(props.data)

        const element = sheetEl.current;
        const sheet = new XSpreadsheet("#x-spreadsheet-demo", sheetStyle)

        sheet.loadData(sheetData)
        sheetRef.current = sheet;
        return () => {
            element.innerHTML = "";
        };

    }, [props.dataAreaRefresh])
    return (
        <>
            <div id="x-spreadsheet-demo" ref={sheetEl}></div>
        </>

    );
}