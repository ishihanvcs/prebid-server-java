#!/usr/bin/env node

const child_process = require("child_process");
const path = require("path");
const fs = require("fs");

const globalNodeModulesPath = child_process.execSync("npm root --quiet -g", { encoding: "utf-8" }).trim();
const storedRequestDir = path.resolve(__dirname, "../stored-data/requests");
const defaultDataDir = path.resolve(__dirname, "./data");
const defaultDataFileName = "default-request.json";
const dataFileNameRE = /\.json$/;
const JSON_INDENT_WITH = 2;

const { mergeWith, isPlainObject } = globalRequire("lodash");

function globalRequire(moduleName) {
    try {
        return require(moduleName);
    } catch (e) {
        try {
            return require(path.resolve(globalNodeModulesPath, moduleName));
        } catch (e) {
            console.error(
                `This script requires ${moduleName} to be installed as a global module. Please install using 'npm install -g ${moduleName}' command.`
            );
            process.exit(1);
        }
    }
}

function readAndParseFile(fileName, fileDir = storedRequestDir) {
    const filePath = path.resolve(fileDir, fileName);
    try {
        const content = fs.readFileSync(filePath, { encoding: "utf-8" });
        return JSON.parse(content);
    } catch (err) {
        console.log(`Error reading file ${fileName}`, err);
    }
    return false;
}

function writeJsonToFile(data, fileName) {
    const filePath = path.resolve(storedRequestDir, fileName);
    const content = JSON.stringify(data, null, JSON_INDENT_WITH);
    try {
        fs.writeFileSync(filePath, content);
    } catch (err) {
        console.log(`Error writing file ${fileName}`, err);
    }
}

const defaultData = readAndParseFile(defaultDataFileName, defaultDataDir);

function mergeWithDefaultAndWrite(fileName) {
    const data = readAndParseFile(fileName);
    if (!data) {
        return writeJsonToFile(defaultData, fileName);
    }
    const mergedData = mergeWith(data, defaultData, (objValue, srcValue, key) => {
        if (srcValue === undefined || !isPlainObject(objValue)) {
            return objValue;
        }
        if (objValue === undefined) {
            return srcValue;
        }
        return undefined;
    });
    writeJsonToFile(mergedData, fileName);
    console.info(`Merged data from ${defaultDataFileName} into ${fileName} successfully`);
}

if (defaultData === false) {
    console.log(`No file with name ${defaultDataFileName} found, nothing to do!!!`);
    process.exit(0);
}

const dirEntries = fs.readdirSync(storedRequestDir, { withFileTypes: true });
console.log(`Processing data files located in ${storedRequestDir}\n`);
dirEntries.forEach((entry) => {
    if (entry.isFile() && dataFileNameRE.test(entry.name)) {
        mergeWithDefaultAndWrite(entry.name);
    }
});
