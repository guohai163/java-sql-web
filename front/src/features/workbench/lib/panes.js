
export function getArray(panes, key) {
    let lastIndex;

    panes.forEach((pane, i) => {

      if(pane.key === key){
          lastIndex = i;
      }
    });
    return panes[lastIndex];
}

export function editArray(panes, key, data) {
    let lastIndex=-1;
    panes.forEach((pane, i) => {
        if(pane.key === key){
            lastIndex = i;
        }
    });
    if(lastIndex>=0) {
        panes[lastIndex] = data;
    }
    return panes;
}