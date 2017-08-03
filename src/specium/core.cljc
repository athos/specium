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
             (js/eval (comp/munge (str x)))
             (catch :default _ nil))))

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
  (let [v #?(:clj (reify clojure.lang.IDeref
                    (deref [this] (resolve* mm)))
             :cljs (reify IDeref
                     (-deref [this] (resolve* mm))))]
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

(defn- parse-req [rk f]
  (letfn [(rec [x]
            (if (keyword? x)
              (let [k (f x)]
                [`(contains? ~'% ~k)
                 (fn [x] (contains? x k))])
              (let [[op & args] x, form-pred-pairs (map rec args)
                    forms (map first form-pred-pairs)
                    preds (map second form-pred-pairs)]
                (case op
                  and [`(and ~@forms)
                       (fn [x] (every? #(% x) preds))]
                  or [`(or ~@forms)
                      (fn [x] (some #(% x) preds))]))))]
    (map rec rk)))

(defmethod ->spec* `s/keys [[_ & {:keys [req req-un opt opt-un gen]}]]
  (let [unk #(-> % name keyword)
        req-keys (filterv keyword? (flatten req))
        req-un-specs (filterv keyword? (flatten req-un))
        req-specs (into req-keys req-un-specs)
        req-keys (into req-keys (map unk req-un-specs))
        opt-keys (into (vec opt) (map unk opt-un))
        opt-specs (into (vec opt) opt-un)
        form-pred-pairs (-> [[`(map? ~'%) map?]]
                            (into (parse-req req identity))
                            (into (parse-req req-un unk)))
        pred-forms (mapv (fn [[form _]] `(fn [~'%] ~form))
                         form-pred-pairs)
        pred-exprs (mapv second form-pred-pairs)
        keys-pred (fn [x] (every? #(% x) pred-exprs))]
    (s/map-spec-impl {:req req :opt opt
                      :req-un req-un :opt-un opt-un
                      :req-keys req-keys :req-specs req-specs
                      :opt-keys opt-keys :opt-specs opt-specs
                      :pred-forms pred-forms :pred-exprs pred-exprs
                      :keys-pred keys-pred :gfn (some-> gen ->spec)})))

(defmethod ->spec* `s/every [[_ pred & {:keys [into kind count max-count min-count distinct gen-max gen] :as opts}]]
  (let [desc (or (::s/describe opts)
                 `(s/every ~pred
                           ~@(c/into [] (comp (remove #(= (key %) ::s/describe))
                                              cat)
                                    opts)))
        resolved-kind (some-> kind resolve*)
        nopts (-> opts
                  (dissoc :gen ::s/describe)
                  (assoc :kind resolved-kind ::s/kind-form kind
                         ::s/describe desc))
        cpreds (cond-> [(or resolved-kind coll?)]
                 count (conj #(= count (bounded-count count %)))

                 (or min-count max-count)
                 (conj #(<= (or min-count 0)
                            (bounded-count (if max-count
                                             (inc max-count)
                                             min-count)
                                           %)
                            (or max-count #?(:clj Integer/MAX_VALUE
                                             :cljs s/MAX_INT))))

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
