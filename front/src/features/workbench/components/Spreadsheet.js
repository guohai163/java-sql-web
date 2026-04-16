import React, { useEffect, useRef } from 'react';
import XSpreadsheet from 'x-data-spreadsheet';

import 'x-data-spreadsheet/dist/xspreadsheet.css';

export default function Spreadsheet(props) {
  const sheetEl = useRef(null);
  const sheetId = `x-spreadsheet-${props.dataId}`;
  const styles = [
    {
      bgcolor: '#93d051',
    },
  ];

  function dataTransfer(param) {
    if (param[0] !== undefined) {
      const rows10 = {};
      const cells = {};
      let colNum = 0;

      Object.keys(param[0]).forEach((col, cindex) => {
        colNum += 1;
        cells[cindex] = { text: col, style: 0 };
      });

      const sheetStyle = {
        showToolbar: false,
        showBottomBar: false,
        mode: 'read',
        showContextmenu: false,
        row: { len: param.length + 1 },
        col: { len: colNum },
        view: {
          height: () => (param.length + 4) * 25,
          width: () => colNum * 110,
        },
      };

      rows10[0] = { cells };

      param.forEach((row, rindex) => {
        const rowCells = {};
        Object.keys(row).forEach((col, cindex) => {
          rowCells[cindex] = {
            text: row[col] === null ? 'null' : row[col].toString(),
          };
        });
        rows10[rindex + 1] = {
          cells: rowCells,
        };
      });

      return {
        sheetStyle,
        sheetData: { name: 't', rows: rows10, styles },
      };
    }

    return {
      sheetStyle: {
        showToolbar: false,
        showBottomBar: false,
        mode: 'read',
        showContextmenu: false,
      },
      sheetData: {},
    };
  }

  useEffect(() => {
    const element = sheetEl.current;
    if (!element) {
      return undefined;
    }

    element.innerHTML = '';
    const { sheetData, sheetStyle } = dataTransfer(props.data);
    const sheet = new XSpreadsheet(`#${sheetId}`, sheetStyle);
    sheet.loadData(sheetData);

    return () => {
      element.innerHTML = '';
    };
  }, [props.data, props.dataAreaRefresh, sheetId]);

  return <div id={sheetId} ref={sheetEl}></div>;
}
