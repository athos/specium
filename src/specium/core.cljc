(ns specium.core
  (:require [clojure.spec.alpha :as s]
            #?(:cljs [cljs.compiler :as comp])))

(def ^:dynamic *eval-fn*
  #?(:clj eval :cljs nil))

(defn- eval* [x]
  (*eval-fn* x))

(defmulti ->spec* (fn [x] (when (seq? x) (first x))))
(defmethod ->spec* :default [x]
  (eval* x))

(defn- resolve* [x]
  #?(:clj (resolve x)
     :cljs (try
             ;; I'm not sure this is the right way to resolve a symbol
             ;; in CLJS
             (js/eval (str (comp/munge multi-name)))
             (catch js/Error _ nil))))

(defn ->spec [x]
  (cond (or (keyword? x) (set? x)) x
        (symbol? x) (resolve* x)
        :else (->spec* x)))

(defmethod ->spec* `s/spec [[_ form]]
  (s/spec-impl form (eval* form) nil nil))

(defmethod ->spec* `s/and [[_ & pred-forms]]
  (s/and-spec-impl pred-forms (mapv ->spec pred-forms) nil))

(defmethod ->spec* `s/merge [[_ & pred-forms]]
  (s/merge-spec-impl pred-forms (mapv ->spec pred-forms) nil))

(defmethod ->spec* `s/or [[_ & key-pred-forms]]
  (let [pairs (partition 2 key-pred-forms)
        keys (mapv first pairs)
        pred-forms (mapv second pairs)]
    (s/or-spec-impl keys pred-forms (mapv ->spec pred-forms) nil)))
