(ns zero-one.geni.polymorphic
  (:refer-clojure :exclude [alias
                            count
                            filter
                            first
                            last
                            max
                            min
                            shuffle])
  (:require
    [zero-one.geni.column :refer [->col-array ->column]]
    [zero-one.geni.dataset]
    [zero-one.geni.dataset-creation]
    [zero-one.geni.interop :as interop]
    [zero-one.geni.utils :refer [->string-map arg-count]])
  (:import
    (org.apache.spark.ml.stat Correlation)
    (org.apache.spark.sql Dataset
                          RelationalGroupedDataset
                          functions)))

(defmulti as (fn [head & _] (class head)))
(defmethod as :default [expr new-name] (.as (->column expr) (name new-name)))
(defmethod as Dataset [dataframe new-name] (.as dataframe (name new-name)))
(def alias as)

(defmulti count class)
(defmethod count :default [expr] (functions/count (->column expr)))
(defmethod count Dataset [dataset] (.count dataset))
(defmethod count RelationalGroupedDataset [grouped] (.count grouped))

(defmulti explain (fn [head & _] (class head)))
(defmethod explain :default [expr extended] (.explain (->column expr) extended))
(defmethod explain Dataset
  ([dataset] (.explain dataset))
  ([dataset extended] (.explain dataset extended)))

(defmulti mean (fn [head & _] (class head)))
(defmethod mean :default [expr & _] (functions/mean (->column expr)))
(defmethod mean RelationalGroupedDataset [grouped & col-names]
  (.mean grouped (interop/->scala-seq (clojure.core/map name col-names))))
(def avg mean)

(defmulti max (fn [head & _] (class head)))
(defmethod max :default [expr] (functions/max (->column expr)))
(defmethod max RelationalGroupedDataset [grouped & col-names]
  (.max grouped (interop/->scala-seq (clojure.core/map name col-names))))

(defmulti min (fn [head & _] (class head)))
(defmethod min :default [expr] (functions/min (->column expr)))
(defmethod min RelationalGroupedDataset [grouped & col-names]
  (.min grouped (interop/->scala-seq (clojure.core/map name col-names))))

(defmulti sum (fn [head & _] (class head)))
(defmethod sum :default [expr] (functions/sum (->column expr)))
(defmethod sum RelationalGroupedDataset [grouped & col-names]
  (.sum grouped (interop/->scala-seq (clojure.core/map name col-names))))

(defmulti coalesce (fn [head & _] (class head)))
(defmethod coalesce Dataset [dataframe n-partitions]
  (.coalesce dataframe n-partitions))
(defmethod coalesce :default [& exprs]
  (functions/coalesce (->col-array exprs)))

(defmulti shuffle class)
(defmethod shuffle :default [expr]
  (functions/shuffle (->column expr)))
(defmethod shuffle Dataset [dataframe]
  (zero-one.geni.dataset/sort dataframe (functions/randn)))

(defmulti first class)
(defmethod first Dataset [dataframe]
  (-> dataframe (zero-one.geni.dataset/take 1) clojure.core/first))
(defmethod first :default [expr] (functions/first (->column expr)))

(defmulti last class)
(defmethod last Dataset [dataframe]
  (-> dataframe (zero-one.geni.dataset/tail 1) clojure.core/first))
(defmethod last :default [expr] (functions/last (->column expr)))

(defmulti filter (fn [head & _] (class head)))
(defmethod filter Dataset [dataframe expr]
  (.filter dataframe (.cast (->column expr) "boolean")))
(defmethod filter :default [expr predicate]
  (let [scala-predicate (if (= (arg-count predicate) 2)
                          (interop/->scala-function2 predicate)
                          (interop/->scala-function1 predicate))]
    (functions/filter (->column expr) scala-predicate)))
(def where filter)

(defmulti to-json (fn [head & _] (class head)))
(defmethod to-json Dataset [dataframe] (.toJSON dataframe))
(defmethod to-json :default
  ([expr] (functions/to_json (->column expr) {}))
  ([expr options]
   (functions/to_json (->column expr) (->string-map options))))

(defmulti to-df (fn [head & _] (class head)))
(defmethod to-df :default [spark table col-names]
  (zero-one.geni.dataset-creation/table->dataset spark table col-names))
(defmethod to-df Dataset
  ([dataframe] (.toDF dataframe))
  ([dataframe col-names] (.toDF dataframe (interop/->scala-seq (map name col-names)))))

(defmulti corr (fn [head & _] (class head)))
(defmethod corr :default [l-expr r-expr]
  (functions/corr (->column l-expr) (->column r-expr)))
(defmethod corr Dataset
  ([dataframe col-name]
   (Correlation/corr dataframe (name col-name)))
  ([dataframe col-name1 col-name2]
   (-> dataframe .stat (.corr (name col-name1) (name col-name2))))
  ([dataframe col-name1 col-name2 method]
   (-> dataframe .stat (.corr (name col-name1) (name col-name2) method))))