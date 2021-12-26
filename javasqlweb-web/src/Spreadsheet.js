import React, {useEffect} from "react";
import XSpreadsheet from "x-data-spreadsheet";

import "x-data-spreadsheet/dist/xspreadsheet.css";

export default function Spreadsheet(props) {

    const sheetStyle = {showToolbar: false,
        showBottomBar: false,
        showContextmenu: true,};
    useEffect(()=>{
        console.log(props.data)

        const rows10 = { len: 1000 };
        const sheetData = props.data.map((row, index)=>{
            rows10[index] = {
                cells: {0:{text: 'A'},}
            }

        })
        console.log(rows10)
        const sheet = new XSpreadsheet("#x-spreadsheet-demo", sheetStyle)
            .loadData({ name: 'sheet-test', rows: rows10 })
            .change(data=>{
                console.log(data)
            });
    }, [])
    return (
        <div id="x-spreadsheet-demo" ></div>

    );
}