import fetch from "node-fetch";
import _ from "lodash";

const configEndpoints = {
  imp: "pbsimpression",
  // "account": "pbsaccount",
  // "request": "pbsrequest"
};

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

  constructor({ apiBase, userName, password }) {
    assert(apiBase, "apiBase must not be empty");
    assert(userName, "userName must not be empty");
    assert(password, "password must not be empty");
    this.#apiBase = apiBase.replace(/\/$/, "") + "/";
    this.#userName = userName;
    this.#password = password;
    this.#token = null;
  }

  async #callApi(endpoint, data) {
    const url = `${this.#apiBase}${endpoint}`;
    const body = JSON.stringify({ data });
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
    // console.log({ url, ...requestOptions });
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
    const data = {
      id,
      config: JSON.stringify(config, null, " ".repeat(4)),
      isActive,
    };
    return await this.#callApi(endpoint, data);
  }

  async insertConfig(configType, id, config) {
    return await this.#callConfigApi(configType, "insert", id, config);
  }

  async updateConfig(configType, id, config) {
    return await this.#callConfigApi(configType, "update", id, config);
  }

  async saveConfig(configType, id, config) {
    for (let index = 0; index < supportedOps.length; index++) {
      const operation = supportedOps[index];
      try {
        console.log(`Calling ${operation} api for ${configType} with id: ${id}`);
        const data = await this.#callConfigApi(configType, operation, id, config);
        console.log(`Successfully ${pastForms[operation]} ${configType} with id: ${id}`);
        return data;
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
