(ns model
  (:require [clojure.string :as str]
            [clojure.walk :as walk]))

(defn- switch-case [s fn]
  (let [[first & rest] s]
    (str/join (cons (fn first) rest))))

(defn- camel [s] (switch-case s str/lower-case))
(defn- pascal [s] (switch-case s str/upper-case))

(defn- interpolate
  "Takes a string and a collection of fields to create a .NET format string"
  [s fields]
  (take 2
        (reduce
         (fn [acc field]
           (let [
                 [s matched idx] acc
                 prop (:prop field)
                 id (or (:id field) prop)
                 regex (re-pattern (str "(?i)\\{(" (str/join "|" [prop (name id)]) ")\\}"))
                 ]
             (if (re-find regex s)
               [(str/replace s regex (str "{" idx "}")) (conj matched prop) (inc idx)]
               [s matched idx]))
           )
         [s [] 0] fields)))


(defn- field
  ([type] (field type type))
  ([type name] (field type name nil))
  ([type name schema] (if (vector? type)
     (let [type (first type)]
       (assoc (field (str type "[]") name schema) :array-of (str type)))
     {:name (camel (str name)) :type (str type) :prop (pascal (str name)) :schema schema})))

(defn unwrap-field
  "field can be :const [type] [type name] [type name schema]"
  [index fld agg]
  (let [
        tuple (if (keyword? fld) (get-in agg [:const fld]) fld)
        id (if-not (seq? fld) fld)
        f (apply field tuple)
        schema (:schema f)
        schema (walk/prewalk #(if (keyword? %) (get-in agg [:schema %]) %) schema)
        ]
    (assoc (apply field tuple) :id id :order (inc index) :schema schema)))

(defn ->seq
  "Convert symbol to vector or return it"
  [x]
  (cond
    (nil? x)
    nil (sequential? x) x
    :else (vector x)))

(defn unwrap-message- [msg agg const extern]
  (let [
        [kind id fs txt cfg] msg
        fs (if (= kind 'rec) fs  (concat (:common agg) fs))
        clean (filter some? (map-indexed #(if %2 (unwrap-field %1 %2 agg)) fs))
        hs (if txt (interpolate txt clean))
        ;; We take base from the aggregate (by kind) and concat
        ;; with message-level additions
        base (->> kind keyword agg ->seq (concat (->seq (:base cfg))) (apply hash-set))
        ]
    {
     :name id
     :fields clean
     :string hs
     :kind kind
     :base base
     :extern extern
     }
    ))

(defn unwrap-agg- [cfg agg]
  (let [
        ;; transfer const into agg
        agg (merge-with merge agg (select-keys cfg [:const :schema]))
        extern (:extern cfg)
        {:keys [event command const messages]} agg
        ]
    (println (str "    - aggregate " (or (:name agg) "default")))
    (assoc agg :messages (map #(unwrap-message- % agg const extern) messages))))

(defn dsl->model [cfg]
  (let [{:keys [const aggs extern]} cfg]
    (assoc cfg :aggs (map #(unwrap-agg- cfg %) aggs))))
