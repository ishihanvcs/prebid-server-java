const path = require("path");
const debug = require("debug")("routes:config");
const fs = require("fs");
const yaml = require("js-yaml");
const accountsDataPath = path.resolve(__dirname, "../../../improvedigital-config/settings.yaml");
const requestsDataPath = path.resolve(__dirname, "../../../stored-data/requests");
const impsDataPath = path.resolve(__dirname, "../../../stored-data/imps");

const paramNames = {
    ACCOUNT: "account-ids",
    REQUEST: "request-ids",
    IMP: "imp-ids",
    LAST_MODIFIED: "last-modified",
};

const resultKeys = {
    ACCOUNT: "accounts",
    REQUEST: "requests",
    IMP: "imps",
};

const jsonRegEx = /\.json$/;

function extractIds(req, paramName) {
    let ids = null;
    if (req.query[paramName]) {
        ids = JSON.parse(req.query[paramName]);
    }
    return ids;
}

function getAccounts(accountIds) {
    const doc = yaml.load(fs.readFileSync(accountsDataPath, "utf8"));
    const allAccounts = doc.accounts;
    const result = {};
    if (!accountIds || accountIds.length === 0) {
        accountIds = Object.values(allAccounts).map((acc) => acc.id);
    }
    accountIds.forEach((id) => {
        const acc = allAccounts.find((a) => {
            return a.id == id;
        });
        result[id] = acc !== undefined ? acc : null;
    });
    return result;
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

function getRequestData(dataPath, ids, result, resultKey) {
    if (ids && ids.length) {
        result[resultKey] = getJson(dataPath, ids);
    }
}

function getAllRequestIds(dataPath) {
    return fs
        .readdirSync(dataPath, {
            encoding: "utf-8",
            withFileTypes: true,
        })
        .filter((f) => f.isFile() && jsonRegEx.test(f.name) && f.name !== "default.json")
        .map((f) => f.name.replace(jsonRegEx, ""));
}

function getRandomInt(max) {
    return Math.floor(Math.random() * max);
}

module.exports = function (req, res) {
    const result = {
        [resultKeys.ACCOUNT]: {},
        [resultKeys.REQUEST]: {},
        [resultKeys.IMP]: {},
    };
    debug("query: %O", req.query);
    if (!req.query.amp && !req.query.video) {
        let accountIds = extractIds(req, paramNames.ACCOUNT);
        let requestIds = extractIds(req, paramNames.REQUEST);
        let impIds = extractIds(req, paramNames.IMP);
        if (accountIds !== null || req.query.accounts) {
            if (req.query[paramNames.LAST_MODIFIED]) {
                const allAccounts = getAccounts(null);
                accountIds = Object.keys(allAccounts);
                accountIds = accountIds.length > 0 ? [accountIds[getRandomInt(accountIds.length)]] : null;
            }
            result[resultKeys.ACCOUNT] = getAccounts(accountIds);
        } else {
            requestIds = requestIds || getAllRequestIds(requestsDataPath);
            impIds = impIds || getAllRequestIds(impsDataPath);
            if (req.query[paramNames.LAST_MODIFIED]) {
                requestIds = requestIds.length > 0 ? [requestIds[getRandomInt(requestIds.length)]] : null;
                impIds = impIds.length > 0 ? [impIds[getRandomInt(impIds.length)]] : null;
            }
            getRequestData(requestsDataPath, requestIds, result, resultKeys.REQUEST);
            getRequestData(impsDataPath, impIds, result, resultKeys.IMP);
        }
    }
    debug("result: %O", result);
    res.send(result);
};
