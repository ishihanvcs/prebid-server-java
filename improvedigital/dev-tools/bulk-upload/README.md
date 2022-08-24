## Options

* **apiBase** (optional): can be specified via **HEADERLIFT_API_BASE** environment variable and/or `--api-base` option. If `--api-base` option is provided, it's value will be used instead of value of `HEADERLIFT_API_BASE` environment variable (if exist). If neither is available, then default value `https://api.headerlift.com/` will be used.
* **userName** (required): can be specified via `AZERION_SSO_USER` environment variable and/or `--user` (short form: `-u`) option. If `--user` option is provided, it's value will be used instead of value of `AZERION_SSO_USER` environment variable (if exist).
* **password** (required): can be specified via `AZERION_SSO_PASSWORD` environment variable and/or `--password` (short form: `-p`) option. If `--password` option is provided, it's value will be used instead of value of `AZERION_SSO_PASSWORD` environment variable (if exist).
* **dataFilePath** (optional): can be specified via `--file` (short form: `-f`) option and/or as the first argument to the script. If `--file` is provided, it's value will be used instead of value of first argument. If neither is available, then `stdin` will be read for the content of data file.
* **debug** (optional): can be specified via `--debug` option. If `--debug` option is provided, additional debug info will be logged into console.

## Structure of data

```json
{
  "imps": {
    "3246": {
      "ext": {
        "prebid": {
          "improvedigitalpbs": {
            "accountId": "1220"
          },
          "bidder": {
            "improvedigital": {
              "placementId": 3246
            },
            "appnexus": {
              "placement_id": 18785899
            },
            "outbrain": {
              "publisher": {
                "id": "1234"
              }
            }
          }
        }
      }
    }
  }
}
```

## Examples

```sh
# run via node: dataFilePath as --file option and userName & password via environment variable

AZERION_SSO_USER=<userName> AZERION_SSO_PASSWORD=<password> node index.mjs --file=<dataFilePath>
```

```sh
# run via node: dataFilePath as --file option

node index.mjs --user=<userName> --password=<password> --file=<dataFilePath>
```

```sh
# run via node: dataFilePath as the argument
node index.mjs -u <userName> -p <password> <dataFilePath>
```

```sh
# run via node: enable debug mode
node index.mjs -u <userName> -p <password> --debug <dataFilePath>
```

```sh
# run via node: read data from stdin

cat <dataFilePath> | node index.mjs -u <userName> -p <password>
```

```sh
# run via npm script: read data from stdin

cat <dataFilePath> | npm run bulk-upload -- -u <userName> -p <password>
```

```sh
# run via npm script: read data from stdin

cat <dataFilePath> | AZERION_SSO_USER=<userName> AZERION_SSO_PASSWORD=<password> npm run bulk-upload
```

```sh
# run via npm script: AZERION_SSO_USER and AZERION_SSO_PASSWORD are set globally in shell environment

cat <dataFilePath> | npm run bulk-upload
```

```sh
# run via npm script: enable debug mode

cat <dataFilePath> | npm run bulk-upload -- --debug
```
