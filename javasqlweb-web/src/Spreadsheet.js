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

    function dataTransfer(param) {

        if(param[0] !== undefined) {

            const rows10 = {};
            const cells = {};
            let colNum=0;
            Object.keys(props.data[0]).map((col,cindex) => {
                colNum++;
                cells[cindex] = {text:col}
            })
            sheetStyle = {showToolbar: false,
                showBottomBar: false,
                mode: 'read',
                showContextmenu: false,
                row:{len:param.length+2},
                col:{len:colNum}}

            rows10[0] = {cells}

            props.data.map((row, rindex)=>{
                const cells = {};
                Object.keys(row).map((col,cindex) => {
                    console.log(cindex)
                    console.log(row[col])
                    cells[cindex] = {text:row[col] === null?'null':row[col].toString()}
                })
                rows10[rindex+1] = {
                    cells
                }

            })
            console.log(rows10)
            return {name:"t",rows: rows10};
        }
        return {};
    }



    useEffect(()=>{
        console.log("in useEffect")

        console.log(dataTransfer(props.data))
        const element = sheetEl.current;
        const sheet = new XSpreadsheet("#x-spreadsheet-demo", sheetStyle)

        sheet.loadData(dataTransfer(props.data))

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