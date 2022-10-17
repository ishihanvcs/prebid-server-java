import fetch from "node-fetch";
import _ from "lodash";

const configEndpoints = {
  imp: "pbsimpression",
  account: "pbsaccount",
  request: "pbsrequest"
};

const copyIdForTypes = ["account"];

const supportedOps = ["insert", "update"];

const pastForms = {
  insert: "inserted",
  update: "updated",
};

function assert(condition, errorMsg) {
  if (!condition) {
    throw new Error(errorMsg);
  }
}

export class ApiError extends Error {
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

  async #callConfigApi(configType, operation, id, config) {
    this.#ensureToken();
    assert(configEndpoints[configType], `Invalid configType: ${configType} `);
    assert(supportedOps.indexOf(operation) >= 0, `Unsupported operation: ${operation} `);
    assert(config, `config must not be empty`);
    assert(id, `id must not be empty`);
    config = _.cloneDeep(config);
    const endpoint = `${configEndpoints[configType]}/${operation}`;

    let isActive = true;
    if (config.active !== undefined) {
      isActive = !!config.active;
      delete config.active;
    }

    if (copyIdForTypes.includes(configType)) {
        config.id = id;
    }
    const data = {
      id,
      config: this.#stringify(config, false),
      isActive,
    };
    console.log(`Calling ${operation} api for ${configType} with id: ${id}`);
    const result = await this.#callApi(endpoint, data);
    console.log(`Successfully ${pastForms[operation]} ${configType} with id: ${id}`);
    return result;
  }

  async insertConfig(configType, id, config) {
    return await this.#callConfigApi(configType, "insert", id, config);
  }

  async updateConfig(configType, id, config) {
    return await this.#callConfigApi(configType, "update", id, config);
  }

  async saveConfig(configType, id, config) {
    const methodsToTry = [
      this.insertConfig, this.updateConfig
    ];

    for (let index = 0; index < methodsToTry.length; index++) {
      try {
        const method = methodsToTry[index].bind(this);
        return await method(configType, id, config);
      } catch (error) {
        if (error instanceof ApiError) {
          console.log(error.message, error.details);
        } else {
          throw error;
        }
      }
    }
    return null;
  }
}
