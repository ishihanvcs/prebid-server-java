const path = require("path");
const debug = require("debug")("routes:config");
const fs = require("fs");
const yaml = require("js-yaml");

const projectRoot = path.resolve(__dirname, "../../../../");
// const configRoot = "imporvedigital";
const configRoot = "local";

const dataKeys = Object.freeze({
    ACCOUNT: "accounts",
    REQUEST: "requests",
    IMP: "imps",
});

const dataFilePaths = Object.freeze({
    [dataKeys.ACCOUNT]: path.resolve(projectRoot, `${configRoot}/config/settings.yaml`),
    [dataKeys.REQUEST]: path.resolve(projectRoot, `${configRoot}/stored-data/requests`),
    [dataKeys.IMP]: path.resolve(projectRoot, `${configRoot}/stored-data/imps`)
});

const paramNames = Object.freeze({
    ACCOUNT: "account-ids",
    REQUEST: "request-ids",
    IMP: "imp-ids",
    LAST_MODIFIED: "last-modified",
});


const jsonRegEx = /\.json$/;

const dataCache = {};

const deletedEntry = {
    deleted: true
};

function loadAllAccounts() {
    const doc = yaml.load(fs.readFileSync(dataFilePaths[dataKeys.ACCOUNT], "utf8"));
    const allAccounts = doc.accounts || {};
    const result = {};
    Object.values(allAccounts).forEach((account) => {
        result[account.id] = account;
    });
    return result;
}

function getAllJsonObjectIds(dataType) {
    const dataPath = dataFilePaths[dataType];
    return fs
            .readdirSync(dataPath, {
                encoding: "utf-8",
                withFileTypes: true,
            })
            .filter((f) => f.isFile() && jsonRegEx.test(f.name) && f.name !== "default.json")
            .map((f) => f.name.replace(jsonRegEx, ""));
}


function getJson(dataPath, ids) {
    const result = {};
    ids.forEach((id) => {
        try {
            const data = fs.readFileSync(path.resolve(dataPath, `${id}.json`), "utf8");
            result[id] = JSON.parse(data);
        } catch (e) {
            result[id] = null;
        }
    });
    return result;
}

function getRandomInt(max) {
    return Math.floor(Math.random() * max);
}

function getRandomChoice(...args) {
    return args[getRandomInt(args.length)];
}

function getData(dataType, {ids, randomDelete} = {ids: null, randomDelete: false}) {
    let data;
    if (dataCache[dataType] !== undefined) {
        data = dataCache[dataType]
    } else {
        if (dataType === "accounts") {
            data = loadAllAccounts();
        } else {
            const allIds = getAllJsonObjectIds(dataType) || [];
            data = getJson(dataFilePaths[dataType], allIds);
        }
        dataCache[dataType] = data;
    }

    if (!ids && !randomDelete) {
        return data;
    }

    if (!ids) {
        ids = []
        let availableIds = Object.keys(data);
        const targetCount = getRandomInt(availableIds.length);
        let count = 0;
        while (count < targetCount) {
            const randomId = getRandomChoice(...availableIds);
            ids.push(randomId);
            availableIds = availableIds.filter((id) => id !== randomId);
            count++;
        }
    }

    const result = {}
    for (let i = 0; i < ids.length; i++) {
        const isDeleted = randomDelete ? getRandomChoice(true, false) : false;
        const id = ids[i];
        result[id] = isDeleted || data[id] === undefined ? deletedEntry : data[id];
    }

    return result;
}

function extractIds(req, paramName) {
    let ids = null;
    if (req.query[paramName]) {
        ids = JSON.parse(req.query[paramName]);
    }
    return ids;
}

module.exports = function (req, res) {
    const result = {
        [dataKeys.ACCOUNT]: {},
        [dataKeys.REQUEST]: {},
        [dataKeys.IMP]: {},
    };
    debug("query: %O", req.query);
    if (!req.query.amp && !req.query.video) {
        const accountIds = extractIds(req, paramNames.ACCOUNT);
        const randomDelete = req.query[paramNames.LAST_MODIFIED] !== undefined
        if (accountIds !== null || req.query.accounts) {
            result[dataKeys.ACCOUNT] = getData(dataKeys.ACCOUNT, {ids: accountIds, randomDelete})
        } else {
            const requestIds = extractIds(req, paramNames.REQUEST);
            const impIds = extractIds(req, paramNames.IMP);
            result[dataKeys.REQUEST] = getData(dataKeys.REQUEST, {ids: requestIds, randomDelete})
            result[dataKeys.IMP] = getData(dataKeys.IMP, {ids: impIds, randomDelete})
        }
    }
    debug("result: %O", result);
    res.send(result);
};
