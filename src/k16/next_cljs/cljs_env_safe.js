if (!global.$CLJSRUN) {
  global.$CLJSRUN = require("./cljs_env.js")
}
module.exports = global.$CLJSRUN;
