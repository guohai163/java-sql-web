

const cacheLocalStorage = {
    set: function(key, value, ttl) {
        let date = new Date().getTime()+ ttl;

        let data = { value: value, expirse: new Date(date).getTime() };
        localStorage.setItem(key, JSON.stringify(data));
    },
    get: function(key) {

        let data = JSON.parse(localStorage.getItem(key));
        if (data !== null) {
            if (data.expirse != null && data.expirse < new Date().getTime()) {
                localStorage.removeItem(key);
            } else {
                return data.value;
            }
        }
        return null;
    }
}



export default cacheLocalStorage