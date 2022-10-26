#!/usr/bin/env node

const fetch = require('node-fetch')
const path = require('path')
const fs = require('fs')
const yaml = require('js-yaml')
const colors = require('colors')
const _ = require('lodash')

const bidderConfigDir = path.resolve(
  __dirname,
  '../../src/main/resources/bidder-config',
)

const bidderParamsDir = path.resolve(
  __dirname,
  '../../src/main/resources/static/bidder-params',
)

const applicationConfigPath = path.resolve(
  __dirname,
  '../../deployment/templates/config.yaml.j2',
)

const templatePath = path.resolve(__dirname, './schema-templates')
const outputPath = path.resolve(__dirname, '../json-schema')

const impSchemaTplPath = path.resolve(templatePath, 'imp.json')
const impSchemaOutputPath = path.resolve(outputPath, 'imp.json')

const requestSchemaTplPath = path.resolve(templatePath, 'request.json')
const requestSchemaOutputPath = path.resolve(outputPath, 'request.json')

const ansibleTagsRe = /^\s*\{%\s*.+\s*%\}\s*$/gm

function readBidderConfig(configPath, contentModifier) {
  let content = fs.readFileSync(configPath, 'utf8');
  if (_.isFunction(contentModifier)) {
    content = contentModifier(content);
  }
  const config = yaml.load(content)
  return config.adapters
}

function resolveSupportedBidders() {
  const files = fs.readdirSync(bidderConfigDir);
  const aliasToBidderMap = {};
  const supportedBidders = {};
  files.forEach(file => {
    const adapters = readBidderConfig(path.resolve(bidderConfigDir, file));
    if (adapters) {
      _.each(adapters, (config, bidderName) => {
        supportedBidders[bidderName] = config;
        if (config.aliases) {
          Object.keys(config.aliases).forEach(alias => {
            aliasToBidderMap[alias] = bidderName;
          });
        }
      })
    }
  })
  return { supportedBidders, aliasToBidderMap }
}

const { supportedBidders, aliasToBidderMap } = resolveSupportedBidders();

function getEnabledBidders() {
  const adapters = readBidderConfig(applicationConfigPath, content => content.replace(ansibleTagsRe, ''));
  return Object.keys(adapters)
}


function resolveSchema(bidderOrAlias) {
  const actualBidder = aliasToBidderMap[bidderOrAlias] ? aliasToBidderMap[bidderOrAlias] : bidderOrAlias;
  const possibleNames = [actualBidder];

  if (supportedBidders[actualBidder] &&
          supportedBidders[actualBidder].usersync &&
          supportedBidders[actualBidder].usersync['cookie-family-name']
  ) {
    possibleNames.push(supportedBidders[actualBidder].usersync['cookie-family-name']);
  }

  if (actualBidder !== bidderOrAlias) {
    possibleNames.push(bidderOrAlias);
  }

  for (let i = 0; i < possibleNames.length; i++) {
    const schemaPath = path.resolve(bidderParamsDir, `${possibleNames[i]}.json`)
    if (fs.existsSync(schemaPath)) {
      try {
        const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'))
        delete schema.$schema
        return schema;
      } catch (e) {
        console.log(`JSON could not be parsed for bidder: ${possibleNames[i].red}`)
      }
    }
  }
  return null;
}

async function run() {
  const enabledBidders = getEnabledBidders()
  const bidderRefs = {}
  const bidderDefinitions = {}
  enabledBidders.forEach((bidder) => {
    const isAlias = !!aliasToBidderMap[bidder];
    const actualBidder = isAlias ? aliasToBidderMap[bidder] : bidder;
    const refName = `bidder_config_${actualBidder}`;
    if (!bidderDefinitions[refName]) {
      const schema = resolveSchema(bidder)
      if (schema) {
        bidderDefinitions[refName] = schema
      } else {
        console.log(`Bidder not supported by PBS: ${bidder.brightRed}`)
      }
    }

    if (bidderDefinitions[refName]) {
      bidderRefs[bidder] = {
        "$ref": `#/definitions/${refName}`
      };
      console.log(`Schema found for bidder: ${bidder.green}`)
    }
  })

  const impSchema = JSON.parse(fs.readFileSync(impSchemaTplPath))

  impSchema.definitions.imp_ext_prebid_bidder.properties = bidderRefs
  impSchema.definitions = _.extend(impSchema.definitions, bidderDefinitions)

  fs.writeFileSync(impSchemaOutputPath, JSON.stringify(impSchema, null, 2))
  console.log(`imp schema successfully updated with latest bidder params`.green)

  const requestSchema = JSON.parse(
    fs.readFileSync(requestSchemaTplPath, 'utf8'),
  )

  Object.keys(impSchema.definitions).forEach((defName) => {
    requestSchema.definitions[defName] = impSchema.definitions[defName]
  })

  const specialAttributes = ['definitions', '$schema']

  Object.keys(impSchema).forEach((key) => {
    if (!specialAttributes.includes(key)) {
      requestSchema.definitions.imp[key] = impSchema[key]
    }
  })

  fs.writeFileSync(
    requestSchemaOutputPath,
    JSON.stringify(requestSchema, null, 2),
  )
  console.log(
    `request schema successfully updated with latest imp definition`.green,
  )
}

run()
