(ns leiningen.datomap-plugin
  (:require [leiningen.core.eval :refer (eval-in-project)]
            [leiningen.core.project :as project]))


(defn datomap-plugin
  "This should not be loaded when lein runs."
  [project & args]
  (eval-in-project
   (project/merge-profiles project
                           [{:dependencies
                             [['djjolicoeur/datomap "0.1.1-SNAPSHOT"]]}])
   `(do
      (require 'datomic.api)
      (require 'datomap.core)
      (require 'datomap.io)
      (println "ABOUT TO LETFN's")
      (letfn [;; Parse arg kv pain
              (parse-arg# [[k# v#]]
                (let [kw# (keyword (subs k# 1))]
                  [kw# v#]))
              ;; Parse plugin arg vector
              (parse-args# [args#]
                (println args#)
                (let [arg-count# (count args#)]
                  (if (even? arg-count#)
                    (into {} (map parse-arg# (apply hash-map args#)))
                    (throw
                     (ex-info (str "Even number of forms required! got: " arg-count#)
                              {:args args#
                               :causes #{:invalid-args}})))))
              ;; Render graphviz graph
              (render-graph# [graph-type# db#]
                (case graph-type#
                  "tables" (datomap.io/show-schema-tables! db#)
                  "nodes" (datomap.io/show-schema-nodes! db#)))
              ;; save graphviz image
              (save-graph# [file-out# db#]
                (if file-out#
                  (datomap.io/save-schema-tables! db# file-out#)
                  (throw (ex-info "No File Specified! pass in :file-out <file>"
                                  {:causes #{:no-file-out}}))))
              ;; dump schema file
              (dump-edn-schema# [file-out# db#]
                (if file-out#
                  (datomap.core/schema->edn db# file-out#)
                  (throw (ex-info "No File Specified! pass in :file-out <file>"
                                  {:causes #{:no-file-out}}))))
              ;; plugin entry point
              (plugin# [args#]
                (println args#)
                (let [{op# :op
                       uri# :uri
                       graph-type# :graph-type
                       file-out# :file-out
                       :as parsed-args#
                       :or {op# "graph"
                            graph-type# "tables"}} (parse-args# args#)
                      conn# (datomic.api/connect uri#)
                      db# (datomic.api/db conn#)]
                  (println "GETS HERE")
                  (case op#
                    "graph" (render-graph# graph-type# db#)
                    "save-graph" (save-graph# file-out# db#)
                    "dump-schema" (dump-edn-schema# file-out# db#))))]
        ;; call plugin
        (println "ABOUT TO CALL PLUGIN")
        (println ~@args)
        (plugin# [~@args])))))
