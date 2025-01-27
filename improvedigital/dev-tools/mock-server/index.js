const express = require("express");
const app = express();
const bodyParser = require("body-parser");
const logger = require("morgan");
const configRoute = require("./modules/config");
const pbsRoute = require("./modules/pbs");
const PORT = 8989;

app.use(bodyParser.urlencoded({ extended: false }));
app.use(bodyParser.json());
app.use(logger("dev"));

app.get("/config", configRoute);
app.post("/pbs", pbsRoute);

app.listen(PORT, function () {
    console.log(`Server is listening on port ${PORT}`);
});
