import { getOptions, showHelp, getData } from "./utils.mjs";
import ApiClient from './api.mjs';
import _ from "lodash";

const dataKeys = ["imps", "requests", "accounts"];

async function saveData(api, data) {
    const stats = {};
    for (let i = 0; i < dataKeys.length; i++) {
        const dataKey = dataKeys[i];
        stats[dataKey] = (stats[dataKey] || {});
        if (data[dataKey] && _.isPlainObject(data[dataKey])) {
            const configIds = Object.keys(data[dataKey]);
            for (let j = 0; j < configIds.length; j++) {
                const configId = configIds[j];
                const config = data[dataKey][configId];
                const retVal = await api.saveConfig(dataKey, configId, config);
                stats[dataKey][configId] = !!retVal;
            }
        }
    }
    return stats;
}

(async () => {
    try {
        const options = getOptions();
        const data = await getData(options.dataFilePath);
        const api = new ApiClient(options);
        await api.tryLogin();
        const stats = await saveData(api, data);
        console.log("Stats for save operation:", stats);
    } catch (e) {
        console.log(`Error: ${e.message}`);
        showHelp();
    }
})();
