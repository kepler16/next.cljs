(ns k16.next-cljs.core
  (:require [clojure.java.io :as io]
            [shadow.build :as comp]
            [shadow.build.data :as data]
            [shadow.build.output :as output]
            [shadow.build.targets.npm-module :as module]
            [shadow.cljs.util :as util]
            [cljs.compiler :as cljscomp]
            [clojure.string :as str]))

(defn js-module-src-prepend [state {:keys [output-name] :as src} require?]
  (let [dep-syms
        (data/deps->syms state src)

        roots
        (->> (get-in state [:compiler-env :shadow/ns-roots])
             (map cljscomp/munge))]

    (str (when require?
           (str "var $CLJS = require(\"./cljs_env_safe\");\n"
                "var $jscomp = $CLJS.$jscomp;\n"))

         ;; :npm-module is picky about this
         "var COMPILED = false;\n"

         (when require?
           ;; emit requires to actual files to ensure that they were loaded properly
           ;; can't ensure that the files were loaded before this as goog.require would
           (->> dep-syms
                (remove #{'goog})
                (map #(data/get-source-id-by-provide state %))
                (distinct)
                (map #(data/get-source-by-id state %))
                (remove :goog-src)
                (map (fn [{:keys [output-name] :as x}]
                       (str "require(\"./" output-name "\");")))
                (str/join "\n")))
         "\n"

         ;; take existing roots or create in case they don't exist yet
         (->> roots
              (map (fn [root]
                     (str "var " root "=$CLJS." root " || ($CLJS." root " = {});")))
              (str/join "\n"))
         "\n"
         "\n$CLJS.SHADOW_ENV.setLoaded(" (pr-str output-name) ");\n"
         "\n")))

(defn flush-dev-js-modules
  [{:shadow.build/keys [build-info] :as state} mode config]


  (util/with-logged-time [state {:type :npm-flush
                                 :output-path (.getAbsolutePath (get-in state [:build-options :output-dir]))}]

    (let [env-file
          (data/output-file state "cljs_env.js")

          env-content
          (str (output/js-module-env state config)
               "\n"
               (->> (:build-sources state)
                    (remove #{output/goog-base-id})
                    (map #(data/get-source-by-id state %))
                    (filter :goog-src)
                    (map #(data/get-output! state %))
                    (map :js)
                    (str/join "\n")))

          env-modified?
          (or (not (.exists env-file))
              (not= env-content (slurp env-file)))

          env-safe-file
          (data/output-file state "cljs_env_safe.js")

          env-safe-content (slurp (io/resource "k16/next_cljs/cljs_env_safe.js"))

          env-safe-modified?
          (or (not (.exists env-safe-file))
              (not= env-safe-content (slurp env-safe-file)))]


      (when env-modified?
        (io/make-parents env-file)
        (spit env-file env-content))


      (when env-safe-modified?
        (io/make-parents env-safe-file)
        (spit env-safe-file env-safe-content))

      (doseq [src-id (:build-sources state)
              :when (not= src-id output/goog-base-id)
              :let [src (get-in state [:sources src-id])]
              :when (and (not (:goog-src src))
                         (not (util/foreign? src)))]

        (let [{:keys [output-name last-modified]}
              src

              {:keys [js] :as output}
              (data/get-output! state src)

              target
              (data/output-file state output-name)]

          ;; flush everything if env was modified, otherwise only flush modified
          (when (or env-modified?
                    (contains? (:compiled build-info) src-id)
                    (not (.exists target))
                    (>= last-modified (.lastModified target)))

            (let [prepend
                  (js-module-src-prepend state src true)

                  content
                  (str prepend
                       js
                       (output/js-module-src-append state src)
                       (output/generate-source-map state src output target prepend))]

              (spit target content)))))))

  state)

(defn target-alpha [{::comp/keys [mode stage config] :as state}]
  ;; (println config)
  (case stage
    ;; :configure
    ;; (configure state mode config)

    ;; :resolve
    ;; (resolve state mode config)

    ;; :compile-prepare
    ;; (node/replace-goog-global state)

    :flush
    (case mode
      :dev
      (flush-dev-js-modules state mode config)
      :release
      (output/flush-optimized state))

    (module/process state)))

(defn hook-alpha
  {:shadow.build/stage :flush}
  ([build-state]
   (hook-alpha build-state {}))
  ([build-state options]
   build-state))
