import { getOptions, showHelp, getData } from "./utils.mjs";
import ApiClient from './api.mjs';
import getStdin from 'get-stdin';
import _ from "lodash";

const configTypeToDataKey = {
    "imp": "imps"
}

async function saveData(api, data) {
    const configTypes = Object.keys(configTypeToDataKey);
    const stats = {};
    for (let i = 0; i < configTypes.length; i++) {
        const configType = configTypes[i];
        const dataKey = configTypeToDataKey[configType];
        stats[dataKey] = (stats[dataKey] || {});
        if (data[dataKey] && _.isPlainObject(data[dataKey])) {
            const configIds = Object.keys(data[dataKey]);
            for (let j = 0; j < configIds.length; j++) {
                const configId = configIds[j];
                const config = data[dataKey][configId];
                const retVal = await api.saveConfig(configType, configId, config);
                stats[dataKey][configId] = !!retVal;
            }
        }
    }
    return stats;
};

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
