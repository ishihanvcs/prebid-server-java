#!/usr/bin/env node

const fetch = require('node-fetch')
const path = require('path')
const fs = require('fs')
const colors = require('colors')

const bidderParamsDir = path.resolve(
  __dirname,
  '../src/main/resources/static/bidder-params',
)

const hbModulesUrl =
  'https://hb.improvedigital.com/pbw/prebid/prebid-idhb-v6.6-modules.json'

const impSchemaRelativePath = 'json-schema/stored-imp.json'
const requestSchemaRelativePath = 'json-schema/stored-request.json'
const impSchemaPath = path.resolve(__dirname, impSchemaRelativePath)
const requestSchemaPath = path.resolve(__dirname, requestSchemaRelativePath)
const bidderModuleRe = /BidAdapter$/

async function getEnabledBidders() {
  const response = await fetch(hbModulesUrl)
  const data = await response.json()
  return data
    .filter((module) => bidderModuleRe.test(module))
    .map((module) => module.replace(bidderModuleRe, ''))
}

async function run() {
  const enabledBidders = await getEnabledBidders()
  const bidderRefs = {}
  enabledBidders.forEach((bidder) => {
    const schemaPath = path.resolve(bidderParamsDir, `${bidder}.json`)
    if (fs.existsSync(schemaPath)) {
      try {
        const schema = JSON.parse(fs.readFileSync(schemaPath))
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

  const impSchema = JSON.parse(fs.readFileSync(impSchemaPath))

  impSchema.definitions.imp_ext_prebid_bidder = {
    type: 'object',
    additionalProperties: false,
    minProperties: 1,
    properties: bidderRefs,
  }

  fs.writeFileSync(impSchemaPath, JSON.stringify(impSchema, null, 2))
  console.log(
    `${impSchemaRelativePath} successfully updated with latest bidder params`
      .green,
  )

  const requestSchema = JSON.parse(fs.readFileSync(requestSchemaPath))

  Object.keys(impSchema.definitions).forEach((defName) => {
    requestSchema.definitions[defName] = impSchema.definitions[defName]
  })

  const specialAttributes = ['definitions', '$schema']

  Object.keys(impSchema).forEach((key) => {
    if (!specialAttributes.includes(key)) {
      requestSchema.definitions.imp[key] = impSchema[key]
    }
  })

  fs.writeFileSync(requestSchemaPath, JSON.stringify(requestSchema, null, 2))
  console.log(
    `${requestSchemaRelativePath} successfully updated with latest imp definition`
      .green,
  )
}

run()
