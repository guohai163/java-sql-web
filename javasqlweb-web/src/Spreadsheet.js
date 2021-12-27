import React, {useEffect} from "react";
import XSpreadsheet from "x-data-spreadsheet";

import "x-data-spreadsheet/dist/xspreadsheet.css";

export default function Spreadsheet(props) {
    const sheetStyle = {showToolbar: false,
        showBottomBar: false,
        showContextmenu: true,

    };

    useEffect(()=>{
        console.log(props.data)
        if(props.data[0] !== undefined){


            const rows10 = { len: 100 };
            const cells = {len:100};
            Object.keys(props.data[0]).map((col,cindex) => {
                cells[cindex] = {text:col}
            })
            rows10[0] = {cells}

            const sheetData = props.data.map((row, rindex)=>{
                const cells = {len: 100};
                Object.keys(row).map((col,cindex) => {
                    cells[cindex] = {text:row[col].toString()}
                    // cols10[cindex] = {cindex:{text: row[col]}}
                })
                console.log(cells)
                rows10[rindex+1] = {

                    cells


                }

            })
            console.log(rows10)
            const sheet = new XSpreadsheet("#x-spreadsheet-demo", sheetStyle)
                .loadData({ name: 'sheet-test', rows: rows10 })
                .change(data=>{
                    console.log(data)
                });

        }

    }, props.data)
    return (
        <div id="x-spreadsheet-demo" ></div>

    );
}