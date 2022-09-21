#!/usr/bin/env node

const fetch = require('node-fetch')
const path = require('path')
const fs = require('fs')
const yaml = require('js-yaml')
const colors = require('colors')

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

function getEnabledBidders() {
  const content = fs.readFileSync(applicationConfigPath, 'utf8').replace(ansibleTagsRe, '');
  const config = yaml.load(content)
  return Object.keys(config.adapters)
}

async function run() {
  const enabledBidders = getEnabledBidders()
  const bidderRefs = {}
  enabledBidders.forEach((bidder) => {
    const schemaPath = path.resolve(bidderParamsDir, `${bidder}.json`)
    if (fs.existsSync(schemaPath)) {
      try {
        const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'))
        delete schema.$schema
        bidderRefs[bidder] = schema
        console.log(`Schema found for bidder: ${bidder.green}`)
      } catch (e) {
        console.log(`JSON could not be parsed for bidder: ${bidder.red}`)
      }
    } else {
      console.log(`Bidder not supported by PBS: ${bidder.brightRed}`)
    }
  })

  const impSchema = JSON.parse(fs.readFileSync(impSchemaTplPath))

  impSchema.definitions.imp_ext_prebid_bidder.properties = bidderRefs

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
