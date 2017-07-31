(ns specium.core-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is are]]
            [specium.core :as specium]))

(s/def ::int integer?)
(s/def ::even even?)
(s/def ::str string?)

(deftest ->spec-is-inverse-of-form
  (binding [specium/*eval-fn*
            (fn [_] (assert false "eval shouldn't be called"))]
    (is (= integer? (specium/->spec (s/form (s/spec ::int)))))
    (is (= integer? (specium/->spec (s/form (s/spec integer?)))))

    (are [spec]
        ;; Perhaps there is no way but s/form to check equality
        ;; between two specs
        (= (s/form spec)
           (s/form (specium/->spec (s/form spec))))

      (s/and ::int ::even)
      (s/and integer? even?)

      (s/or :int ::int :str ::str)
      (s/or :int integer? :str string?)

      (s/tuple ::int ::str)
      (s/tuple integer? string?)

      (s/every ::int :kind vector? :count 3 :distinct true)
      (s/every integer? :into #{} :min-count 3 :max-count 5 :gen-max 3)

      (s/every-kv ::int ::str :count 3)
      (s/every-kv integer? string? :min-count 3 :max-count 5 :gen-max 3)

      (s/coll-of ::int :kind vector? :count 3 :distinct true)
      (s/coll-of integer? :into #{} :min-count 3 :max-count 5 :gen-max 3)

      (s/map-of ::int ::str :count 3 :conform-keys true)
      (s/map-of integer? string? :min-count 3 :max-count 5 :gen-max 3)

      (s/* ::int)
      (s/* integer?)

      (s/+ ::int)
      (s/+ integer?)

      (s/? ::int)
      (s/? integer?)

      (s/alt :int ::int :str ::str)
      (s/alt :int integer? :str string?)

      (s/cat :int ::int :str ::str)
      (s/cat :int integer? :str string?)

      ;; Cannot test these cases due to CLJ-2152
      #_(s/& ::int ::even)
      #_(s/& integer? even?)

      )))
