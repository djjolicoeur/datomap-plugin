(ns leiningen.datomap-plugin
  (:require [leiningen.core.eval :refer (eval-in-project)]
            [leiningen.core.project :as project]))


(defn datomap-plugin
  "Run datomap from lein. This is most useful when working with an application
  that runs in a headless environment such as a docker container or headless VM
  as it allows you to:
    1) render an graphviz image of your DB schema to your
       local machine
    2) allows you to dump a copy of your schema
  with the output of both going to the enviroment from which the plugin was
  called, i.e. your local machine.

  Usage:
   1) lein datomap :uri <datomic uri>
      --renders graphiz table representation to screen
   2) lein datomap :uri <datomic uri> :graph-type \"nodes\"
     --renders graphiz directed graph where every attribute is a node
   3) lein datomap :uri <datomic uri> :op \"save-graph\"
                   :file-out \"/path/to/file.png\"
     --save graphiz tables of schema to file
   4) lein datomap :uri <datomic uri> :op \"dump-schema \"
                   :file-out \"path/to/file.end>\"
     --dumps edn file of schema attributes to :file-out"
  [project & args]
  (eval-in-project
   (project/merge-profiles project
                           [{:dependencies
                             [['djjolicoeur/datomap "0.1.1-SNAPSHOT"]]}])
   `(do
      (require 'datomic.api)
      (require 'datomap.core)
      (require 'datomap.io)
      (import 'javax.swing.JFrame)
      (letfn [;; Parse arg kv pain
              (parse-arg# [[k# v#]]
                (let [kw# (keyword (subs k# 1))]
                  [kw# v#]))
              ;; Parse plugin arg vector
              (parse-args# [args#]
                (let [arg-count# (count args#)]
                  (if (even? arg-count#)
                    (into {} (map parse-arg# (apply hash-map args#)))
                    (throw
                     (ex-info (str "Even number of forms required! got: " arg-count#)
                              {:args args#
                               :causes #{:invalid-args}})))))
              ;; Render graphviz graph
              (render-graph# [graph-type# db#]
                (let [jframe# (case graph-type#
                               "tables" (datomap.io/show-schema-tables! db#)
                               "nodes" (datomap.io/show-schema-nodes! db#))]
                  (.setDefaultCloseOperation jframe# JFrame/EXIT_ON_CLOSE)
                  (while true (Thread/sleep 500))))
              ;; save graphviz image
              (save-graph# [file-out# db#]
                (if file-out#
                  (datomap.io/save-schema-tables! db# file-out#)
                  (throw (ex-info "No File Specified! pass in :file-out <file>"
                                  {:causes #{:no-file-out}})))
                (System/exit 0))
              ;; dump schema file
              (dump-edn-schema# [file-out# db#]
                (if file-out#
                  (datomap.core/schema->edn db# file-out#)
                  (throw (ex-info "No File Specified! pass in :file-out <file>"
                                  {:causes #{:no-file-out}})))
                (System/exit 0))
              ;; plugin entry point
              (plugin# [args#]
                (let [{op# :op
                       uri# :uri
                       graph-type# :graph-type
                       file-out# :file-out
                       :as parsed-args#
                       :or {op# "graph"
                            graph-type# "tables"}} (parse-args# args#)
                      conn# (datomic.api/connect uri#)
                      db# (datomic.api/db conn#)]
                  (case op#
                    "graph" (render-graph# graph-type# db#)
                    "save-graph" (save-graph# file-out# db#)
                    "dump-schema" (dump-edn-schema# file-out# db#))))]
        ;; call plugin
        (plugin# [~@args])))))
