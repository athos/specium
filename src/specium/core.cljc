(ns specium.core
  (:require [clojure.core :as c]
            [clojure.spec.alpha :as s]
            #?(:cljs [cljs.compiler :as comp])))

(def ^:dynamic *eval-fn*
  #?(:clj eval :cljs nil))

(defn- eval* [x]
  (*eval-fn* x))

(defmulti ->spec* (fn [x] (when (seq? x) (first x))))
(defmethod ->spec* :default [x]
  (eval* x))

(defn- resolve* [x]
  #?(:clj @(resolve x)
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
  (s/spec-impl form (->spec form) nil nil))

(defmethod ->spec* `s/and [[_ & pred-forms]]
  (s/and-spec-impl pred-forms (mapv ->spec pred-forms) nil))

(defmethod ->spec* `s/merge [[_ & pred-forms]]
  (s/merge-spec-impl pred-forms (mapv ->spec pred-forms) nil))

(defmethod ->spec* `s/or [[_ & key-pred-forms]]
  (let [pairs (partition 2 key-pred-forms)
        keys (mapv first pairs)
        pred-forms (mapv second pairs)]
    (s/or-spec-impl keys pred-forms (mapv ->spec pred-forms) nil)))

(defmethod ->spec* `s/tuple [[_ & preds]]
  (s/tuple-impl preds (mapv ->spec preds)))

(defmethod ->spec* `s/every [[_ pred & {:keys [into kind count max-count min-count distinct gen-max gen] :as opts}]]
  (let [desc (or (::s/describe opts)
                 `(s/every ~pred
                           ~@(c/into [] (comp (remove #(= (key %) ::s/describe))
                                             cat)
                                    opts)))
        nopts (-> opts
                  (dissoc :gen ::s/describe)
                  (assoc ::s/kind-form kind ::s/describe desc))
        cpreds (cond-> [(or (some-> kind resolve*) coll?)]
                 count (conj #(= count (bounded-count count %)))

                 (or min-count max-count)
                 (conj #(<= (or min-count 0)
                            (bounded-count (if max-count
                                             (inc max-count)
                                             min-count)
                                           %)
                            (or max-count Integer/MAX_VALUE)))

                 distinct (conj #(or (empty? %) (apply distinct? %))))]
    (s/every-impl pred
                  (->spec pred)
                  (assoc nopts ::s/cpred (fn [x] (every? #(% x) cpreds)))
                  (some-> gen ->spec))))

(defmethod ->spec* `s/coll-of [[_ pred & opts]]
  (let [desc `(s/coll-of ~pred ~@opts)]
    (->spec `(s/every ~pred ::s/conform-all true ::s/describe ~desc ~@opts))))
