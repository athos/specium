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
  (when form
    (s/spec-impl form (->spec form) nil nil)))

(defmethod ->spec* `s/multi-spec [[_ mm retag]]
  ;; Essentially identical to `delay`, but won't cache the result.
  ;; This behavior successfully emulates deref'ing a var.
  (let [v (reify #?(:clj clojure.lang.IDeref
                    :cljs cljs.core.IDeref)
            (deref [this] (resolve* mm)))]
    (s/multi-spec-impl mm v (->spec retag))))

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

(defmethod ->spec* `s/every-kv [[_ kpred vpred & opts]]
  (let [desc `(s/every-kv ~kpred ~vpred ~@opts)]
    (->spec `(s/every (s/tuple ~kpred ~vpred)
                      ::s/kfn ~(fn [i v] (nth v 0))
                      :into {} ::s/describe ~desc ~@opts))))

(defmethod ->spec* `s/coll-of [[_ pred & opts]]
  (let [desc `(s/coll-of ~pred ~@opts)]
    (->spec `(s/every ~pred ::s/conform-all true ::s/describe ~desc ~@opts))))

(defmethod ->spec* `s/map-of [[_ kpred vpred & opts]]
  (let [desc `(s/map-of ~kpred ~vpred ~@opts)]
    (->spec `(s/every-kv ~kpred ~vpred ::s/conform-all true :kind map? ::s/describe ~desc ~@opts))))

(defmethod ->spec* `s/* [[_ pred-form]]
  (s/rep-impl pred-form (->spec pred-form)))

(defmethod ->spec* `s/+ [[_ pred-form]]
  (s/rep+impl pred-form (->spec pred-form)))

(defmethod ->spec* `s/? [[_ pred-form]]
  (s/maybe-impl (->spec pred-form) pred-form))

(defmethod ->spec* `s/alt [[_ & key-pred-forms]]
  (let [pairs (partition 2 key-pred-forms)
        keys (mapv first pairs)
        pred-forms (mapv second pairs)]
    (s/alt-impl keys (mapv ->spec pred-forms) pred-forms)))

(defmethod ->spec* `s/cat [[_ & key-pred-forms]]
  (let [pairs (partition 2 key-pred-forms)
        keys (mapv first pairs)
        pred-forms (mapv second pairs)]
    (s/cat-impl keys (mapv ->spec pred-forms) pred-forms)))

(defmethod ->spec* `s/& [[_ re & preds]]
  (let [pv (vec preds)]
    (s/amp-impl (->spec re) (mapv ->spec pv) pv)))

(defmethod ->spec* `s/fspec [[_ & {:keys [args ret fn gen] :or {ret `any?}}]]
  (s/fspec-impl (->spec `(s/spec ~args)) args
                (->spec `(s/spec ~ret)) ret
                (->spec `(s/spec fn)) fn
                (some-> gen ->spec)))

(defmethod ->spec* `s/conformer [[_ f unf]]
  (if unf
    (s/spec-impl `(s/conformer ~f ~unf) (->spec f) nil true (->spec unf))
    (s/spec-impl `(s/conformer ~f) (->spec f) nil true)))

(defmethod ->spec* `s/nonconforming [[_ spec]]
  (s/nonconforming (->spec spec)))
