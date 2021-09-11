(ns example.app
  (:require ["react" :as r]))

(defn ^:export page []
  (let [[x set-x] (r/useState "")]
    (r/createElement "div" #js {:onClick (fn [_] (set-x (str x "a")))}
                     "Hello from CLJS!!" x)))
