import arg from "arg";
import _ from "lodash";
import path from "path";
import getStdin from 'get-stdin';
import { readFileSync, existsSync } from 'fs';

import { fileURLToPath } from 'url';
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PROJECT_DIR = path.resolve(__dirname, '../../../');

const defaultOptions = {
    apiBase: process.env.HEADERLIFT_API_BASE || 'https://api.headerlift.com/',
    userName: process.env.AZERION_SSO_USER,
    password: process.env.AZERION_SSO_PASSWORD,
    dataFilePath: null,
    debug: false,
}

const argToOptKeys = {
    "--user": "userName",
    "--password": "password",
    "--file": "dataFilePath",
    "--api-base": "apiBase",
    "--debug": "debug",
}

const argSpec = {
    "--user": String,
    "--api-base": String,
    "--password": String,
    "--file": String,
    "--debug": Boolean,
    "-u": "--user",
    "-p": "--password",
    "-f": "--file"
}

function validatedOpts({ apiBase, userName, password, dataFilePath, debug }) {
    if (!userName) {
        throw new Error("apiUserName must not be empty");
    }

    if (!password) {
        throw new Error("apiPassword must not be empty");
    }

    if (!apiBase) {
        throw new Error("apiPassword must not be empty");
    }

    if (dataFilePath && !existsSync(dataFilePath)) {
        let resolvedFilePath = path.resolve(PROJECT_DIR, dataFilePath);

        if (!existsSync(resolvedFilePath)) {
            resolvedFilePath = path.resolve(__dirname, dataFilePath)
        }

        if (!existsSync(resolvedFilePath)) {
            throw new Error(`${dataFilePath} does not exist.`);
        }
        dataFilePath = resolvedFilePath;
    }

    return {
        apiBase, userName, password, dataFilePath, debug
    }
}

export function getOptions() {
    const parsedArgs = arg(argSpec, { permissive: true });
    const opts = _.extend({}, defaultOptions);
    _.forOwn(argToOptKeys, (value, key) => {
        if (parsedArgs[key]) {
            opts[value] = parsedArgs[key];
        }
    });

    if (!opts.dataFilePath && parsedArgs._.length > 0) {
        opts.dataFilePath = parsedArgs._[0];
    }

    return validatedOpts(opts);
}

export async function getData(dataFilePath) {
    let content;
    if (dataFilePath) {
        content = readFileSync(dataFilePath, 'utf8');
    } else {
        content = await getStdin();
    }

    if (content) {
        return JSON.parse(content);
    }
    throw Error("No input data provided.");
}

export function showHelp() {
    console.log("Usage: node index.mjs --user=apiUserName  --password=apiPassword [--api-base=https://api.headerlift.com/] [--file=dataFilePath] [dataFilePath] [--debug]");
}


