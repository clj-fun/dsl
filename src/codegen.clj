(ns codegen
  (:require
   [clojure.string :as str]
   ))

(defn indent
  "Indents a code block by inserting :indent before lines"
  [& seq]
  (->> seq
       flatten
       (filter some?)
       (reduce
        (fn [agg next]
          (if (or (empty? agg) (= :nl (peek agg)))
            (conj agg :indent next)
            (conj agg next)))
        [])))

(defn brackets
  "Wraps sequence in bracket"
  [s]
  (if (seq s)
    [:nl "{" :nl s "}" :nl]
    [" {}" :nl]))

(defn parens [s] ["( " s " )"])

(defn gen-property
  [m]
  (let [{:keys [order prop type]} m]
    ["[DataMember(Order = " order ")] public " type " " prop " { get; private set; }" :nl]))

(defn gen-assignment [m] [(:prop m) " = " (:name m) ";" :nl])


(defn gen-assert-body
  [name schema]
  (if-not (sequential? schema)
    (case schema
      NotNull [name " != null"]
      schema)
    (let [[op & args] schema]
      (if (str/starts-with? (str op) ".")
        (gen-assert-body (str name op) (first args))
        (case op
          and (interpose " && " (map #(parens (gen-assert-body name %)) args))
          [ name " " (str op) " " args]
          )))))

(defn gen-assert
  [f]
  (let [{:keys [schema name]} f]
    (if schema
      ["if (!(   " (gen-assert-body name schema) "   ))" :nl :indent " throw new ArgumentException( \"" name "\", \"Violated schema '" (str schema) "'\" );" :nl])))

(defn gen-arg [m] [(:type m) " " (:name m)])
(defn gen-array-init [m] [(:prop m) " = new " (:array-of m) "[0];" :nl])

(defn gen-private-ctor
  [name fields]
  (let [arrays (filter #(:array-of %) fields)]
    [name " ()" (brackets  (indent (map gen-array-init arrays)))]))

(defn gen-public-ctor
  [name fields]
  ["public " name " (" (interpose ", " (map gen-arg fields)  ) ")"
   (brackets
    (indent
     (map gen-assert fields)
     (map gen-assignment fields))
    )])

(defn gen-to-string
  [txt names]
  ["public override string ToString()"
   (brackets
    (indent
     ["return string.Format(@\"" txt "\", " (str/join ", " names) ");" :nl]))])

(defn gen-contract
  [model]
  (let [{:keys [name base fields string extern]} model]
    [
     "[DataContract(Namespace = \"" extern "\")]" :nl
     "public partial class " name (if base [" : " (str/join ", " base)])
     (brackets
      (if (seq fields)
        (indent
         (map gen-property fields) :nl
         (gen-private-ctor name fields) :nl
         (gen-public-ctor name fields)
         (if string [:nl (apply gen-to-string string)]))))]))

(defn gen-agg [agg] (map gen-contract (:messages agg)))

(defn gen-service-call [msg kind char]
  (if (= kind (:kind msg))
    ["void When(" (:name msg) " " char ");" :nl]))

(defn gen-service
  [agg]
  (let [{:keys [name messages]} agg]
    (when (some? name)
      [
       :nl "public interface I" name "ApplicationService"
       (brackets
        (indent
         (map #(gen-service-call % 'cmd "c") messages)))

       :nl "public interface I" name "State"
       (brackets
        (indent
         (map #(gen-service-call % 'evt "e") messages)))
       ])))

(defn model->code
  "Generates C# code tree given the model"
  [model]
  (let [{:keys [using namespace aggs]} model]
    (concat
     (map #(list "using " % ";" :nl) using)
     [
      "// ReSharper disable PartialTypeWithSinglePart" :nl
      "// ReSharper disable UnusedMember.Local" :nl
      "namespace " namespace
      (brackets
       (indent
        "#region Generated by Lokad Code DSL" :nl
        (map gen-agg aggs)
        (map gen-service (reverse aggs))
        "#endregion" :nl))
      ])))
