(ns core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hawk.core :as hawk]
   [model :as model]
   [codegen :as gen])
  (:gen-class))

(defn read-dsl [filename]
  (with-open [r (io/reader filename)]
    (read (java.io.PushbackReader. r))))

(defn code->str
  [seq]
  (for [x (flatten seq)]
    (case x
      :indent "    "
      :nl "\r\n"
      (str x))))

(defn print-code [seq] (doseq [x seq] (print x)))

(defn edn? [file] (re-matches #".*\.edn" (.getName file)))

(defn interesting? [_ {:keys [file]}]
  (and (.isFile file) (edn? file)))

(defn walk [dirpath]
  (doall (filter edn? (file-seq (io/file dirpath)))))

(defn gen-file
  [file]
  (let [
        _ (println (str "Reading " file  "... "))
        model (-> file
                  .getPath
                  read-dsl
                  model/dsl->model)
        lines (-> model
                  gen/model->code
                  code->str)
        out (io/file (.getParent file) (str (:file model)))
        ]
    (println (str "  Saving to " out))
    (with-open [w (io/writer (str out))]
      (doseq [l lines] (.write w l)))
    (println "  Done")))

(defn -main [& args]
  (let [
        [dir] args
        dir (or dir ".")
        ]
    (doseq [file (walk dir)]
      (gen-file file))
    
    (let [watcher (hawk/watch!
                   [{:paths [dir]
                     :filter interesting?
                     :handler (fn [ctx e] (gen-file (:file e)))
                     }])]
      (read-line)
      (hawk/stop! watcher))
    ))
