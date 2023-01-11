import fetch from "node-fetch";
import _ from "lodash";

const configEndpoints = {
  imp: "pbsimpression",
  account: "pbsaccount",
  request: "pbsrequest"
};

const dataTypeToConfigType = {
    "imps": "imp",
    "requests": "request",
    "accounts": "account"
}

const copyIdForConfigTypes = ["account"];

const supportedOps = ["insert", "update"];

const pastForms = {
  insert: "inserted/updated",
  update: "updated",
};

function assert(condition, errorMsg) {
  if (!condition) {
    throw new AssertionError(errorMsg);
  }
}

class ApiError extends Error {
  #details

  constructor(details) {
    super(details.ResponseText);
    this.details = details;
  }

  set details(details) {
    this.#details = details;
  }
  get details() {
    return this.#details;
  }
}

class AssertionError extends Error {
    constructor(message) {
        super(message);
    }
}

export default class ApiClient {
  #apiBase;
  #userName;
  #password;
  #token;
  #debug

  constructor({ apiBase, userName, password, debug }) {
    assert(apiBase, "apiBase must not be empty");
    assert(userName, "userName must not be empty");
    assert(password, "password must not be empty");
    this.#apiBase = apiBase.replace(/\/$/, "") + "/";
    this.#userName = userName;
    this.#password = password;
    this.#debug = debug;
    this.#token = null;
  }

  #stringify(value, pretty) {
    pretty = pretty === undefined ? this.#debug : pretty;
    return JSON.stringify(
      value, null,
      pretty ? " ".repeat(4) : null
    )
  }

  async #callApi(endpoint, data) {
    const url = `${this.#apiBase}${endpoint}`;
    const body = this.#stringify({ data });
    const headers = {
      "Content-Type": "application/json",
    };

    if (this.#token) {
      headers["Authorization"] = this.#token;
    }

    const requestOptions = {
      method: "POST",
      cache: "no-cache",
      mode: "cors",
      headers,
      body,
    };
    if (this.#debug) {
      console.log(`Invoking ${endpoint} api:`, { url, ...requestOptions });
    }

    const response = await fetch(url, requestOptions);
    return response.json().then((res) => {
      if (!res.IsValid) {
        throw new ApiError(res);
      }
      return res.Data;
    });
  }

  async tryLogin() {
    console.log(`Trying login with userName: ${this.#userName} and provided password.`);
    const data = await this.#callApi("auth/login", {
      userName: this.#userName,
      userPassword: this.#password,
    });
    this.#token = data.Token;
    console.log("Login successful.");
  }

  async #ensureToken() {
    if (!this.#token) {
      throw new Error("Client not logged in. Call tryLogin() first.");
    }
  }

  async #callConfigApi(dataType, operation, dataKey, config) {
    this.#ensureToken();
    assert(dataTypeToConfigType[dataType], `Invalid dataType: ${dataType}`);
    const configType = dataTypeToConfigType[dataType];
    assert(configEndpoints[configType], `Invalid configType: ${configType} `);
    assert(supportedOps.indexOf(operation) >= 0, `Unsupported operation: ${operation} `);
    assert(config, `config must not be empty`);
    assert(dataKey, `dataKey must not be empty`);
    config = _.cloneDeep(config);
    assert(!config.id || config.id === dataKey,`${dataType} dataKey did not match config.id: ${dataKey} != ${config.id}`);

    const endpoint = `${configEndpoints[configType]}/${operation}`;

    let isActive = true;
    if (config.active !== undefined) {
      isActive = !!config.active;
      delete config.active;
    }

    if (copyIdForConfigTypes.includes(configType) && !config.id) {
      config.id = dataKey;
    }
    const data = {
      id: dataKey,
      config: this.#stringify(config, false),
      isActive,
    };
    console.log(`Calling ${operation} api for ${configType} with id: ${dataKey}`);
    const result = await this.#callApi(endpoint, data);
    console.log(`Successfully ${pastForms[operation]} ${configType} with id: ${dataKey}`);
    return result;
  }

  async insertConfig(dataKey, id, config) {
    return await this.#callConfigApi(dataKey, "insert", id, config);
  }

  // async updateConfig(configType, id, config) {
  //   return await this.#callConfigApi(configType, "update", id, config);
  // }

  async saveConfig(dataKey, id, config) {
    const methodsToTry = [
      this.insertConfig, /*this.updateConfig*/
    ];

    for (let index = 0; index < methodsToTry.length; index++) {
      try {
        const method = methodsToTry[index].bind(this);
        return await method(dataKey, id, config);
      } catch (error) {
        if (error instanceof AssertionError) {
          console.log(`AssertionError: ${error.message}`);
        } else if (error instanceof ApiError) {
          console.log(`ApiError: ${error.message}`, error.details);
        } else {
          throw error;
        }
      }
    }
    return null;
  }
}
